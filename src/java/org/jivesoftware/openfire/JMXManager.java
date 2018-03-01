package org.jivesoftware.openfire;

import java.lang.management.ManagementFactory;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;

import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the JMX configuration for Openfire.
 *
 * @author Tom Evans
 */
public class JMXManager {

    private static final Logger Log = LoggerFactory.getLogger(JMXManager.class);

    private static final String XMPP_JMX_ENABLED = "xmpp.jmx.enabled";
    private static final String XMPP_JMX_SECURE = "xmpp.jmx.secure";
    private static final String XMPP_JMX_PORT = "xmpp.jmx.port";
    
    public static final int DEFAULT_PORT = Registry.REGISTRY_PORT;
    
    private static JMXManager instance = null;
    
    private MBeanContainer mbContainer;
    private ConnectorServer jmxServer;

    /**
     * Returns true if the JMX connector is configured to require 
     * Openfire admin credentials. This option can be configured via
     * the admin console or by setting the following system property:
     * <pre>
     *    xmpp.jmx.secure=false (default: true)
     * </pre>
     * 
     * @return true if the JMX connector requires authentication
     */
    public static boolean isSecure() {
        return JiveGlobals.getBooleanProperty(XMPP_JMX_SECURE, true);
    }
    
    public static void setSecure(boolean secure) {
        JiveGlobals.setProperty("xmpp.jmx.secure", String.valueOf(secure));
    }

    /**
     * Returns the port number for the JMX connector. This option can 
     * be configured via the admin console or by setting the following 
     * system property:
     * <pre>
     *    xmpp.jmx.port=[port] (default: 1099)
     * </pre>
     * 
     * @return Port number for the JMX connector
     */
    public static int getPort() {
        return JiveGlobals.getIntProperty(XMPP_JMX_PORT, DEFAULT_PORT);
    }
    
    public static void setPort(int port) {
        JiveGlobals.setProperty("xmpp.jmx.port", String.valueOf(port));
    }

    /**
     * Returns true if JMX support is enabled. This option can be 
     * configured via the admin console or by setting the following 
     * system property:
     * <pre>
     *    xmpp.jmx.enabled=true (default: false)
     * </pre>
     * 
     * @return true if JMX support is enabled
     */
    public static boolean isEnabled() {
        return JiveGlobals.getBooleanProperty(XMPP_JMX_ENABLED, false);
    }
    
    public static void setEnabled(boolean enabled) {
        JiveGlobals.setProperty("xmpp.jmx.enabled", String.valueOf(enabled));
    }

    public static JMXManager getInstance() {
        if (instance == null) {
            instance = new JMXManager();
            if (isEnabled()) {
                instance.start();
            }
        }
        return instance;
    }

    private void start() {

        setContainer(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));
        int jmxPort = JMXManager.getPort();
        String jmxUrl = "/jndi/rmi://localhost:" + jmxPort + "/jmxrmi";
        Map<String, Object> env = new HashMap<>();
        if (JMXManager.isSecure()) {
            env.put("jmx.remote.authenticator", new JMXAuthenticator() {
                @Override
                public Subject authenticate(Object credentials) {
                    if (!(credentials instanceof String[])) {
                        if (credentials == null) {
                            throw new SecurityException("Credentials required");
                        }
                        throw new SecurityException("Credentials should be String[]");
                    }
                    final String[] aCredentials = (String[]) credentials;
                    if (aCredentials.length < 2) {
                        throw new SecurityException("Credentials should have at least two elements");
                    }
                    String username = aCredentials[0];
                    String password = aCredentials[1];

                    try {
                        AuthFactory.authenticate(username, password);
                    } catch (Exception ex) {
                        Log.error("Authentication failed for " + username);
                        throw new SecurityException();
                    }

                    if (AdminManager.getInstance().isUserAdmin(username, true)) {
                        return new Subject(true,
                                           Collections.singleton(new JMXPrincipal(username)),
                                           Collections.EMPTY_SET,
                                           Collections.EMPTY_SET);
                    } else {
                        Log.error("Authorization failed for " + username);
                        throw new SecurityException();
                    }
                }
            });
        }
        
        try {
            jmxServer = new ConnectorServer(new JMXServiceURL("rmi", null, jmxPort, jmxUrl), 
                    env, "org.eclipse.jetty.jmx:name=rmiconnectorserver");
            jmxServer.start();
        } catch (Exception e) {
            Log.error("Failed to start JMX connector", e);
        }
    }
    
    public MBeanContainer getContainer() {
        return mbContainer;
    }

    public void setContainer(MBeanContainer mbContainer) {
        this.mbContainer = mbContainer;
    }
}
