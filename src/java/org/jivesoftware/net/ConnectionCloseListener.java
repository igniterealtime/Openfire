/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.net;

/**
 * Implement and register with a connection to receive notification
 * of the connection closing.
 *
 * @author Iain Shigeoka
 */
public interface ConnectionCloseListener {
    /**
     * Called when a connection is closed.
     *
     * @param handback The handback object associated with the connection listener during Connection.registerCloseListener()
     */
    public void onConnectionClose(Object handback);
}
