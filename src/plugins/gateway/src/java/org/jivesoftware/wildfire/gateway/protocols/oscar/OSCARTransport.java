/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.oscar;

import org.jivesoftware.wildfire.gateway.*;
import org.xmpp.packet.JID;

/**
 * OSCAR Transport Interface.
 *
 * This handles the bulk of the XMPP work via BaseTransport and provides
 * some gateway specific interactions.
 *
 * @author Daniel Henninger
 */
public class OSCARTransport extends BaseTransport {

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyUsername()
     */
    public String getTerminologyUsername() {
        if (getType().equals(TransportType.icq)) {
            return "UIN/ICQ#";
        }
        else {
            return "ScreenName";
        }
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyPassword()
     */
    public String getTerminologyPassword() {
        return "Password";
    }

    /**
     * @see org.jivesoftware.wildfire.gateway.BaseTransport#getTerminologyRegistration()
     */
    public String getTerminologyRegistration() {
        if (getType().equals(TransportType.icq)) {
            return "Please enter your ICQ# (UIN) and password.";
        }
        else {
            return "Please enter your AIM ScreenName and password.";
        }
    }

    /**
     * Handles creating an OSCAR session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     * @param jid JID that is logged into the transport.
     * @param presenceType Type of presence.
     * @param verboseStatus Longer status description.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid, PresenceType presenceType, String verboseStatus, Integer priority) {
        TransportSession session = new OSCARSession(registration, jid, this, priority);
//        Thread sessionThread = new Thread(session);
//        sessionThread.start();
        ((OSCARSession)session).logIn(presenceType, verboseStatus);
        return session;
    }

    /**
     * Handles logging out of a Yahoo session.
     *
     * @param session The session to be disconnected.
     */
    public void registrationLoggedOut(TransportSession session) {
        ((OSCARSession)session).logOut();
//        session.sessionDone();
    }

}
