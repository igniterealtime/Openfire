/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.DriverManager;
import java.util.Properties;

/**
 * A connection provider for the embedded hsqlDB database. The database file is stored at
 * <tt>home/database</tt>. The log file for this connection provider is stored at
 * <tt>[home]/logs/EmbeddedConnectionProvider.log</tt>, so you should ensure
 * that the <tt>[home]/logs</tt> directory exists.
 *
 * @author Matt Tucker
 */
public class EmbeddedConnectionProvider implements ConnectionProvider {

    private Properties settings;
    private String serverURL;
    private String driver = "org.hsqldb.jdbcDriver";
    private String proxoolURL;

    public EmbeddedConnectionProvider() {
        System.setProperty("org.apache.commons.logging.LogFactory", "org.jivesoftware.util.log.util.CommonsLogFactory");
    }

    public boolean isPooled() {
        return true;
    }

    public Connection getConnection() throws SQLException {
        try {
            Class.forName("org.logicalcobwebs.proxool.ProxoolDriver");
            return DriverManager.getConnection(proxoolURL, settings);
        }
        catch (ClassNotFoundException e) {
            throw new SQLException("EmbeddedConnectionProvider: Unable to find driver: ", e);
        }
    }

    public void start() {
        File databaseDir = new File(JiveGlobals.getHomeDirectory(), File.separator + "embedded-db");
        // If the database doesn't exist, create it.
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }

        try {
            serverURL = "jdbc:hsqldb:" + databaseDir.getCanonicalPath() + File.separator + "openfire";
        }
        catch (IOException ioe) {
            Log.error("EmbeddedConnectionProvider: Error starting connection pool: ", ioe);
        }
        proxoolURL = "proxool.openfire:"+driver+":"+serverURL;
        settings = new Properties();
        settings.setProperty("proxool.maximum-connection-count", "25");
        settings.setProperty("proxool.minimum-connection-count", "3");
        settings.setProperty("proxool.maximum-connection-lifetime", Integer.toString((int)(86400000 * 0.5)));
        settings.setProperty("user", "sa");
        settings.setProperty("password", "");
    }

    public void restart() {
        // Kill off pool.
        destroy();
        // Start a new pool.
        start();
    }

    public void destroy() {
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
        // Blank out the settings
        settings = null;
    }

    public void finalize() throws Throwable {
        super.finalize();
        destroy();
    }
}