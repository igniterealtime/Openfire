/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Default Jive connection provider, which uses an internal connection pool.<p>
 *
 * @author Jive Software
 */
public class DefaultConnectionProvider implements ConnectionProvider {

    private ConnectionPool connectionPool = null;

    private String driver;
    private String serverURL;
    private String username;
    private String password;
    private int minConnections = 3;
    private int maxConnections = 10;

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

    private Object initLock = new Object();

    /**
     * Creates a new DefaultConnectionProvider.
     */
    public DefaultConnectionProvider() {
        loadProperties();
    }

    public boolean isPooled() {
        return true;
    }

    public Connection getConnection() throws SQLException {
        if (connectionPool == null) {
            // block until the init has been done
            synchronized (initLock) {
                // if still null, something has gone wrong
                if (connectionPool == null) {
                    Log.error("DbConnectionDefaultPool.getConnection() was " +
                    "called before the internal pool has been initialized or " +
                    "there was a problem connecting to the database.");
                    // Attempt to connect again if setup was already done
                    if ("true".equals(JiveGlobals.getXMLProperty("setup"))) {
                        this.restart();
                        if (connectionPool == null) {
                            Log.error("Recovery logic failed to reconnect to the database.");
                            return null;
                        }
                    }
                    else {
                        return null;
                    }
                }
            }
        }

        return connectionPool.getConnection();
    }

    public void start() {
        // acquire lock so that no connections can be returned.
        synchronized (initLock) {

            try {
                connectionPool = new ConnectionPool(driver, serverURL, username,
                        password, minConnections, maxConnections, connectionTimeout,
                        mysqlUseUnicode);
            }
            catch (IOException e) {
                Log.error(e);
            }
        }
    }

    public void restart() {
        // Kill off pool.
        destroy();
        // Reload properties.
        loadProperties();
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
        // See if we should use Unicode under MySQL
        mysqlUseUnicode = Boolean.valueOf(JiveGlobals.getXMLProperty("database.mysql.useUnicode")).booleanValue();
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

        JiveGlobals.setXMLProperty("database.defaultProvider.minConnections",
                Integer.toString(minConnections));
        JiveGlobals.setXMLProperty("database.defaultProvider.maxConnections",
                Integer.toString(maxConnections));
        JiveGlobals.setXMLProperty("database.defaultProvider.connectionTimeout",
                Double.toString(connectionTimeout));
    }

    public String toString() {
        return connectionPool.toString();
    }
}
