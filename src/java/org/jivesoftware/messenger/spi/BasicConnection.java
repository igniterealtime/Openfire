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
package org.jivesoftware.messenger.spi;

import org.jivesoftware.messenger.Connection;
import org.jivesoftware.messenger.ConnectionCloseListener;
import org.jivesoftware.messenger.Session;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Connection helper base class. Automates common connection management
 * tasks without specific knowledge of the underlying connection provider.
 *
 * @author Iain Shigeoka
 */
abstract public class BasicConnection implements Connection {
    private Map listeners = new HashMap();

    public void init(Session session) {
    }

    public Object registerCloseListener(ConnectionCloseListener listener, Object handbackMessage) {
        Object status = null;
        if (isClosed()) {
            listener.onConnectionClose(handbackMessage);
        }
        else {
            status = listeners.put(listener, handbackMessage);
        }
        return status;
    }

    public Object removeCloseListener(ConnectionCloseListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Notifies all close listeners that the connection has been closed.
     * Used by subclasses to properly finish closing the connection.
     */
    protected void notifyCloseListeners() {
        synchronized (listeners) {
            Iterator itr = listeners.keySet().iterator();
            while (itr.hasNext()) {
                ConnectionCloseListener listener = (ConnectionCloseListener)itr.next();
                listener.onConnectionClose(listeners.get(listener));
            }
        }
    }
}
