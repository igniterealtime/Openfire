/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway;

import java.util.*;

import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
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
     * Timer to check for orphaned sessions.
     */
    private Timer timer = new Timer();

    /**
     * Interval at which sessions are reaped.
     */
    private int reaperInterval = 300000; // 5 minutes

    /**
     * The actual repear task.
     */
    private SessionReaper sessionReaper;

    /**
     * The transport we are associated with.
     */
    BaseTransport transport;

    /**
     * Group of all threads related to this session.
     */
    public ThreadGroup threadGroup;

    /**
     * Creates the transport session manager instance and initializes.
     *
     * @param transport Transport associated with this session manager.
     */
    TransportSessionManager(BaseTransport transport) {
        this.transport = transport;
        sessionReaper = new SessionReaper();
        timer.schedule(sessionReaper, reaperInterval, reaperInterval);
    }

    /**
     * Shuts down the session manager.
     */
    public void shutdown() {
        sessionReaper.cancel();
        timer.cancel();
    }

    /**
     * Initialize the thread group manager.
     *
     * @param jid JID for naming purposes of the thread group manager.
     */
    public void startThreadManager(JID jid) {
        threadGroup = new ThreadGroup(jid.toString());
    }

    /**
     * Destroys the thread group manager.
     */
    public void stopThreadManager() {
        threadGroup.destroy();
    }

    /**
     * Starts a thread associated with a session.
     *
     * @param session Session the thread will be associated with.
     * @return A thread wrapped around the session.
     */
    public Thread startThread(TransportSession session) {
        // TODO: This does not work well.  Disabling.
//        Thread sessionThread = new Thread(threadGroup, session);
//        sessionThread.start();
//        return sessionThread;
        return null;
    }

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
     * retrieves the session instance for a given user.
     *
     * @param username Username of the instance to be retrieved.
     * @throws NotFoundException if the given username is not found.
     * @return TransportSession instance requested.
     */
    public TransportSession getSession(String username) throws NotFoundException {
        TransportSession session = activeSessions.get(XMPPServer.getInstance().createJID(username, null));
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
        try {
            getTransport().notifyRosterOffline(jid);
        }
        catch (UserNotFoundException e) {
            // Don't care
        }

    }

    /**
     * Retrieves a collection of all active sessions.
     *
     * @return List of active sessions.
     */
    public Collection<TransportSession> getSessions() {
        return activeSessions.values();
    }

    /**
     * Bury any transport sessions that no longer have an associated xmpp session.
     */
    private class SessionReaper extends TimerTask {
        /**
         * Kill any session that has been orphaned.
         */
        public void run() {
            cleanupOrphanedSessions();
        }
    }

    /**
     * Compares active xmpp sessions with active transport sessions and buries the orphaned.
     */
    private void cleanupOrphanedSessions() {
        SessionManager sessionManager = SessionManager.getInstance();
        for (TransportSession session : getSessions()) {
            if (sessionManager.getSessionCount(session.getJID().getNode()) == 0) {
                transport.registrationLoggedOut(session);
            }
        }
    }

    /**
     * Retrieves the transport this session manager is associated with.
     *
     * @return transport associated with this session manager.
     */
    public BaseTransport getTransport() {
        return this.transport;
    }
}
