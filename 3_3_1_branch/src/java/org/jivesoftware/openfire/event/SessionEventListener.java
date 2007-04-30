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
     * Notification event indicating that a user has authenticated with the server. The
     * authenticated user is not an anonymous user.
     *
     * @param session the authenticated session of a non anonymous user.
     */
    public void sessionCreated(Session session);    

    /**
     * An authenticated session of a non anonymous user was destroyed.
     *
     * @param session the authenticated session of a non anonymous user.
     */
    public void sessionDestroyed(Session session);

    /**
     * Notification event indicating that an anonymous user has authenticated with the server.
     *
     * @param session the authenticated session of an anonymous user.
     */
    public void anonymousSessionCreated(Session session);
    
    /**
     /**
      * An authenticated session of an anonymous user was destroyed.
      *
      * @param session the authenticated session of an anonymous user.
     */
    public void anonymousSessionDestroyed(Session session);
}