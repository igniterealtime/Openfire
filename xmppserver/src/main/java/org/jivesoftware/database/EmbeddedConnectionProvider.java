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

import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A connection provider for the embedded hsqlDB database. The database file is stored at
 * {@code home/database}. The log file for this connection provider is stored at
 * {@code [home]/logs/EmbeddedConnectionProvider.log}, so you should ensure
 * that the {@code [home]/logs} directory exists.
 *
 * @author Matt Tucker
 */
public class EmbeddedConnectionProvider implements ConnectionProvider {

    private static final Logger Log = LoggerFactory.getLogger(EmbeddedConnectionProvider.class);

    private String serverURL;
    private PoolingDataSource<PoolableConnection> dataSource;

    public EmbeddedConnectionProvider() {
    }

    @Override
    public boolean isPooled() {
        return true;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Check HSQLDB properties; data source was not be initialised");
        }
        return dataSource.getConnection();
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
        final ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(serverURL, "sa", "");
        final PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setMaxConnLifetimeMillis((long) (0.5 * JiveConstants.DAY));

        final GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMinIdle(3);
        poolConfig.setMaxTotal(25);
        final GenericObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory, poolConfig);
        poolableConnectionFactory.setPool(connectionPool);
        dataSource = new PoolingDataSource<>(connectionPool);
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
    }

    @Override
    public void finalize() throws Throwable {
        destroy();
        super.finalize();
    }
}
