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
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.TooManyListenersException;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.SipProvider;
import javax.sip.SipStack;
import javax.sip.TransportNotSupportedException;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.message.MessageFactory;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.BaseTransport;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportLoginStatus;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

/**
 * A transport implementation for SIMPLE protocol.
 * @author  Patrick Siu
 * @version 0.0.1
 */
public class SimpleTransport extends BaseTransport {
	SipFactory sipFactory = null;
	
	public SimpleTransport() {
		super();
		
		// Initialize the SipFactory
		sipFactory = SipFactory.getInstance();
		sipFactory.setPathName("gov.nist");
	}
	
	public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
		TransportSession session = new SimpleSession(registration, jid, this, priority); 
		
		// Possibly more work here!
		((SimpleSession) session).login(presenceType, verboseStatus);
		
		return session;
	}

	public void registrationLoggedOut(TransportSession session) {
		((SimpleSession) session).logout();
		
		((SimpleSession) session).removeStack();
		session.sessionDone();
		
        // Just in case.
		session.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
	}
	
	/**
	 */
	public String getTerminologyUsername() {
		return LocaleUtils.getLocalizedString("gateway.sip.username", "gateway");
	}

	public String getTerminologyPassword() {
		return LocaleUtils.getLocalizedString("gateway.sip.password", "gateway");
	}
	
	/**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyNickname()
     */
	public String getTerminologyNickname() {
		// If this string is needed, then take it.  Just put a draft code to ensure integrity.
		
		String result = null;
		return result;
	}
	
	/**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyRegistration()
     */
	public String getTerminologyRegistration() {
		return LocaleUtils.getLocalizedString("gateway.sip.registration", "gateway");
	}
	
	/**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isPasswordRequired()
     */
	public Boolean isPasswordRequired() {
		// Just put a draft code to ensure integrity.
		
		Boolean result = true;
		return result;
	}
	
	/**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isNicknameRequired()
     */
	public Boolean isNicknameRequired() {
		// Just put a draft code to ensure integrity.
		
		Boolean result = false;
		return result;
	}
	
	/**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#isUsernameValid(String)
     */
	public Boolean isUsernameValid(String username) {
		// Just put a draft code to ensure integrity.
		Log.debug("SimpleTransport.isUsernameValid:  Checking '" + username + "'");
//		Boolean result = username.matches("\\w+");
		Boolean result = username.matches("[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+@[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+");
		return result;
	}
	
	
	// The following code are generic custom classes for SIP-XMPP conversion.
	public SimplePresence convertJabStatusToSIP(PresenceType jabStatus) {
		SimplePresence simplePresence = new SimplePresence();
		
		switch (jabStatus) {
			case available:
//				simplePresence.setRpid(SimplePresence.Rpid.UNKNOWN);
				simplePresence.setDmNote("Online");
				break;
			case away:
				simplePresence.setRpid(SimplePresence.Rpid.AWAY);
				break;
			case chat:
				simplePresence.setRpid(SimplePresence.Rpid.OTHER);
				simplePresence.setDmNote("Free to chat");
				break;
			case dnd:
				simplePresence.setRpid(SimplePresence.Rpid.BUSY);
				break;
			case unavailable:
				simplePresence.setTupleStatus(SimplePresence.TupleStatus.CLOSED);
				break;
			case unknown:
				simplePresence.setRpid(SimplePresence.Rpid.UNKNOWN);
				break;
			case xa:
				simplePresence.setRpid(SimplePresence.Rpid.AWAY);
				break;
			default:
				break;
		}
		
		return simplePresence;
	}
	
	public void convertSIPStatusToJap(Presence presence, SimplePresence simplePresence) {
		if (simplePresence.getTupleStatus().isOpen()) {
			switch (simplePresence.getRpid()) {
				case APPOINTMENT:
					presence.setShow(Presence.Show.dnd);
					break;
				case AWAY:
					presence.setShow(Presence.Show.away);
					break;
				case BREAKFAST:
					presence.setShow(Presence.Show.xa);
					break;
				case BUSY:
					presence.setShow(Presence.Show.dnd);
					break;
				case DINNER:
					presence.setShow(Presence.Show.xa);
					break;
				case HOLIDAY:
					presence.setShow(Presence.Show.xa);
					break;
				case IN_TRANSIT:
					presence.setShow(Presence.Show.xa);
					break;
				case LOOKING_FOR_WORK:
					presence.setShow(Presence.Show.dnd);
					break;
				case LUNCH:
				case MEAL:
					presence.setShow(Presence.Show.xa);
					break;
				case MEETING:
					presence.setShow(Presence.Show.dnd);
					break;
				case ON_THE_PHONE:
					presence.setShow(Presence.Show.away);
					presence.setStatus("On Phone");
					break;
				case OTHER:
					break;
				case PERFORMANCE:
					presence.setShow(Presence.Show.dnd);
					break;
				case PERMANENT_ABSENCE:
					presence.setType(Presence.Type.unavailable);
					break;
				case PLAYING:
					presence.setShow(Presence.Show.away);
					break;
				case PRESENTATION:
					presence.setShow(Presence.Show.dnd);
					break;
				case SHOPPING:
					presence.setShow(Presence.Show.xa);
					break;
				case SLEEPING:
					presence.setShow(Presence.Show.xa);
					break;
				case SPECTATOR:
					presence.setShow(Presence.Show.xa);
					break;
				case STEERING:
					presence.setShow(Presence.Show.xa);
					break;
				case TRAVEL:
					presence.setShow(Presence.Show.xa);
					break;
				case TV:
					presence.setShow(Presence.Show.away);
					break;
				case UNKNOWN:
//					presence.setType(Presence.Type.unavailable);
					break;
				case VACATION:
					presence.setShow(Presence.Show.xa);
					break;
				case WORKING:
					presence.setShow(Presence.Show.dnd);
					break;
				case WORSHIP:
					presence.setShow(Presence.Show.dnd);
					break;
				default:
					break;
			}
		}
		else {
			presence.setType(Presence.Type.unavailable);
		}
	}
	
	
	/**
	 * An improved method to do the trick.
	 */
	public String convertJIDToID(JID jid) {
		String node = jid.getNode();
		while (!JID.unescapeNode(node).equals(node)) {
			node = JID.unescapeNode(node);
		}
		return node;
	}
	
	
	int portOffset = 0;
	synchronized int generateListenerPort() {
		return (ListeningPoint.PORT_5060 + (++portOffset));
	}
}
