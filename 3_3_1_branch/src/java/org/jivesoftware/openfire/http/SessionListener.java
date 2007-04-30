/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.http;

/**
 * Listens for HTTP binding session events.
 *
 * @author Alexander Wenckus
 */
public interface SessionListener {

    /**
     * A connection was opened.
     *
     * @param session the session.
     * @param connection the connection.
     */
    public void connectionOpened(HttpSession session, HttpConnection connection);

    /**
     * A conneciton was closed.
     *
     * @param session the session.
     * @param connection the connection.
     */
    public void connectionClosed(HttpSession session, HttpConnection connection);

    /**
     * A session ended.
     *
     * @param session the session.
     */
    public void sessionClosed(HttpSession session);
}