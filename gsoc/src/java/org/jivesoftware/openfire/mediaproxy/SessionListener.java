/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.mediaproxy;

/**
 * Listener for media proxy session events.
 *
 * @author Thiago Camargo
 */
public interface SessionListener {

    /**
     * A media proxy session was closed as a result of normal termination or because
     * the max idle time elapsed.
     *
     * @param session the session that closed.
     */
    public void sessionClosed(MediaProxySession session);

}