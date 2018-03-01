/* ====================================================================
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2000 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * Portions of this software are based upon public domain software
 * originally written at the National Center for Supercomputing Applications,
 * University of Illinois, Urbana-Champaign.
 *
 * Copyright 2007 Sun Microsystems, Inc.
 */
package com.sun.voip.server;

import java.io.IOException;
import java.text.*;
import java.util.*;
import javax.sip.*;
import javax.sip.address.*;
import javax.sip.header.*;
import javax.sip.message.*;

import com.sun.voip.Logger;
import org.voicebridge.*;

/**
 * <p>Title: SIP COMMUNICATOR-1.1</p>
 * <p>Description: JAIN-SIP-1.1 Audio/Video Phone Application</p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Organisation: LSIIT Laboratory (http://lsiit.u-strasbg.fr)</p>
 * <p>Network Research Team (http://www-r2.u-strasbg.fr))</p>
 * <p>Louis Pasteur University - Strasbourg - France</p>
 * @author Emil Ivov
 * @version 1.1
 */
class RegisterProcessing implements SipListener {
    private Request registerRequest = null;
    private boolean isRegistered = false;

    private Timer reRegisterTimer = new Timer();

    private String registrar;
    private String address;
    private ProxyCredentials proxyCredentials;

    private String sipCallId;

    private SipServer.SipServerCallback sipServerCallback;

    int registrarPort = 5060;

    int expires = 120;

    private HeaderFactory headerFactory = SipServer.getHeaderFactory();
    private AddressFactory addressFactory = SipServer.getAddressFactory();
    private MessageFactory messageFactory = SipServer.getMessageFactory();
    private SipProvider sipProvider = SipServer.getSipProvider();

    public RegisterProcessing(String address, String registrar, ProxyCredentials proxyCredentials)
    {
        Logger.println("Start registering...." + registrar);

        this.registrar = registrar;
        this.proxyCredentials = proxyCredentials;
        this.address = address;

        sipServerCallback = SipServer.getSipServerCallback();

        try {
            register();
        } catch (IOException e) {
            Logger.println(e.getMessage());
        }
    }

    public void processRequest(RequestEvent requestReceivedEvent) {
    Logger.println("Request ignored:  "
        + requestReceivedEvent.getRequest());
    }

    public void processResponse(ResponseEvent responseReceivedEvent) {

        //Logger.println("Registering response...." + sipCallId);

        Response response = (Response)responseReceivedEvent.getResponse();
        int statusCode = response.getStatusCode();
        String method = ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod();

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Got response " + response);
        }

        if (statusCode == Response.OK) {
                isRegistered = true;

            Logger.println("Voice bridge successfully registered with "	+ registrar + " for " + proxyCredentials.getXmppUserName());
            Application.registerNotification("Registered", proxyCredentials);

            sipServerCallback.removeSipListener(sipCallId);

        } else if (statusCode == Response.UNAUTHORIZED || statusCode == Response.PROXY_AUTHENTICATION_REQUIRED) {

            if (method.equals(Request.REGISTER))
            {
                CSeqHeader cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

                if (cseq.getSequenceNumber() < 2) {

                    ClientTransaction regTrans = SipServer.handleChallenge(response, responseReceivedEvent.getClientTransaction(), proxyCredentials);

                    if (regTrans != null)
                    {
                        try {
                            regTrans.sendRequest();

                        } catch (Exception e) {

                            Logger.println("Registration failed, cannot send transaction " + e);
                            Application.registerNotification("RegistrationFailed", proxyCredentials);
                        }

                    } else {
                        Logger.println("Registration failed, cannot create transaction");
                        Application.registerNotification("RegistrationFailed", proxyCredentials);
                    }

                } else {
                    Logger.println("Registration failed " + responseReceivedEvent);
                    Application.registerNotification("RegistrationFailed", proxyCredentials);
                }
            }

        } else {
            Logger.println("Unrecognized response:  " + response);
        }

    }

    public void processTimeout(TimeoutEvent timeoutEvent) {
    Logger.println("Timeout trying to register with " + registrar);
        sipServerCallback.removeSipListener(sipCallId);
    }

    public void processDialogTerminated(DialogTerminatedEvent dte) {
        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("processDialogTerminated called");
        }
        sipServerCallback.removeSipListener(sipCallId);
    }

    public void  processTransactionTerminated(TransactionTerminatedEvent tte) {
        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("processTransactionTerminated called");
        }
        sipServerCallback.removeSipListener(sipCallId);
    }

    public void  processIOException(IOExceptionEvent ioee) {
        if (Logger.logLevel >= Logger.LOG_SIP) {
            Logger.println("processTransactionTerminated called");
        }
        sipServerCallback.removeSipListener(sipCallId);
    }

    private void register() throws IOException
    {
        Logger.println("Registering with " + registrar);

        Application.registerNotification("Registering", proxyCredentials);

        FromHeader fromHeader = getFromHeader();

        Address fromAddress = fromHeader.getAddress();

        //Request URI
        SipURI requestURI = null;
        try {
            requestURI = addressFactory.createSipURI(null, registrar);

    } catch (ParseException e) {
            throw new IOException("Bad registrar address:" + registrar + " " + e.getMessage());
        }
        //requestURI.setPort(registrarPort);


        try {
            requestURI.setTransportParam(
        sipProvider.getListeningPoint().getTransport());
        } catch (ParseException e) {
            throw new IOException(sipProvider.getListeningPoint().getTransport()
        + " is not a valid transport! " + e.getMessage());
        }
        //Call ID Header
        CallIdHeader callIdHeader = sipProvider.getNewCallId();


        //CSeq Header
        CSeqHeader cSeqHeader = null;
        try {
            cSeqHeader = headerFactory.createCSeqHeader(1, Request.REGISTER);
        } catch (ParseException e) {
            //Should never happen
            throw new IOException("Corrupt Sip Stack " + e.getMessage());
        } catch (InvalidArgumentException e) {
            //Should never happen
            throw new IOException("The application is corrupt "  );
        }

        //To Header
        ToHeader toHeader = null;
        try {
            String proxyWorkAround =
                System.getProperty("com.sun.mc.softphone.REGISTRAR_WORKAROUND");

            if (proxyWorkAround != null &&
                    proxyWorkAround.toUpperCase().equals("TRUE")) {

                SipURI toURI = (SipURI)(requestURI.clone());
                toURI.setUser(System.getProperty("user.name"));

                toHeader = headerFactory.createToHeader(
            addressFactory.createAddress(toURI), null);
            } else {
                toHeader = headerFactory.createToHeader(fromAddress, null);
            }
        } catch (ParseException e) {
            throw new IOException("Could not create a To header for address:"
                + fromHeader.getAddress() + " " + e.getMessage());
        }



        //Via Headers
        ArrayList viaHeaders = getLocalViaHeaders();
        //MaxForwardsHeader
        MaxForwardsHeader maxForwardsHeader = getMaxForwardsHeader();
    //Request
        Request request = null;
        try {
            request = messageFactory.createRequest(requestURI,
                Request.REGISTER,
                callIdHeader,
                cSeqHeader, fromHeader, toHeader,
                viaHeaders,
                maxForwardsHeader);
        } catch (ParseException e) {
            throw new IOException(
        "Could not create the register request! " + e.getMessage());
    }

        //Expires Header
        ExpiresHeader expHeader = null;

        for (int retry = 0; retry < 2; retry++) {
            try {
                expHeader = headerFactory.createExpiresHeader(
                    expires);
            } catch (InvalidArgumentException e) {
                if (retry == 0) {
                    continue;
                }
                throw new IOException(
                    "Invalid registrations expiration parameter - "
                    + expires + " " + e.getMessage());
            }
        }

        request.addHeader(expHeader);
        //Contact Header should contain IP - bug report - Eero Vaarnas
        ContactHeader contactHeader = getRegistrationContactHeader();
    request.addHeader(contactHeader);

    try {
        SipURI routeURI = (SipURI) addressFactory.createURI("sip:" + proxyCredentials.getProxy() + ";lr");
        RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(routeURI));
        request.addHeader(routeHeader);

    } catch (Exception e) {

        Logger.error("Creating registration route error " + e);
    }

        //Transaction
        ClientTransaction regTrans = null;
        try {
            regTrans = sipProvider.getNewClientTransaction(request);
        } catch (TransactionUnavailableException e) {
            throw new IOException("Could not create a register transaction!\n"
                + "Check that the Registrar address is correct! "
        + e.getMessage());
        }



        try {
        sipCallId = callIdHeader.getCallId();
        sipServerCallback.addSipListener(sipCallId, this);
        registerRequest = request;
            regTrans.sendRequest();

        if (Logger.logLevel >= Logger.LOG_MOREINFO) {
            Logger.println("Sent register request " + registerRequest);
        }


        if (expires > 0) {
                scheduleReRegistration();
        }
        } catch (Exception e) {
            //we sometimes get a null pointer exception here so catch them all
        throw new IOException("Could not send out the register request! "
        + e.getMessage());
        }


        this.registerRequest = request;
    }

    public void unregister() throws IOException
    {
        if (!isRegistered) {
            return;
        }

        cancelPendingRegistrations();
        isRegistered = false;

        if (this.registerRequest == null) {
            Logger.println("Couldn't find the initial register request");
            throw new IOException("Couldn't find the initial register request");
        }

        Request unregisterRequest = (Request) registerRequest.clone();

        try {
            unregisterRequest.getExpires().setExpires(0);
            CSeqHeader cSeqHeader =
                (CSeqHeader)unregisterRequest.getHeader(CSeqHeader.NAME);
            //[issue 1] - increment registration cseq number
            //reported by - Roberto Tealdi <roby.tea@tin.it>
            cSeqHeader.setSequenceNumber(cSeqHeader.getSequenceNumber()+1);
        } catch (InvalidArgumentException e) {
            Logger.println("Unable to set Expires Header " + e.getMessage());
        return;
        }

        ClientTransaction unregisterTransaction = null;

        try {
            unregisterTransaction = sipProvider.getNewClientTransaction(
        unregisterRequest);
        } catch (TransactionUnavailableException e) {
            throw new IOException("Unable to create a unregister transaction "
        + e.getMessage());
        }
        try {
            unregisterTransaction.sendRequest();
        } catch (SipException e) {
            Logger.println("Faied to send unregister request "
        + e.getMessage());
        return;
        }

        Application.registerNotification("Unregistering", proxyCredentials);
    }

    public boolean isRegistered() {
        return isRegistered;
    }

    private FromHeader fromHeader;

    private FromHeader getFromHeader() throws IOException {

    if (fromHeader != null) {
        return fromHeader;
        }

    try {
        SipURI fromURI = (SipURI) addressFactory.createURI("sip:" + proxyCredentials.getUserName() + "@" + registrar);

            fromURI.setTransportParam(sipProvider.getListeningPoint().getTransport());

            fromURI.setPort(sipProvider.getListeningPoint().getPort());

            Address fromAddress = addressFactory.createAddress(fromURI);

            fromAddress.setDisplayName(proxyCredentials.getUserDisplay());

        fromHeader = headerFactory.createFromHeader(fromAddress, Integer.toString(hashCode()));

        } catch (ParseException e) {
            throw new IOException(
        "A ParseException occurred while creating From Header! "
        + e.getMessage());
        }

    return fromHeader;
    }

    private ArrayList viaHeaders;

    private ArrayList getLocalViaHeaders() throws IOException {
    /*
         * We can't keep a cached copy because the callers
         * of this method change the viaHeaders.  In particular
         * a branch may be added which causes INVITES to fail.
         */
        if (viaHeaders != null) {
            return viaHeaders;
        }

        ListeningPoint lp = sipProvider.getListeningPoint();
    viaHeaders = new ArrayList();

        try {
        String addr = lp.getIPAddress();

            ViaHeader viaHeader = headerFactory.createViaHeader(
                addr, lp.getPort(), lp.getTransport(), null);

        viaHeader.setRPort();

            viaHeaders.add(viaHeader);
            return viaHeaders;
        } catch (ParseException e) {
            throw new IOException (
        "A ParseException occurred while creating Via Headers! "
        + e.getMessage());
        } catch (InvalidArgumentException e) {
            throw new IOException(
        "Unable to create a via header for port " + lp.getPort()
                    + " " + e.getMessage());
        }
    }

    private static final int  MAX_FORWARDS = 70;
    private MaxForwardsHeader maxForwardsHeader;

    private MaxForwardsHeader getMaxForwardsHeader() throws IOException {
        if (maxForwardsHeader != null) {
        return maxForwardsHeader;
        }

    try {
            maxForwardsHeader =
        headerFactory.createMaxForwardsHeader(MAX_FORWARDS);
            return maxForwardsHeader;
        } catch (InvalidArgumentException e) {
                throw new IOException(
                    "A problem occurred while creating MaxForwardsHeader "
            + e.getMessage());
        }
    }

    private ContactHeader contactHeader;

    private ContactHeader getRegistrationContactHeader() throws IOException {
        if (contactHeader != null) {
            return contactHeader;
        }

        try {
            SipURI contactURI = (SipURI) addressFactory.createURI("sip:" + proxyCredentials.getUserName() + "@" + Config.getInstance().getPublicHost());

            contactURI.setTransportParam(
        sipProvider.getListeningPoint().getTransport());
        contactURI.setPort(sipProvider.getListeningPoint().getPort());
            Address contactAddress = addressFactory.createAddress(contactURI);
            contactAddress.setDisplayName(proxyCredentials.getUserDisplay());
            contactHeader = headerFactory.createContactHeader(contactAddress);
            return contactHeader;
    } catch (ParseException e) {
             throw new IOException(
                    "A ParseException occurred while creating From Header! "
        + " " + e.getMessage());
        }
    }

class ReRegisterTask extends TimerTask {

        public ReRegisterTask() {
        }

        public void run() {
            try {
                if (isRegistered()) {
                    register();
        }
            } catch (IOException e) {
                Logger.println("Failed to reRegister " + e.getMessage());
            }
        }
    }

    private void cancelPendingRegistrations() {
        reRegisterTimer.cancel();
        reRegisterTimer = null;

        reRegisterTimer = new Timer();
    }

    private void scheduleReRegistration() {
        ReRegisterTask reRegisterTask = new ReRegisterTask();

    //java.util.Timer thinks in miliseconds
        //bug report and fix by Willem Romijn (romijn at lucent.com)
        reRegisterTimer.schedule(reRegisterTask, expires * 1000);
    }
}
