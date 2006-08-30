/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway;

import java.util.*;

import org.jivesoftware.util.NotFoundException;
import org.xmpp.packet.JID;

/**
 * Manages sessions with legacy transports implementations.
 *
 * Keeps track of all of the active sessions with the various transports.
 * Only one expected to be associated with a single transport instance.
 *
 * @author Daniel Henninger
 */
public class TransportSessionManager {

    /**
     * Container for all active sessions.
     */
    private Map<JID,TransportSession> activeSessions = new HashMap<JID,TransportSession>();

    /**
     * Retrieve the session instance for a given JID.
     *
     * Ignores the resource part of the jid.
     *
     * @param jid JID of the instance to be retrieved.
     * @throws NotFoundException if the given jid is not found.
     * @return TransportSession instance requested.
     */
    public TransportSession getSession(JID jid) throws NotFoundException {
        TransportSession session = activeSessions.get(new JID(jid.toBareJID()));
        if (session == null) {
            throw new NotFoundException("Could not find session requested.");
        }
        return session;
    }

    /**
     * Stores a new session instance with the legacy service.
     *
     * Expects to be given a JID and a pre-created session.  Ignores the
     * resource part of the JID.
     *
     * @param jid JID information used to track the session.
     * @param session TransportSession associated with the jid.
     */
    public void storeSession(JID jid, TransportSession session) {
        activeSessions.put(new JID(jid.toBareJID()), session);
    }

    /**
     * Removes a session instance with the legacy service.
     *
     * Expects to be given a JID which indicates which session we are
     * removing.
     * 
     * @param jid JID to be removed.
     */
    public void removeSession(JID jid) {
        activeSessions.remove(new JID(jid.toBareJID()));
    }

    /**
     * Retrieves a collection of all active sessions.
     *
     * @return List of active sessions.
     */
    public Collection<TransportSession> getSessions() {
        return activeSessions.values();
    }

}
