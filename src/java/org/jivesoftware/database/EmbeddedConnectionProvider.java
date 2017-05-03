/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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

    private static final Logger Log = LoggerFactory.getLogger(EmbeddedConnectionProvider.class);

    private Properties settings;
    private String serverURL;
    private String driver = "org.hsqldb.jdbcDriver";
    private String proxoolURL;

    public EmbeddedConnectionProvider() {
    }

    @Override
    public boolean isPooled() {
        return true;
    }

    @Override
    public Connection getConnection() throws SQLException {
        try {
            Class.forName("org.logicalcobwebs.proxool.ProxoolDriver");
            return DriverManager.getConnection(proxoolURL, settings);
        }
        catch (ClassNotFoundException e) {
            throw new SQLException("EmbeddedConnectionProvider: Unable to find driver: "+e);
        }
    }

    @Override
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

    @Override
    public void restart() {
        // Kill off pool.
        destroy();
        // Start a new pool.
        start();
    }

    @Override
    public void destroy() {
        // Shutdown the database.
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = getConnection();
            pstmt = con.prepareStatement("SHUTDOWN");
            pstmt.execute();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        // Blank out the settings
        settings = null;
    }

    @Override
	public void finalize() throws Throwable {
        super.finalize();
        destroy();
    }
}