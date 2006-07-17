/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.yahoo;

import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.gateway.BaseTransport;
import org.jivesoftware.wildfire.gateway.Registration;
import org.jivesoftware.wildfire.gateway.TransportSession;
import org.xmpp.packet.JID;

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
     * Handles creating a Yahoo session and triggering a login.
     *
     * @param registration Registration information to be used to log in.
     */
    public TransportSession registrationLoggedIn(Registration registration, JID jid) {
        Log.debug("Logging in to Yahoo gateway.");
        TransportSession session = new YahooSession(registration, jid, this);
        session.logIn();
        return session;
    }

    /**
     * Handles logging out of a Yahoo session.
     *
     * @param session The session to be disconnected.
     */
    public void registrationLoggedOut(TransportSession session) {
        Log.debug("Logging out of Yahoo gateway.");
        session.logOut();
    }

}
