/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.event;

import org.jivesoftware.openfire.session.Session;

/**
 * Interface to listen for session events. Use the
 * {@link SessionEventDispatcher#addListener(SessionEventListener)}
 * method to register for events.
 *
 * @author Matt Tucker
 */
public interface SessionEventListener {

    /**
     * A session was created.
     *
     * @param session the session.
     */
    public void sessionCreated(Session session);    

    /**
     * A session was destroyed
     *
     * @param session the session.
     */
    public void sessionDestroyed(Session session);

    /**
     * An anonymous session was created.
     *
     * @param session the session.
     */
    public void anonymousSessionCreated(Session session);
    
    /**
     * An anonymous session was created.
     *
     * @param session the session.
     */
    public void anonymousSessionDestroyed(Session session);
}