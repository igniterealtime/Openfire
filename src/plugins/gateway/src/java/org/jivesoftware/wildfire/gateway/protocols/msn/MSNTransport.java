/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.msn;

import org.hn.sleek.jmml.ContactStatus;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.BaseTransport;
import org.jivesoftware.wildfire.gateway.PresenceType;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

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
     * Handles creating a MSN session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus) {
        Log.debug("Logging in to MSN gateway.");
        TransportSession session = new MSNSession(registration, jid, this);
        session.start();
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
        session.stop();
    }

    /**
     * Converts a jabber status to an MSN status.
     *
     * @param jabStatus Jabber presence type.
     */
    public String convertJabStatusToMSN(PresenceType jabStatus) {
        if (jabStatus == PresenceType.available) {
            return ContactStatus.ONLINE;
        }
        else if (jabStatus == PresenceType.away) {
            return ContactStatus.AWAY;
        }
        else if (jabStatus == PresenceType.xa) {
            return ContactStatus.AWAY;
        }
        else if (jabStatus == PresenceType.dnd) {
            return ContactStatus.BUSY;
        }
        else if (jabStatus == PresenceType.chat) {
            return ContactStatus.ONLINE;
        }
        else if (jabStatus == PresenceType.unavailable) {
            return ContactStatus.OFFLINE;
        }
        else {
            return ContactStatus.ONLINE;
        }
    }

    /**
     * Sets up a presence packet according to MSN status.
     *
     * @param msnStatus MSN ContactStatus constant.
     */
    public void setUpPresencePacket(Presence packet, String msnStatus) {
        if (msnStatus.equals(ContactStatus.ONLINE)) {
            // We're good, send as is..
        }
        else if (msnStatus.equals(ContactStatus.AWAY)) {
            packet.setShow(Presence.Show.away);
        }
        else if (msnStatus.equals(ContactStatus.BE_RIGHT_BACK)) {
            packet.setShow(Presence.Show.away);
        }
        else if (msnStatus.equals(ContactStatus.BUSY)) {
            packet.setShow(Presence.Show.dnd);
        }
        else if (msnStatus.equals(ContactStatus.IDLE)) {
            packet.setShow(Presence.Show.away);
        }
        else if (msnStatus.equals(ContactStatus.OFFLINE)) {
            packet.setType(Presence.Type.unavailable);
        }
        else if (msnStatus.equals(ContactStatus.ON_THE_PHONE)) {
            packet.setShow(Presence.Show.dnd);
        }
        else if (msnStatus.equals(ContactStatus.OUT_TO_LUNCH)) {
            packet.setShow(Presence.Show.xa);
        }
    }

}
