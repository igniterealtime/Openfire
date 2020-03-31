package org.jivesoftware.database;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.jivesoftware.openfire.user.property.JDBCUserPropertyProvider;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class manages the JDBC connections to an external dabatase used by the following classes:
 * <ul>
 *     <li>{@link org.jivesoftware.openfire.user.JDBCUserProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.user.property.JDBCUserPropertyProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.group.JDBCGroupProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.admin.JDBCAdminProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.auth.JDBCAuthProvider}</li>
 * </ul>
 * <p>
 * When using one or more of the aforementioned classes, you <u>must</u> setup the class
 * {@link ExternalDbConnectionProperties}. Check the documentation of this class to see the available properties.
 */
public class ExternalDbConnectionManager {
    private static final Logger Log = LoggerFactory.getLogger(ExternalDbConnectionManager.class);

    private static ExternalDbConnectionManager instance;
    private static final Map<String, DefaultConnectionProvider> mapOfConnectionProviders = new ConcurrentHashMap<>();

    private ExternalDbConnectionManager() {
    }


    /**
     * Use this no-parameter {@code getInstance()} if you wish to use the same external database
     * for all your JDBC providers (including for {@link JDBCUserPropertyProvider}).
     * @return The instance of {@link ExternalDbConnectionManager} with the {@link DefaultConnectionProvider}
     * created from the default properties of {@link ExternalDbConnectionProperties}
     */
    public static synchronized ExternalDbConnectionManager getInstance() {
        return getInstance(ExternalDbConnectionProperties.getDefault());
    }

    /**
     * Get the instance of {@link ExternalDbConnectionManager} with inside a newly
     * created created connection to the external database
     * @param connProps Properties defining the connection to the external database. Must be not null.
     * @return The instance of {@link ExternalDbConnectionManager}
     */
    public static synchronized ExternalDbConnectionManager getInstance(ExternalDbConnectionProperties connProps) {
        if (instance == null) {
            instance = new ExternalDbConnectionManager();
        }
        if (connProps != null) {
            mapOfConnectionProviders.computeIfAbsent(connProps.getKey(), (l) -> createConnectionManager(connProps));
        } else {
            Log.error("getInstance() - Impossible to create new database connections with null properties");
        }

        return instance;
    }


    /**
     * Create a new {@link DefaultConnectionProvider} and set it up with the properties from
     * {@link ExternalDbConnectionProperties}.
     * @param cProps Properties defining the connection to the database
     * @return The newly created {@link DefaultConnectionProvider}
     */
    private static DefaultConnectionProvider createConnectionManager(@NonNull ExternalDbConnectionProperties cProps) {
        DefaultConnectionProvider dcp = new DefaultConnectionProvider(true);

        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.DRIVER));
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.CONN_STRING));
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.USERNAME));
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.PWD));
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.POOL_MIN_CONN));
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.POOL_MAX_CONN));
        JiveGlobals.migrateProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.CONN_TIMEOUT));

        // This case is the one where we use an external DB
        String jdbcDriver = JiveGlobals.getProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.DRIVER));
        try {
            // Check if the driver is available
            Class.forName(jdbcDriver);
        } catch (Exception e) {
            Log.error("ExternalDbConnectionManager() Fatal : Unable to load JDBC driver: " + jdbcDriver, e);
            return null;
        }

        dcp.setDriver(jdbcDriver);
        dcp.setServerURL(JiveGlobals.getProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.CONN_STRING)));
        dcp.setMinConnections(JiveGlobals.getIntProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.POOL_MIN_CONN), 1));
        dcp.setMaxConnections(JiveGlobals.getIntProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.POOL_MAX_CONN), 10_000));
        dcp.setConnectionTimeout(JiveGlobals.getDoubleProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.CONN_TIMEOUT), 1));
        dcp.setTestBeforeUse(false);
        dcp.setTestAfterUse(false);
        dcp.setTestSQL("select 1");

        String username = JiveGlobals.getProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.USERNAME));
        if (StringUtils.isNotBlank(username)) {
            dcp.setUsername(username);
        }
        String pwd = JiveGlobals.getProperty(cProps.getProps().get(ExternalDbConnectionProperties.DbConPropKeys.PWD));
        if (StringUtils.isNotBlank(pwd)) {
            dcp.setPassword(pwd);
        }

        dcp.start();
        return dcp;
    }


    /**
     * Try to get a connection from the underlying {@link DefaultConnectionProvider}.<br/>
     * The connection will be on the database defined by the default properties of
     * {@link ExternalDbConnectionProperties}
     * @return Return a connection to the external database
     * @throws SQLException Throws a SQLException if the underlying getConnection() throws a SQLException
     */
    public Connection getConnection() throws SQLException {
        return this.getConnection(ExternalDbConnectionProperties.DEFAULT_EXTERNAL_DB_PROVIDER_KEY);
    }

    /**
     * Try to get a connection from the underlying {@link DefaultConnectionProvider}
     * @param key The key of the object asking for a Connection to the database
     * @return Return a SQL connection to the database
     * @throws SQLException Throws a SQLException if the underlying getConnection() throws a SQLException
     */
    public Connection getConnection(String key) throws SQLException {
        Connection con;
        int maxTry = 5;
        int currentTries = 0;
        do {
            try {
                con = mapOfConnectionProviders.get(key).getConnection();
                return con;
            } catch (NullPointerException npe) {
                Log.error("getConnection() the ExternalDbConnectionManager was not correctly configured: " +
                  "DefaultConnectionProvider.get() for key'{}' is null", key, npe);
            } catch (Exception e) {
                String msg = "getConnection() For serverURL" + mapOfConnectionProviders.get(key).getServerURL() +
                    "try number " + currentTries + " failed with exception:";
                Log.error(msg, e);
            }
            currentTries++;

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                String msg = "getConnection() Interrupted waiting for DB connection";
                Log.info(msg, ex);
                Thread.currentThread().interrupt();
                throw new SQLException(msg, ex);
            }

        } while (currentTries < maxTry);

        throw new SQLException("getConnection() exception trying to get a connection to "+mapOfConnectionProviders.get(key).getServerURL());
    }




}
