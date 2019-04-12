/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Default Jive connection provider, which uses an internal connection pool.<p>
 *
 * @author Jive Software
 */
public class DefaultConnectionProvider implements ConnectionProvider {

    private static final Logger Log = LoggerFactory.getLogger(DefaultConnectionProvider.class);

    private String driver;
    private String serverURL;
    private String username;
    private String password;
    private int minConnections = 3;
    private int maxConnections = 10;
    private String testSQL = "";
    private Boolean testBeforeUse = true;
    private Boolean testAfterUse = true;
    private int testTimeout = (int) JiveConstants.SECOND / 2;
    private long timeBetweenEvictionRuns = 30 * JiveConstants.SECOND;
    private long minIdleTime = 15 * JiveConstants.MINUTE;
    private long maxWaitTime = (int) JiveConstants.SECOND / 2;
    private long refusedCount = 0;
    private PoolingDataSource<PoolableConnection> dataSource;
    private GenericObjectPool<PoolableConnection> connectionPool;

    /**
     * Maximum time a connection can be open before it's reopened (in days)
     */
    private double connectionTimeout = 0.5;

    /**
     * MySQL doesn't currently support Unicode. However, a workaround is
     * implemented in the mm.mysql JDBC driver. Setting the Jive property
     * database.mysql.useUnicode to true will turn this feature on.
     */
    private boolean mysqlUseUnicode;

    /**
     * Creates a new DefaultConnectionProvider.
     */
    public DefaultConnectionProvider() {
        loadProperties();
    }

    @Override
    public boolean isPooled() {
        return true;
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Check JDBC properties; data source was not be initialised");
        }
        // DBCP doesn't expose the number of refused connections, so count them ourselves
        try {
            return dataSource.getConnection();
        } catch (final SQLException e) {
            refusedCount++;
            throw e;
        }
    }

    @Override
    public void start() {

        try {
            Class.forName(driver);
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException("Unable to find JDBC driver " + driver, e);
        }

        final ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(serverURL, username, password);
        final PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setValidationQuery(testSQL);
        poolableConnectionFactory.setValidationQueryTimeout(testTimeout);
        poolableConnectionFactory.setMaxConnLifetimeMillis((long) (connectionTimeout * JiveConstants.DAY));

        final GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setTestOnBorrow(testBeforeUse);
        poolConfig.setTestOnReturn(testAfterUse);
        poolConfig.setMinIdle(minConnections);
        if( minConnections > GenericObjectPoolConfig.DEFAULT_MAX_IDLE )
        {
            poolConfig.setMaxIdle(minConnections);
        }
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRuns);
        poolConfig.setSoftMinEvictableIdleTimeMillis(minIdleTime);
        poolConfig.setMaxWaitMillis(maxWaitTime);
        connectionPool = new GenericObjectPool<>(poolableConnectionFactory, poolConfig);
        poolableConnectionFactory.setPool(connectionPool);
        dataSource = new PoolingDataSource<>(connectionPool);
    }

    @Override
    public void restart() {
    }

    @Override
    public void destroy() {
        try {
            dataSource.close();
        } catch (final Exception e) {
            Log.error("Unable to close the data source", e);
        }
    }

    /**
     * Returns the JDBC driver classname used to make database connections.
     * For example: com.mysql.jdbc.Driver
     *
     * @return the JDBC driver classname.
     */
    public String getDriver() {
        return driver;
    }

    /**
     * Sets the JDBC driver classname used to make database connections.
     * For example: com.mysql.jdbc.Driver
     *
     * @param driver the fully qualified JDBC driver name.
     */
    public void setDriver(String driver) {
        this.driver = driver;
        saveProperties();
    }

    /**
     * Returns the JDBC connection URL used to make database connections.
     *
     * @return the JDBC connection URL.
     */
    public String getServerURL() {
        return serverURL;
    }

    /**
     * Sets the JDBC connection URL used to make database connections.
     *
     * @param serverURL the JDBC connection URL.
     */
    public void setServerURL(String serverURL) {
        this.serverURL = serverURL;
        saveProperties();
    }

    /**
     * Returns the username used to connect to the database. In some cases,
     * a username is not needed so this method will return null.
     *
     * @return the username used to connect to the datbase.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username used to connect to the database. In some cases, a
     * username is not needed so null should be passed in.
     *
     * @param username the username used to connect to the database.
     */
    public void setUsername(String username) {
        this.username = username;
        saveProperties();
    }

    /**
     * Returns the password used to connect to the database. In some cases,
     * a password is not needed so this method will return null.
     *
     * @return the password used to connect to the database.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password used to connect to the database. In some cases, a
     * password is not needed so null should be passed in.
     *
     * @param password the password used to connect to the database.
     */
    public void setPassword(String password) {
        this.password = password;
        saveProperties();
    }

    /**
     * Returns the minimum number of connections that the pool will use. This
     * should probably be at least three.
     *
     * @return the minimum number of connections in the pool.
     */
    public int getMinConnections() {
        return minConnections;
    }

    /**
     * Sets the minimum number of connections that the pool will use. This
     * should probably be at least three.
     *
     * @param minConnections the minimum number of connections in the pool.
     */
    public void setMinConnections(int minConnections) {
        this.minConnections = minConnections;
        saveProperties();
    }

    /**
     * Returns the maximum number of connections that the pool will use. The
     * actual number of connections in the pool will vary between this value
     * and the minimum based on the current load.
     *
     * @return the max possible number of connections in the pool.
     */
    public int getMaxConnections() {
        return maxConnections;
    }

    /**
     * Sets the maximum number of connections that the pool will use. The
     * actual number of connections in the pool will vary between this value
     * and the minimum based on the current load.
     *
     * @param maxConnections the max possible number of connections in the pool.
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        saveProperties();
    }

    /**
     * Returns the amount of time between connection recycles in days. For
     * example, a value of .5 would correspond to recycling the connections
     * in the pool once every half day.
     *
     * @return the amount of time in days between connection recycles.
     */
    public double getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Sets the amount of time between connection recycles in days. For
     * example, a value of .5 would correspond to recycling the connections
     * in the pool once every half day.
     *
     * @param connectionTimeout the amount of time in days between connection
     *                          recycles.
     */
    public void setConnectionTimeout(double connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        saveProperties();
    }

    /**
     * Returns the SQL statement used to test if a connection is valid.
     *
     * @return the SQL statement that will be run to test a connection.
     */
    public String getTestSQL() {
        return testSQL;
    }

    public int getTestTimeout() {
        return testTimeout;
    }

    public long getTimeBetweenEvictionRunsMillis() {
        return connectionPool.getTimeBetweenEvictionRunsMillis();
    }

    public long getMinIdleTime() {
        return connectionPool.getSoftMinEvictableIdleTimeMillis();
    }

    public int getActiveConnections() {
        return connectionPool.getNumActive();
    }

    public int getIdleConnections() {
        return connectionPool.getNumIdle();
    }

    public long getConnectionsServed() {
        return connectionPool.getBorrowedCount();
    }

    public long getRefusedCount() {
        return refusedCount;
    }

    public long getMaxWaitTime() {
        return connectionPool.getMaxWaitMillis();
    }

    public long getMeanBorrowWaitTime() {
        return connectionPool.getMeanBorrowWaitTimeMillis();
    }

    public long getMaxBorrowWaitTime() {
        return connectionPool.getMaxBorrowWaitTimeMillis();
    }

    /**
     * Sets the SQL statement used to test if a connection is valid.  House keeping
     * and before/after connection tests make use of this.  This
     * should be something that causes the minimal amount of work by the database
     * server and is as quick as possible.
     *
     * @param testSQL the SQL statement that will be run to test a connection.
     */
    public void setTestSQL(String testSQL) {
        this.testSQL = testSQL;
    }

    /**
     * Returns whether returned connections will be tested before being handed over
     * to be used.
     *
     * @return True if connections are tested before use.
     */
    public Boolean getTestBeforeUse() {
        return testBeforeUse;
    }

    /**
     * Sets whether connections will be tested before being handed over to be used.
     *
     * @param testBeforeUse True or false if connections are to be tested before use.
     */
    public void setTestBeforeUse(Boolean testBeforeUse) {
        this.testBeforeUse = testBeforeUse;
    }

    /**
     * Returns whether returned connections will be tested after being returned to
     * the pool.
     *
     * @return True if connections are tested after use.
     */
    public Boolean getTestAfterUse() {
        return testAfterUse;
    }

    /**
     * Sets whether connections will be tested after being returned to the pool.
     *
     * @param testAfterUse True or false if connections are to be tested after use.
     */
    public void setTestAfterUse(Boolean testAfterUse) {
        this.testAfterUse = testAfterUse;
    }

    public boolean isMysqlUseUnicode() {
        return mysqlUseUnicode;
    }

    /**
     * Load properties that already exist from Jive properties.
     */
    private void loadProperties() {

        driver = JiveGlobals.getXMLProperty("database.defaultProvider.driver");
        serverURL = JiveGlobals.getXMLProperty("database.defaultProvider.serverURL");
        username = JiveGlobals.getXMLProperty("database.defaultProvider.username");
        password = JiveGlobals.getXMLProperty("database.defaultProvider.password");
        String minCons = JiveGlobals.getXMLProperty("database.defaultProvider.minConnections");
        String maxCons = JiveGlobals.getXMLProperty("database.defaultProvider.maxConnections");
        String conTimeout = JiveGlobals.getXMLProperty("database.defaultProvider.connectionTimeout");
        testSQL = JiveGlobals.getXMLProperty("database.defaultProvider.testSQL", DbConnectionManager.getTestSQL(driver));
        testBeforeUse = JiveGlobals.getXMLProperty("database.defaultProvider.testBeforeUse", false);
        testAfterUse = JiveGlobals.getXMLProperty("database.defaultProvider.testAfterUse", false);
        testTimeout = JiveGlobals.getXMLProperty("database.defaultProvider.testTimeout", (int) JiveConstants.SECOND / 2);
        timeBetweenEvictionRuns = JiveGlobals.getXMLProperty("database.defaultProvider.timeBetweenEvictionRuns", (int) (30 * JiveConstants.SECOND));
        minIdleTime = JiveGlobals.getXMLProperty("database.defaultProvider.minIdleTime", (int) (15 * JiveConstants.MINUTE));
        maxWaitTime = JiveGlobals.getXMLProperty("database.defaultProvider.maxWaitTime", (int) JiveConstants.SECOND / 2);

        // See if we should use Unicode under MySQL
        mysqlUseUnicode = Boolean.valueOf(JiveGlobals.getXMLProperty("database.mysql.useUnicode"));
        try {
            if (minCons != null) {
                minConnections = Integer.parseInt(minCons);
            }
            if (maxCons != null) {
                maxConnections = Integer.parseInt(maxCons);
            }
            if (conTimeout != null) {
                connectionTimeout = Double.parseDouble(conTimeout);
            }
        }
        catch (Exception e) {
            Log.error("Error: could not parse default pool properties. " +
                    "Make sure the values exist and are correct.", e);
        }
    }

    /**
     * Save properties as Jive properties.
     */
    private void saveProperties() {

        JiveGlobals.setXMLProperty("database.defaultProvider.driver", driver);
        JiveGlobals.setXMLProperty("database.defaultProvider.serverURL", serverURL);
        JiveGlobals.setXMLProperty("database.defaultProvider.username", username);
        JiveGlobals.setXMLProperty("database.defaultProvider.password", password);
        JiveGlobals.setXMLProperty("database.defaultProvider.testSQL", testSQL);
        JiveGlobals.setXMLProperty("database.defaultProvider.testBeforeUse", testBeforeUse.toString());
        JiveGlobals.setXMLProperty("database.defaultProvider.testAfterUse", testAfterUse.toString());
        JiveGlobals.setXMLProperty("database.defaultProvider.testTimeout", String.valueOf(testTimeout));
        JiveGlobals.setXMLProperty("database.defaultProvider.timeBetweenEvictionRuns", String.valueOf(timeBetweenEvictionRuns));
        JiveGlobals.setXMLProperty("database.defaultProvider.minIdleTime", String.valueOf(minIdleTime));
        JiveGlobals.setXMLProperty("database.defaultProvider.maxWaitTime", String.valueOf(maxWaitTime));

        JiveGlobals.setXMLProperty("database.defaultProvider.minConnections", Integer.toString(minConnections));
        JiveGlobals.setXMLProperty("database.defaultProvider.maxConnections", Integer.toString(maxConnections));
        JiveGlobals.setXMLProperty("database.defaultProvider.connectionTimeout", Double.toString(connectionTimeout));
    }
}
