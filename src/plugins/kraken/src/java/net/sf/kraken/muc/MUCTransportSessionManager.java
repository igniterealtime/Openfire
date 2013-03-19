/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.muc;

import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.session.TransportSession;

import org.jivesoftware.util.NotFoundException;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.lang.ref.WeakReference;

/**
 * @author Daniel Henninger
 */
public class MUCTransportSessionManager<B extends TransportBuddy> {

    /**
     * Creates a MUC session manager for a transport session.
     *
     * @param session Transport session we are attached to.
     */
    public MUCTransportSessionManager(TransportSession<B> session) {
        this.transportSessionRef = new WeakReference<TransportSession<B>>(session);
    }

    /* The transport session we are attached to */
    public WeakReference<TransportSession<B>> transportSessionRef;

    /**
     * Retrieve the transport session the manager is associated with.
     *
     * @return transport session manager is associated with.
     */
    public TransportSession<B> getTransportSession() {
        return transportSessionRef.get();
    }

    /**
     * Container for all active sessions.
     */
    private Map<String,MUCTransportSession<B>> activeSessions = new HashMap<String,MUCTransportSession<B>>();

    /**
     * Retrieve the session instance for a given JID.
     *
     * Ignores the resource part of the jid.
     *
     * @param roomname Room name of the instance to be retrieved.
     * @throws NotFoundException if the given jid is not found.
     * @return MUCTransportSession instance requested.
     */
    public MUCTransportSession<B> getSession(String roomname) throws NotFoundException {
        MUCTransportSession<B> session = activeSessions.get(roomname.toLowerCase());
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
     * @param roomname Room name used to track the session.
     * @param session MUCTransportSession associated with the jid.
     */
    public void storeSession(String roomname, MUCTransportSession<B> session) {
        activeSessions.put(roomname.toLowerCase(), session);
    }

    /**
     * Removes a session instance with the legacy service.
     *
     * Expects to be given a JID which indicates which session we are
     * removing.
     *
     * @param roomname Room name to be removed.
     */
    public void removeSession(String roomname) {
        activeSessions.remove(roomname.toLowerCase());
    }

    /**
     * Retrieves a collection of all active sessions.
     *
     * @return List of active sessions.
     */
    public Collection<MUCTransportSession<B>> getSessions() {
        return activeSessions.values();
    }

}
