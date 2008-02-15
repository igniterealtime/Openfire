/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.component.ExternalComponentConfiguration;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.component.ExternalComponentManagerListener;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.ModificationNotAllowedException;
import org.jivesoftware.util.StringUtils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Centralized administration of Clearspace connections. The {@link #getInstance()} method
 * should be used to get an instace. The following properties configure this manager:
 *
 * <ul>
 *      <li>clearspace.uri</li>
 *      <li>clearspace.sharedSecret</li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class ClearspaceManager extends BasicModule implements ExternalComponentManagerListener {

    private static ClearspaceManager instance;

    private Map<String, String> properties;
    private String uri;
    private String sharedSecret;

    /**
     * Provides singleton access to an instance of the ClearspaceManager class. A <tt>null</tt>
     * value will be returned before the setup is completed.
     *
     * @return an ClearspaceManager instance.
     */
    public static ClearspaceManager getInstance() {
        return instance;
    }

    /**
     * Constructs a new ClearspaceManager instance. Typically, {@link #getInstance()} should be
     * called instead of this method. ClearspaceManager instances should only be created directly
     * for testing purposes.
     *
     * @param properties the Map that contains properties used by the Clearspace manager, such as
     *      Clearspace host and shared secret.
     */
    public ClearspaceManager(Map<String, String> properties) {
        super("Clearspace integration module for testing only");
        this.properties = properties;

        this.uri = properties.get("clearspace.uri");
        sharedSecret = properties.get("clearspace.sharedSecret");

        if (Log.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Created new ClearspaceManager() instance, fields:\n");
            buf.append("\t URI: ").append(uri).append("\n");
            buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

            Log.debug("ClearspaceManager: " + buf.toString());
        }
    }

    /**
     * Constructs a new ClearspaceManager instance. Typically, {@link #getInstance()} should be
     * called instead of this method. ClearspaceManager instances should only be created directly
     * for testing purposes.
     *
     */
    public ClearspaceManager() {
        super("Clearspace integration module");
        // Create a special Map implementation to wrap XMLProperties. We only implement
        // the get, put, and remove operations, since those are the only ones used. Using a Map
        // makes it easier to perform LdapManager testing.
        this.properties = new Map<String, String>() {

            public String get(Object key) {
                return JiveGlobals.getXMLProperty((String)key);
            }

            public String put(String key, String value) {
                JiveGlobals.setXMLProperty(key, value);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }

            public String remove(Object key) {
                JiveGlobals.deleteXMLProperty((String)key);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }


            public int size() {
                return 0;
            }

            public boolean isEmpty() {
                return false;
            }

            public boolean containsKey(Object key) {
                return false;
            }

            public boolean containsValue(Object value) {
                return false;
            }

            public void putAll(Map<? extends String, ? extends String> t) {
            }

            public void clear() {
            }

            public Set<String> keySet() {
                return null;
            }

            public Collection<String> values() {
                return null;
            }

            public Set<Entry<String, String>> entrySet() {
                return null;
            }
        };

        this.uri = JiveGlobals.getXMLProperty("clearspace.uri");
        sharedSecret = JiveGlobals.getXMLProperty("clearspace.sharedSecret");

        if (Log.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Created new ClearspaceManager() instance, fields:\n");
            buf.append("\t URI: ").append(uri).append("\n");
            buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

            Log.debug("ClearspaceManager: " + buf.toString());
        }

        instance = this;
    }

    /**
     * Check a username/password pair for valid authentication.
     *
     * TODO: This is a temporary stub until the real interface is worked out.
     *
     * @param username Username to authenticate against.
     * @param password Password to use for authentication.
     * @return True or false of the authentication succeeded.
     */
    public Boolean checkAuthentication(String username, String password) {
        if (username.equals("daniel")) {
            return false;
        }
        return true;
    }

    /**
     * Tests the web services connection with Clearspace given the manager's current configuration.
     *
     * TODO: This is a temporary stub until the real interface is worked out.
     *
     * @return True if connection test was successful.
     */
    public Boolean testConnection() {
        if (uri.equals("http://localhost:80/fail")) {
            return false;
        }
        return true;
    }

    /**
     * Returns the Clearspace service URI; e.g. <tt>https://localhost:80/clearspace</tt>.
     * This value is stored as the Jive Property <tt>clearspace.uri</tt>.
     *
     * @return the Clearspace service URI.
     */
    public String getConnectionURI() {
        return uri;
    }

    /**
     * Sets the URI of the Clearspace service; e.g., <tt>https://localhost:80/clearspace</tt>.
     * This value is stored as the Jive Property <tt>clearspace.uri</tt>.
     *
     * @param uri the Clearspace service URI.
     */
    public void setConnectionURI(String uri) {
        this.uri = uri;
         properties.put("clearspace.uri", uri);
        if (isEnabled()) {
            // TODO Reconfigure webservice connection with new setting
        }
    }

    /**
     * Returns the password, configured in Clearspace, that Openfire will use to authenticate
     * with Clearspace to perform it's integration.
     *
     * @return the password Openfire will use to authenticate with Clearspace.
     */
    public String getSharedSecret() {
        return sharedSecret;
    }

    /**
     * Sets the shared secret for the Clearspace service we're connecting to.
     *
     * @param sharedSecret the password configured in Clearspace to authenticate Openfire.
     */
    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
        properties.put("clearspace.sharedSecret", sharedSecret);
        // Set new password for external component
        ExternalComponentConfiguration configuration = new ExternalComponentConfiguration("clearspace",
                ExternalComponentConfiguration.Permission.allowed, sharedSecret);
        try {
            ExternalComponentManager.allowAccess(configuration);
        }
        catch (ModificationNotAllowedException e) {
            Log.warn("Failed to configure password for Clearspace", e);
        }
    }

    /**
     * Returns true if Clearspace is being used as the backend of Openfire. When
     * integrated with Clearspace then users and groups will be pulled out from
     * Clearspace. User authentication will also rely on Clearspace.
     *
     * @return true if Clearspace is being used as the backend of Openfire.
     */
    public boolean isEnabled() {
        return AuthFactory.getAuthProvider() instanceof ClearspaceAuthProvider;
    }

    public void start() throws IllegalStateException {
        super.start();
        if (isEnabled()) {
            // Before starting up service make sure there is a default secret
            if (ExternalComponentManager.getDefaultSecret() == null ||
                    "".equals(ExternalComponentManager.getDefaultSecret())) {
                try {
                    ExternalComponentManager.setDefaultSecret(StringUtils.randomString(10));
                }
                catch (ModificationNotAllowedException e) {
                    Log.warn("Failed to set a default secret to external component service", e);
                }
            }
            // Make sure that external component service is enabled
            if (!ExternalComponentManager.isServiceEnabled()) {
                try {
                    ExternalComponentManager.setServiceEnabled(true);
                }
                catch (ModificationNotAllowedException e) {
                    Log.warn("Failed to start external component service", e);
                }
            }
            // Listen for changes to external component settings
            ExternalComponentManager.addListener(this);
            // TODO Send current xmpp domain, network interfaces, external component port and external component secret to CS 
        }
    }

    public void serviceEnabled(boolean enabled) throws ModificationNotAllowedException {
        // Do not let admins shutdown the external component service
        if (!enabled) {
            throw new ModificationNotAllowedException("Service cannot be disabled when integrated with Clearspace.");
        }
    }

    public void portChanged(int newPort) throws ModificationNotAllowedException {
        //TODO Send the new port to Clearspace
    }

    public void defaultSecretChanged(String newSecret) throws ModificationNotAllowedException {
        // Do nothing
    }

    public void permissionPolicyChanged(ExternalComponentManager.PermissionPolicy newPolicy)
            throws ModificationNotAllowedException {
        // Do nothing
    }

    public void componentAllowed(String subdomain, ExternalComponentConfiguration configuration)
            throws ModificationNotAllowedException {
        if (subdomain.startsWith("clearspace")) {
            // TODO Send new password to Clearspace
            //configuration.getSecret();
        }
    }

    public void componentBlocked(String subdomain) throws ModificationNotAllowedException {
        if (subdomain.startsWith("clearspace")) {
            throw new ModificationNotAllowedException("Communication with Clearspace cannot be blocked.");
        }
    }

    public void componentSecretUpdated(String subdomain, String newSecret) throws ModificationNotAllowedException {
        if (subdomain.startsWith("clearspace")) {
            // TODO Send new password to Clearspace
        }
    }

    public void componentConfigurationDeleted(String subdomain) throws ModificationNotAllowedException {
        // Do not let admins delete configuration of Clearspace component
        if (subdomain.startsWith("clearspace")) {
            throw new ModificationNotAllowedException("Use 'Profile Settings' to change password.");
        }
    }
}