package org.jivesoftware.wildfire.gateway.protocols.simple;

import java.util.Date;
import java.util.ListIterator;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
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
		int responseCode = 200;
		
		Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Received a request event:  \n" + requestEvent.getRequest().toString());
		
		String       fromAddr    = "";
		Request      request     = requestEvent.getRequest();
		ListIterator headerNames = request.getHeaderNames();
		
		while (headerNames.hasNext()) {
			String headerName = (String) headerNames.next();
			if (headerName.equals(FromHeader.NAME)) {
				FromHeader fromHeader  = (FromHeader) request.getHeader(FromHeader.NAME);
				Address    fromAddress = fromHeader.getAddress();
				
				String displayName = fromAddress.getDisplayName();
				URI    fromUri     = fromAddress.getURI();
				if (fromUri != null) {
					fromAddr = fromUri.toString();
					break;
				}
			}
		}
		
		Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Request method = '" + request.getMethod() + "'");
		
		if (request.getMethod().equals(Request.MESSAGE)) {
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Starting MESSAGE request handling process.");
			
			JID    senderJid  = mySimpleSession.getTransport().convertIDToJID(fromAddr);
			String msgContent = new String((byte []) request.getContent());
			
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Forwarding MESSAGE request as XMPP message, setting from = " +
			          senderJid + " and content = '" + msgContent + "'");
			
			mySimpleSession.getTransport().sendMessage(mySimpleSession.getJID(), senderJid, msgContent);
			
			mySimpleSession.sendResponse(responseCode, request);
		}
		else if (request.getMethod().equals(Request.NOTIFY)) {
			SubscriptionStateHeader subscriptionStateHeader = (SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME);
			
			Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  NOTIFY request handling process started.");
			
			if (subscriptionStateHeader.getState().equalsIgnoreCase(SubscriptionStateHeader.ACTIVE)) {
				Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  NOTIFY Active!");
				
				int expires = subscriptionStateHeader.getExpires();
				Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  NOTIFY Expiry = " + expires);
				
				try {
					Presence presence = new Presence();
					presence.setFrom(mySimpleSession.getTransport().convertIDToJID(fromAddr));
					presence.setTo(mySimpleSession.getJID());
					
					if (expires > 0) {
						String content = "";
						if (request.getContent() != null)
							content = new String((byte []) request.getContent());
						
						if (content.length() > 0) {
							SimplePresence simplePresence = SimplePresence.parseSimplePresence(content);
							if (simplePresence.getTupleStatus().isOpen()) {
								presence.setStatus("Online");
								Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  " +
								          "SIP user '" + fromAddr + "' is '" + simplePresence.getRpid().toString() + "'!");
								switch (simplePresence.getRpid()) {
									case AWAY:
										presence.setShow(Presence.Show.away);
										presence.setStatus("Away");
										break;
									case BUSY:
										presence.setShow(Presence.Show.dnd);
										presence.setStatus("Do Not Disturb");
										break;
									case HOLIDAY:
										presence.setShow(Presence.Show.xa);
										presence.setStatus("(SIP) On Holiday");
										break;
									case IN_TRANSIT:
										presence.setShow(Presence.Show.xa);
										presence.setStatus("(SIP) In Transit");
										break;
									case ON_THE_PHONE:
										presence.setShow(Presence.Show.away);
										presence.setStatus("On Phone");
										break;
									case PERMANENT_ABSENCE:
										presence.setStatus("Offline");
										break;
									case SLEEPING:
										presence.setShow(Presence.Show.away);
										presence.setStatus("(SIP) Idle");
										break;
									default:
										break;
								}
							}
						} else {
							presence.setStatus("Offline");
						}
					} else {
						presence.setType(Presence.Type.unsubscribed);
					}
					
					Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Sending XMPP presence packet.");
					mySimpleSession.getTransport().sendPacket(presence);
				} catch (Exception ex) {
					Log.debug("SimpleSessionListener(" + myUsername + ").processRequest:  Exception occured when processing NOTIFY packet...", ex);
				}
			}
			
			mySimpleSession.sendResponse(responseCode, request);
		}
		else if (request.getMethod().equals(Request.SUBSCRIBE)) {
			Log.debug("SimpleSessionListener for " + myUsername + ":  SUBSCRIBE request handling process.");
			
			mySimpleSession.sendResponse(202, request);
			
			Log.debug("SimpleSessionListener for " + myUsername + ":  SUBSCRIBE should be followed by a NOTIFY");
			
			// Send NOTIFY packet.
			try {
				mySimpleSession.sendNotify(request);
			}
			catch (Exception e) {
				Log.debug("SimpleSessionListener for " + myUsername + ":  Unable to prepare NOTIFY packet.", e);
			}
			
//			long seqNum = 1L;
//			if (request.getHeader(CSeqHeader.NAME) != null) {
//				seqNum = ((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber() + 1;
//			}
//			
//			int expires = 0;
//			if (request.getHeader(ExpiresHeader.NAME) != null) {
//				expires = ((ExpiresHeader) request.getHeader(ExpiresHeader.NAME)).getExpires();
//			}
//			
//			String callId = null;
//			if (request.getHeader(CallIdHeader.NAME) != null) {
//				callId = ((CallIdHeader) request.getHeader(CallIdHeader.NAME)).getCallId();
//			}
			
//			int maxForward = 0;
//			if (request.getHeader(MaxForwardsHeader.NAME) != null) {
//				maxForward = ((MaxForwardsHeader) request.getHeader(MaxForwardsHeader.NAME)).getMaxForwards();
//			}
//			if (maxForward > 0) maxForward--;
			
//			if (request.getHeader(RecordRouteHeader.NAME) != null) {
//				String recordRouteHeader = ((RecordRouteHeader) request.getHeader(RecordRouteHeader.NAME)).toString();
//			}
			
			// Send notify packet to show my own presence.
//			mySimpleSession.sendNotify(fromAddr, callId, seqNum, expires, maxForward, new Presence());
		}
	}
	
	public void processResponse(ResponseEvent responseEvent) {
		Log.debug("SimpleSessionListener for " + myUsername + ":  Received a response event:  " + responseEvent.getResponse().toString());
		
		String   fromAddr = "";
		String   toAddr   = "";
		Response response = responseEvent.getResponse();
		if (response.getHeader(FromHeader.NAME) != null) {
			FromHeader fromHeader = (FromHeader) response.getHeader(FromHeader.NAME);
			fromAddr = fromHeader.getAddress().getURI().toString();
		}
		if (response.getHeader(ToHeader.NAME) != null) {
			ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
			toAddr = toHeader.getAddress().getURI().toString();
		}
		
		if (response.getHeader(CSeqHeader.NAME) != null) {
			String method = ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).getMethod();
			if (method.equals(Request.REGISTER)) {
				if (response.getStatusCode() - response.getStatusCode() % 100 == 200) {
					int expires = 0;
					if (response.getHeader(ContactHeader.NAME) != null) {
						expires = ((ContactHeader) response.getHeader(ContactHeader.NAME)).getExpires();
					} else if (response.getHeader(ExpiresHeader.NAME) != null) {
						expires = ((ExpiresHeader) response.getHeader(ExpiresHeader.NAME)).getExpires();
					}
					
					if (expires > 0) {
						Log.debug("SimpleSessionListener(" + myUsername + ").processResponse:  " +
						          mySimpleSession.getRegistration().getUsername() + " log in successful!");
						
						mySimpleSession.getRegistration().setLastLogin(new Date());
						mySimpleSession.setLoginStatus(TransportLoginStatus.LOGGED_IN);
					}
					else {
						if (mySimpleSession.getLoginStatus().equals(TransportLoginStatus.LOGGING_OUT)) {
							Log.debug("SimpleSessionListener(" + myUsername + ").processResponse:  " +
							          mySimpleSession.getRegistration().getUsername() + " log out successful!");

							mySimpleSession.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
							mySimpleSession.removeStack();
						}
					}
				}
			}
			if (method.equals(Request.SUBSCRIBE)) {
				if (response.getStatusCode() - response.getStatusCode() % 100 == 200) {
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
						// Confirm addition of roster item
//						presence.setType(Presence.Type.subscribed);
						mySimpleSession.contactSubscribed(toAddr);
					} else {
						// Confirm deletion of roster item
//						presence.setType(Presence.Type.unsubscribed);
					}
					
					Log.debug("SimpleSessionListener for " + myUsername + ":  Handled SUBSCRIBE acknowledgement!!");
//					mySimpleSession.getTransport().sendPacket(presence);
				}
			}
		}
	}
	
	public void processTimeout(TimeoutEvent timeoutEvent) {
		Log.debug("SimpleSessionListener for " + myUsername + " received a timeout event:  " +
				timeoutEvent.getTimeout().toString());
	}
	
	public void processIOException(IOExceptionEvent iOExceptionEvent) {
		Log.debug("SimpleSessionListener for " + myUsername + " received an IOException event:  " +
				iOExceptionEvent.toString());
	}
	
	public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
		Log.debug("SimpleSessionListener(" + myUsername + "):  Received a TransactionTerminatedEvent [" + transactionTerminatedEvent.hashCode() + "]");
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