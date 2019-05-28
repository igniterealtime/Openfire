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

package org.jivesoftware.openfire.server;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.jivesoftware.openfire.server.RemoteServerConfiguration.Permission;
import org.jivesoftware.openfire.session.ConnectionSettings;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the connection permissions for remote servers. When a remote server is allowed to
 * connect to this server then a special configuration for the remote server will be kept.
 * The configuration holds information such as the port to use when creating an outgoing connection.
 *
 * @author Gaston Dombiak
 */
public class RemoteServerManager {

    private static final Logger Log = LoggerFactory.getLogger(RemoteServerManager.class);

    private static final String ADD_CONFIGURATION =
        "INSERT INTO ofRemoteServerConf (xmppDomain,remotePort,permission) VALUES (?,?,?)";
    private static final String DELETE_CONFIGURATION =
        "DELETE FROM ofRemoteServerConf WHERE xmppDomain=?";
    private static final String LOAD_CONFIGURATION =
        "SELECT remotePort,permission FROM ofRemoteServerConf where xmppDomain=?";
    private static final String LOAD_CONFIGURATIONS =
        "SELECT xmppDomain,remotePort FROM ofRemoteServerConf where permission=?";

    private static Cache configurationsCache;

    static {
        configurationsCache = CacheFactory.createCache("Remote Server Configurations");
    }

    /**
     * Allows a remote server to connect to the local server with the specified configuration.
     *
     * @param configuration the configuration for the remote server.
     */
    public static void allowAccess(RemoteServerConfiguration configuration) {
        // Remove any previous configuration for this remote server
        deleteConfiguration(configuration.getDomain());
        // Update the database with the new granted permission and configuration
        configuration.setPermission(Permission.allowed);
        addConfiguration(configuration);
    }

    /**
     * Blocks a remote server from connecting to the local server. If the remote server was
     * connected when the permission was revoked then the connection of the entity will be closed.
     *
     * @param domain the domain of the remote server that is not allowed to connect.
     */
    public static void blockAccess(String domain) {
        // Remove any previous configuration for this remote server
        deleteConfiguration(domain);
        // Update the database with the new revoked permission
        RemoteServerConfiguration config = new RemoteServerConfiguration(domain);
        config.setPermission(Permission.blocked);
        addConfiguration(config);
        // Check if the remote server was connected and proceed to close the connection
        for (Session session : SessionManager.getInstance().getIncomingServerSessions(domain)) {
            Log.debug( "Closing session for domain '{}' as the domain is being blocked. Affected session: {}", domain, session );
            session.close();
        }
        // Can't just lookup a single remote server anymore, so check them all.
        for (DomainPair domainPair : SessionManager.getInstance().getOutgoingDomainPairs()) {
            if (domainPair.getRemote().equals(domain)) {
                Session session = SessionManager.getInstance().getOutgoingServerSession(domainPair);
                Log.debug( "Closing (domain-pair) session for domain '{}' as the domain is being blocked. Affected session: {}", domain, session );
                session.close();
            }
        }
    }

    /**
     * Returns true if the remote server with the specified domain can connect to the
     * local server.
     *
     * @param domain the domain of the remote server.
     * @return true if the remote server with the specified domain can connect to the
     *         local server.
     */
    public static boolean canAccess(String domain) {
        // If s2s is disabled then it is not possible to send packets to remote servers or
        // receive packets from remote servers
        if (!JiveGlobals.getBooleanProperty(ConnectionSettings.Server.SOCKET_ACTIVE, true)) {
            return false;
        }

        // By default there is no permission defined for the XMPP entity
        Permission permission = null;

        RemoteServerConfiguration config = getConfiguration(domain);
        if (config != null) {
            permission = config.getPermission();
        }

        if (PermissionPolicy.blacklist == getPermissionPolicy()) {
            // Anyone can access except those entities listed in the blacklist
            return Permission.blocked != permission;
        }
        else {
            // Access is limited to those present in the whitelist
            return Permission.allowed == permission;
        }
    }

    /**
     * Returns the list of registered remote servers that are allowed to connect to/from this
     * server when using a whitelist policy. However, when using a blacklist policy (i.e. anyone
     * may connect to the server) the returned list of configurations will be used for obtaining
     * the specific connection configuration for each remote server.
     *
     * @return the configuration of the registered external components.
     */
    public static Collection<RemoteServerConfiguration> getAllowedServers() {
        return getConfigurations(Permission.allowed);
    }

    /**
     * Returns the list of remote servers that are NOT allowed to connect to/from this
     * server.
     *
     * @return the configuration of the blocked external components.
     */
    public static Collection<RemoteServerConfiguration> getBlockedServers() {
        return getConfigurations(Permission.blocked);
    }

    /**
     * Returns the number of milliseconds to wait to connect to a remote server or read
     * data from a remote server. Default timeout value is 120 seconds. Configure the
     * {@code xmpp.server.read.timeout} global property to override the default value.
     *
     * @return the number of milliseconds to wait to connect to a remote server or read
     *         data from a remote server.
     */
    public static int getSocketTimeout() {
        return JiveGlobals.getIntProperty(ConnectionSettings.Server.SOCKET_READ_TIMEOUT, 120000);
    }

    /**
     * Removes any existing defined permission and configuration for the specified
     * remote server.
     *
     * @param domain the domain of the remote server.
     */
    public static void deleteConfiguration(String domain) {
        // Remove configuration from cache
        configurationsCache.remove(domain);
        // Remove the permission for the entity from the database
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_CONFIGURATION);
            pstmt.setString(1, domain);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Adds a new permission for the specified remote server.
     *
     * @param configuration the new configuration for a remote server
     */
    private static void addConfiguration(RemoteServerConfiguration configuration) {
        // Remove configuration from cache
        configurationsCache.put(configuration.getDomain(), configuration);
        // Remove the permission for the entity from the database
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_CONFIGURATION);
            pstmt.setString(1, configuration.getDomain());
            pstmt.setInt(2, configuration.getRemotePort());
            pstmt.setString(3, configuration.getPermission().toString());
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Returns the configuration for a remote server or {@code null} if none was found.
     *
     * @param domain the domain of the remote server.
     * @return the configuration for a remote server or {@code null} if none was found.
     */
    public static RemoteServerConfiguration getConfiguration(String domain) {
        Object value = configurationsCache.get(domain);
        if ("null".equals(value)) {
            return null;
        }
        RemoteServerConfiguration configuration = (RemoteServerConfiguration) value;
        if (configuration == null) {
            java.sql.Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_CONFIGURATION);
                pstmt.setString(1, domain);
                rs = pstmt.executeQuery();
                while (rs.next()) {
                    configuration = new RemoteServerConfiguration(domain);
                    configuration.setRemotePort(rs.getInt(1));
                    configuration.setPermission(Permission.valueOf(rs.getString(2)));
                }
            }
            catch (SQLException sqle) {
                Log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }
            if (configuration != null) {
                configurationsCache.put(domain, configuration);
            }
            else {
                configurationsCache.put(domain, "null");
            }
        }
        return configuration;
    }

    private static Collection<RemoteServerConfiguration> getConfigurations(
            Permission permission) {
        Collection<RemoteServerConfiguration> answer =
                new ArrayList<>();
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATIONS);
            pstmt.setString(1, permission.toString());
            rs = pstmt.executeQuery();
            RemoteServerConfiguration configuration;
            while (rs.next()) {
                configuration = new RemoteServerConfiguration(rs.getString(1));
                configuration.setRemotePort(rs.getInt(2));
                configuration.setPermission(permission);
                answer.add(configuration);
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return answer;
    }

    /**
     * Returns the remote port to connect for the specified remote server. If no port was
     * defined then use the default port (e.g. 5269).
     *
     * @param domain the domain of the remote server to get the remote port to connect to.
     * @return the remote port to connect for the specified remote server.
     */
    public static int getPortForServer(String domain) {
        int port = JiveGlobals.getIntProperty(ConnectionSettings.Server.REMOTE_SERVER_PORT, ConnectionManager.DEFAULT_SERVER_PORT);
        RemoteServerConfiguration config = getConfiguration(domain);
        if (config != null) {
            port = config.getRemotePort();
            if (port == 0) {
                port = JiveGlobals
                        .getIntProperty(ConnectionSettings.Server.REMOTE_SERVER_PORT, ConnectionManager.DEFAULT_SERVER_PORT);
            }
        }
        return port;
    }

    /**
     * Returns the permission policy being used for new XMPP entities that are trying to
     * connect to the server. There are two types of policies: 1) blacklist: where any entity
     * is allowed to connect to the server except for those listed in the black list and
     * 2) whitelist: where only the entities listed in the white list are allowed to connect to
     * the server.
     *
     * @return the permission policy being used for new XMPP entities that are trying to
     *         connect to the server.
     */
    public static PermissionPolicy getPermissionPolicy() {
        try {
            return PermissionPolicy.valueOf(JiveGlobals.getProperty(ConnectionSettings.Server.PERMISSION_SETTINGS,
                    PermissionPolicy.blacklist.toString()));
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            return PermissionPolicy.blacklist;
        }
    }

    /**
     * Sets the permission policy being used for new XMPP entities that are trying to
     * connect to the server. There are two types of policies: 1) blacklist: where any entity
     * is allowed to connect to the server except for those listed in the black list and
     * 2) whitelist: where only the entities listed in the white list are allowed to connect to
     * the server.
     *
     * @param policy the new PermissionPolicy to use.
     */
    public static void setPermissionPolicy(PermissionPolicy policy) {
        JiveGlobals.setProperty(ConnectionSettings.Server.PERMISSION_SETTINGS, policy.toString());
        // Check if the connected servers can remain connected to the server
        for (String hostname : SessionManager.getInstance().getIncomingServers()) {
            if (!canAccess(hostname)) {
                for (Session session : SessionManager.getInstance().getIncomingServerSessions(hostname)) {
                    Log.debug( "Closing session for hostname '{}' as a changed permission policy is taken into effect. Affected session: {}", hostname, session );
                    session.close();
                }
            }
        }
        for (DomainPair domainPair : SessionManager.getInstance().getOutgoingDomainPairs()) {
            if (!canAccess(domainPair.getRemote())) {
                Session session = SessionManager.getInstance().getOutgoingServerSession(domainPair);
                Log.debug( "Closing session as a changed permission policy is taken into effect. Affected session: {}", session );
                session.close();
                // After the session has been close, inform all listeners as well.
                ServerSessionEventDispatcher.dispatchEvent(session, ServerSessionEventDispatcher.EventType.session_destroyed);
            }
        }
    }

    /**
     * Sets the permission policy being used for new XMPP entities that are trying to
     * connect to the server. There are two types of policies: 1) blacklist: where any entity
     * is allowed to connect to the server except for those listed in the black list and
     * 2) whitelist: where only the entities listed in the white list are allowed to connect to
     * the server.
     *
     * @param policy the new policy to use.
     */
    public static void setPermissionPolicy(String policy) {
        setPermissionPolicy(PermissionPolicy.valueOf(policy));
    }

    public enum PermissionPolicy {
        /**
         * Any XMPP entity is allowed to connect to the server except for those listed in
         * the <b>not allowed list</b>.
         */
        blacklist,

        /**
         * Only the XMPP entities listed in the <b>allowed list</b> are able to connect to
         * the server.
         */
        whitelist;
    }
}
