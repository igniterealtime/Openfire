/*
 * Copyright 2017 IgniteRealtime.org
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
package org.jivesoftware.openfire.user.property;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.ExternalDbConnectionManager;
import org.jivesoftware.database.ExternalDbConnectionProperties;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * The JDBC user property provider allows you to use an external database to define the user properties. It is best used
 * with the JDBCUserProvider, JDBCAuthProvider &amp; JDBCGroupProvider to provide integration between your external
 * system and Openfire. All data is treated as read-only so any set operations will result in an exception.
 *
 * This implementation will not explicitly verify if a user exists, when operating on its properties. The methods of
 * this implementation will <em>not</em> throw {@link org.jivesoftware.openfire.user.UserNotFoundException}.
 *
 * To enable this provider, set the following in the system properties:
 *
 * <ul>
 * <li>{@code provider.userproperty.className = org.jivesoftware.openfire.user.property.JDBCUserPropertyProvider}</li>
 * </ul>
 *
 * Then you need to set the <b>driver properties</b> for the external database. You can use the the properties
 * described below which are specific to this class, or you can use the generic properties defined in the class
 * {@link ExternalDbConnectionManager}. <br />
 * If you want to use the {@link ExternalDbConnectionManager} properties, set the following to <b>true</b> and
 * check the documentation of {@link ExternalDbConnectionManager}:
 * <ul>
 *  <li>{@code jdbcUserPropertyProvider.useSameExternalDb = true}</li>
 * </ul>
 * <br />
 * If you want to use the specific properties of this class, set the following:
 * <ul>
 *     <li>{@code jdbcUserPropertyProvider.driver = com.mysql.jdbc.Driver} (example) (mandatory)</li>
 *     <li>{@code jdbcUserPropertyProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret} (mandatory)</li>
 *     <li>{@code jdbcUserPropertyProvider.username = user} (if not defined in the connection string)</li>
 *     <li>{@code jdbcUserPropertyProvider.password = password} (if not defined in the connection string)</li>
 *     <li>{@code jdbcUserPropertyProvider.poolMinConnection = Integer > 0}</li>
 *     <li>{@code jdbcUserPropertyProvider.poolMaxConnection = Integer > poolMinConnection}</li>
 *     <li>{@code jdbcUserPropertyProvider.connectionTimeout = Double, eg 0.5 (in Day max time before a connection is forcibly renewed)}</li>
 * </ul>
 * <br />
 *
 * Then, the properties to set for the SQL requests are the following (examples):
 * <ul>
 * <li>{@code jdbcUserPropertyProvider.loadPropertySQL = SELECT propName, propValue FROM myUser WHERE user = ? AND propName = ?}</li>
 * <li>{@code jdbcUserPropertyProvider.loadPropertiesSQL = SELECT propValue FROM myUser WHERE user = ?}</li>
 * </ul>
 *
 * In order to use the configured JDBC connection provider do not use a JDBCconnection string, set the following
 * property:

 * {@code jdbcUserPropertyProvider.useConnectionProvider = true}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class JDBCUserPropertyProvider implements UserPropertyProvider
{
    private static final Logger Log = LoggerFactory.getLogger( JDBCUserPropertyProvider.class );

    private String loadPropertySQL;
    private String loadPropertiesSQL;

    private boolean useConnectionProvider;

    private ExternalDbConnectionManager externalDb;
    private String externalDbKey;

    /**
     * Constructs a new JDBC user property provider.
     */
    public JDBCUserPropertyProvider()
    {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty( "jdbcUserPropertyProvider.loadPropertySQL" );
        JiveGlobals.migrateProperty( "jdbcUserPropertyProvider.loadPropertiesSQL" );

        useConnectionProvider = JiveGlobals.getBooleanProperty( "jdbcUserProvider.useConnectionProvider" );

        // Load the JDBC driver and connection string.
        if ( !useConnectionProvider ) {
            boolean useSameExternalDb = JiveGlobals.getBooleanProperty("jdbcUserPropertyProvider.useSameExternalDb", false);
            if (!useSameExternalDb) {
                ExternalDbConnectionProperties exProps = new ExternalDbConnectionProperties(this.getClass().getName());
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.DRIVER, "jdbcUserPropertyProvider.driver");
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.CONN_STRING, "jdbcUserPropertyProvider.connectionString");
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.USERNAME, "jdbcUserPropertyProvider.username");
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.PWD, "jdbcUserPropertyProvider.password");
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.POOL_MIN_CONN, "jdbcUserPropertyProvider.poolMinConnection");
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.POOL_MAX_CONN, "jdbcUserPropertyProvider.poolMaxConnection");
                exProps.getProps().put(ExternalDbConnectionProperties.DbConPropKeys.CONN_TIMEOUT, "jdbcUserPropertyProvider.connectionTimeout");
                externalDb = ExternalDbConnectionManager.getInstance(exProps);
                externalDbKey = this.getClass().getName();
            } else {
                externalDb = ExternalDbConnectionManager.getInstance();
                externalDbKey = ExternalDbConnectionProperties.DEFAULT_EXTERNAL_DB_PROVIDER_KEY;
            }
        }

        // Load database statements for user data.
        loadPropertySQL = JiveGlobals.getProperty( "jdbcUserPropertyProvider.loadPropertySQL" );
        loadPropertiesSQL = JiveGlobals.getProperty( "jdbcUserPropertyProvider.loadPropertiesSQL" );
    }

    /**
     * XMPP disallows some characters in identifiers, requiring them to be escaped.
     *
     * This implementation assumes that the database returns properly escaped identifiers,
     * but can apply escaping by setting the value of the 'jdbcUserPropertyProvider.isEscaped'
     * property to 'false'.
     *
     * @return 'false' if this implementation needs to escape database content before processing.
     */
    protected boolean assumePersistedDataIsEscaped()
    {
        return JiveGlobals.getBooleanProperty( "jdbcUserPropertyProvider.isEscaped", true );
    }

    private Connection getConnection() throws SQLException {
        if ( useConnectionProvider ) {
            return DbConnectionManager.getConnection();
        } else {
            return externalDb.getConnection(externalDbKey);
        }
    }

    @Override
    public Map<String, String> loadProperties( String username ) throws UnsupportedOperationException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        // OF-1837: When the database does not hold escaped data, our query should use unescaped values in the 'where' clause.
        final String queryValue = assumePersistedDataIsEscaped() ? username : JID.unescapeNode( username );

        try
        {
            con = getConnection();
            pstmt = con.prepareStatement( loadPropertiesSQL );
            pstmt.setString( 1, queryValue );
            rs = pstmt.executeQuery();

            final Map<String, String> result = new HashMap<>();
            while ( rs.next() )
            {
                final String propName = rs.getString( 1 );
                final String propValue = rs.getString( 2 );
                result.put( propName, propValue );
            }
            return result;
        }
        catch ( Exception e )
        {
            throw new UnsupportedOperationException( e );
        }
        finally
        {
            DbConnectionManager.closeConnection( rs, pstmt, con );
        }
    }

    @Override
    public String loadProperty( String username, String propName )
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        // OF-1837: When the database does not hold escaped data, our query should use unescaped values in the 'where' clause.
        final String queryValue = assumePersistedDataIsEscaped() ? username : JID.unescapeNode( username );

        try
        {
            con = getConnection();
            pstmt = con.prepareStatement( loadPropertySQL );
            pstmt.setString( 1, queryValue );
            pstmt.setString( 2, propName );
            rs = pstmt.executeQuery();

            if ( rs.next() )
            {
                return rs.getString( 1 );
            }

            return null;
        }
        catch ( Exception e )
        {
            throw new UnsupportedOperationException( e );
        }
        finally
        {
            DbConnectionManager.closeConnection( rs, pstmt, con );
        }
    }

    @Override
    public void insertProperty( String username, String propName, String propValue ) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateProperty( String username, String propName, String propValue ) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteProperty( String username, String propName ) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly()
    {
        return true;
    }
}
