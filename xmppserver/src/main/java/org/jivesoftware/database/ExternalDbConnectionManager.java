package org.jivesoftware.database;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * This class manages the JDBC connection to the external dabatase that can be defined for the classes:
 * <ul>
 *     <li>{@link org.jivesoftware.openfire.user.JDBCUserProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.user.property.JDBCUserPropertyProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.group.JDBCGroupProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.admin.JDBCAdminProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.auth.JDBCAuthProvider}</li>
 * </ul>
 * <p>
 * When using one or more of the aforementioned classes, you <u>must define</u> at least the following two properties:
 * <ul>
 *     <li>{@code jdbcProvider.driver = com.mysql.jdbc.Driver} (example)</li>
 *     <li>{@code jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret}</li>
 * </ul>
 * <p>
 * And optionally, you can define the following properties to manage the connections:
 * <ul>
 *     <li>{@code jdbcProvider.username = user} (if not defined in the connection string)</li>
 *     <li>{@code jdbcProvider.password = password} (if not defined in the connection string)</li>
 *     <li>{@code jdbcProvider.poolMinConnection = Integer > 0}</li>
 *     <li>{@code jdbcProvider.poolMaxConnection = Integer > poolMinConnection}</li>
 *     <li>{@code jdbcProvider.connectionTimeout = Double, eg 0.5 (in Day max time before a connection is forcibly renewed)}</li>
 * </ul>
 * <p>
 */
public class ExternalDbConnectionManager {
    private static final Logger Log = LoggerFactory.getLogger(ExternalDbConnectionManager.class);

    private static ExternalDbConnectionManager instance = null;

    private DefaultConnectionProvider externalConnProvider;

    // Properties for the connectionProvider of the external database
    private static final String EX_DRIVER = "jdbcProvider.driver";
    private static final String EX_CONN_STRING = "jdbcProvider.connectionString";
    private static final String EX_USERNAME = "jdbcProvider.username";
    private static final String EX_PASSWORD = "jdbcProvider.password";
    private static final String EX_MIN_CONN = "jdbcProvider.poolMinConnection";
    private static final String EX_MAX_CONN = "jdbcProvider.poolMaxConnection";
    private static final String EX_CONN_TIMEOUT = "jdbcProvider.connectionTimeout";
    // TODO add variables to manage retries, sleep between tries etc ?

    private ExternalDbConnectionManager() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty(EX_DRIVER);
        JiveGlobals.migrateProperty(EX_CONN_STRING);
        JiveGlobals.migrateProperty(EX_USERNAME);
        JiveGlobals.migrateProperty(EX_PASSWORD);
        JiveGlobals.migrateProperty(EX_MIN_CONN);
        JiveGlobals.migrateProperty(EX_MAX_CONN);
        JiveGlobals.migrateProperty(EX_CONN_TIMEOUT);

        // This case is the one where we use an external DB
        this.externalConnProvider = new DefaultConnectionProvider(true);
        String jdbcDriver = JiveGlobals.getProperty(EX_DRIVER);
        // Check if the driver is available
        try {
            Class.forName(jdbcDriver);
        } catch (Exception e) {
            Log.error("ExternalDbConnectionManager() Fatal : Unable to load JDBC driver: " + jdbcDriver, e);
            return;
        }

        externalConnProvider.setDriver(jdbcDriver);
        externalConnProvider.setServerURL(JiveGlobals.getProperty(EX_CONN_STRING));
        externalConnProvider.setMinConnections(JiveGlobals.getIntProperty(EX_MIN_CONN, 1));
        externalConnProvider.setMaxConnections(JiveGlobals.getIntProperty(EX_MAX_CONN, 10_000));
        externalConnProvider.setConnectionTimeout(JiveGlobals.getDoubleProperty(EX_CONN_TIMEOUT, 1));
        externalConnProvider.setTestBeforeUse(false);
        externalConnProvider.setTestAfterUse(false);
        externalConnProvider.setTestSQL("select 1");

        String username = JiveGlobals.getProperty(EX_USERNAME);
        if (StringUtils.isNotBlank(username)) {
            externalConnProvider.setUsername(username);
        }
        String pwd = JiveGlobals.getProperty(EX_PASSWORD);
        if (StringUtils.isNotBlank(pwd)) {
            externalConnProvider.setPassword(pwd);
        }

        externalConnProvider.start();
    }


    public static synchronized ExternalDbConnectionManager getInstance() {
        if (instance == null) {
            instance = new ExternalDbConnectionManager();
        }
        return instance;
    }

    /**
     * Try to get a connection from the underlying {@link DefaultConnectionProvider}
     *
     * @return Return a SQL connection to the database
     * @throws SQLException Throws a SQLException if the underlying getConnection() throws a SQLException
     */
    public Connection getConnection() throws SQLException {
        Connection con;
        int maxTry = 5;
        int currentTries = 0;
        do {
            try {
                con = this.externalConnProvider.getConnection();
                return con;
            } catch (Exception e) {
                String msg = "getConnection() For serverURL" + externalConnProvider.getServerURL() +
                    "try number " + currentTries + " failed with exception:";
                Log.info(msg, e);
            }
            currentTries++;

            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                String msg = "getConnection() Interrupted waiting for DB connection for serverURL:" + externalConnProvider.getServerURL();
                Log.info(msg,ex);
                Thread.currentThread().interrupt();
                throw new SQLException(msg,ex);
            }

        } while (currentTries < maxTry);

        throw new SQLException("getConnection() exception trying to get a connection to {}", externalConnProvider.getServerURL());
    }
}
