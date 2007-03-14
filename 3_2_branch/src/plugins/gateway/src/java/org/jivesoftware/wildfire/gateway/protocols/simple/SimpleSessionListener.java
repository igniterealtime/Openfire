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

import gov.nist.javax.sip.address.SipUri;
import java.util.ListIterator;
import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.TransportLoginStatus;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * A listener for a SIMPLE session.
 * <br><br>
 * Instances of this class serve as an assistant to SimpleSession objects,
 * carrying out works of receiving messages or responses from the SIP server
 * or another remote client.
 *
 * @author  Patrick Siu
 * @version 0.0.1
 */
public class SimpleSessionListener implements SipListener {
	
	/**
	 * Stores the SIMPLE session object to which this listener belongs.
	 */
	private SimpleSession mySimpleSession;
	/**
	 * Stores the Jive username using this SIMPLE session listener.
	 * <br><br>
	 * The storage is for logging purpose.
	 */
	private String        myUsername;
	
	/**
	 * Constructor.
	 * @param mySimpleSession The SIMPLE session object to which this listener belongs.
	 */
	public SimpleSessionListener(SimpleSession mySimpleSession) {
		this.mySimpleSession = mySimpleSession;
		this.myUsername      = mySimpleSession.getJID().getNode();
	}
	
	public void processRequest(RequestEvent requestEvent) {
		ServerTransaction serverTransaction = requestEvent.getServerTransaction();
		Dialog            dialog            = null;
		if (serverTransaction != null) {
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Getting dialog");
			dialog = serverTransaction.getDialog();
		}
		
		int responseCode = 200;
		
		Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Received a request event:  \n" + requestEvent.getRequest().toString());
		
		String       fromAddr    = "";
		Request      request     = requestEvent.getRequest();
		
		if (request.getHeader(FromHeader.NAME) != null) {
			FromHeader fromHeader  = (FromHeader) request.getHeader(FromHeader.NAME);
			Address    fromAddress = fromHeader.getAddress();
			
			String displayName = fromAddress.getDisplayName();
			URI    fromUri     = fromAddress.getURI();
			if (fromUri != null) {
				if (fromUri.isSipURI()) {
					SipURI fromSipUri = (SipURI) fromUri;
					
					fromAddr = fromSipUri.getUser() + "@" + fromSipUri.getHost();
				}
				else {
					fromAddr = fromUri.toString();
				}
			}
		}
		
		Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  FromAddr = "        + fromAddr);
		Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Request method = '" + request.getMethod() + "'");
		
		if (request.getMethod().equals(Request.MESSAGE)) {
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Starting MESSAGE request handling process.");
			
			JID    senderJid  = mySimpleSession.getTransport().convertIDToJID(fromAddr);
			String msgContent = new String((byte []) request.getContent());
			
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Forwarding MESSAGE request as XMPP message, setting from = " +
			          senderJid + " and content = '" + msgContent + "'");
			
			mySimpleSession.getTransport().sendMessage(mySimpleSession.getJID(), senderJid, msgContent);
			
			mySimpleSession.sendResponse(responseCode, request, serverTransaction);
		}
		else if (request.getMethod().equals(Request.NOTIFY)) {
			Presence presence = new Presence();
			presence.setFrom(mySimpleSession.getTransport().convertIDToJID(fromAddr));
			presence.setTo(mySimpleSession.getJID());
			
			SubscriptionStateHeader subscriptionStateHeader = (SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME);
			
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  NOTIFY request handling process started.");
			
			if (subscriptionStateHeader.getState().equalsIgnoreCase(SubscriptionStateHeader.ACTIVE)) {
				Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  NOTIFY Active!");
				
				int expires = subscriptionStateHeader.getExpires();
				Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  NOTIFY Expiry = " + expires);
				
				try {
					if (expires > 0) {
						String content = "";
						if (request.getContent() != null)
							content = new String((byte []) request.getContent());
						
						if (content.length() > 0) {
							SimplePresence simplePresence = SimplePresence.parseSimplePresence(content);
							((SimpleTransport) mySimpleSession.getTransport()).convertSIPStatusToJap(presence, simplePresence);
						}
					}
					else {
						presence.setType(Presence.Type.unsubscribed);
					}
					
					Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Sending XMPP presence packet.");
				}
				catch (Exception ex) {
					Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Exception occured when processing NOTIFY packet...", ex);
				}
			}
			else if (subscriptionStateHeader.getState().equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
				presence.setType(Presence.Type.unsubscribed);
			}
			
			mySimpleSession.getTransport().sendPacket(presence);
			
			mySimpleSession.sendResponse(responseCode, request, serverTransaction);
		}
		else if (request.getMethod().equals(Request.SUBSCRIBE)) {
			Log.debug("SimpleSessionListener for " + myUsername + ":  SUBSCRIBE request handling process.");
			
			ServerTransaction transaction = mySimpleSession.sendResponse(202, request, serverTransaction);
			
			Log.debug("SimpleSessionListener for " + myUsername + ":  SUBSCRIBE should be followed by a NOTIFY");
			
			// Send NOTIFY packet.
			try {
				if (transaction != null)
					mySimpleSession.sendNotify(transaction.getDialog());
				else
					mySimpleSession.sendNotify(dialog);
			}
			catch (Exception e) {
				Log.debug("SimpleSessionListener for " + myUsername + ":  Unable to prepare NOTIFY packet.", e);
			}
		}
	}
	
	public void processResponse(ResponseEvent responseEvent) {
		if (responseEvent.getClientTransaction() != null) {
			Log.debug("SimpleSessionListener for " + myUsername + ":  Getting client transaction...");
			ClientTransaction clientTransaction = responseEvent.getClientTransaction();
			Dialog            clientDialog      = clientTransaction.getDialog();
			mySimpleSession.printDialog(clientDialog);
		}
		
		Log.debug("SimpleSessionListener for " + myUsername + ":  Received a response event:  " + responseEvent.getResponse().toString());
		
		String   fromAddr = "";
		String   toAddr   = "";
		Response response = responseEvent.getResponse();
		if (response.getHeader(FromHeader.NAME) != null) {
			FromHeader fromHeader = (FromHeader) response.getHeader(FromHeader.NAME);
			URI        fromUri    = fromHeader.getAddress().getURI();
			if (fromUri instanceof SipUri)
				fromAddr = ((SipUri) fromUri).getUser() + "@" + ((SipUri) fromUri).getHost();
			else
				fromAddr = fromUri.toString();
		}
		if (response.getHeader(ToHeader.NAME) != null) {
			ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
			URI      toUri    = toHeader.getAddress().getURI();
			if (toUri instanceof SipUri)
				toAddr = ((SipUri) toUri).getUser() + "@" + ((SipUri) toUri).getHost();
			else
				toAddr = toUri.toString();
		}
		
		if (response.getHeader(CSeqHeader.NAME) != null) {
			String method = ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod();
			if (method.equals(Request.REGISTER)) {
				if (response.getStatusCode() / 100 == 2) {
					int expires = 0;
					if (response.getHeader(ContactHeader.NAME) != null) {
						expires = ((ContactHeader) response.getHeader(ContactHeader.NAME)).getExpires();
					} else if (response.getHeader(ExpiresHeader.NAME) != null) {
						expires = ((ExpiresHeader) response.getHeader(ExpiresHeader.NAME)).getExpires();
					}
					
					if (expires > 0) {
						Log.debug("SimpleSessionListener(" + myUsername + ").processResponse:  " +
						          mySimpleSession.getRegistration().getUsername() + " log in successful!");
						
						mySimpleSession.sipUserLoggedIn();
//						mySimpleSession.getRegistration().setLastLogin(new Date());
//						mySimpleSession.setLoginStatus(TransportLoginStatus.LOGGED_IN);
					}
					else {
						if (mySimpleSession.getLoginStatus().equals(TransportLoginStatus.LOGGING_OUT)) {
							Log.debug("SimpleSessionListener(" + myUsername + ").processResponse:  " +
							          mySimpleSession.getRegistration().getUsername() + " log out successful!");

							mySimpleSession.sipUserLoggedOut();
							mySimpleSession.removeStack();
						}
					}
				}
			}
			if (method.equals(Request.SUBSCRIBE)) {
				if (response.getStatusCode() / 100 == 2) {
					Log.debug("SimpleSessionListener for " + myUsername + ":  Handling SUBSCRIBE acknowledgement!!");
					
					int expires = 0;
					if (response.getHeader(ContactHeader.NAME) != null) {
						expires = ((ContactHeader) response.getHeader(ContactHeader.NAME)).getExpires();
					}
					if (response.getHeader(ExpiresHeader.NAME) != null) {
						expires = ((ExpiresHeader) response.getHeader(ExpiresHeader.NAME)).getExpires();
					}
					
//					Presence presence = new Presence();
//					presence.setFrom(mySimpleSession.getTransport().convertIDToJID(toAddr));
//					presence.setTo(mySimpleSession.getJID());
					
					if (expires > 0) {
						// Confirm subscription of roster item
						mySimpleSession.contactSubscribed(toAddr);
					} else {
						// Confirm unsubscription of roster item
						mySimpleSession.contactUnsubscribed(toAddr);
					}
					
					Log.debug("SimpleSessionListener for " + myUsername + ":  Handled SUBSCRIBE acknowledgement!!");
				}
			}
		}
	}
	
	public void processTimeout(TimeoutEvent timeoutEvent) {
		Log.debug("SimpleSessionListener for " + myUsername + " received a timeout event:  " + timeoutEvent.getTimeout().toString());
		
//		timeoutEvent.getTimeout().
		// Should we try to resend the packet?
	}
	
	public void processIOException(IOExceptionEvent iOExceptionEvent) {
		Log.debug("SimpleSessionListener for " + myUsername + " received an IOException event:  " + iOExceptionEvent.toString());
	}
	
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
		Log.debug("SimpleSessionListener(" + myUsername + "):  Received a TransactionTerminatedEvent [" + transactionTerminatedEvent.hashCode() + "]");
		
		// Should we obtain the transaction and log down the tranaction terminated?
	}
	
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
		Log.debug("SimpleSessionListener for " + myUsername + " received a dialog terminated event:  " +
		          dialogTerminatedEvent.getDialog().getDialogId());
	}
	
	public void finalize() {
		Log.debug("SimpleSessionListener for " + myUsername + " is being shut down!!");
		mySimpleSession = null;
	}
}