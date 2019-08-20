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
import org.jivesoftware.util.JiveGlobals;
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
 *
 * @author Robert Marcano
 */
public class JDBCAdminProvider implements AdminProvider {

    private static final Logger Log = LoggerFactory.getLogger(JDBCAdminProvider.class);

    private final String getAdminsSQL;
    private final String insertAdminsSQL;
    private final String deleteAdminsSQL;
    private final String xmppDomain;
    private final boolean useConnectionProvider;

    private String connectionString;

    /**
     * Constructs a new JDBC admin provider.
     */
    public JDBCAdminProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcAdminProvider.getAdminsSQL");

        xmppDomain = XMPPServerInfo.XMPP_DOMAIN.getValue();
        useConnectionProvider = JiveGlobals.getBooleanProperty("jdbcAdminProvider.useConnectionProvider");

        // Load database statement for reading admin list
        getAdminsSQL = JiveGlobals.getProperty("jdbcAdminProvider.getAdminsSQL");
        insertAdminsSQL = JiveGlobals.getProperty("jdbcAdminProvider.insertAdminsSQL", "");
        deleteAdminsSQL = JiveGlobals.getProperty("jdbcAdminProvider.deleteAdminsSQL", "");

        // Load the JDBC driver and connection string
        if (!useConnectionProvider) {
            String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
            try {
                Class.forName(jdbcDriver).newInstance();
            } catch (Exception e) {
                Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
                return;
            }
            connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");
        }
    }

    /**
     * XMPP disallows some characters in identifiers, requiring them to be escaped.
     *
     * This implementation assumes that the database returns properly escaped identifiers,
     * but can apply escaping by setting the value of the 'jdbcAdminProvider.isEscaped'
     * property to 'false'.
     *
     * @return 'false' if this implementation needs to escape database content before processing.
     */
    protected boolean assumePersistedDataIsEscaped()
    {
        return JiveGlobals.getBooleanProperty( "jdbcAdminProvider.isEscaped", true );
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
            pstmt = con.prepareStatement(getAdminsSQL);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                // OF-1837: When the database does not hold escaped data, escape values before processing them further.
                final String username;
                if (assumePersistedDataIsEscaped()) {
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
                    final String queryValue = assumePersistedDataIsEscaped() ? jid.getNode() : JID.unescapeNode( jid.getNode() );
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
                changeAdmins(con, insertAdminsSQL, adminsToAdd);
                changeAdmins(con, deleteAdminsSQL, currentAdmins);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public boolean isReadOnly() {
        return insertAdminsSQL.isEmpty() || deleteAdminsSQL.isEmpty();
    }

    private Connection getConnection() throws SQLException {
        if (useConnectionProvider) {
            return DbConnectionManager.getConnection();
        }
        return DriverManager.getConnection(connectionString);
    }
}
