/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.protocols.yahoo;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.openfire.gateway.*;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;
import org.openymsg.network.Status;

/**
 * Yahoo Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class YahooTransport extends BaseTransport {

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#getTerminologyUsername()
     */
    public String getTerminologyUsername() {
        return LocaleUtils.getLocalizedString("gateway.yahoo.username", "gateway");
    }

    /**
     * @see org.jivesoftware.openfire.gateway.BaseTransport#getTerminologyPassword()
     */
    public String getTerminologyPassword() {
        return LocaleUtils.getLocalizedString("gateway.yahoo.password", "gateway");
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
        return LocaleUtils.getLocalizedString("gateway.yahoo.registration", "gateway");
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
        return username.matches("[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+@?[^ \\p{Cntrl}()@,;:\\\\\"\\[\\]]+");
    }

    /**
     * Handles creating a Yahoo session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        Log.debug("Logging in to Yahoo gateway.");
        TransportSession session = new YahooSession(registration, jid, this, priority);
        this.getSessionManager().startThread(session);
        ((YahooSession)session).logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a Yahoo session.
     *
     * @param session The session to be disconnected.
     */
    public void registrationLoggedOut(TransportSession session) {
        Log.debug("Logging out of Yahoo gateway.");
        ((YahooSession)session).logOut();
        session.sessionDone();
        // Just in case.
        session.setLoginStatus(TransportLoginStatus.LOGGED_OUT);
    }

    /**
     * Converts a jabber status to an Yahoo status.
     *
     * @param jabStatus Jabber presence type.
     * @return Yahoo status identifier.
     */
    public Status convertJabStatusToYahoo(PresenceType jabStatus) {
        if (jabStatus == PresenceType.available) {
            return Status.AVAILABLE;
        }
        else if (jabStatus == PresenceType.away) {
            return Status.BRB;
        }
        else if (jabStatus == PresenceType.xa) {
            return Status.STEPPEDOUT;
        }
        else if (jabStatus == PresenceType.dnd) {
            return Status.BUSY;
        }
        else if (jabStatus == PresenceType.chat) {
            return Status.AVAILABLE;
        }
        else if (jabStatus == PresenceType.unavailable) {
            return Status.OFFLINE;
        }
        else {
            return Status.AVAILABLE;
        }
    }

    /**
     * Sets up a presence packet according to Yahoo status.
     *
     * @param packet Presence packet to be set up.
     * @param yahooStatus Yahoo StatusConstants constant.
     */
    public void setUpPresencePacket(Presence packet, Status yahooStatus) {
        if (yahooStatus == Status.AVAILABLE) {
            // We're good, leave the type as blank for available.
        }
        else if (yahooStatus == Status.BRB) {
            packet.setShow(Presence.Show.away);
        }
        else if (yahooStatus == Status.BUSY) {
            packet.setShow(Presence.Show.dnd);
        }
        else if (yahooStatus == Status.IDLE) {
            packet.setShow(Presence.Show.away);
        }
        else if (yahooStatus == Status.OFFLINE) {
            packet.setType(Presence.Type.unavailable);
        }
        else if (yahooStatus == Status.NOTATDESK) {
            packet.setShow(Presence.Show.away);
        }
        else if (yahooStatus == Status.NOTINOFFICE) {
            packet.setShow(Presence.Show.away);
        }
        else if (yahooStatus == Status.ONPHONE) {
            packet.setShow(Presence.Show.away);
        }
        else if (yahooStatus == Status.ONVACATION) {
            packet.setShow(Presence.Show.xa);
        }
        else if (yahooStatus == Status.OUTTOLUNCH) {
            packet.setShow(Presence.Show.xa);
        }
        else if (yahooStatus == Status.STEPPEDOUT) {
            packet.setShow(Presence.Show.away);
        }
        else {
            // Not something we handle, we're going to ignore it.
        }
    }
}
