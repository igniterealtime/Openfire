package org.jivesoftware.database;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class that contains the default properties that should be used when setting up
 * a connection to an external database for the JDBC providers classes:
 * <ul>
 *     <li>{@link org.jivesoftware.openfire.user.JDBCUserProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.user.property.JDBCUserPropertyProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.group.JDBCGroupProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.admin.JDBCAdminProvider}</li>
 *     <li>{@link org.jivesoftware.openfire.auth.JDBCAuthProvider}</li>
 * </ul>
 *
 * This class can be used with the default properties (see below), or custom properties can be
 * defined when creating an instance of {@link ExternalDbConnectionProperties}
 * (See class {@link org.jivesoftware.openfire.user.property.JDBCUserPropertyProvider} for an example
 * of the later).<br/><br/>
 *
 *
 * Default properties are:<br/><br/>
 *
 * <ul>
 *     <li>{@code jdbcProvider.driver = com.mysql.jdbc.Driver} (example)</li>
 *     <li>{@code jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret}</li>
 *     <li>{@code jdbcProvider.username = user} (if not defined in the connection string)</li>
 *     <li>{@code jdbcProvider.password = password} (if not defined in the connection string)</li>
 *     <li>{@code jdbcProvider.poolMinConnection = Integer > 0}</li>
 *     <li>{@code jdbcProvider.poolMaxConnection = Integer > poolMinConnection}</li>
 *     <li>{@code jdbcProvider.connectionTimeout = Double, eg 0.5 (in Day max time before a connection is forcibly renewed)}</li>
 * </ul>
 */
public class ExternalDbConnectionProperties {
    private static final Logger Log = LoggerFactory.getLogger( ExternalDbConnectionProperties.class );

    private String key;
    private Map<DbConPropKeys, String> props = new ConcurrentHashMap<>();

    public static final String DEFAULT_EXTERNAL_DB_PROVIDER_KEY = "_DEFAULT_";

    private ExternalDbConnectionProperties() {}

    /**
     * @param key The key for registering the connection defined by the properties
     *             of this instance in the {@link ExternalDbConnectionManager} set of
     *             external databases connections.
     */
    public ExternalDbConnectionProperties(String key) {
        if (StringUtils.isBlank(key)) {
            Log.error("ExternalDbConnectionProperties() key cannot be null");
            return;
        }
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    /**
     * @return Return the Map of the properties
     */
    public Map<DbConPropKeys, String> getProps() {
        return props;
    }


    /**
     * Default properties used to setup a connection to an external database
     */
    private void setDefaultProps() {
        props.put(DbConPropKeys.DRIVER, "jdbcProvider.driver");
        props.put(DbConPropKeys.CONN_STRING, "jdbcProvider.connectionString");
        props.put(DbConPropKeys.USERNAME, "jdbcProvider.username");
        props.put(DbConPropKeys.PWD, "jdbcProvider.password");
        props.put(DbConPropKeys.POOL_MIN_CONN, "jdbcProvider.poolMinConnection");
        props.put(DbConPropKeys.POOL_MAX_CONN, "jdbcProvider.poolMaxConnection");
        props.put(DbConPropKeys.CONN_TIMEOUT, "jdbcProvider.connectionTimeout");
    }


    /**
     *
     * @return Return an instance of {@link ExternalDbConnectionProperties} built with
     * the default properties. See the documentation of this class for the list of properties.
     */
    public static synchronized ExternalDbConnectionProperties getDefault() {
        ExternalDbConnectionProperties properties = new ExternalDbConnectionProperties();
        properties.setKey(DEFAULT_EXTERNAL_DB_PROVIDER_KEY);
        properties.setDefaultProps();
        return properties;
    }


    /**
     * The keys of the properties to be used when building a SQL connection to a database
     */
    public enum DbConPropKeys {
        /**
         * JDBC Driver type
         */
        DRIVER,
        /**
         * JDBC Connection String
         */
        CONN_STRING,
        /**
         * username is optionnal as it can be defined directly in the JDBC connection string
         */
        USERNAME,
        /**
         * password is optionnal as it can be defined directly in the JDBC connection string
         */
        PWD,
        /**
         * Minimum number of connections in the pool
         */
        POOL_MIN_CONN,
        /**
         * Maximum number of connections in the pool
         */
        POOL_MAX_CONN,
        /**
         * Timeout after which a connexion in the pool will be renewed forcibly
         */
        CONN_TIMEOUT
    }


}
