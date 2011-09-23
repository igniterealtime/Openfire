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

import gov.nist.javax.sip.address.SipUri;

import java.lang.ref.WeakReference;

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
import javax.sip.header.ContactHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import net.sf.kraken.type.TransportLoginStatus;

import org.apache.log4j.Logger;
import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * A listener for a SIMPLE session.
 * <br><br>
 * Instances of this class serve as an assistant to SimpleSession objects,
 * carrying out works of receiving messages or responses from the SIP server
 * or another remote client.
 *
 * @author Patrick Siu
 * @author Daniel Henninger
 */
public class SimpleListener implements SipListener {

    static Logger Log = Logger.getLogger(SimpleListener.class);

    /**
	 * Stores the SIMPLE session object to which this listener belongs.
	 */
	private WeakReference<SimpleSession> mySimpleSessionRef;

    /**
     * Returns the Simple session this listener is attached to.
     *
     * @return Simple session we are attached to.
     */
    public SimpleSession getSession() {
        return mySimpleSessionRef.get();
    }

    /**
	 * Stores the XMPP server username using this SIMPLE session listener.
	 * <br><br>
	 * The storage is for logging purpose.
	 */
	private String        myUsername;
	
	/**
	 * Constructor.
	 * @param mySimpleSession The SIMPLE session object to which this listener belongs.
	 */
	public SimpleListener(SimpleSession mySimpleSession) {
		this.mySimpleSessionRef = new WeakReference<SimpleSession>(mySimpleSession);
		this.myUsername      = mySimpleSession.getJID().getNode();
	}
	
	public void processRequest(RequestEvent requestEvent) {
		ServerTransaction serverTransaction = requestEvent.getServerTransaction();
		Dialog            dialog            = null;
		if (serverTransaction != null) {
			Log.debug("SimpleListener(" + myUsername + ").processRequest:  Getting dialog");
			dialog = serverTransaction.getDialog();
		}
		
		int responseCode = 200;
		
		Log.debug("SimpleListener(" + myUsername + ").processRequest:  Received a request event:  \n" + requestEvent.getRequest().toString());
		
		String       fromAddr    = "";
		Request      request     = requestEvent.getRequest();
		
		if (request.getHeader(FromHeader.NAME) != null) {
			FromHeader fromHeader  = (FromHeader) request.getHeader(FromHeader.NAME);
			Address    fromAddress = fromHeader.getAddress();
			
//			String displayName = fromAddress.getDisplayName();
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
		
		Log.debug("SimpleListener(" + myUsername + ").processRequest:  FromAddr = "        + fromAddr);
		Log.debug("SimpleListener(" + myUsername + ").processRequest:  Request method = '" + request.getMethod() + "'");
		
		if (request.getMethod().equals(Request.MESSAGE)) {
			Log.debug("SimpleListener(" + myUsername + ").processRequest:  Starting MESSAGE request handling process.");
			
			JID    senderJid  = getSession().getTransport().convertIDToJID(fromAddr);
			String msgContent = new String((byte []) request.getContent());
			
			Log.debug("SimpleListener(" + myUsername + ").processRequest:  Forwarding MESSAGE request as XMPP message, setting from = " +
			          senderJid + " and content = '" + msgContent + "'");
			
			getSession().getTransport().sendMessage(getSession().getJID(), senderJid, msgContent);
			
			getSession().sendResponse(responseCode, request, serverTransaction);
		}
		else if (request.getMethod().equals(Request.NOTIFY)) {
			SubscriptionStateHeader subscriptionStateHeader = (SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME);
			
			Log.debug("SimpleListener(" + myUsername + ").processRequest:  NOTIFY request handling process started.");
			
			if (subscriptionStateHeader.getState().equalsIgnoreCase(SubscriptionStateHeader.ACTIVE)) {
				Log.debug("SimpleListener(" + myUsername + ").processRequest:  NOTIFY Active!");
				
				int expires = subscriptionStateHeader.getExpires();
				Log.debug("SimpleListener(" + myUsername + ").processRequest:  NOTIFY Expiry = " + expires);
				
				try {
					if (expires > 0) {
						String content = "";
						if (request.getContent() != null)
							content = new String((byte []) request.getContent());
						
						if (content.length() > 0) {
							SimplePresence simplePresence = SimplePresence.parseSimplePresence(content);
                            try {
                                SimpleBuddy buddy = getSession().getBuddyManager().getBuddy(getSession().getTransport().convertIDToJID(fromAddr));
                                String verboseStatus = null;
                                if (simplePresence.getTupleStatus().isOpen()) {
			                        switch (simplePresence.getRpid()) {
                                        case ON_THE_PHONE:
                                            // TODO: Translate this
                                            verboseStatus = "On Phone";
                                    }
                                }
                                buddy.setPresenceAndStatus(((SimpleTransport)getSession().getTransport()).convertSIPStatusToXMPP(simplePresence), verboseStatus);
                            }
                            catch (NotFoundException e) {
                                // Not in our contact list.  Ignore.
                                Log.debug("SIMPLE: Received presense notification for contact we don't care about: "+fromAddr);
                            }
                        }
                    }
					else {
                        Presence p = new Presence();
                        p.setType(Presence.Type.unsubscribed);
                        p.setTo(getSession().getJID());
                        p.setFrom(getSession().getTransport().convertIDToJID(fromAddr));
                        getSession().getTransport().sendPacket(p);
                    }
					
					Log.debug("SimpleListener(" + myUsername + ").processRequest:  Sending XMPP presence packet.");
				}
				catch (Exception ex) {
					Log.debug("SimpleListener(" + myUsername + ").processRequest:  Exception occured when processing NOTIFY packet...", ex);
				}
			}
			else if (subscriptionStateHeader.getState().equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
                Presence p = new Presence();
                p.setType(Presence.Type.unsubscribed);
                p.setTo(getSession().getJID());
                p.setFrom(getSession().getTransport().convertIDToJID(fromAddr));
                getSession().getTransport().sendPacket(p);
			}
			
			getSession().sendResponse(responseCode, request, serverTransaction);
		}
		else if (request.getMethod().equals(Request.SUBSCRIBE)) {
			Log.debug("SimpleListener for " + myUsername + ":  SUBSCRIBE request handling process.");
			
			ServerTransaction transaction = getSession().sendResponse(202, request, serverTransaction);
			
			Log.debug("SimpleListener for " + myUsername + ":  SUBSCRIBE should be followed by a NOTIFY");
			
			// Send NOTIFY packet.
			try {
				if (transaction != null)
					getSession().sendNotify(transaction.getDialog());
				else
					getSession().sendNotify(dialog);
			}
			catch (Exception e) {
				Log.debug("SimpleListener for " + myUsername + ":  Unable to prepare NOTIFY packet.", e);
			}
		}
	}
	
	public void processResponse(ResponseEvent responseEvent) {
		if (responseEvent.getClientTransaction() != null) {
			Log.debug("SimpleListener for " + myUsername + ":  Getting client transaction...");
			ClientTransaction clientTransaction = responseEvent.getClientTransaction();
			Dialog            clientDialog      = clientTransaction.getDialog();
			getSession().printDialog(clientDialog);
		}
		
		Log.debug("SimpleListener for " + myUsername + ":  Received a response event:  " + responseEvent.getResponse().toString());
		
//		String   fromAddr = "";
		String   toAddr   = "";
		Response response = responseEvent.getResponse();
//		if (response.getHeader(FromHeader.NAME) != null) {
//			FromHeader fromHeader = (FromHeader) response.getHeader(FromHeader.NAME);
//			URI        fromUri    = fromHeader.getAddress().getURI();
//			if (fromUri instanceof SipUri)
//				fromAddr = ((SipUri) fromUri).getUser() + "@" + ((SipUri) fromUri).getHost();
//			else
//				fromAddr = fromUri.toString();
//		}
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
						Log.debug("SimpleListener(" + myUsername + ").processResponse:  " +
						          getSession().getRegistration().getUsername() + " log in successful!");
						
						getSession().sipUserLoggedIn();
					}
					else {
						if (getSession().getLoginStatus().equals(TransportLoginStatus.LOGGING_OUT)) {
							Log.debug("SimpleListener(" + myUsername + ").processResponse:  " +
							          getSession().getRegistration().getUsername() + " log out successful!");

							getSession().sipUserLoggedOut();
							getSession().removeStack();
						}
					}
				}
			}
			if (method.equals(Request.SUBSCRIBE)) {
				if (response.getStatusCode() / 100 == 2) {
					Log.debug("SimpleListener for " + myUsername + ":  Handling SUBSCRIBE acknowledgement!!");
					
					int expires = 0;
					if (response.getHeader(ContactHeader.NAME) != null) {
						expires = ((ContactHeader) response.getHeader(ContactHeader.NAME)).getExpires();
					}
					if (response.getHeader(ExpiresHeader.NAME) != null) {
						expires = ((ExpiresHeader) response.getHeader(ExpiresHeader.NAME)).getExpires();
					}
					
//					Presence presence = new Presence();
//					presence.setFrom(getSession().getTransport().convertIDToJID(toAddr));
//					presence.setTo(getSession().getJID());
					
					if (expires > 0) {
						// Confirm subscription of roster item
						getSession().contactSubscribed(toAddr);
					} else {
						// Confirm unsubscription of roster item
						getSession().contactUnsubscribed(toAddr);
					}
					
					Log.debug("SimpleListener for " + myUsername + ":  Handled SUBSCRIBE acknowledgement!!");
				}
			}
		}
	}
	
	public void processTimeout(TimeoutEvent timeoutEvent) {
		Log.debug("SimpleListener for " + myUsername + " received a timeout event:  " + timeoutEvent.getTimeout().toString());
		
//		timeoutEvent.getTimeout().
		// Should we try to resend the packet?
	}
	
	public void processIOException(IOExceptionEvent iOExceptionEvent) {
		Log.debug("SimpleListener for " + myUsername + " received an IOException event:  " + iOExceptionEvent.toString());
	}
	
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
		Log.debug("SimpleListener(" + myUsername + "):  Received a TransactionTerminatedEvent [" + transactionTerminatedEvent.hashCode() + "]");
		
		// Should we obtain the transaction and log down the tranaction terminated?
	}
	
	public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
		Log.debug("SimpleListener for " + myUsername + " received a dialog terminated event:  " +
		          dialogTerminatedEvent.getDialog().getDialogId());
	}
	
	@Override
    public void finalize() {
        try {
            super.finalize();
        }
        catch (Throwable e) {
            // Hrm
        }
        Log.debug("SimpleListener for " + myUsername + " is being shut down!!");
	}
}
