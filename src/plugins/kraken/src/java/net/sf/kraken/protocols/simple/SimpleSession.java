/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.protocols.simple;

import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.TooManyListenersException;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.pseudoroster.PseudoRoster;
import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.pseudoroster.PseudoRosterManager;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.ChatStateType;
import net.sf.kraken.type.ConnectionFailureReason;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * A gateway session to a SIMPLE IM server.
 * @author Patrick Siu
 * @author Daniel Henninger
 */
public class SimpleSession extends TransportSession<SimpleBuddy> {

    static Logger Log = Logger.getLogger(SimpleSession.class);

    private SipFactory sipFactory = null;
	
	private String sipHost;
	private int    sipPort;
//	private String username;
	private String sessionId = null;
	private long   seqNum;
	
	private ListeningPoint tcp = null;
	private ListeningPoint udp = null;
	private SipProvider tcpSipProvider;
	private SipProvider udpSipProvider;
	
	private MessageFactory        messageFactory;
	private AddressFactory        addressFactory;
	private HeaderFactory         headerFactory;
	private SipStack              sipStack;
	private SimpleListener myListener;

    /**
     * Our pseudo roster.
     *
     * No server side buddy list, so we track it all here.
     */
    private PseudoRoster pseudoRoster;

    /**
	 * Constructor utilizing the greater constructor of the super class.
     *
     * This process initializes the session by building (or retrieving) a SIP Stack and adding listeners to it.
     *
     * @param registration The registration information to use during login.
     * @param jid The JID associated with this session.
     * @param transport The transport that created this session.
     * @param priority Priority of this session.
	 */
	@SuppressWarnings("unchecked")
    public SimpleSession(Registration registration, JID jid, BaseTransport transport, Integer priority) {
		super(registration, jid, transport, priority);
		
		// Initialize local variable first!
        pseudoRoster = PseudoRosterManager.getInstance().getPseudoRoster(registration);
        for (String contact : pseudoRoster.getContacts()) {
            getBuddyManager().storeBuddy(new SimpleBuddy(getBuddyManager(), contact, pseudoRoster.getItem(contact)));
        }

		seqNum   = 1L;
		
		sipHost = JiveGlobals.getProperty("plugin.gateway.simple.connecthost", "");
		sipPort = ((SimpleTransport) transport).generateListenerPort();
		
		// Initialize the SipFactory
		sipFactory = SipFactory.getInstance();
		if (sipFactory.getPathName() == null || !sipFactory.getPathName().equals("gov.nist"))
			sipFactory.setPathName("gov.nist");
		
		// Initialize the SipStack for this session
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", jid.getNode());
		//properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");

        try {
			String localIP = InetAddress.getLocalHost().getHostAddress();
			
			sipStack       = sipFactory.createSipStack(properties);
			headerFactory  = sipFactory.createHeaderFactory();
			addressFactory = sipFactory.createAddressFactory();
			messageFactory = sipFactory.createMessageFactory();
			
			Iterator listeningPointIterator = sipStack.getListeningPoints();
			while (listeningPointIterator.hasNext()) {
				ListeningPoint listeningPoint = (ListeningPoint) listeningPointIterator.next();
				
				if (listeningPoint.getIPAddress() != null &&
				    listeningPoint.getIPAddress().equals(localIP) &&
//				    listeningPoint.getPort() == sipPort &&
				    listeningPoint.getTransport().equals(ListeningPoint.TCP)) {
					tcp = listeningPoint;
					sipPort = tcp.getPort();
				}
				
				if (listeningPoint.getIPAddress() != null &&
				    listeningPoint.getIPAddress().equals(localIP) &&
//				    listeningPoint.getPort() == sipPort &&
				    listeningPoint.getTransport().equals(ListeningPoint.UDP)) {
					udp     = listeningPoint;
					sipPort = udp.getPort();
				}
			}
			
			if (tcp == null) {
				tcp = sipStack.createListeningPoint(localIP, sipPort, ListeningPoint.TCP);
				sipPort = tcp.getPort();
			}
			if (udp == null) {
				udp = sipStack.createListeningPoint(localIP, sipPort, ListeningPoint.UDP);
				sipPort = udp.getPort();
			}
			
			Iterator sipProviderIterator = sipStack.getSipProviders();
			while (sipProviderIterator.hasNext()) {
				SipProvider sipProvider = (SipProvider) sipProviderIterator.next();
				
				if (sipProvider.getListeningPoint(ListeningPoint.TCP) != null) {
					tcpSipProvider = sipProvider;
				}
				if (sipProvider.getListeningPoint(ListeningPoint.UDP) != null) {
					udpSipProvider = sipProvider;
				}
			}
			if (tcpSipProvider == null)
				tcpSipProvider = sipStack.createSipProvider(tcp);
			if (udpSipProvider == null)
				udpSipProvider = sipStack.createSipProvider(udp);
		} catch (Exception ex) {
			Log.debug(ex);
		}
		
		try {
			myListener = new SimpleListener(this);
			tcpSipProvider.addSipListener(myListener);
			udpSipProvider.addSipListener(myListener);
		} catch (TooManyListenersException ex) {
			Log.debug(ex);
		}
		
		try {
			sipStack.start();
		} catch (SipException ex) {
			Log.debug(ex);
		}
	}

    /**
     * Retrieves the sip factory.
     *
     * @return Returns the sip factory.
     */
    public SipFactory getSipFactory() {
        return sipFactory;
    }

    /**
     * Retrieves the pseudo roster.
     *
     * @return The pseudo roster.
     */
    public PseudoRoster getPseudoRoster() {
        return pseudoRoster;
    }

    /**
	 * Perform rollback action once the login fails or logout goes on the way.
	 */
//	private void rollback() {
//		if (myListener != null)
//
//	}

    /**
     * @see net.sf.kraken.session.TransportSession#updateStatus(net.sf.kraken.type.PresenceType, String) 
     */
    @Override
    public void updateStatus(PresenceType presenceType, String verboseStatus) {
		Log.debug("SimpleSession(" + getJID().getNode() + ").updateStatus:  Method commenced!");
		
//		SimplePresence simplePresence = ((SimpleTransport) getTransport()).convertJabStatusToSIP(presenceType);
        // TODO: Make this functional
    }

    /**
     * @see net.sf.kraken.session.TransportSession#addContact(org.xmpp.packet.JID, String, java.util.ArrayList)
     */
    @Override
    public void addContact(JID jid, String nickname, ArrayList<String> groups) {
        Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Roster of " + jid.toString() + " locked!");

		String destId     = getTransport().convertJIDToID(jid);

        PseudoRosterItem rosterItem;
        if (pseudoRoster.hasItem(destId)) {
            rosterItem = pseudoRoster.getItem(destId);
            rosterItem.setNickname(nickname);
        }
        else {
            rosterItem = pseudoRoster.createItem(destId, nickname, null);
        }

        getBuddyManager().storeBuddy(new SimpleBuddy(getBuddyManager(), destId, rosterItem));

        Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Starting addContact function for " + destId);
		Request subscribeRequest;
		try {
			subscribeRequest = prepareSubscribeRequest(destId);
			subscribeRequest.addHeader(headerFactory.createExpiresHeader(365 * 24 * 60 * 60));
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Unable to prepare SUBSCRIBE request.", e);
			
			Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Roster of " + jid.toString() + " unlocked!");
			return;
		}
		
		try {
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.ACK));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.BYE));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.CANCEL));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.INFO));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.INVITE));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.MESSAGE));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.NOTIFY));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.OPTIONS));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.REFER));
			subscribeRequest.addHeader(headerFactory.createAllowHeader(Request.SUBSCRIBE));
			subscribeRequest.addHeader(headerFactory.createEventHeader("presence"));
			subscribeRequest.addHeader(headerFactory.createAcceptHeader("application", "pidf+xml"));
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Unable to add a header", e);
		}
		
		try {
			sendRequest(subscribeRequest, ListeningPoint.UDP);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Unable to send request.", e);
			
			Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Roster of " + jid.toString() + " unlocked!");
			return;
		}
		
		Log.debug("SimpleSession(" + jid.getNode() + ").addContact:  Roster of " + jid.toString() + " unlocked!");
	}

    /**
     * @see net.sf.kraken.session.TransportSession#removeContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void removeContact(SimpleBuddy contact) {
//		String nickname = getTransport().convertJIDToID(item.getJid());
//        if (item.getNickname() != null && !item.getNickname().equals("")) {
//            nickname = item.getNickname();
//        }
		Log.debug("SimpleSession(" + jid.getNode() + ").removeContact:  Roster of " + contact.getJID().toString() + " locked!");
		
		JID    destJid    = contact.getJID();
		String destId     = getTransport().convertJIDToID(destJid);
		
		Log.debug("SimpleSession(" +jid.getNode() + ").removeContact:  Starting addContact function for " + destId);
		
//		List<Header> customHeaders = new ArrayList<Header>(13);
//		try {
//            customHeaders.add(headerFactory.createExpiresHeader(0));
//        }
//		catch (Exception e) {
//            // Ignore
//        }
//        try {
//            customHeaders.add(headerFactory.createEventHeader("presence"));
//        }
//		catch (Exception e) {
//            // Ignore
//        }
		
//		prepareRequest(RequestType.SUBSCRIBE, destId, null, null, 1L, 70, customHeaders, null);

        pseudoRoster.removeItem(destId);

        destJid = getTransport().convertIDToJID(destId);
		
		try {
			Log.debug("SimpleSession(" + jid.getNode() + ").removeContact:  Removing contact '" + destJid.toString() + "' from roster...");
            getTransport().removeFromRoster(getJID(), destJid);
			Log.debug("SimpleSession(" + jid.getNode() + ").removeContact:  Contact '" + destJid.toString() + "' removed!");
        }
		catch (Exception ex) {
			Log.debug("SimpleSession(" + jid.getNode() + ").removeContact:  Unable to add contact.", ex);
		}
		
		Log.debug("SimpleSession(" + jid.getNode() + ").removeContact:  Roster of " + contact.getJID().toString() + " unlocked!");
	}

    /**
     * @see net.sf.kraken.session.TransportSession#updateContact(net.sf.kraken.roster.TransportBuddy)
     */
    @Override
    public void updateContact(SimpleBuddy contact) {
		Log.debug("SimpleSession(" + jid.getNode() + ").updateContact:  I was called!");

        JID    destJid    = contact.getJID();
        String destId     = getTransport().convertJIDToID(destJid);

        PseudoRosterItem rosterItem;
        if (pseudoRoster.hasItem(destId)) {
            rosterItem = pseudoRoster.getItem(destId);
            rosterItem.setNickname(contact.getNickname());
        }
        else {
            rosterItem = pseudoRoster.createItem(destId, contact.getNickname(), null);
        }

        try {
            SimpleBuddy simpleBuddy = getBuddyManager().getBuddy(destJid);
            simpleBuddy.pseudoRosterItem = rosterItem;
        }
        catch (NotFoundException e) {
            Log.debug("SIMPLE: Newly added buddy not found in buddy manager: "+destId);
        }
    }
    
    /**
     * @see net.sf.kraken.session.TransportSession#acceptAddContact(JID)
     */
    @Override
    public void acceptAddContact(JID jid) {
        final String userID = getTransport().convertJIDToID(jid);
        Log.debug("SIMPLE: accept-adding is currently not implemented."
                + " Cannot accept-add: " + userID);
        // TODO: Currently unimplemented
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendMessage(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendMessage(JID jid, String message) {
		Log.debug("SimpleSession(" + jid.getNode() + "):  Starting message sending process.");
		ContentTypeHeader contentTypeHeader;
		
		try {
			contentTypeHeader = headerFactory.createContentTypeHeader("text", "plain");
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + jid.getNode() + ").sendMessage:  Unable to initiate ContentType header.", e);
			return;
		}
		Log.debug("SimpleSession(" + jid.getNode() + "):  Finished adding ContentType header.");
		
		MessageContent    content           = new MessageContent(contentTypeHeader, message);
		
		try {
			Request request = prepareMessageRequest(content, getTransport().convertJIDToID(jid));
			sendRequest(request, ListeningPoint.UDP);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + jid.getNode() + ").sendMessage:  Unable to send message.", e);
		}

//		if (!prepareRequest(RequestType.MESSAGE, ((SimpleTransport) transport).convertJIDToID(jid), null, null, 1L, 70, null, content)) {
//			Log.debug("SimpleSession(" + this.jid.getNode() + ").sendMessage:  Unable to send message!");
//		}
	}

    /**
     * @see net.sf.kraken.session.TransportSession#sendChatState(org.xmpp.packet.JID, net.sf.kraken.type.ChatStateType)
     */
    @Override
    public void sendChatState(JID jid, ChatStateType chatState) {
		Log.debug("SimpleSession(" + jid.getNode() + ").sendChatState:  I was called!");
    }

    /**
     * @see net.sf.kraken.session.TransportSession#sendBuzzNotification(org.xmpp.packet.JID, String)
     */
    @Override
    public void sendBuzzNotification(JID jid, String message) {
    }

    /**
     * @see net.sf.kraken.session.TransportSession#updateLegacyAvatar(String, byte[])
     */
    @Override
    public void updateLegacyAvatar(String type, byte[] data) {
    }

    // The following are SimpleSession specific methods
    /**
     * @see net.sf.kraken.session.TransportSession#logIn(net.sf.kraken.type.PresenceType, String)
     */
    @Override
    public void logIn(PresenceType presenceType, String verboseStatus) {
		if (!this.isLoggedIn()) {
			this.setLoginStatus(TransportLoginStatus.LOGGING_IN);
			
			Log.debug("SimpleSession(" + jid.getNode() + ").login:  Start login as " + registration.getUsername() + ".");
			
			Request registerRequest = prepareRegisterRequest();
			
			if (registerRequest.getHeader(CallIdHeader.NAME) == null) {
				Log.debug("SimpleSession(" + getJID().getNode() + ").login:  Unable to create a SIP session ID!!");
				this.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                setFailureStatus(ConnectionFailureReason.UNKNOWN);
                sessionDisconnected("Unable to create SIP session ID!");
				return;
			}
			else {
				sessionId = ((CallIdHeader) registerRequest.getHeader(CallIdHeader.NAME)).getCallId();
			}
			
			try {
				registerRequest.addHeader(headerFactory.createExpiresHeader(365 * 24 * 60 * 60));
			}
			catch (Exception e) {
				Log.debug("SimpleSession(" + jid.getNode() + ").login:  " +
				          "Unable to set the expiry interval, which is essential for a login.", e);
				this.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
				return;
			}
			
			try {
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.ACK));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.BYE));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.CANCEL));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.INFO));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.INVITE));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.MESSAGE));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.NOTIFY));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.OPTIONS));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.REFER));
				registerRequest.addHeader(headerFactory.createAllowHeader(Request.SUBSCRIBE));
			}
			catch (Exception e) {
                // Ignore
            }
			
			try {
				sendRequest(registerRequest, ListeningPoint.UDP);
			}
			catch (Exception e) {
				Log.debug("SimpleSession(" + jid.getNode() + ").login:  Unable to send login packet.", e);
				this.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
                setFailureStatus(ConnectionFailureReason.CAN_NOT_CONNECT);
                sessionDisconnected("Unable to send login packet!");
			}
		}
	}

    /**
     * @see net.sf.kraken.session.TransportSession#logOut()
     */
    @Override
    public void logOut() {
        cleanUp();
        sessionDisconnectedNoReconnect(null);
	}

    /**
     * @see net.sf.kraken.session.TransportSession#cleanUp()
     */
    @Override
    public void cleanUp() {
        Request registerRequest = prepareRegisterRequest();

        // Lots of logout work here...
        Log.debug("SimpleSession(" + getJID().getNode() + ").logout:  Preparing logout packet...");

        try {
            registerRequest.addHeader(headerFactory.createExpiresHeader(0));
            sendRequest(registerRequest, ListeningPoint.UDP);
        }
        catch (Exception e) {
            Log.debug("SimpleSession(" + jid.getNode() + ").login:  Unable to logout.", e);
        }

//		List<Header> customHeaders = new ArrayList<Header>(1);
//		try { customHeaders.add(headerFactory.createExpiresHeader(0)); }
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + getJID().getNode() + ").logout:  " +
//			          "Unable to set the expiry interval, which is essential for a logout.", e);
//			return;
//		}

//		String myUsername = registration.getUsername();
//		if (myUsername.indexOf("sip:") < 0) myUsername = "sip:" + myUsername;
//		if (myUsername.indexOf("@")    < 0) myUsername = myUsername + "@" + sipHost;
//
//		prepareRequest(RequestType.REGISTER, myUsername, null, this.sessionId, seqNum++, 70, customHeaders, null);
    }

    public void sipUserLoggedIn() {
		setLoginStatus(TransportLoginStatus.LOGGED_IN);
        setPresence(PresenceType.available);

        try {
            getTransport().syncLegacyRoster(getJID(), getBuddyManager().getBuddies());
            getBuddyManager().activate();
        }
        catch (UserNotFoundException e) {
            Log.debug("SIMPLE: Unable to find user whose roster we're trying to sync: "+getJID());
        }
    }
	
	public void sipUserLoggedOut() {
		// TODO: If anybody subscribed me, send NOTIFY or subscription termination
		
		setLoginStatus(TransportLoginStatus.LOGGED_OUT);
	}

    public void removeStack() {
		Log.debug("SimpleSession for " + jid.getNode() + " is going to shut down!");
		
		tcpSipProvider.removeSipListener(myListener);
		udpSipProvider.removeSipListener(myListener);
		
		myListener = null;
		
		Log.debug("SimpleSession for " + jid.getNode() + " has their listeners removed.");
		
		try {
			sipStack.deleteSipProvider(tcpSipProvider);
			sipStack.deleteSipProvider(udpSipProvider);
			Log.debug("SimpleSession(" + jid.getNode() + ").shutdown:  SIP Providers deleted.");
			
			sipStack.deleteListeningPoint(tcp);
			sipStack.deleteListeningPoint(udp);
			Log.debug("SimpleSession(" + jid.getNode() + ").shutdown:  Listening points deleted.");
		}
		catch (Exception ex) {
			Log.debug("SimpleSession for " + jid.getNode() + " is unable to gracefully shut down.", ex);
		}
		
		sipStack.stop();
		sipStack = null;
	}
	
	@Override
    public void finalize() {
        try {
            super.finalize();
        }
        catch (Throwable e) {
            // Hrm.
        }
        Log.debug("SimpleSession for " + jid.getNode() + ":  Finalize function initialized!");

		if (this.getLoginStatus().equals(TransportLoginStatus.LOGGED_IN)) {
			logOut();
		}
	}
	
	
	///// Simple specific functions
	
	/**
	 * An inner enum encapsulating SIP Request types.
	 */
	private enum RequestType {
		ACK      (Request.ACK),
		BYE      (Request.BYE),
		CANCEL   (Request.CANCEL),
		INFO     (Request.INFO),
		INVITE   (Request.INVITE),
		MESSAGE  (Request.MESSAGE),
		NOTIFY   (Request.NOTIFY),
		OPTIONS  (Request.OPTIONS),
		PRACK    (Request.PRACK),
		PUBLISH  (Request.PUBLISH),
		REFER    (Request.REFER),
		REGISTER (Request.REGISTER),
		SUBSCRIBE(Request.SUBSCRIBE),
		UPDATE   (Request.UPDATE);
		
		private String sipReqType;
		
		RequestType(String sipReqType) {
			this.sipReqType = sipReqType;
		}
		
		@Override
        public String toString() {
			return sipReqType;
		}
	}
	
	/**
	 * An inner class representing the content of a SIP message.
	 */
	private class MessageContent {
		private ContentTypeHeader contentTypeHeader;
		private String            content;
		
		MessageContent(ContentTypeHeader contentTypeHeader, String content) {
			this.contentTypeHeader = contentTypeHeader;
			this.content           = content;
		}
		
		@SuppressWarnings("unused")
        public ContentTypeHeader getContentTypeHeader() {
			return this.contentTypeHeader;
		}
		
		@SuppressWarnings("unused")
        public String getContent() {
			return this.content;
		}
	}
	
	/**
	 * Sends a request with the specified request and transport.
	 * @param request   The request packet.
	 * @param transport The transport protocol used.
     * @throws javax.sip.SipException Unable to communicate.
	 */
	private void sendRequest(Request request, String transport) throws SipException {
		sendRequest(request, transport, null);
	}
	
	/**
	 * Sends a request with the specified request and transport.
	 * @param request   The request packet.
	 * @param transport The transport protocol used.
	 * @param dialog    The dialog for a persistent transaction.
	 *                  Leave it <code>null</code> if no dialog is associated with this request.
     * @throws javax.sip.SipException Unable to communicate.
	 */
	@SuppressWarnings("unchecked")
    private void sendRequest(Request request, String transport, Dialog dialog) throws SipException {
		for (Iterator sipProviders = sipStack.getSipProviders(); sipProviders.hasNext(); ) {
			SipProvider provider = (SipProvider) sipProviders.next();
			if (provider.getListeningPoint(transport) != null) {
				Log.debug("Sending packet:  \n" + request.toString() + "\n========\n");
				
				ClientTransaction transaction = provider.getNewClientTransaction(request);
				if (dialog != null)
					dialog.sendRequest(transaction);
				else
					transaction.sendRequest();
				
				return;
			}
		}
		
		Log.debug("SimpleSession(" + this.jid.getNode() + "):  No SipProvider found for that transport!");
	}
	
	/**
     * @param requestType Type of request
	 * @param destUri    The SipURI for the destination.  Leave <code>null</code> if a loopback request (e.g. REGISTER) is being made.
	 * @param toTag      The tag for to header.  Can leave null.
	 * @param requestUri The Request URI to set in the message.  Leave null if the default destination SipURI should be used.
     * @param callId     ID of call
     * @param seqNum     Sequence number
     * @return Prepared request
	 */
	private Request prepareRequest(RequestType requestType, SipURI destUri, String toTag, SipURI requestUri, String callId, long seqNum) {
		Request request  = null;
		
		String  myXMPPUsername = this.jid.getNode();
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing request packet of type '" + requestType + "'");
		
		try {
			// Prepare request packet first
			request = messageFactory.createRequest(null);
			request.setMethod(requestType.toString());
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing request.", e);
		}
		
		// Prepare "From" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"From\" header...");
		String mySipUsername = registration.getUsername();
		try {
			SipURI     fromUri         = addressFactory.createSipURI(mySipUsername, sipHost);
			Address    fromNameAddress = addressFactory.createAddress(fromUri);
			fromNameAddress.setDisplayName(mySipUsername);
			
			FromHeader fromHeader      = headerFactory.createFromHeader(fromNameAddress, getTag());
			
			// Use "set" because this header is mandatory.
			request.setHeader(fromHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing FromHeader.", e);
			
			return null;
		}
		
		// Prepare "To" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"To\" header...");
		try {
			if (destUri == null)
				destUri = addressFactory.createSipURI(mySipUsername, sipHost);
			
			Address  toNameAddress = addressFactory.createAddress(destUri);
			ToHeader toHeader      = headerFactory.createToHeader(toNameAddress, toTag);
			
			// Use "set" because this header is mandatory.
			request.setHeader(toHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing ToHeader.", e);
			
			return null;
		}
		
		// Prepare "Via" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"Via\" header...");
		try {
			ViaHeader viaHeader = headerFactory.createViaHeader(InetAddress.getLocalHost().getHostAddress(), sipPort, ListeningPoint.UDP, null);
			
			// Use "set" because this header is mandatory.
			request.setHeader(viaHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing ViaHeader.", e);
			
			return null;
		}
		
		// Prepare "CallId" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"CallId\" header...");
		CallIdHeader callIdHeader;
		try {
			if (callId != null)
				callIdHeader = headerFactory.createCallIdHeader(callId);
			else
				callIdHeader = udpSipProvider.getNewCallId();
			
			// Use "set" because this header is mandatory.
			request.setHeader(callIdHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing CallIdHeader.", e);
			
			return null;
		}
		
		// Prepare "CSeq" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"CSeq\" header...");
		try {
			CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(seqNum, requestType.toString());
			
			// Use "set" because this header is mandatory.
			request.setHeader(cSeqHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing CSeqHeader.", e);
			
			return null;
		}
		
		// Prepare "MaxForwards" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"MaxForwards\" header...");
		try {
			MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
			
			// Use "set" because this header is mandatory.
			request.setHeader(maxForwardsHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing MaxForwardsHeader.", e);
			
			return null;
		}
		
		// Setting Request URI
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  setting request URI...");
		try {
			if (requestUri == null) {
				requestUri = (SipURI) destUri.clone();
				requestUri.setTransportParam(ListeningPoint.UDP);
			}
			request.setRequestURI(requestUri);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when setting request URI.", e);
			
			return null;
		}
		
		// Add "Contact" header
		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"Contact\" header...");
		try {
			SipURI contactURI = addressFactory.createSipURI(mySipUsername, InetAddress.getLocalHost().getHostAddress());
			contactURI.setPort(sipPort);
			
			Address contactAddress      = addressFactory.createAddress(contactURI);
			
			contactAddress.setDisplayName(mySipUsername);
			
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			request.setHeader(contactHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when adding ContactHeader.", e);
			
			return null;
		}
		
		return request;
	}

//	/**
//	 * Prepares a Request packet.
//	 * <br><br>
//	 * The "From", "To", "Via", "Contact", "CSeq", "MaxForwards" and "CallId" headers,
//	 * as well as the content (if provided) are prepared in the method.
//	 * <br>
//	 * Additional headers should be provided as customer headers.  See the "headers" parameter for further details.
//	 * @param requestType An inner request type enum
//	 * @param destination The recipient of this request
//	 * @param toTag       Additional tag code of the destination.  Can usually leave it <code>null</code>.
//	 * @param callId      A String representing an ongoing SIP CallID.  Leave it <code>null</code> if a new CallID should be generated.
//	 * @param seqNum      A sequence number for an ongoing session.
//	 * @param maxForward  Maximum times of forwarding allowed for this packet.
//	 * @param headers     Additional headers that have to be added.  Leave <code>null</code> if no custom header is to be added.
//	 * @param content     An object containing the content of the message, as well as the header defining the content type.
//	 *                    Can leave <code>null</code> if no content is to be specified.
//	 * @see   net.sf.kraken.protocols.simple.SimpleSession.RequestType
//	 */
//	private boolean prepareRequest(
//			RequestType    requestType,
//			String         destination,
//			String         toTag,
//			String         callId,
//			long           seqNum,
//			int            maxForward,
//			List<Header>   headers,
//			MessageContent content) {
//		String myXMPPUsername      = this.jid.getNode();
//		String mySipUsername = registration.getUsername();
//
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing request packet of type '" + requestType + "'");
//
//		// Prepare "From" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"From\" header...");
//		FromHeader fromHeader = null;
//		try {
//			SipURI     fromUri         = addressFactory.createSipURI(mySipUsername, sipHost);
//			Address    fromNameAddress = addressFactory.createAddress(fromUri);
//			fromNameAddress.setDisplayName(mySipUsername);
//
//			fromHeader = headerFactory.createFromHeader(fromNameAddress, getTag());
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing FromHeader.", e);
//
//			return false;
//		}
//
//		// Prepare "To" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"To\" header...");
//		ToHeader toHeader = null;
//
//		String destUsername = "";
//		String destAddress  = "";
//
//		// Code modification to allow address be input without specifying "sip:"
//		if (destination.startsWith("sip:")) {
//			destination = destination.substring("sip:".length());
//		}
//
//		if (destination.indexOf("@") > 0) {
//			destUsername = destination.substring(destination.indexOf(":") + 1, destination.indexOf("@"));
//			destAddress  = destination.substring(destination.indexOf("@") + 1);
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  destUsername = '" + destUsername + "';  destAddress = '" + destAddress + "'");
//		}
//		else {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing ToHeader.",
//			          new IllegalArgumentException("The destination specified is not a valid SIP address"));
//
//			return false;
//		}
//
//		try {
//			SipURI   toAddress     = addressFactory.createSipURI(destUsername, destAddress);
//			Address  toNameAddress = addressFactory.createAddress(toAddress);
//
//			String displayName = destUsername;
//			try {
//				RosterItem ri = getRoster().getRosterItem(this.getTransport().convertIDToJID(destination));
//				if (ri != null) displayName = ri.getNickname();
//			}
//			catch (Exception e) {} // Ignore the exception.  We don't need to handle it.
//
//			toNameAddress.setDisplayName(displayName);
//
//			toHeader = headerFactory.createToHeader(toNameAddress, toTag);
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing ToHeader.", e);
//
//			return false;
//		}
//
//		// Prepare "Via" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"Via\" header...");
//		ArrayList viaHeaders = new ArrayList();
//		try {
//			ViaHeader viaHeader  = headerFactory.createViaHeader(InetAddress.getLocalHost().getHostAddress(), sipPort, ListeningPoint.UDP, null);
//			viaHeaders.add(viaHeader);
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing ViaHeader.", e);
//
//			return false;
//		}
//
//		// Prepare "CallId" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"CallId\" header...");
//		CallIdHeader callIdHeader = null;
//		try {
//			if (callId != null)
//				callIdHeader = headerFactory.createCallIdHeader(callId);
//			else
//				callIdHeader = udpSipProvider.getNewCallId();
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing CallIdHeader.", e);
//
//			return false;
//		}
//
//		// Prepare "CSeq" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"CSeq\" header...");
//		CSeqHeader cSeqHeader = null;
//		try {
//			cSeqHeader = headerFactory.createCSeqHeader(seqNum, requestType.toString());
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing CSeqHeader.", e);
//
//			return false;
//		}
//
//		// Prepare "MaxForwards" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"MaxForwards\" header...");
//		MaxForwardsHeader maxForwardsHeader = null;
//		try {
//			maxForwardsHeader = headerFactory.createMaxForwardsHeader(maxForward);
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing MaxForwardsHeader.", e);
//
//			return false;
//		}
//
//		// Prepare request URI
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing request URI...");
//		SipURI requestURI = null;
//		try {
//			requestURI = addressFactory.createSipURI(destUsername, destAddress);
//			requestURI.setTransportParam(ListeningPoint.UDP);
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when preparing Request URI.", e);
//
//			return false;
//		}
//
//		// Instantiate Request packet
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Instantiating Request packet...");
//		Request request = null;
//		try {
//			request = messageFactory.createRequest(
//					requestURI, requestType.toString(),
//					callIdHeader, cSeqHeader,
//					fromHeader, toHeader,
//					viaHeaders, maxForwardsHeader
//					);
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when instantiating Request packet.", e);
//
//			return false;
//		}
//
//		// Add custom headers
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Start adding custom headers...");
//		int headerCount = 0;
//		if (headers != null) {
//			headerCount = headers.size();
//			for (ListIterator<Header> headersIterator = headers.listIterator(); headersIterator.hasNext(); ) {
//				Header aHeader = headersIterator.next();
//				try {
//					request.addHeader(aHeader);
//				}
//				catch (Exception e) {
//					Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when adding a " +
//					          aHeader.getClass().toString() + " to the request packet.", e);
//					headerCount--;
//				}
//			}
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Finished adding custom headers.  " +
//			          headerCount + " of " + headers.size() + " headers successfully added.");
//		}
//		else {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  The custom headers input is null.  No custom headers to add.");
//		}
//
//		// Add "Contact" header
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Preparing \"Contact\" header...");
//		try {
//			SipURI contactURI;
//			if (requestType.equals(RequestType.NOTIFY))
//				contactURI = addressFactory.createSipURI(null, InetAddress.getLocalHost().getHostAddress());
//			else
//				contactURI = addressFactory.createSipURI(mySipUsername, InetAddress.getLocalHost().getHostAddress());
//			contactURI.setPort(sipPort);
//
//			Address contactAddress      = addressFactory.createAddress(contactURI);
//
//			if (!requestType.equals(RequestType.NOTIFY))
//				contactAddress.setDisplayName(mySipUsername);
//
//			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
//			request.addHeader(contactHeader);
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when adding ContactHeader.", e);
//
//			return false;
//		}
//
//		if (content != null) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Content is specified.  Adding content...");
//			try {
//				request.setContent(content.getContent(), content.getContentTypeHeader());
//			}
//			catch (Exception e) {
//				Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when adding content to the request packet.", e);
//				// Just tell, then continue the request!
//			}
//		}
//
//		// Send the request
//		Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Sending Request packet:  \n" + request.toString());
//		try {
//			ClientTransaction clientTransaction = udpSipProvider.getNewClientTransaction(request);
//			clientTransaction.sendRequest();
//		}
//		catch (Exception e) {
//			Log.debug("SimpleSession(" + myXMPPUsername + ").prepareRequest:  Exception occured when sending Request packet.", e);
//			return false;
//		}
//
//		return true;
//	}

	private Request prepareRegisterRequest() {
		return prepareRequest(RequestType.REGISTER, null, null, null, sessionId, seqNum++);
	}
	
	private Request prepareMessageRequest(MessageContent content, String destination) throws InvalidArgumentException, ParseException {
		String destUsername = destination;
		String destHost     = sipHost;
		
		if (destination.indexOf("@") == 0 || destination.indexOf("@") == destination.length() - 1) {
			throw new InvalidArgumentException("The address provided is invalid!");
		}
		else if (destination.indexOf("@") > 0) {
			destUsername = destination.substring(0, destination.indexOf("@"));
			destHost     = destination.substring(destination.indexOf("@") + 1);
		}
		
		SipURI destUri = addressFactory.createSipURI(destUsername, destHost);
		
		Request messageRequest = prepareRequest(RequestType.MESSAGE, destUri, null, destUri, sessionId, seqNum++);
		
		messageRequest.setContent(content.content, content.contentTypeHeader);
		
		return messageRequest;
	}
	
	private Request prepareSubscribeRequest(String destination) throws InvalidArgumentException, ParseException {
		String destUsername = destination;
		String destHost     = sipHost;
		
		if (destination.indexOf("@") == 0 || destination.indexOf("@") == destination.length() - 1) {
			throw new InvalidArgumentException("The address provided is invalid!");
		}
		else if (destination.indexOf("@") > 0) {
			destUsername = destination.substring(0, destination.indexOf("@"));
			destHost     = destination.substring(destination.indexOf("@") + 1);
		}
		
		SipURI destUri = addressFactory.createSipURI(destUsername, destHost);
		
		return prepareRequest(RequestType.SUBSCRIBE, destUri, null, destUri, null, 1L);
	}
	
	private Request prepareNotifyRequest(Dialog dialog) throws ParseException {
        if (dialog == null) {
            return null;
        }

        printDialog(dialog);

		String  fromTag      = dialog.getRemoteTag();
		Address fromAddress  = dialog.getRemoteParty();
		SipURI  destUri      = (SipURI) fromAddress.getURI();
		
        dialog.incrementLocalSequenceNumber();
		long   seqNum = dialog.getLocalSeqNumber();
		String callId = dialog.getCallId().getCallId();
		
		SipURI fromReqUri = null;
		
        Log.debug("Getting request URI from dialog");
        Address fromReqAddr = dialog.getRemoteTarget();

        if (fromReqAddr != null && fromReqAddr.getURI() != null && fromReqAddr.getURI() instanceof SipURI)
            fromReqUri = (SipURI) fromReqAddr.getURI();

		if (fromReqUri == null) {
			Log.debug("Getting request URI from destination URI");
			fromReqUri = destUri;
		}
		
		// Instantiate request packet
		Request notifyRequest = prepareRequest(RequestType.NOTIFY, destUri, fromTag, fromReqUri, callId, seqNum);
//		Request notifyRequest = dialog.createRequest(Request.NOTIFY);
		
		((FromHeader) notifyRequest.getHeader(FromHeader.NAME)).setTag(dialog.getLocalTag());
		
		// Set "subscription state" header
		SubscriptionStateHeader subscriptionStateHeader = headerFactory.createSubscriptionStateHeader(SubscriptionStateHeader.ACTIVE.toLowerCase());
//		if (expires > 0) subscriptionStateHeader.setExpires(expires);
		notifyRequest.setHeader(subscriptionStateHeader);
		
		// Set "event" header
		notifyRequest.setHeader(headerFactory.createEventHeader("presence"));
		
		return notifyRequest;
	}
	
//	private Request prepareNotifyRequest(Dialog dialog, SimplePresence simplePresence) throws ParseException {
//		Request request = prepareNotifyRequest(dialog);
//		request.setContent(simplePresence.toXML(), headerFactory.createContentTypeHeader("application", "pidf+xml"));
//
//		return request;
//	}
	
	public void contactSubscribed(String targetSipAddress) {
		try {
//			Roster     roster     = getTransport().getRosterManager().getRoster(getJID().getNode());
			JID        contactJID = getTransport().convertIDToJID(targetSipAddress);
//            RosterItem item       = roster.getRosterItem(contactJID);

			Log.debug("SimpleSession(" + jid.getNode() + ").contactSubscribed:  Preparing presence packet...");
			Presence presence = new Presence();
			presence.setFrom(contactJID);
			presence.setTo(getJID());
			presence.setType(Presence.Type.subscribed);
			getTransport().sendPacket(presence);
			Log.debug("SimpleSession(" + jid.getNode() + ").contactSubscribed:  Presence packet sent ==> \n" + presence.toXML());


//			Log.debug("SimpleSession(" + jid.getNode() + ").contactSubscribed:  Synchronizing SIP user roster...");
//			String rosteruserid = ((SimpleTransport) transport).convertJIDToID(item.getJid());
//			if (myRoster.getEntry(rosteruserid) == null) {
//				SimpleBuddy simpleRosterItem = new SimpleBuddy(rosteruserid, item.getNickname(), 1L);
//				myRoster.addEntry(rosteruserid, simpleRosterItem);
//			}
//			Log.debug("SimpleSession(" + jid.getNode() + ").contactSubscribed:  Finished synchronizing SIP user roster.");

//			syncContactGroups(contact, item.getGroups());

        }
        catch (Exception e) {
			Log.debug("SimpleSession(" + jid.getNode() + ").contactSubscribed:  Exception occured when adding pending contact " + targetSipAddress, e);

//			JID contactJID = getTransport().convertIDToJID(targetSipAddress);
        }
	}
	
	public void contactUnsubscribed(String targetSipAddress) {
		try {
//			Roster     roster     = getTransport().getRosterManager().getRoster(getJID().getNode());
			JID        contactJID = getTransport().convertIDToJID(targetSipAddress);
//			RosterItem item       = roster.getRosterItem(contactJID);
			
			Log.debug("SimpleSession(" + getJID().getNode() + ").contactUnsubscribed:  Preparing presence packet...");
			Presence presence = new Presence();
			presence.setFrom(contactJID);
			presence.setTo(getJID());
			presence.setType(Presence.Type.unsubscribed);
			getTransport().sendPacket(presence);
			Log.debug("SimpleSession(" + getJID().getNode() + ").contactUnsubscribed:  Presence packet sent ==> \n" + presence.toXML());
			
//			Log.debug("SimpleSession(" + jid.getNode() + ").contactUnsubscribed:  Synchronizing SIP user roster...");
//			String rosteruserid = ((SimpleTransport) transport).convertJIDToID(item.getJid());
//			myRoster.removeEntry(rosteruserid);
//			Log.debug("SimpleSession(" + jid.getNode() + ").contactUnsubscribed:  Finished synchronizing SIP user roster.");
			
//			syncContactGroups(contact, item.getGroups());
			
        }
        catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").contactUnsubscribed:  Exception occured when adding pending contact " + targetSipAddress, e);
			
//			JID contactJID = getTransport().convertIDToJID(targetSipAddress);
        }
	}
	
	public ServerTransaction sendResponse(int status, Request request, ServerTransaction serverTransaction) {
		try {
			Log.debug("SimpleSession(" + jid.getNode() + ").sendResponse:  Starting response sending process.");
			
			if (serverTransaction == null)
				serverTransaction = udpSipProvider.getNewServerTransaction(request);
			
			Response response = messageFactory.createResponse(status, request);
			
			// Set "Exprires" header
			if (request.getHeader(ExpiresHeader.NAME) != null)
				response.setHeader(request.getHeader(ExpiresHeader.NAME));
			
			// Add "Contact" header
			Log.debug("SimpleSession(" + jid.getNode() + ").sendResponse:  Preparing \"Contact\" header...");
			try {
				SipURI contactURI = addressFactory.createSipURI(null, InetAddress.getLocalHost().getHostAddress());
				contactURI.setPort(sipPort);
			
				Address contactAddress      = addressFactory.createAddress(contactURI);
				
//				contactAddress.setDisplayName(mySipUsername);
				
				ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
				response.addHeader(contactHeader);
			}
			catch (Exception e) {
				Log.debug("SimpleSession(" + jid.getNode() + ").sendResponse:  Exception occured when adding ContactHeader.", e);
//				return false;	// We can continue with this though.
			}
			
			Log.debug("SimpleSession(" + jid.getNode() + ").sendResponse:  Sending response:  " + response.toString());
			
			serverTransaction.sendResponse(response);
//			udpSipProvider.sendResponse(response);
			
			Log.debug("SimpleSession(" + jid.getNode() + ").sendResponse:  Response sent!");
			
			return serverTransaction;
		}
		catch (Exception ex) {
			Log.debug("SimpleSession(" + jid.getNode() + ").sendResponse:  ", ex);
		}
		
		return null;
	}

	public void sendNotify(Dialog dialog) throws ParseException, SipException, InvalidArgumentException {
		Request notifyRequest = prepareNotifyRequest(dialog);
		
		try {
			User     me         = XMPPServer.getInstance().getUserManager().getUser(getJID().getNode());
			Presence myPresence = XMPPServer.getInstance().getPresenceManager().getPresence(me);
			
			String         presenceContent;
			SimplePresence simplePresence  = new SimplePresence();
			simplePresence.setEntity("pres:" + registration.getUsername() + "@" + sipHost);
			simplePresence.setDmNote(myPresence.getStatus());
			
			if (myPresence.getStatus() != null && myPresence.getStatus().equalsIgnoreCase("Offline"))
				simplePresence.setTupleStatus(SimplePresence.TupleStatus.CLOSED);
			else {
				simplePresence.setTupleStatus(SimplePresence.TupleStatus.OPEN);
				
				if (myPresence.getShow() != null) {
					switch (myPresence.getShow()) {
						case away:
							simplePresence.setRpid(SimplePresence.Rpid.AWAY);
							break;
						case dnd:
							simplePresence.setRpid(SimplePresence.Rpid.BUSY);
							break;
						case xa:
							simplePresence.setRpid(SimplePresence.Rpid.AWAY);
							break;
						default:
							break;
					}
				}
			}
			
			presenceContent = simplePresence.toXML();
			
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "pidf+xml");
			notifyRequest.setContent(presenceContent, contentTypeHeader);
		}
		catch (Exception e) {
			Log.debug("Unable to include presence details in the packet.", e);
		}
		
		sendRequest(notifyRequest, ListeningPoint.UDP, dialog);
	}
	
	private String getTag() {
		StringBuffer tag = new StringBuffer(Integer.toHexString(this.hashCode()));
		while (tag.length() < 8) {
			tag.insert(0, "0");
		}
		
		return new String(tag);
	}
	
	void printDialog(Dialog dialog) {
		if (dialog != null) {
			StringBuffer log = new StringBuffer(1024);
			log.append("Printing dialog:  \n");
			log.append("Call id      = ");
			log.append(dialog.getCallId().getCallId());
			log.append("\n");
			log.append("Dialog id    = ");
			log.append(dialog.getDialogId());
			log.append("\n");
			log.append("Local party  = ");
			log.append(dialog.getLocalParty());
			log.append("\n");
			log.append("Remote party = ");
			log.append(dialog.getRemoteParty());
			log.append("\n");
			log.append("Remote targ  = ");
			log.append(dialog.getRemoteTarget());
			log.append("\n");
			log.append("Local seq    = ");
			log.append(dialog.getLocalSeqNumber());
			log.append("\n");
			log.append("Remote seq   = ");
			log.append(dialog.getRemoteSeqNumber());
			log.append("\n");
			log.append("Local tag    = ");
			log.append(dialog.getLocalTag());
			log.append("\n");
			log.append("Remote tag   = ");
			log.append(dialog.getRemoteTag());
			log.append("\n");
			log.append("Dialog state = ");
			log.append(dialog.getState());
			Log.debug(new String(log));
		}
	}
}
