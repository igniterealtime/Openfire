/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

/**
 * Interface that let observers be notified when the server has been started or is
 * about to be stopped. Use {@link XMPPServer#addServerListener(XMPPServerListener)} to
 * add new listeners.
 *
 * @author Gaston Dombiak
 */
public interface XMPPServerListener {

    /**
     * Notification message indicating that the server has been started. At this point
     * all server modules have been initialized and started. Message sending and receiving
     * is now possible. However, some plugins may still be pending to be loaded.
     */
    void serverStarted();

    /**
     * Notification message indication that the server is about to be stopped. At this point
     * all modules are still running so all services are still available.
     */
    void serverStopping();
}
