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

package org.jivesoftware.database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * An implementation of the Connection interface that wraps an underlying
 * Connection object. It releases the connection back to a connection pool
 * when Connection.close() is called.
 *
 * @author Jive Software
 */
public class ConnectionWrapper extends AbstractConnection {

    public ConnectionPool pool;
    public boolean checkedout = false;
    public long createTime;
    public long lockTime;
    public long checkinTime;
    public Exception exception;
    public boolean hasLoggedException = false;

    public ConnectionWrapper(Connection connection, ConnectionPool pool) {
        super(connection);

        this.pool = pool;
        createTime = System.currentTimeMillis();
        lockTime = createTime;
        checkinTime = lockTime;
    }

    public void setConnection(Connection connection) {
        super.connection = connection;
    }

    /**
     * Instead of closing the underlying connection, we simply release
     * it back into the pool.
     */
    public void close() throws SQLException {
        synchronized (this) {
            checkedout = false;
            checkinTime = System.currentTimeMillis();
        }

        pool.freeConnection();

        // Release object references. Any further method calls on the connection will fail.
        // super.connection = null;
    }

    public String toString() {
        if (connection != null) {
            return connection.toString();
        }
        else {
            return "Jive Software Connection Wrapper";
        }
    }

    public synchronized boolean isCheckedOut() {
        return checkedout;
    }
}
