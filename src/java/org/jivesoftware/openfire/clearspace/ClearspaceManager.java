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
 *      <li>clearspace.host</li>
 *      <li>clearspace.port</li>
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


    private String host;
    private int port;
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

        String host = properties.get("clearspace.host");
        if (host != null) {
            this.host = host;
        }
        String portStr = properties.get("clearspace.port");
        port = 8080;
        if (portStr != null) {
            try {
                this.port = Integer.parseInt(portStr);
            }
            catch (NumberFormatException nfe) {
                Log.error(nfe);
            }
        }
        sharedSecret = properties.get("clearspace.sharedSecret");

        StringBuilder buf = new StringBuilder();
        buf.append("Created new ClearspaceManager() instance, fields:\n");
        buf.append("\t host: ").append(host).append("\n");
        buf.append("\t port: ").append(port).append("\n");
        buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

        if (Log.isDebugEnabled()) {
            Log.debug("ClearspaceManager: "+buf.toString());
        }
    }

    /**
     * Temporary connection tester.
     *
     * TODO: This is a temporary stub until the real interface is worked out.
     *
     * @return True if connection test was successful.
     */
    public Boolean testConnection() {
        if (host.equals("notlocalhost")) {
            return false;
        }
        return true;
    }

    /**
     * Returns the Clearspace service host; e.g. <tt>cs.example.org</tt>.
     * This value is stored as the Jive Property <tt>clearspace.host</tt>.
     *
     * @return the Clearspace service host name.
     */
    public String getHost() {
        return host;
    }

    /**
     * Sets the hostname of the Clearspace service; e.g., <tt>cs.example.org</tt>.
     * This value is stored as the Jive Property <tt>clearspace.host</tt>.
     *
     * @param host the Clearspace service host name.
     */
    public void setHost(String host) {
        this.host = host;
        properties.put("clearspace.host", host);
    }

    /**
     * Returns the Clearspace service port number. The default is 8080. This value is
     * stored as the Jive Property <tt>clearspace.port</tt>.
     *
     * @return the Clearspace service port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Sets the Clearspace service port number. The default is 8080. This value is
     * stored as the Jive property <tt>clearspace.port</tt>.
     *
     * @param port the Clearspace service port number.
     */
    public void setPort(int port) {
        this.port = port;
        properties.put("clearspace.port", Integer.toString(port));
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