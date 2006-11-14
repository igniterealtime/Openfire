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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * An implementation of the Connection interface that wraps an underlying
 * Connection object. It releases the connection back to a connection pool
 * when Connection.close() is called.
 *
 * @author Jive Software
 */
public class ConnectionWrapper {

    private static Method close;

    static {
        try {
            close = Connection.class.getMethod("close");
        }
        catch (NoSuchMethodException e) {
            throw new NoSuchMethodError(e.getMessage());
        }
    }

    ConnectionPool pool;
    boolean checkedout = false;
    long createTime;
    long lockTime;
    long checkinTime;
    Exception exception;
    boolean hasLoggedException = false;
    private Connection poolConnection;

    public ConnectionWrapper(Connection connection, ConnectionPool pool) {
        setConnection(connection);

        this.pool = pool;
        createTime = System.currentTimeMillis();
        lockTime = createTime;
        checkinTime = lockTime;
    }

    public void setConnection(Connection connection) {
        if (connection == null) {
            this.poolConnection = null;
        }
        else {
            this.poolConnection = (Connection)java.lang.reflect.Proxy.newProxyInstance(
                    connection.getClass().getClassLoader(),
                    connection.getClass().getInterfaces(),
                    new ConnectionProxy(connection));
        }
    }

    public Connection getConnection() {
        return poolConnection;
    }

    public String toString() {
        if (poolConnection != null) {
            return poolConnection.toString();
        }
        else {
            return "Jive Software Connection Wrapper";
        }
    }

    public synchronized boolean isCheckedOut() {
        return checkedout;
    }

    /**
     * Dynamic proxy for connection object that returns connection to the pool when
     * closing. 
     */
    public class ConnectionProxy implements InvocationHandler {

        private Connection connection;

        public ConnectionProxy(Connection connection) {
            this.connection = connection;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.equals(close)) {
                close();
                return null;
            }
            else {
                // Invoke the method normally if all else fails.
                return method.invoke(connection, args);
            }
        }

        /**
         * Instead of closing the underlying connection, we simply release
         * it back into the pool.
         *
         * @throws SQLException if an SQL Exception occurs.
         */
        private void close() throws SQLException {
            synchronized (this) {
                checkedout = false;
                checkinTime = System.currentTimeMillis();
            }

            pool.freeConnection();

            // Release object references. Any further method calls on the connection will fail.
            poolConnection = null;
            connection = null;
        }
    }
}