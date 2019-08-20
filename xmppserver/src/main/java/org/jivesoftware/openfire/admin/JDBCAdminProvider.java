/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 * Copyright 2008-2016 Robert Marcano
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
package org.jivesoftware.openfire.admin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.user.JDBCUserProvider;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * The JDBC admin provider allows you to use an external database to define the administrators
 * users. It is best used with the JDBCAuthProvider &amp; JDBCGroupProvider to provide integration
 * between your external system and Openfire. All data is treated as read-only so any
 * set operations will result in an exception.<p>
 *
 * To enable this provider, set the following in the system properties:
 *
 * <ul>
 * <li>{@code provider.admin.className = org.jivesoftware.openfire.admin.JDBCAdminProvider}</li>
 * </ul>
 *
 * Then you need to set your driver, connection string and SQL statements:
 * <ul>
 * <li>{@code jdbcProvider.driver = com.mysql.jdbc.Driver}</li>
 * <li>{@code jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret}</li>
 * <li>{@code jdbcAdminProvider.getAdminsSQL = SELECT user FROM myAdmins}</li>
 * </ul>
 * <p>
 * If you want to be able to update the admin users via the UI, add the following properties:
 * <ul>
 * <li>{@code jdbcAdminProvider.insertAdminsSQL = INSERT INTO myAdmins (user) VALUES (?)}</li>
 * <li>{@code jdbcAdminProvider.deleteAdminsSQL = DELETE FROM myAdmins WHERE user = ?}</li>
 * </ul>
 * <p>
 * In order to use the configured JDBC connection provider do not use a JDBC
 * connection string, set the following property
 *
 * <ul>
 * <li>{@code jdbcAdminProvider.useConnectionProvider = true}</li>
 * </ul>
 *
 * XMPP disallows some characters in identifiers (notably: JID node-parts), requiring them to be escaped. This
 * implementation assumes that the database returns properly escaped identifiers, but can apply escaping by
 * setting the value of the {@code jdbcAdminProvider.isEscaped} property to 'false'.
 *
 * @author Robert Marcano
 */
public class JDBCAdminProvider implements AdminProvider {

    private static final Logger Log = LoggerFactory.getLogger(JDBCAdminProvider.class);

    public static final SystemProperty<Boolean> useConnectionProvider = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("jdbcAdminProvider.useConnectionProvider")
        .setDefaultValue( false )
        .setDynamic( false )
        .build();

    public static final SystemProperty<Boolean> dataIsEscaped = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("jdbcAdminProvider.dataIsEscaped")
        .setDefaultValue( true )
        .setDynamic( false )
        .build();

    public static final SystemProperty<String> getAdminsSQL = SystemProperty.Builder.ofType( String.class )
        .setKey("jdbcAdminProvider.getAdminsSQL")
        .setDynamic( false )
        .build();

    public static final SystemProperty<String> insertAdminsSQL = SystemProperty.Builder.ofType( String.class )
        .setKey("jdbcAdminProvider.insertAdminsSQL")
        .setDefaultValue( "" )
        .setDynamic( false )
        .build();

    public static final SystemProperty<String> deleteAdminsSQL = SystemProperty.Builder.ofType( String.class )
        .setKey("jdbcAdminProvider.deleteAdminsSQL")
        .setDefaultValue( "" )
        .setDynamic( false )
        .build();

    private final String xmppDomain;

    /**
     * Constructs a new JDBC admin provider.
     */
    public JDBCAdminProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcAdminProvider.getAdminsSQL");

        xmppDomain = XMPPServerInfo.XMPP_DOMAIN.getValue();

        // Load the JDBC driver and connection string.
        if (!useConnectionProvider.getValue()) {
            String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
            try {
                Class.forName(jdbcDriver).newInstance();
            }
            catch (Exception e) {
                Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
            }
        }
    }

    @Override
    public List<JID> getAdmins() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<JID> jids = new ArrayList<>();
        synchronized (getAdminsSQL) {
        try {
            con = getConnection();
            pstmt = con.prepareStatement(getAdminsSQL.getValue());
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // OF-1837: When the database does not hold escaped data, escape values before processing them further.
                final String username;
                if (dataIsEscaped.getValue()) {
                    username = rs.getString(1);
                } else {
                    username = JID.escapeNode( rs.getString(1) );
                }
                jids.add(new JID(username + "@" + xmppDomain));
            }
            return jids;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        }
    }

    private void changeAdmins(final Connection con, final String sql, final List<JID> admins) throws SQLException {
        if (!admins.isEmpty()) {
            try (final PreparedStatement pstmt = con.prepareStatement(sql)) {
                for (final JID jid : admins) {
                    // OF-1837: When the database does not hold escaped data, our query should use unescaped values in the 'where' clause.
                    final String queryValue = dataIsEscaped.getValue() ? jid.getNode() : JID.unescapeNode( jid.getNode() );
                    pstmt.setString(1, queryValue);
                    pstmt.execute();
                }
            }
        }
    }

    @Override
    public void setAdmins(List<JID> newAdmins) {
        if (isReadOnly()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }

        synchronized (getAdminsSQL) {
            final List<JID> currentAdmins = getAdmins();
            // Get a list of everyone in the new list not in the current list
            final List<JID> adminsToAdd = new ArrayList<>(newAdmins);
            adminsToAdd.removeAll(currentAdmins);
            // Get a list of everyone in the current list not in the new list
            currentAdmins.removeAll(newAdmins);
            try (final Connection con = getConnection()) {
                changeAdmins(con, insertAdminsSQL.getValue(), adminsToAdd);
                changeAdmins(con, deleteAdminsSQL.getValue(), currentAdmins);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean isReadOnly() {
        return insertAdminsSQL.getValue().isEmpty() || deleteAdminsSQL.getValue().isEmpty();
    }

    private Connection getConnection() throws SQLException
    {
        if ( useConnectionProvider.getValue() )
        {
            return DbConnectionManager.getConnection();
        }
        else
        {
            return DriverManager.getConnection( JDBCUserProvider.connectionString.getValue() );
        }
    }
}
