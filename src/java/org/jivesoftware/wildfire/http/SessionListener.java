/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.wildfire.http;


/**
 *
 */
public interface SessionListener {
    public void connectionOpened(HttpSession session, HttpConnection connection);

    public void connectionClosed(HttpSession session, HttpConnection connection);

    public void sessionClosed(HttpSession session);
}
