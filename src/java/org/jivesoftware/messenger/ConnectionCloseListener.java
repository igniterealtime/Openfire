/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;

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
