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

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import java.util.*;

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
public class ClearspaceManager {

    private static ClearspaceManager instance;
    static {
        // Create a special Map implementation to wrap XMLProperties. We only implement
        // the get, put, and remove operations, since those are the only ones used. Using a Map
        // makes it easier to perform LdapManager testing.
        Map<String, String> properties = new Map<String, String>() {

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
        instance = new ClearspaceManager(properties);
    }

    private String uri;
    private String sharedSecret;

    private Map<String, String> properties;

    /**
     * Provides singleton access to an instance of the ClearspaceManager class.
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
        this.properties = properties;

        String uri = properties.get("clearspace.uri");
        if (uri != null) {
            this.uri = uri;
        }
        sharedSecret = properties.get("clearspace.sharedSecret");

        StringBuilder buf = new StringBuilder();
        buf.append("Created new ClearspaceManager() instance, fields:\n");
        buf.append("\t URI: ").append(uri).append("\n");
        buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

        if (Log.isDebugEnabled()) {
            Log.debug("ClearspaceManager: "+buf.toString());
        }
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
    }

}