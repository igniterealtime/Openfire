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

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A connection provider for the embedded hsqlDB database. The database file is stored at
 * <tt>home/database</tt>. The log file for this connection provider is stored at
 * <tt>[home]/logs/EmbeddedConnectionProvider.log</tt>, so you should ensure
 * that the <tt>[home]/logs</tt> directory exists.
 *
 * @author Matt Tucker
 */
public class EmbeddedConnectionProvider implements ConnectionProvider {

    private ConnectionPool connectionPool = null;
    private final Object initLock = new Object();

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
                File databaseDir = new File(JiveGlobals.getHomeDirectory(), File.separator +
                        "embedded-db");
                // If the database doesn't exist, create it.
                if (!databaseDir.exists()) {
                    databaseDir.mkdirs();
                }

                String serverURL = "jdbc:hsqldb:" + databaseDir.getCanonicalPath() +
                        File.separator + "openfire";
                String username = "sa";
                String password = "";
                int minConnections = 3;
                int maxConnections = 25;
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
        if (connectionPool == null) {
            return;
        }
        // Shutdown the database.
        Connection con = null;
        try {
            con = getConnection();
            Statement stmt = con.createStatement();
            stmt.execute("SHUTDOWN");
            stmt.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        // Close the connection pool.
        try {
            connectionPool.destroy();
        }
        catch (Exception e) {
            Log.error(e);
        }
        // Release reference to connectionPool
        connectionPool = null;
    }

    public void finalize() throws Throwable {
        super.finalize();
        destroy();
    }
}