/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.xmpp;

import net.sf.kraken.BaseTransport;
import net.sf.kraken.protocols.xmpp.packet.AttentionExtension;
import net.sf.kraken.registration.Registration;
import net.sf.kraken.session.TransportSession;
import net.sf.kraken.type.PresenceType;
import net.sf.kraken.type.TransportLoginStatus;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.util.LocaleUtils;
import org.xmpp.packet.JID;

/**
 * XMPP Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 * @author Mehmet Ecevit
 */
public class XMPPTransport extends BaseTransport<XMPPBuddy> {

    static {
        final ProviderManager pm = ProviderManager.getInstance();
        pm.addExtensionProvider(AttentionExtension.ELEMENT_NAME, AttentionExtension.NAMESPACE, new AttentionExtension.Provider());
    }
    
    /*
     * @see net.sf.kraken.BaseTransport#getTerminologyUsername()
     */
	@Override
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway."+getType().toString()+".username", "kraken");
    }

    /*
     * @see net.sf.kraken.BaseTransport#getTerminologyPassword()
     */
	@Override
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.xmpp.password", "kraken");
    }

    /*
     * @see net.sf.kraken.BaseTransport#getTerminologyNickname()
     */
	@Override
    public String getTerminologyNickname() {
        return null;
    }

    /*
     * @see net.sf.kraken.BaseTransport#getTerminologyRegistration()
     */
	@Override
    public String getTerminologyRegistration() {
        return LocaleUtils.getLocalizedString("gateway."+getType().toString()+".registration", "kraken");
    }

    /*
     * @see net.sf.kraken.BaseTransport#isPasswordRequired()
     */
	@Override
    public Boolean isPasswordRequired() { return true; }

    /*
     * @see net.sf.kraken.BaseTransport#isNicknameRequired()
     */
	@Override
    public Boolean isNicknameRequired() { return false; }

    /*
     * @see net.sf.kraken.BaseTransport#isUsernameValid(String)
     */
	@Override
    public Boolean isUsernameValid(String username) {
        return true;
    }

    /**
     * Handles creating an OSCAR session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
	@Override
    public TransportSession<XMPPBuddy> registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        TransportSession<XMPPBuddy> session = new XMPPSession(registration, jid, this, priority);
        session.setLoginStatus(TransportLoginStatus.LOGGING_IN);
        session.logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a Yahoo session.
     *
     * @param session The session to be disconnected.
     */
	@Override
    public void registrationLoggedOut(TransportSession<XMPPBuddy> session) {
        session.setLoginStatus(TransportLoginStatus.LOGGING_OUT);
        session.logOut();
    }

    /**
     * Converts an XMPP/Smack status to a Gateway status.
     *
     * @param type Smack presence type.
     * @param mode Smack presence mode (show).
     * @return Gateway status.
     */
    public PresenceType convertXMPPStatusToGateway(org.jivesoftware.smack.packet.Presence.Type type, org.jivesoftware.smack.packet.Presence.Mode mode) {
        if (mode == Presence.Mode.away) {
            return PresenceType.away;
        }
        else if (mode == Presence.Mode.dnd) {
            return PresenceType.dnd;
        }
        else if (mode == Presence.Mode.xa) {
            return PresenceType.xa;
        }
        else if (mode == Presence.Mode.chat) {
            return PresenceType.chat;
        }
        else if (type == Presence.Type.unavailable) {
            return PresenceType.unavailable;
        }
        else if (type == Presence.Type.error) {
            return PresenceType.unavailable;
        }        
        else {
            return PresenceType.available;
        }
    }

    /**
     * Converts a Gateway status to an XMPP/Smack status.
     *
     * Returns null if the status is "unavailable".
     *
     * @param type Gateway status.
     * @return Smack presence mode.
     */
    public Presence.Mode convertGatewayStatusToXMPP(PresenceType type) {
        if (type == PresenceType.available) {
            return Presence.Mode.available;
        }
        else if (type == PresenceType.away) {
            return Presence.Mode.away;
        }
        else if (type == PresenceType.chat) {
            return Presence.Mode.chat;
        }
        else if (type == PresenceType.dnd) {
            return Presence.Mode.dnd;
        }
        else if (type == PresenceType.unavailable) {
            return null;
        }
        else if (type == PresenceType.xa) {
            return Presence.Mode.xa;
        }
        else if (type == PresenceType.unknown) {
            return Presence.Mode.available;
        }
        else {
            return Presence.Mode.available;
        }
    }

}
