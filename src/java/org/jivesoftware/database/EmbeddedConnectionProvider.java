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

import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.database.ConnectionPool;
import org.jivesoftware.database.ConnectionProvider;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A connection provider for the embedded hsqlDB database. The database file is stored at
 * <tt>jiveHome/database</tt>. The log file for this connection provider is stored at
 * <tt>[jiveHome]/logs/EmbeddedConnectionProvider.log</tt>, so you should ensure
 * that the <tt>[jiveHome]/logs</tt> directory exists.
 *
 * @author Matt Tucker
 */
public class EmbeddedConnectionProvider implements ConnectionProvider {

    private ConnectionPool connectionPool = null;

    private Object initLock = new Object();


    public boolean isPooled() {
        return true;
    }

    public Connection getConnection() throws SQLException {
        if (connectionPool == null) {
            // Block until the init has been done
            synchronized (initLock) {
                // If still null, something has gone wrong
                if (connectionPool == null) {
                    Log.error("Error: EmbeddedConnectionProvider.getConnection() was" +
                            "called before the internal pool has been initialized.");
                    return null;
                }
            }
        }
        return connectionPool.getConnection();
    }

    public void start() {
        // Acquire lock so that no connections can be returned while creating the pool.
        synchronized (initLock) {
            try {
                String driver = "org.hsqldb.jdbcDriver";
                File jiveHome = new File(JiveGlobals.getJiveHome(), File.separator + "database" + File.separator + "messenger");
                String path = jiveHome.getCanonicalPath();

                String serverURL = "jdbc:hsqldb:" + path;
                String username = "sa";
                String password = "jiverocks";
                int minConnections = 3;
                int maxConnections = 10;
                double connectionTimeout = 0.5;

                connectionPool = new ConnectionPool(driver, serverURL, username, password,
                        minConnections, maxConnections, connectionTimeout, false);
            }
            catch (IOException ioe) {
                Log.error("Error starting connection pool.", ioe);
            }
        }
    }

    public void restart() {
        // Kill off pool.
        destroy();
        // Start a new pool.
        start();
    }

    public void destroy() {
        if (connectionPool != null) {
            try {
                connectionPool.destroy();
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        // Release reference to connectionPool
        connectionPool = null;
    }

    public void finalize() {
        destroy();
    }
}
