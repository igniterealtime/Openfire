/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.simple;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.TooManyListenersException;

import javax.sip.ClientTransaction;
import javax.sip.ListeningPoint;
import javax.sip.SipException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.gateway.*;
import org.jivesoftware.wildfire.roster.*;
import org.jivesoftware.wildfire.user.User;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * A gateway session to a SIMPLE IM server.
 * @author  Patrick Siu
 * @version 0.0.1
 */
public class SimpleSession extends TransportSession {
	
	private SipFactory sipFactory = null;
	
	private String sipHost;
	private int    sipPort;
	private String username;
	private String sessionId = "";
	private long   seqNum;
	
	private ListeningPoint tcp = null;
	private ListeningPoint udp = null;
	private SipProvider tcpSipProvider;
	private SipProvider udpSipProvider;
	
	private MessageFactory        messageFactory;
	private AddressFactory        addressFactory;
	private HeaderFactory         headerFactory;
	private SipStack              sipStack;
	private SimpleSessionListener myListener;
	
	
	/**
	 * Constructor utilizing the lesser constructor of the super class.
	 * @see org.jivesoftware.wildfire.gateway.TransportSession#TransportSession(org.jivesoftware.wildfire.gateway.Registration,org.xmpp.packet.JID,org.jivesoftware.wildfire.gateway.BaseTransport)
	 */
	public SimpleSession(Registration registration, JID jid, BaseTransport transport) {
		super(registration, jid, transport);
		
		init();
	}
	
	/**
	 * Constructor utilizing the greater constructor of the super class.
	 * @see org.jivesoftware.wildfire.gateway.TransportSession#TransportSession(org.jivesoftware.wildfire.gateway.Registration,org.xmpp.packet.JID,org.jivesoftware.wildfire.gateway.BaseTransport,java.lang.Integer)
	 */
	public SimpleSession(Registration registration, JID jid, BaseTransport transport, Integer priority) {
		super(registration, jid, transport, priority);
		
		init();
	}
	
	/**
	 * The initialization process.
	 */
	private void init() {
		sipHost = JiveGlobals.getProperty("plugin.gateway.sip.connecthost", "");
		sipPort = ((SimpleTransport) transport).generateListenerPort();
		
		// Initialize the SipFactory
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
		
		// Initialize the SipStack for this session
		Properties properties = new Properties();
		properties.setProperty("javax.sip.STACK_NAME", jid.getNode());
		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
		
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
			}
			if (udp == null) {
				udp = sipStack.createListeningPoint(localIP, sipPort, ListeningPoint.UDP);
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
            return;
        }
		
		try {
			myListener = new SimpleSessionListener(this);
			tcpSipProvider.addSipListener(myListener);
			udpSipProvider.addSipListener(myListener);
		} catch (TooManyListenersException ex) {
			Log.debug(ex);
            return;
        }
		
		try {
			sipStack.start();
		} catch (SipException ex) {
			Log.debug(ex);
            return;
        }
		
		seqNum = 1L;
	}

	/**
	 * Perform rollback action once the login fails or logout goes on the way.
	 */
	private void rollback() {

	}

	public void updateStatus(PresenceType presenceType, String verboseStatus) {
		Log.debug("SimpleSession(" + getJID().getNode() + ").updateStatus:  Method commenced!");
	}
	
	public void addContact(RosterItem item) {
		String nickname = getTransport().convertJIDToID(item.getJid());
        if (item.getNickname() != null && !item.getNickname().equals("")) {
            nickname = item.getNickname();
        }
        lockRoster(item.getJid().toString());
		
		JID    destJid    = item.getJid();
		String destId     = ((SimpleTransport) transport).convertJIDToID(destJid);
		
		Log.debug("SimpleSession(" + this.jid.getNode() + ").addContact:  Starting addContact function for " + destId);
		
		List<Header> customHeaders = new ArrayList<Header>(13);
		try { customHeaders.add(headerFactory.createExpiresHeader(365 * 24 * 60 * 60)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.ACK)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.BYE)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.CANCEL)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.INFO)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.INVITE)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.MESSAGE)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.NOTIFY)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.OPTIONS)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.REFER)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createAllowHeader(Request.SUBSCRIBE)); }
		catch (Exception e) {}	// Ignore
		try { customHeaders.add(headerFactory.createEventHeader("presence")); }
		catch (Exception e) {}	// Ignore
		
		prepareRequest(RequestType.SUBSCRIBE, destId, null, null, 1L, 70, customHeaders, null);
		
		destJid = getTransport().convertIDToJID(destId);
		
		try {
			Log.debug("SimpleSession(" + getJID().getNode() + ").addContact:  Adding contact '" + destJid.toString() + "' to roster...");
            getTransport().addOrUpdateRosterItem(getJID(), destJid, nickname, item.getGroups());
			Log.debug("SimpleSession(" + getJID().getNode() + ").addContact:  Contact '" + destJid.toString() + "' added!");
        }
		catch (Exception ex) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").addContact:  Unable to add contact.", ex);
		}
	}
	
	public void removeContact(RosterItem item) {
	}
	
	public void updateContact(RosterItem item) {
	}
	
	public void sendMessage(JID jid, String message) {
		Log.debug("SimpleSession(" + this.jid.getNode() + "):  Starting message sending process.");
		ContentTypeHeader contentTypeHeader = null;
		
		try {
			contentTypeHeader = headerFactory.createContentTypeHeader("text", "plain");
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + this.jid.getNode() + ").sendMessage:  Unable to initiate ContentType header.", e);
			return;
		}
		Log.debug("SimpleSession(" + this.jid.getNode() + "):  Finished adding ContentType header.");
		
		MessageContent    content           = new MessageContent(contentTypeHeader, message);
		if (!prepareRequest(RequestType.MESSAGE, ((SimpleTransport) transport).convertJIDToID(jid), null, null, 1L, 70, null, content)) {
			Log.debug("SimpleSession(" + this.jid.getNode() + ").sendMessage:  Unable to send message!");
		}
	}
	
	public void sendServerMessage(String message) {
	}
	
	public void sendChatState(JID jid, ChatStateType chatState) {
	}
	
	public void retrieveContactStatus(JID jid) {
	}
	
	public void resendContactStatuses(JID jid) {
	}
	
	
	// The following are SimpleSession specific methods
	public void login(PresenceType presenceType, String verboseStatus) {
		if (!this.isLoggedIn()) {
			this.setLoginStatus(TransportLoginStatus.LOGGING_IN);
			
			Log.debug("SimpleSession(" + getJID().getNode() + ").login:  Start login as " + registration.getUsername() + ".");
			
			List<Header> customHeaders = new ArrayList<Header>(13);
			try { customHeaders.add(headerFactory.createExpiresHeader(365 * 24 * 60 * 60)); }
			catch (Exception e) {
				Log.debug("SimpleSession(" + getJID().getNode() + ").login:  " +
				          "Unable to set the expiry interval, which is essential for a login.", e);
				return;
			}
			
			try { customHeaders.add(headerFactory.createAllowHeader(Request.ACK)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.BYE)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.CANCEL)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.INFO)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.INVITE)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.MESSAGE)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.NOTIFY)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.OPTIONS)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.REFER)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createAllowHeader(Request.SUBSCRIBE)); }
			catch (Exception e) {}	// Ignore
			try { customHeaders.add(headerFactory.createEventHeader("presence")); }
			catch (Exception e) {}	// Ignore
			
			String myUsername = registration.getUsername();
			if (myUsername.indexOf("sip:") < 0) myUsername = "sip:" + myUsername;
			if (myUsername.indexOf("@")    < 0) myUsername = myUsername + "@" + sipHost;
			
			String callId = null;
			try {
				callId         = udpSipProvider.getNewCallId().getCallId();
				this.sessionId = callId;
			}
			catch (Exception e) {
				Log.debug("SimpleSession(" + getJID().getNode() + ").login:  Unable to create a SIP session ID!!", e);
				
				this.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
				return;
			}
			
			Log.debug("SimpleSession(" + getJID().getNode() + ").login:  Created Session ID = '" + this.sessionId + "'!!");
			
			if (!prepareRequest(RequestType.REGISTER, myUsername, null, this.sessionId, seqNum++, 70, customHeaders, null)) {
				this.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
			}
		}
	}
	
	public void logout() {
		this.setLoginStatus(TransportLoginStatus.LOGGING_OUT);
		
		// Lots of logout work here...
		Log.debug("SimpleSession(" + getJID().getNode() + ").logout:  Preparing logout packet...");
		
		List<Header> customHeaders = new ArrayList<Header>(1);
		try { customHeaders.add(headerFactory.createExpiresHeader(0)); }
		catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").logout:  " +
			          "Unable to set the expiry interval, which is essential for a logout.", e);
			return;
		}	// Ignore
		
		String myUsername = registration.getUsername();
		if (myUsername.indexOf("sip:") < 0) myUsername = "sip:" + myUsername;
		if (myUsername.indexOf("@")    < 0) myUsername = myUsername + "@" + sipHost;
		
		prepareRequest(RequestType.REGISTER, myUsername, null, this.sessionId, seqNum++, 70, customHeaders, null);
//		this.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
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
			sipStack.deleteListeningPoint(tcp);
			sipStack.deleteListeningPoint(udp);
		}
		catch (Exception ex) {
			Log.debug(ex);
			Log.debug("SimpleSession for " + jid.getNode() + " is unable to gracefully shut down.");
		}
		
		sipStack.stop();
		sipStack = null;
	}
	
	public void finalize() {
		Log.debug("SimpleSession for " + jid.getNode() + ":  Finalize function initialized!");

		if (this.getLoginStatus().equals(TransportLoginStatus.LOGGED_IN)) {
			logout();
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
		
		public ContentTypeHeader getContentTypeHeader() {
			return this.contentTypeHeader;
		}
		
		public String getContent() {
			return this.content;
		}
	}
	
	/**
	 * Prepares a Request packet.
	 * <br><br>
	 * The "From", "To", "Via", "Contact", "CSeq", "MaxForwards" and "CallId" headers,
	 * as well as the content (if provided) are prepared in the method.
	 * <br>
	 * Additional headers should be provided as customer headers.  See the "headers" parameter for further details.
	 * @param requestType An inner request type enum
	 * @param destination The recipient of this request
	 * @param toTag       Additional tag code of the destination.  Can usually leave it <code>null</code>.
	 * @param callId      A String representing an ongoing SIP CallID.  Leave it <code>null</code> if a new CallID should be generated.
	 * @param seqNum      A sequence number for an ongoing session.
	 * @param maxForward  Maximum times of forwarding allowed for this packet.
	 * @param headers     Additional headers that have to be added.  Leave <code>null</code> if no custom header is to be added.
	 * @param content     An object containing the content of the message, as well as the header defining the content type.
	 *                    Can leave <code>null</code> if no content is to be specified.
	 * @see   org.jivesoftware.wildfire.gateway.protocols.simple.SimpleSession.RequestType
	 */
	private boolean prepareRequest(
			RequestType    requestType,
			String         destination,
			String         toTag,
			String         callId,
			long           seqNum,
			int            maxForward,
			List<Header>   headers,
			MessageContent content) {
		String myJiveId      = this.jid.getNode();
		String mySipUsername = registration.getUsername();
		
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing request packet of type '" + requestType + "'");
		
		// Prepare "From" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"From\" header...");
		FromHeader fromHeader = null;
		try {
			SipURI     fromUri         = addressFactory.createSipURI(mySipUsername, sipHost);
			Address    fromNameAddress = addressFactory.createAddress(fromUri);
			fromNameAddress.setDisplayName(mySipUsername);
			
			fromHeader = headerFactory.createFromHeader(fromNameAddress, "SipGateway");
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing FromHeader.", e);
			
			return false;
		}
		
		// Prepare "To" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"To\" header...");
		ToHeader toHeader = null;
		
		String destUsername = "";
		String destAddress  = "";
		if (destination.indexOf(":") > 0 && destination.indexOf("@") > destination.indexOf(":")) {
			destUsername = destination.substring(destination.indexOf(":") + 1, destination.indexOf("@"));
			destAddress  = destination.substring(destination.indexOf("@") + 1);
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  destUsername = '" + destUsername + "';  destAddress = '" + destAddress + "'");
		}
		else {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing ToHeader.",
			          new IllegalArgumentException("The destination specified is not a valid SIP address"));
			
			return false;
		}
		
		try {
			SipURI   toAddress     = addressFactory.createSipURI(destUsername, destAddress);
			Address  toNameAddress = addressFactory.createAddress(toAddress);
			
			String displayName = destUsername;
			try {
				RosterItem ri = getRoster().getRosterItem(this.getTransport().convertIDToJID(destination));
				if (ri != null) displayName = ri.getNickname();
			}
			catch (Exception e) {} // Ignore the exception.  We don't need to handle it.
			
			toNameAddress.setDisplayName(displayName);
			
			toHeader = headerFactory.createToHeader(toNameAddress, toTag);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing ToHeader.", e);
			
			return false;
		}
		
		// Prepare "Via" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"Via\" header...");
		ArrayList viaHeaders = new ArrayList();
		try {
			ViaHeader viaHeader  = headerFactory.createViaHeader(InetAddress.getLocalHost().getHostAddress(), sipPort, ListeningPoint.UDP, null);
			viaHeaders.add(viaHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing ViaHeader.", e);
			
			return false;
		}
		
		// Prepare "CallId" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"CallId\" header...");
		CallIdHeader callIdHeader = null;
		try {
			if (callId != null)
				callIdHeader = headerFactory.createCallIdHeader(callId);
			else
				callIdHeader = udpSipProvider.getNewCallId();
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing CallIdHeader.", e);
			
			return false;
		}
		
		// Prepare "CSeq" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"CSeq\" header...");
		CSeqHeader cSeqHeader = null;
		try {
			cSeqHeader = headerFactory.createCSeqHeader(seqNum, requestType.toString());
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing CSeqHeader.", e);
			
			return false;
		}
		
		// Prepare "MaxForwards" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"MaxForwards\" header...");
		MaxForwardsHeader maxForwardsHeader = null;
		try {
			maxForwardsHeader = headerFactory.createMaxForwardsHeader(maxForward);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing MaxForwardsHeader.", e);
			
			return false;
		}

		// Prepare request URI
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing request URI...");
		SipURI requestURI = null;
		try {
			requestURI = addressFactory.createSipURI(destUsername, destAddress);
			requestURI.setTransportParam(ListeningPoint.UDP);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when preparing Request URI.", e);
			
			return false;
		}
		
		// Instantiate Request packet
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Instantiating Request packet...");
		Request request = null;
		try {
			request = messageFactory.createRequest(
					requestURI, requestType.toString(),
					callIdHeader, cSeqHeader,
					fromHeader, toHeader,
					viaHeaders, maxForwardsHeader
					);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when instantiating Request packet.", e);
			
			return false;
		}
		
		// Add custom headers
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Start adding custom headers...");
		int headerCount = 0;
		if (headers != null) {
			headerCount = headers.size();
			for (ListIterator<Header> headersIterator = headers.listIterator(); headersIterator.hasNext(); ) {
				Header aHeader = headersIterator.next();
				try {
					request.addHeader(aHeader);
				}
				catch (Exception e) {
					Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when adding a " +
					          aHeader.getClass().toString() + " to the request packet.", e);
					headerCount--;
				}
			}
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Finished adding custom headers.  " +
			          headerCount + " of " + headers.size() + " headers successfully added.");
		}
		else {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  The custom headers input is null.  No custom headers to add.");
		}
		
		// Add "Contact" header
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Preparing \"Contact\" header...");
		try {
			SipURI contactURI;
			if (requestType.equals(RequestType.NOTIFY))
				contactURI = addressFactory.createSipURI(null, InetAddress.getLocalHost().getHostAddress());
			else
				contactURI = addressFactory.createSipURI(mySipUsername, InetAddress.getLocalHost().getHostAddress());
			contactURI.setPort(sipPort);
			
//			Address contactAddress      = addressFactory.createAddress("<" + InetAddress.getLocalHost().getHostAddress() + ":" + sipPort + ">");
			Address contactAddress      = addressFactory.createAddress(contactURI);
			
			if (!requestType.equals(RequestType.NOTIFY))
				contactAddress.setDisplayName(mySipUsername);
			
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			request.addHeader(contactHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when adding ContactHeader.", e);
			
			return false;
		}
		
		if (content != null) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Content is specified.  Adding content...");
			try {
				request.setContent(content.getContent(), content.getContentTypeHeader());
			}
			catch (Exception e) {
				Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when adding content to the request packet.", e);
				// Just tell, then continue the request!
			}
		}
		
		// Send the request
		Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Sending Request packet:  \n" + request.toString());
		try {
//			udpSipProvider.sendRequest(request);
			ClientTransaction clientTransaction = udpSipProvider.getNewClientTransaction(request);
			clientTransaction.sendRequest();
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + myJiveId + ").prepareRequest:  Exception occured when sending Request packet.", e);
			return false;
		}
		
		return true;
	}
	
	public void contactSubscribed(String targetSipAddress) {
		try {
			Roster     roster     = getTransport().getRosterManager().getRoster(getJID().getNode());
			JID        contactJID = getTransport().convertIDToJID(targetSipAddress);
            RosterItem item       = roster.getRosterItem(contactJID);
			
			Log.debug("SimpleSession(" + getJID().getNode() + ").contactSubscribed:  Preparing presence packet...");
			Presence presence = new Presence();
			presence.setFrom(contactJID);
			presence.setTo(getJID());
			presence.setType(Presence.Type.subscribed);
			getTransport().sendPacket(presence);
			Log.debug("SimpleSession(" + getJID().getNode() + ").contactSubscribed:  Presence packet sent ==> \n" + presence.toXML());
			
//			syncContactGroups(contact, item.getGroups());
			
            unlockRoster(contactJID.toString());
        }
        catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").contactSubscribed:  Exception occured when adding pending contact " + targetSipAddress, e);
			
			JID contactJID = getTransport().convertIDToJID(targetSipAddress);
			unlockRoster(contactJID.toString());
        }
	}
	
	public void sendResponse(int status, Request request) {
		try {
			Log.debug("SimpleSession for " + this.jid.getNode() + ":  Starting response sending process.");
			
			Response response = messageFactory.createResponse(status, request);
			udpSipProvider.sendResponse(response);
			
			Log.debug("SimpleSession for " + this.jid.getNode() + ":  Response sent!");
		}
		catch (Exception ex) {
			Log.debug("SimpleSession for " + this.jid.getNode() + ":  ", ex);
		}
	}
	
	/**
	 * Sends a NOTIFY packet based on the SUBSCRIBE packet received.
	 * @throw
	 */
	public void sendNotify(Request inputRequest) throws Exception {
		if (!inputRequest.getMethod().equals(Request.SUBSCRIBE)) {
			// Should throw an Exception telling the packet is wrong;
			throw new Exception("The REQUEST packet is not of method SUBSCRIBE!");
		}
		
		String dest  = "";
		String toTag = "";
		if (inputRequest.getHeader(FromHeader.NAME) != null) {
			FromHeader fromHeader = (FromHeader) inputRequest.getHeader(FromHeader.NAME);
			
			toTag = fromHeader.getTag();
			dest  = fromHeader.getAddress().getURI().toString();
		}
		
		Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Parsing SUBSCRIBE packet...");
		
		long seqNum = 1L;
		if (inputRequest.getHeader(CSeqHeader.NAME) != null) {
			seqNum = ((CSeqHeader) inputRequest.getHeader(CSeqHeader.NAME)).getSeqNumber() + 1;
		}
		
		int expires = 0;
		if (inputRequest.getHeader(ExpiresHeader.NAME) != null) {
			expires = ((ExpiresHeader) inputRequest.getHeader(ExpiresHeader.NAME)).getExpires();
		}
		
		String callId = null;
		if (inputRequest.getHeader(CallIdHeader.NAME) != null) {
			callId = ((CallIdHeader) inputRequest.getHeader(CallIdHeader.NAME)).getCallId();
		}
		
		User     me         = XMPPServer.getInstance().getUserManager().getUser(getJID().getNode());
		Presence myPresence = XMPPServer.getInstance().getPresenceManager().getPresence(me);
		
		List<Header> routeHeaders = new ArrayList<Header>();
		String routingProxies = "";
		
		for (Iterator recRouteHeaders = inputRequest.getHeaders(RecordRouteHeader.NAME); recRouteHeaders.hasNext(); )
			routingProxies += "," + ((RecordRouteHeader) recRouteHeaders.next()).toString().substring("Record-Route: ".length());
		if (routingProxies.startsWith(",")) routingProxies = routingProxies.substring(1);
		
		int commaIndex        = routingProxies.lastIndexOf(",");
		while (true) {
			String uri = "";
			if (commaIndex > 0)
				uri = routingProxies.substring(commaIndex + 1);
			else
				uri = routingProxies;
			
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  uri = " + uri);
			
			// Works and works here...
			if (uri != null && uri.trim().length() > 0) {
				RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(uri));
				routeHeaders.add(routeHeader);
			}
			
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  routeHeaders.size = " +
			          routeHeaders.size());
			
			if (commaIndex < 0) break;
			
			routingProxies = routingProxies.substring(0, commaIndex);
			commaIndex     = routingProxies.lastIndexOf(",");
		}
		
		sendNotify(dest, toTag, callId, seqNum, expires, 70, myPresence, routeHeaders);
	}
	
	public void sendNotify(String dest, String toTag, String callId, long seqNum, int expires, int maxForward, Presence presence, List<Header> routeHeaders) {
		List<Header> customHeaders = new ArrayList<Header>(3);
		
		Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Setting subscription state header...");
		try {
			SubscriptionStateHeader subscriptionStateHeader = headerFactory.createSubscriptionStateHeader(SubscriptionStateHeader.ACTIVE.toLowerCase());
			subscriptionStateHeader.setExpires(expires);
			customHeaders.add(subscriptionStateHeader);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Unable to set subscription state header.", e);
			return;
		}
		
		Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Setting event header...");
		try {
			customHeaders.add(headerFactory.createEventHeader("presence"));
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Unable to set event header.", e);
			return;
		}
		
		Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Adding route headers...");
		try {
			customHeaders.addAll(routeHeaders);
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Unable to add route headers.", e);
			return;
		}
		
		Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Writing simple presence.");
		String presenceContent = "";
		try {
			SimplePresence simplePresence = new SimplePresence();
			simplePresence.setEntity("pres:" + registration.getUsername() + "@" + sipHost);
			simplePresence.setDmNote(presence.getStatus());
			
			if (presence.getStatus() != null && presence.getStatus().equalsIgnoreCase("Offline"))
				simplePresence.setTupleStatus(SimplePresence.TupleStatus.CLOSED);
			else {
				simplePresence.setTupleStatus(SimplePresence.TupleStatus.OPEN);
				
				if (presence.getShow() != null) {
					switch (presence.getShow()) {
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
			
			presenceContent =simplePresence.toXML();
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Unable to write simple presence.", e);
			return;
		}
		
		Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Creating content type header.");
		ContentTypeHeader contentTypeHeader;
		
		try {
			contentTypeHeader = headerFactory.createContentTypeHeader("application", "pidf+xml");
		}
		catch (Exception e) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Unable to create content type header.", e);
			return;
		}
		
		Log.debug(presenceContent);
		
		MessageContent    msgContent = new MessageContent(contentTypeHeader, presenceContent);
		if (!prepareRequest(RequestType.NOTIFY, dest, toTag, callId, seqNum, maxForward, customHeaders, msgContent)) {
			Log.debug("SimpleSession(" + getJID().getNode() + ").sendNotify:  Unable to send NOTIFY packet.");
		}
	}
}