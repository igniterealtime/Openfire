/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.sip.tester.stack;

import org.jivesoftware.openfire.sip.tester.comm.CommunicationsException;
import org.jivesoftware.openfire.sip.tester.comm.CommunicationsListener;
import org.jivesoftware.openfire.sip.tester.Log;
import org.jivesoftware.openfire.sip.tester.security.UserCredentials;
import org.jivesoftware.openfire.sip.tester.security.SipSecurityManager;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TooManyListenersException;

/**
 * Title: SIP Register Tester
 *
 * @author Thiago Rocha Camargo (thiago@jivesoftware.com)
 */

public class SipManager implements SipListener {

    protected static final int RETRY_OBJECT_DELETES = 10;

    protected static final long RETRY_OBJECT_DELETES_AFTER = 500;

    protected static final String DEFAULT_TRANSPORT = "udp";

    protected InetAddress localAddress = null;

    public SipFactory sipFactory;

    public AddressFactory addressFactory;
    public HeaderFactory headerFactory;
    public MessageFactory messageFactory;
    SipStack sipStack;
    public boolean isBusy = true;
    ListeningPoint listeningPoint;
    public SipProvider sipProvider;
    private InetSocketAddress publicIpAddress = null;
    protected String sipStackPath = "gov.nist";
    protected String currentlyUsedURI = null;
    protected String displayName = null;
    protected String transport = null;
    protected String registrarAddress = null;
    protected int localPort = -1;
    protected int registrarPort = -1;
    protected int registrationsExpiration = -1;
    protected String registrarTransport = null;
    private int registerRetries = 0;
    protected String stackAddress = null;
    protected String stackName = "JiveSIP";
    protected FromHeader fromHeader = null;
    protected ContactHeader contactHeader = null;
    protected ArrayList<ViaHeader> viaHeaders = null;
    protected static final int MAX_FORWARDS = 70;
    protected MaxForwardsHeader maxForwardsHeader = null;
    protected long registrationTransaction = -1;
    protected ArrayList<CommunicationsListener> listeners = new ArrayList<CommunicationsListener>();
    protected boolean isStarted = false;
    private RegisterProcessing registerProcessing = null;
    public SipSecurityManager sipSecurityManager = null;

    /**
     * Constructor. It only creates a SipManager instance without initializing
     * the stack itself.
     *
     * @param localAddress localAddress
     */
    public SipManager(InetAddress localAddress) {

        this.localAddress = localAddress;
        registerProcessing = new RegisterProcessing(this);
        sipSecurityManager = new SipSecurityManager();
        registerRetries = 0;
    }

    /**
     * Creates and initializes JAIN SIP objects (factories, stack, listening
     * point and provider). Once this method is called the application is ready
     * to handle (incoming and outgoing) sip messages.
     *
     * @throws CommunicationsException if an axception should occur during the initialization
     *                                 process
     */
    public void start() throws CommunicationsException {

        initProperties();
        SIPConfig.setSystemProperties();
        this.sipFactory = SipFactory.getInstance();
        sipFactory.setPathName(sipStackPath);
        try {
            addressFactory = sipFactory.createAddressFactory();
            headerFactory = sipFactory.createHeaderFactory();
            messageFactory = sipFactory.createMessageFactory();
        }
        catch (PeerUnavailableException ex) {
            Log.error("start", ex);

            throw new CommunicationsException(
                    "Could not create factories!", ex);
        }

        try {
            sipStack = sipFactory.createSipStack(System.getProperties());
            ((SipCommRouter) sipStack.getRouter())
                    .setOutboundProxy(SIPConfig.getOutboundProxy());
        }
        catch (PeerUnavailableException ex) {
            Log.error("start", ex);

            throw new CommunicationsException(
                    "Cannot connect!\n"
                            + "Cannot reach proxy.\nCheck your connection."
                            + "(Syntax:<proxy_address:port/transport>)", ex);
        }

        try {
            boolean successfullyBound = false;
            while (!successfullyBound) {
                try {

                    publicIpAddress = new InetSocketAddress(localAddress, localPort);
                    listeningPoint = sipStack.createListeningPoint(
                            localPort, transport);
                }
                catch (InvalidArgumentException ex) {
                    // choose another port between 1024 and 65000

                    localPort = (int) ((65000 - 1024) * Math.random()) + 1024;
                    try {
                        Thread.sleep(1000);
                    }
                    catch (Exception e) {
                        // Do Nothing
                    }

                    continue;
                }
                successfullyBound = true;
            }
        }
        catch (TransportNotSupportedException ex) {
            throw new CommunicationsException(
                    "Transport "
                            + transport
                            + " is not suppported by the stack!\n Try specifying another"
                            + " transport in Mais property files.\n", ex);
        }
        try {
            sipProvider = sipStack.createSipProvider(listeningPoint);
        }
        catch (ObjectInUseException ex) {
            Log.error("start", ex);

            throw new CommunicationsException(
                    "Could not create factories!\n", ex);
        }
        try {
            sipProvider.addSipListener(this);
        }
        catch (TooManyListenersException exc) {
            throw new CommunicationsException(
                    "Could not register SipManager as a sip listener!", exc);
        }

        sipSecurityManager.setHeaderFactory(headerFactory);
        sipSecurityManager.setTransactionCreator(sipProvider);
        sipSecurityManager.setSipManCallback(this);

        // Make sure prebuilt headers are nulled so that they get reinited
        // if this is a restart
        contactHeader = null;
        fromHeader = null;
        viaHeaders = null;
        maxForwardsHeader = null;
        isStarted = true;

    }

    /**
     * Unregisters listening points, deletes sip providers, and generally
     * prepares the stack for a re-start(). This method is meant to be used when
     * properties are changed and should be reread by the stack.
     *
     * @throws CommunicationsException CommunicationsException
     */
    synchronized public void stop() throws CommunicationsException {
        if (sipStack == null)
            return;

        // Delete SipProvider
        int tries;
        for (tries = 0; tries < SipManager.RETRY_OBJECT_DELETES; tries++) {
            try {
                sipStack.deleteSipProvider(sipProvider);
            }
            catch (ObjectInUseException ex) {

                SipManager.sleep(SipManager.RETRY_OBJECT_DELETES_AFTER);
                continue;
            }
            break;
        }

        if (sipStack == null)
            return;

        if (tries >= SipManager.RETRY_OBJECT_DELETES)
            throw new CommunicationsException(
                    "Failed to delete the sipProvider!");

        if (sipStack == null)
            return;

        // Delete RI ListeningPoint
        for (tries = 0; tries < SipManager.RETRY_OBJECT_DELETES; tries++) {
            try {
                sipStack.deleteListeningPoint(listeningPoint);
            }
            catch (ObjectInUseException ex) {
                // Log.debug("Retrying delete of riListeningPoint!");
                SipManager.sleep(SipManager.RETRY_OBJECT_DELETES_AFTER);
                continue;
            }
            break;
        }

        if (sipStack != null) {

            for (Iterator it = sipStack.getSipProviders(); it.hasNext();) {
                SipProvider element = (SipProvider) it.next();
                try {
                    sipStack.deleteSipProvider(element);
                }
                catch (Exception e) {
                    // Do nothing
                }
            }
        }
        if (tries >= SipManager.RETRY_OBJECT_DELETES)
            throw new CommunicationsException(
                    "Failed to delete a listeningPoint!");

        listeningPoint = null;
        addressFactory = null;
        messageFactory = null;
        headerFactory = null;
        sipStack = null;

        registrarAddress = null;
        viaHeaders = null;
        contactHeader = null;
        fromHeader = null;

    }

    /**
     * Waits during _no_less_ than sleepFor milliseconds. Had to implement it on
     * top of Thread.sleep() to guarantee minimum sleep time.
     *
     * @param sleepFor the number of miliseconds to wait
     */
    protected static void sleep(long sleepFor) {
        long startTime = System.currentTimeMillis();
        long haveBeenSleeping = 0;
        while (haveBeenSleeping < sleepFor) {
            try {
                Thread.sleep(sleepFor - haveBeenSleeping);
            }
            catch (InterruptedException ex) {
                // we-ll have to wait again!
            }
            haveBeenSleeping = (System.currentTimeMillis() - startTime);
        }
    }

    /**
     * @param uri the currentlyUsedURI to set.
     */
    public void setCurrentlyUsedURI(String uri) {
        this.currentlyUsedURI = uri;
    }

    public void register(String publicAddress) {
        try {

            if (publicAddress == null || publicAddress.trim().length() == 0) {
                Log.debug("PUBLIC NOT FOUND!");
                return; // maybe throw an exception?
            }

            if (!publicAddress.trim().toLowerCase().startsWith("sip:")) {
                publicAddress = "sip:" + publicAddress;
            }

            this.currentlyUsedURI = publicAddress;
            registerProcessing.register(registrarAddress, registrarPort,
                    registrarTransport, registrationsExpiration);

        }
        catch (Exception e) {
            Log.error("register", e);
        }
    }

    public void startRegisterProcess(String userName, String authUserName,
                                     String password) throws CommunicationsException {

        try {
            checkIfStarted();

            // Obtain initial credentials

            String realm = SIPConfig.getAuthenticationRealm();
            realm = realm == null ? "" : realm;

            // put the returned user name in the properties file
            // so that it appears as a default one next time user is prompted
            // for pass
            SIPConfig.setUserName(userName);
            SIPConfig.setAuthUserName(authUserName);

            UserCredentials initialCredentials = new UserCredentials();
            initialCredentials.setUserName(userName);
            initialCredentials.setAuthUserName(authUserName);
            initialCredentials.setPassword(password.toCharArray());

            register(initialCredentials.getUserName() + "@" + realm);

            // at this point a simple register request has been sent and the
            // global
            // from header in SipManager has been set to a valid value by the
            // RegisterProcesing
            // class. Use it to extract the valid user name that needs to be
            // cached by
            // the security manager together with the user provided password.

            initialCredentials.setUserName(((SipURI) getFromHeader()
                    .getAddress().getURI()).getUser());

            // JOptionPane.showMessageDialog(null,( (SipURI)
            // getFromHeader().getAddress().getURI()).getUser());

            cacheCredentials(realm, initialCredentials);

        }
        catch (Exception ee) {
            Log.error("startRegisterProcess", ee);
        }
    }

    /**
     * Causes the PresenceAgent object to notify all subscribers of our brand
     * new offline status and the RegisterProcessing object to send a
     * registration request with a 0 "expires" interval to the registrar defined
     * in net.java.mais.sip.REGISTRAR_ADDRESS.
     *
     * @throws CommunicationsException if an exception is thrown by the underlying stack. The
     *                                 exception that caused this CommunicationsException may be
     *                                 extracted with CommunicationsException.getCause()
     */
    public void unregister() throws CommunicationsException {
        try {
            checkIfStarted();
            registerProcessing.unregister();
            fireUnregistered(registrarAddress == null ? "" : registrarAddress);
        }
        catch (Exception e) {
            Log.error("unregister", e);
        }
    }

    private void registrationFailed(RegistrationEvent.Type type) {
        try {
            fireRegistrationFailed(registrarAddress == null ? "" : registrarAddress, type);
        }
        catch (Exception e) {
            Log.error("unregister", e);
        }
    }

    /**
     * Queries the RegisterProcessing object whether the application is
     * registered with a registrar.
     *
     * @return true if the application is registered with a registrar.
     */
    public boolean isRegistered() {
        return (registerProcessing != null && registerProcessing.isRegistered());
    }

    /**
     * Determines whether the SipManager was started.
     *
     * @return true if the SipManager was started.
     */
    public boolean isStarted() {
        return isStarted;
    }

    /**
     * Sends a NOT_IMPLEMENTED response through the specified transaction.
     *
     * @param serverTransaction the transaction to send the response through.
     * @param request           the request that is being answered.
     */
    void sendNotImplemented(ServerTransaction serverTransaction, Request request) {
        Response notImplemented;
        try {
            notImplemented = messageFactory.createResponse(
                    Response.NOT_IMPLEMENTED, request);
            attachToTag(notImplemented, serverTransaction.getDialog());
        }
        catch (ParseException ex) {
            fireCommunicationsError(new CommunicationsException(
                    "Failed to create a NOT_IMPLEMENTED response to a "
                            + request.getMethod() + " request!", ex));
            return;
        }
        try {
            serverTransaction.sendResponse(notImplemented);
        }
        catch (SipException ex) {
            fireCommunicationsError(new CommunicationsException(
                    "Failed to create a NOT_IMPLEMENTED response to a "
                            + request.getMethod() + " request!", ex));
        }
    }

    public void fireCommunicationsError(Throwable throwable) {
    }

    public FromHeader getFromHeader() throws CommunicationsException {
        return this.getFromHeader(false);
    }

    public FromHeader getFromHeader(boolean isNew)
            throws CommunicationsException {
        if (fromHeader != null && !isNew) {
            return fromHeader;
        }
        try {
            SipURI fromURI = (SipURI) addressFactory
                    .createURI(currentlyUsedURI);

            fromURI.setTransportParam(listeningPoint.getTransport());

            fromURI.setPort(listeningPoint.getPort());
            Address fromAddress = addressFactory.createAddress(fromURI);
            if (displayName != null && displayName.trim().length() > 0) {
                fromAddress.setDisplayName(displayName);
            } else {
                fromAddress
                        .setDisplayName(UserCredentials.getUserDisplay());// UserCredentials.getUser());
                // JOptionPane.showMessageDialog(null,currentlyUsedURI);
            }
            fromHeader = headerFactory.createFromHeader(fromAddress,
                    Integer.toString(hashCode()));

        }
        catch (ParseException ex) {
            throw new CommunicationsException(
                    "A ParseException occurred while creating From Header!",
                    ex);
        }
        return fromHeader;
    }

    /**
     * Same as calling getContactHeader(true)
     *
     * @return the result of getContactHeader(true)
     * @throws CommunicationsException if an exception is thrown while calling
     *                                 getContactHeader(false)
     */
    public ContactHeader getContactHeader() throws CommunicationsException {
        return getContactHeader(true);
    }

    /**
     * Same as calling getContactHeader(true).
     *
     * @return the result of calling getContactHeader(true).
     * @throws CommunicationsException if an exception occurs while executing
     *                                 getContactHeader(true).
     */
    ContactHeader getRegistrationContactHeader() throws CommunicationsException {
        return getContactHeader(true);
    }

    /**
     * Initialises SipManager's contactHeader field in accordance with
     * javax.sip.IP_ADDRESS net.java.mais.sip.DISPLAY_NAME
     * net.java.mais.sip.TRANSPORT net.java.mais.sip.PREFERRED_LOCAL_PORT and
     * returns a reference to it.
     *
     * @param useLocalHostAddress specifies whether the SipURI in the contact header should
     *                            contain the value of javax.sip.IP_ADDRESS (true) or that of
     *                            net.java.mais.sip.PUBLIC_ADDRESS (false).
     * @return a reference to SipManager's contactHeader field.
     * @throws CommunicationsException if a ParseException occurs while initially composing the
     *                                 FromHeader.
     */
    public ContactHeader getContactHeader(boolean useLocalHostAddress)
            throws CommunicationsException {
        if (contactHeader != null) {
            return contactHeader;
        }
        try {

            SipURI contactURI;

            if (useLocalHostAddress) {
                contactURI = addressFactory.createSipURI(null,
                        UserCredentials.getUserDisplay()
                                + "@"
                                + publicIpAddress.getAddress()
                                .getHostAddress());
            } else {
                contactURI = (SipURI) addressFactory
                        .createURI(currentlyUsedURI);
            }

            contactURI.setPort(publicIpAddress.getPort());
            Address contactAddress = addressFactory
                    .createAddress(contactURI);
            if (displayName != null && displayName.trim().length() > 0) {
                contactAddress.setDisplayName(displayName);
            }
            contactHeader = headerFactory
                    .createContactHeader(contactAddress);

        }
        catch (ParseException ex) {
            throw new CommunicationsException(
                    "A ParseException occurred while creating From Header!",
                    ex);
        }
        return contactHeader;

    }

    /**
     * Initializes (if null) and returns an ArrayList with a single ViaHeader
     * containing localhost's address. This ArrayList may be used when sending
     * requests.
     *
     * @return ViaHeader-s list to be used when sending requests.
     * @throws CommunicationsException if a ParseException is to occur while initializing the array
     *                                 list.
     */
    public ArrayList getLocalViaHeaders() throws CommunicationsException {
        if (viaHeaders != null) {
            return viaHeaders;
        }
        ListeningPoint lp = sipProvider.getListeningPoint();
        viaHeaders = new ArrayList<ViaHeader>();
        try {

            ViaHeader viaHeader = headerFactory.createViaHeader(SIPConfig
                    .getIPAddress(), lp.getPort(), lp.getTransport(), null);

            viaHeader.setParameter("rport", null);
            viaHeaders.add(viaHeader);

            return viaHeaders;
        }
        catch (ParseException ex) {
            throw new CommunicationsException(
                    "A ParseException occurred while creating Via Headers!");
        }
        catch (InvalidArgumentException ex) {
            throw new CommunicationsException(
                    "Unable to create a via header for port "
                            + lp.getPort(), ex);
        }
    }

    /**
     * Initializes and returns SipManager's maxForwardsHeader field using the
     * value specified by MAX_FORWARDS.
     *
     * @return an instance of a MaxForwardsHeader that can be used when sending
     *         requests
     * @throws CommunicationsException if MAX_FORWARDS has an invalid value.
     */
    public MaxForwardsHeader getMaxForwardsHeader()
            throws CommunicationsException {
        if (maxForwardsHeader != null) {
            return maxForwardsHeader;
        }
        try {
            maxForwardsHeader = headerFactory
                    .createMaxForwardsHeader(SipManager.MAX_FORWARDS);
            return maxForwardsHeader;
        }
        catch (InvalidArgumentException ex) {
            throw new CommunicationsException(
                    "A problem occurred while creating MaxForwardsHeader",
                    ex);
        }
    }

    /**
     * Returns the user used to create the From Header URI.
     *
     * @return the user used to create the From Header URI.
     */
    public String getLocalUser() {
        try {

            return ((SipURI) getFromHeader().getAddress().getURI()).getUser();
        }
        catch (CommunicationsException ex) {
            return "";
        }
    }

    /**
     * Generates a ToTag (the containingDialog's hashCode())and attaches it to
     * response's ToHeader.
     *
     * @param response         the response that is to get the ToTag.
     * @param containingDialog the Dialog instance that is to extract a unique Tag value
     *                         (containingDialog.hashCode())
     */
    public void attachToTag(Response response, Dialog containingDialog) {
        ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
        if (to == null) {
            fireCommunicationsError(new CommunicationsException(
                    "No TO header found in, attaching a to tag is therefore impossible"));
        }
        try {
            if (to.getTag() == null || to.getTag().trim().length() == 0) {
                int toTag = containingDialog != null ? containingDialog
                        .hashCode() : (int) System.currentTimeMillis();

                to.setTag(Integer.toString(toTag));
            }
        }
        catch (ParseException ex) {
            fireCommunicationsError(new CommunicationsException(
                    "Failed to attach a TO tag to an outgoing response"));
        }
    }

    protected void initProperties() {
        try {

            stackAddress = getLocalHostAddress();
            // Add the host address to the properties that will pass the stack
            SIPConfig.setIPAddress(stackAddress);
            SIPConfig.setSystemProperties();

            // ensure IPv6 address compliance
            if (stackAddress.indexOf(':') != stackAddress.lastIndexOf(':')
                    && stackAddress.charAt(0) != '[') {
                stackAddress = '[' + stackAddress.trim() + ']';
            }
            stackName = SIPConfig.getStackName();
            if (stackName == null) {
                stackName = "SIPark@" + Integer.toString(hashCode());
            }

            currentlyUsedURI = SIPConfig.getPublicAddress();
            if (currentlyUsedURI == null) {
                currentlyUsedURI = SIPConfig.getUserName() + "@" + stackAddress;
            }
            if (!currentlyUsedURI.trim().toLowerCase().startsWith("sip:")) {
                currentlyUsedURI = "sip:" + currentlyUsedURI.trim();
            }

            registrarAddress = SIPConfig.getRegistrarAddress();
            try {
                registrarPort = SIPConfig.getRegistrarPort();
            }
            catch (NumberFormatException ex) {
                registrarPort = 5060;
            }
            registrarTransport = SIPConfig.getRegistrarTransport();

            if (registrarTransport == null) {
                registrarTransport = SipManager.DEFAULT_TRANSPORT;
            }
            try {
                registrationsExpiration = SIPConfig.getRegistrationExpiration();
            }
            catch (NumberFormatException ex) {
                registrationsExpiration = 3600;
            }
            sipStackPath = SIPConfig.getStackPath();
            if (sipStackPath == null) {
                sipStackPath = "gov.nist";
            }

            transport = SIPConfig.getTransport();

            if (transport.equals("")) {
                transport = SipManager.DEFAULT_TRANSPORT;
            }
            try {
                localPort = SIPConfig.getLocalPort();
            }
            catch (NumberFormatException exc) {
                localPort = 5060;
            }
            displayName = SIPConfig.getDisplayName();

        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Adds the specified credentials to the security manager's credentials
     * cache so that they get tried next time they're needed.
     *
     * @param realm       the realm these credentials should apply for.
     * @param credentials a set of credentials (username and pass)
     */
    public void cacheCredentials(String realm, UserCredentials credentials) {
        sipSecurityManager.cacheCredentials(realm, credentials);
    }

    /**
     * Adds a CommunicationsListener to SipManager.
     *
     * @param listener The CommunicationsListener to be added.
     */
    public void addCommunicationsListener(CommunicationsListener listener) {
        try {
            listeners.add(listener);
        }
        catch (Exception e) {
            Log.error("addCommunicationsListener", e);
        }
    }

    // ------------ registerred
    void fireRegistered(String address) {
        RegistrationEvent evt = new RegistrationEvent(address);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            (listeners.get(i)).registered(evt);
        }
    } // call received

    // ------------ registering
    void fireRegistering(String address) {
        RegistrationEvent evt = new RegistrationEvent(address);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            (listeners.get(i)).registering(evt);
        }
    } // call received

    // ------------ unregistered
    public void fireUnregistered(String address) {
        RegistrationEvent evt = new RegistrationEvent(address);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            (listeners.get(i)).unregistered(evt);
        }
    }

    void fireRegistrationFailed(String address, RegistrationEvent.Type type) {
        RegistrationEvent evt = new RegistrationEvent(address, type);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            (listeners.get(i)).registrationFailed(evt);
        }
    }

    void fireUnregistering(String address) {
        RegistrationEvent evt = new RegistrationEvent(address);
        for (int i = listeners.size() - 1; i >= 0; i--) {
            (listeners.get(i)).unregistering(evt);
        }
    }

    public void processRequest(RequestEvent requestEvent) {
    }

    // -------------------- PROCESS RESPONSE
    public void processResponse(ResponseEvent responseReceivedEvent) {
        Log.debug("RESPONSE [" + responseReceivedEvent.getResponse().getStatusCode() + "]");

        ClientTransaction clientTransaction = responseReceivedEvent
                .getClientTransaction();
        if (clientTransaction == null) {
            return;
        }
        Response response = responseReceivedEvent.getResponse();
        String method = ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                .getMethod();

        // OK
        if (response.getStatusCode() == Response.OK) {
            // REGISTER
            if (method.equals(Request.REGISTER)) {
                registerProcessing.processOK(clientTransaction, response);
            }
        }
        // NOT_FOUND
        else if (response.getStatusCode() == Response.NOT_FOUND) {
            if (method.equals(Request.REGISTER)) {
                try {
                    unregister();
                    registrationFailed(RegistrationEvent.Type.NotFound);
                }
                catch (CommunicationsException e) {
                    Log.error("NOT FOUND", e);
                }
                Log.debug("REGISTER NOT FOUND");
            }
        }
        // NOT_IMPLEMENTED
        else if (response.getStatusCode() == Response.NOT_IMPLEMENTED) {
            if (method.equals(Request.REGISTER)) {
                // Fixed typo issues - Reported by pizarro
                registerProcessing.processNotImplemented(clientTransaction,
                        response);
            }
        }
        // REQUEST_TERMINATED
        // 401 UNAUTHORIZED
        else if (response.getStatusCode() == Response.UNAUTHORIZED
                || response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED) {
            if (method.equals(Request.REGISTER)) {
                CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
                if (cseq.getSequenceNumber() < 2)
                    registerProcessing.processAuthenticationChallenge(
                            clientTransaction, response);
                else
                    registrationFailed(RegistrationEvent.Type.WrongPass);
            }
        }
        // 403 Wrong Authorization user for this account
        else if(response.getStatusCode() == Response.FORBIDDEN){
            registrationFailed(RegistrationEvent.Type.Forbidden);
        }
    } // process response

    public void processTimeout(TimeoutEvent timeoutEvent) {

    }

    String getLocalHostAddress() {
        return localAddress.getHostAddress();
    }

    protected void checkIfStarted() throws CommunicationsException {
        if (!isStarted) {

            throw new CommunicationsException(
                    "The underlying SIP Stack had not been"
                            + "properly initialised! Impossible to continue");
        }
    }

    public static void main(String args[]) {

        SIPConfig.setRegistrarAddress("apollo");
        SIPConfig.setAuthenticationRealm("apollo");
        SIPConfig.setDefaultDomain("apollo");

        InetAddress address = null;
        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        SipManager sipManager = new SipManager(address);

        try {
            sipManager.start();
        } catch (CommunicationsException e) {
            e.printStackTrace();
        }

        try {
            sipManager.startRegisterProcess("7512", "7512", "7512");
        } catch (CommunicationsException e) {
            e.printStackTrace();
        }

        try {
            sipManager.unregister();
        } catch (CommunicationsException e) {
            e.printStackTrace();
        }

    }

}
