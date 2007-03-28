/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.msn;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.*;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import net.sf.jml.MsnUserStatus;

/**
 * MSN Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class MSNTransport extends BaseTransport {

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#getTerminologyUsername()
     */
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway.msn.username", "gateway");
    }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#getTerminologyPassword()
     */
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.msn.password", "gateway");
    }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#getTerminologyNickname()
     */
    public String getTerminologyNickname() {
        return null;
    }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#getTerminologyRegistration()
     */
    public String getTerminologyRegistration() {
        return LocaleUtils.getLocalizedString("gateway.msn.registration", "gateway");
    }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#isPasswordRequired()
     */
    public Boolean isPasswordRequired() { return true; }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#isNicknameRequired()
     */
    public Boolean isNicknameRequired() { return false; }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#isUsernameValid(String)
     */
    public Boolean isUsernameValid(String username) {
        return username.matches("[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+@[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+");
    }

    /**
     * Handles creating a MSN session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        Log.debug("Logging in to MSN gateway.");
        TransportSession session = new MSNSession(registration, jid, this, priority);
        this.getSessionManager().startThread(session);
        ((MSNSession)session).logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a MSN session.
     *
     * @param session The session to be disconnected.
     */
    public void registrationLoggedOut(TransportSession session) {
        Log.debug("Logging out of MSN gateway.");
        ((MSNSession)session).logOut();
        session.sessionDone();
        // Just in case.
        session.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    /**
     * Converts a jabber status to an MSN status.
     *
     * @param jabStatus Jabber presence type.
     * @return MSN user status id.
     */
    public MsnUserStatus convertJabStatusToMSN(PresenceType jabStatus) {
        if (jabStatus == PresenceType.available) {
            return MsnUserStatus.ONLINE;
        }
        else if (jabStatus == PresenceType.away) {
            return MsnUserStatus.AWAY;
        }
        else if (jabStatus == PresenceType.xa) {
            return MsnUserStatus.AWAY;
        }
        else if (jabStatus == PresenceType.dnd) {
            return MsnUserStatus.BUSY;
        }
        else if (jabStatus == PresenceType.chat) {
            return MsnUserStatus.ONLINE;
        }
        else if (jabStatus == PresenceType.unavailable) {
            return MsnUserStatus.OFFLINE;
        }
        else {
            return MsnUserStatus.ONLINE;
        }
    }

    /**
     * Sets up a presence packet according to MSN status.
     *
     * @param msnStatus MSN ContactStatus constant.
     * @param packet Presence packet to set up.
     */
    public void setUpPresencePacket(Presence packet, MsnUserStatus msnStatus) {
        if (msnStatus.equals(MsnUserStatus.ONLINE)) {
            // We're good, send as is..
        }
        else if (msnStatus.equals(MsnUserStatus.AWAY)) {
            packet.setShow(Presence.Show.away);
        }
        else if (msnStatus.equals(MsnUserStatus.BE_RIGHT_BACK)) {
            packet.setShow(Presence.Show.away);
        }
        else if (msnStatus.equals(MsnUserStatus.BUSY)) {
            packet.setShow(Presence.Show.dnd);
        }
        else if (msnStatus.equals(MsnUserStatus.IDLE)) {
            packet.setShow(Presence.Show.away);
        }
        else if (msnStatus.equals(MsnUserStatus.OFFLINE)) {
            packet.setType(Presence.Type.unavailable);
        }
        else if (msnStatus.equals(MsnUserStatus.ON_THE_PHONE)) {
            packet.setShow(Presence.Show.dnd);
        }
        else if (msnStatus.equals(MsnUserStatus.OUT_TO_LUNCH)) {
            packet.setShow(Presence.Show.xa);
        }
    }

}
