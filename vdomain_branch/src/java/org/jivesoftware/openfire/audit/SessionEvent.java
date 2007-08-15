/**
 * $RCSfile$
 * $Revision: 38 $
 * $Date: 2004-10-21 03:30:10 -0300 (Thu, 21 Oct 2004) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.audit;

import org.jivesoftware.openfire.session.Session;

import java.util.Date;

/**
 * Events that occur during the session life cycle are repreented
 * by SessionEvents.
 *
 * @author Iain Shigeoka
 */
public class SessionEvent extends AuditEvent {

    /**
     * Session events use the code 1
     */
    public static final int SESSION_CODE = 1;

    // Session reasons
    public static final int SESSION_CONNECT = 1;
    public static final int SESSION_STREAM = 2;
    public static final int SESSION_AUTH_FAILURE = 3;
    public static final int SESSION_AUTH_SUCCESS = 4;
    public static final int SESSION_DISCONNECT = 10;

    /**
     * Session events can only be created using static factory methods.
     *
     * @param eventSession the session that this event is recording.
     * @param eventReason the reason the event is called.
     * @param data the data to associate with the event.
     */
    private SessionEvent(Session eventSession, int eventReason, String data) {
        super(eventSession, new Date(), SESSION_CODE, eventReason, data);
    }

    /**
     * Create an event associated with the initial connection
     * of a session before the stream is created.
     *
     * @param session the session that was connected.
     * @return an event representing the connection event.
     */
    public static SessionEvent createConnectEvent(Session session) {
        return new SessionEvent(session, SESSION_CONNECT, null);
    }

    /**
     * Create an event associated with the establishment of an XMPP session.
     * A connect event that is not followed by a stream event indicates
     * the connection was rejected.
     *
     * @param session the session that began streaming.
     * @return an event representing the connection event.
     */
    public static SessionEvent createStreamEvent(Session session) {
        return new SessionEvent(session, SESSION_STREAM, null);
    }

    /**
     * Create an event associated with the failure of a session to authenticate.
     *
     * @param session the session that made the attempt
     * @param user the user that made the attempt
     * @param resource the resource used for the attempt
     * @return an event representing the connection event
     */
    public static SessionEvent createAuthFailureEvent(Session session, String user,
            String resource)
    {
        return new SessionEvent(session, SESSION_AUTH_FAILURE,
                "User: " + user + " Resource: " + resource);
    }

    /**
     * Create an event associated with a successful authentication.
     *
     * @param session the session that authenticated.
     * @return an event representing the connection event.
     */
    public static SessionEvent createAuthSuccessEvent(Session session) {
        return new SessionEvent(session, SESSION_AUTH_SUCCESS, null);
    }

    /**
     * Create an event associated with the closing of a session.
     *
     * @param session the session that was disconnected.
     * @return an event representing the connection event.
     */
    public static SessionEvent createDisconnectEvent(Session session) {
        return new SessionEvent(session, SESSION_DISCONNECT, null);
    }
    
}