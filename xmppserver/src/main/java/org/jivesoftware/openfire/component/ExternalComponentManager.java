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

package org.jivesoftware.openfire.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.ConnectionManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.component.ExternalComponentConfiguration.Permission;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.ModificationNotAllowedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the connection permissions for external components. When an external component is
 * allowed to connect to this server then a special configuration for the component will be kept.
 * The configuration holds information such as the shared secret that the component should use
 * when authenticating with the server.
 *
 * @author Gaston Dombiak
 */
public class ExternalComponentManager {

    private static final Logger Log = LoggerFactory.getLogger(ExternalComponentManager.class);

    private static final String ADD_CONFIGURATION =
        "INSERT INTO ofExtComponentConf (subdomain,wildcard,secret,permission) VALUES (?,?,?,?)";
    private static final String DELETE_CONFIGURATION =
        "DELETE FROM ofExtComponentConf WHERE subdomain=? and wildcard=?";
    private static final String LOAD_CONFIGURATION =
        "SELECT secret,permission FROM ofExtComponentConf where subdomain=? AND wildcard=0";
    private static final String LOAD_WILDCARD_CONFIGURATION =
        "SELECT secret,permission FROM ofExtComponentConf where ? like subdomain AND wildcard=1";
    private static final String LOAD_CONFIGURATIONS =
        "SELECT subdomain,wildcard,secret FROM ofExtComponentConf where permission=?";

    /**
     * List of listeners that will be notified when vCards are created, updated or deleted.
     */
    private static List<ExternalComponentManagerListener> listeners =
            new CopyOnWriteArrayList<>();

    /**
     * @param enabled enables or disables the service
     * @deprecated Obtain and use the corresponding {@link org.jivesoftware.openfire.spi.ConnectionListener} instead.
     * @throws ModificationNotAllowedException if the status of the service cannot be changed
     */
    @Deprecated
    public static void setServiceEnabled(boolean enabled) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.serviceEnabled(enabled);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'serviceEnabled' event!", e);
            }
        }
        ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
        connectionManager.enableComponentListener(enabled);
    }

    /**
     * @deprecated Obtain and use the corresponding {@link org.jivesoftware.openfire.spi.ConnectionListener} instead.
     * @return {@code true} if the service is enabled, otherwise {@code false}
     */
    @Deprecated
    public static boolean isServiceEnabled() {
        ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
        return connectionManager.isComponentListenerEnabled();
    }

    /**
     * @param port The port to use
     * @deprecated Obtain and use the corresponding {@link org.jivesoftware.openfire.spi.ConnectionListener} instead.
     * @throws ModificationNotAllowedException if the server cannot be modified
     */
    @Deprecated
    public static void setServicePort(int port) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.portChanged(port);  
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'portChanged' event!", e);
            }
        }
        ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
        connectionManager.setComponentListenerPort(port);
    }

    /**
     * @deprecated Obtain and use the corresponding {@link org.jivesoftware.openfire.spi.ConnectionListener} instead.
     * @return the port number
     */
    @Deprecated
    public static int getServicePort() {
        ConnectionManager connectionManager = XMPPServer.getInstance().getConnectionManager();
        return connectionManager.getComponentListenerPort();
    }

    /**
     * Allows an external component to connect to the local server with the specified configuration.
     *
     * @param configuration the configuration for the external component.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    public static void allowAccess(ExternalComponentConfiguration configuration) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.componentAllowed(configuration.getSubdomain(), configuration); 
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentAllowed' event!", e);
            }
        }
        // Remove any previous configuration for this external component
        deleteConfigurationFromDB(configuration);
        // Update the database with the new granted permission and configuration
        configuration.setPermission(Permission.allowed);
        addConfiguration(configuration);
    }

    /**
     * Blocks an external component from connecting to the local server. If the component was
     * connected when the permission was revoked then the connection of the entity will be closed.
     *
     * @param subdomain the subdomain of the external component that is not allowed to connect.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    public static void blockAccess(String subdomain) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.componentBlocked(subdomain);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentBlocked' event!", e);
            }
        }
        // Remove any previous configuration for this external component
        deleteConfigurationFromDB(getConfiguration(subdomain, false));
        // Update the database with the new revoked permission
        ExternalComponentConfiguration config = new ExternalComponentConfiguration(subdomain, false, Permission.blocked, null);
        addConfiguration(config);
        // Check if the component was connected and proceed to close the connection
        String domain = subdomain + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
        Session session = SessionManager.getInstance().getComponentSession(domain);
        if (session != null) {
            Log.debug( "Closing session for external component '{}' as the domain is being blocked. Affected session: {}", domain, session );
            session.close();
        }
    }

    /**
     * Returns true if the external component with the specified subdomain can connect to the
     * local server.
     *
     * @param subdomain the subdomain of the external component.
     * @return true if the external component with the specified subdomain can connect to the
     *         local server.
     */
    public static boolean canAccess(String subdomain) {
        // By default there is no permission defined for the XMPP entity
        Permission permission = null;

        ExternalComponentConfiguration config = getConfiguration(subdomain, true);
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
     * Returns true if there is a configuration for the specified subdomain. This
     * checking can be used as an indirect way of checking that the specified
     * subdomain belongs to an external component.
     *
     * @param subdomain the subdomain of the external component.
     * @return true if there is a configuration for the specified subdomain.
     */
    public static boolean hasConfiguration(String subdomain) {
        return getConfiguration(subdomain, true) != null;
    }

    /**
     * Returns the list of registered external components that are allowed to connect to this
     * server when using a whitelist policy. However, when using a blacklist policy (i.e. anyone
     * may connect to the server) the returned list of configurations will be used for obtaining
     * the shared secret specific for each component.
     *
     * @return the configuration of the registered external components.
     */
    public static Collection<ExternalComponentConfiguration> getAllowedComponents() {
        return getConfigurations(Permission.allowed);
    }

    /**
     * Returns the list of external components that are NOT allowed to connect to this
     * server.
     *
     * @return the configuration of the blocked external components.
     */
    public static Collection<ExternalComponentConfiguration> getBlockedComponents() {
        return getConfigurations(Permission.blocked);
    }

    public static void updateComponentSecret(String subdomain, String secret) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.componentSecretUpdated(subdomain, secret);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentSecretUpdated' event!", e);
            }
        }
        ExternalComponentConfiguration configuration = getConfiguration(subdomain, false);
        if (configuration != null) {
            configuration.setPermission(Permission.allowed);
            configuration.setSecret(secret);
            // Remove any previous configuration for this external component
            deleteConfigurationFromDB(configuration);
        }
        else {
            configuration = new ExternalComponentConfiguration(subdomain, false, Permission.allowed, secret);
        }
        addConfiguration(configuration);
    }

    /**
     * Removes any existing defined permission and configuration for the specified
     * external component.
     *
     * @param subdomain the subdomain of the external component.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    public static void deleteConfiguration(String subdomain) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.componentConfigurationDeleted(subdomain);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentConfigurationDeleted' event!", e);
            }
        }

        // Proceed to delete the configuration of the component
        deleteConfigurationFromDB(getConfiguration(subdomain, false));
    }

    /**
     * Removes any existing defined permission and configuration for the specified
     * external component from the database.
     *
     * @param configuration the external component configuration to delete.
     */
    private static void deleteConfigurationFromDB(ExternalComponentConfiguration configuration) {
        if (configuration == null) {
            // Do nothing
            return;
        }
        // Remove the permission for the entity from the database
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_CONFIGURATION);
            pstmt.setString(1, configuration.getSubdomain() + (configuration.isWildcard() ? "%" : ""));
            pstmt.setInt(2, configuration.isWildcard() ? 1 : 0);
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
     * Adds a new permission for the specified external component.
     *
     * @param configuration the new configuration for a component.
     */
    private static void addConfiguration(ExternalComponentConfiguration configuration) {
        // Remove the permission for the entity from the database
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ADD_CONFIGURATION);
            pstmt.setString(1, configuration.getSubdomain() + (configuration.isWildcard() ? "%" : ""));
            pstmt.setInt(2, configuration.isWildcard() ? 1 : 0);
            pstmt.setString(3, configuration.getSecret());
            pstmt.setString(4, configuration.getPermission().toString());
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
     * Returns the configuration for an external component. A query for the exact requested
     * subdomain will be made. If nothing was found and using wildcards is requested then
     * another query will be made but this time using wildcards.
     *
     * @param subdomain the subdomain of the external component.
     * @param useWildcard true if an attempt to find a subdomain with wildcards should be attempted.
     * @return the configuration for an external component.
     */
    private static ExternalComponentConfiguration getConfiguration(String subdomain, boolean useWildcard) {
        ExternalComponentConfiguration configuration = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            // Check if there is a configuration for the subdomain
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATION);
            pstmt.setString(1, subdomain);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                configuration = new ExternalComponentConfiguration(subdomain, false, Permission.valueOf(rs.getString(2)),
                        rs.getString(1));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        if (configuration == null && useWildcard) {
            // Check if there is a configuration that is using wildcards for domains
            try {
                // Check if there is a configuration for the subdomain
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_WILDCARD_CONFIGURATION);
                pstmt.setString(1, subdomain);
                rs = pstmt.executeQuery();

                while (rs.next()) {
                    configuration = new ExternalComponentConfiguration(subdomain, true, Permission.valueOf(rs.getString(2)),
                            rs.getString(1));
                }
            }
            catch (SQLException sqle) {
                Log.error(sqle.getMessage(), sqle);
            }
            finally {
                DbConnectionManager.closeConnection(rs, pstmt, con);
           }
        }
        return configuration;
    }

    private static Collection<ExternalComponentConfiguration> getConfigurations(
            Permission permission) {
        Collection<ExternalComponentConfiguration> answer =
                new ArrayList<>();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_CONFIGURATIONS);
            pstmt.setString(1, permission.toString());
            rs = pstmt.executeQuery();
            ExternalComponentConfiguration configuration;
            while (rs.next()) {
                String subdomain = rs.getString(1);
                boolean wildcard = rs.getInt(2) == 1;
                // Remove the trailing % if using wildcards
                subdomain = wildcard ? subdomain.substring(0, subdomain.length()-1) : subdomain;
                configuration = new ExternalComponentConfiguration(subdomain, wildcard, permission,
                        rs.getString(3));
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
     * Returns the default secret key to use for those external components that don't have an
     * individual configuration.
     *
     * @return the default secret key to use for those external components that don't have an
     *         individual configuration.
     */
    public static String getDefaultSecret() {
        return JiveGlobals.getProperty("xmpp.component.defaultSecret");
    }

    /**
     * Sets the default secret key to use for those external components that don't have an
     * individual configuration.
     *
     * @param defaultSecret the default secret key to use for those external components that
     *         don't have an individual configuration.
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    public static void setDefaultSecret(String defaultSecret) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.defaultSecretChanged(defaultSecret);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'defaultSecretChanged' event!", e);
            }
        }
        JiveGlobals.setProperty("xmpp.component.defaultSecret", defaultSecret);
    }

    /**
     * Returns the shared secret with the specified external component. If no shared secret was
     * defined then use the default shared secret.
     *
     * @param subdomain the subdomain of the external component to get his shared secret.
     *        (e.g. conference)
     * @return the shared secret with the specified external component or the default shared secret.
     */
    public static String getSecretForComponent(String subdomain) {
        // By default there is no shared secret defined for the XMPP entity
        String secret = null;

        ExternalComponentConfiguration config = getConfiguration(subdomain, true);
        if (config != null) {
            secret = config.getSecret();
        }

        secret = (secret == null ? getDefaultSecret() : secret);
        if (secret == null) {
            Log.error("Setup for external components is incomplete. Property " +
                    "xmpp.component.defaultSecret does not exist.");
        }
        return secret;
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
            return PermissionPolicy.valueOf(JiveGlobals.getProperty("xmpp.component.permission",
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
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    public static void setPermissionPolicy(PermissionPolicy policy) throws ModificationNotAllowedException {
        // Alert listeners about this event
        for (ExternalComponentManagerListener listener : listeners) {
            try {
                listener.permissionPolicyChanged(policy);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'permissionPolicyChanged' event!", e);
            }
        }
        JiveGlobals.setProperty("xmpp.component.permission", policy.toString());
        // Check if connected components can remain connected to the server
        for (ComponentSession session : SessionManager.getInstance().getComponentSessions()) {
            for (String domain : session.getExternalComponent().getSubdomains()) {
                if (!canAccess(domain)) {
                    Log.debug( "Closing session for external component '{}' as a changed permission policy is taken into effect. Affected session: {}", domain, session );
                    session.close();
                    break;
                }
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
     * @throws ModificationNotAllowedException if the operation was denied.
     */
    public static void setPermissionPolicy(String policy) throws ModificationNotAllowedException {
        setPermissionPolicy(PermissionPolicy.valueOf(policy));
    }

    /**
     * Registers a listener to receive events when a configuration change happens. Listeners
     * have the chance to deny the operation from happening.
     *
     * @param listener the listener.
     */
    public static void addListener(ExternalComponentManagerListener listener) {
        if (listener == null) {
            throw new NullPointerException();
        }
        listeners.add(listener);
    }

    /**
     * Unregisters a listener to receive events.
     *
     * @param listener the listener.
     */
    public static void removeListener(ExternalComponentManagerListener listener) {
        listeners.remove(listener);
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
        whitelist
    }
}
