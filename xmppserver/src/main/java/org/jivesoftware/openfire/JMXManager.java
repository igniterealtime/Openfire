/*
 * Copyright (C) 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire;

import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.jivesoftware.openfire.admin.AdminManager;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.management.ObjectName;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.JMXPrincipal;
import javax.management.remote.JMXServiceURL;
import javax.security.auth.Subject;
import java.lang.management.ManagementFactory;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the JMX configuration for Openfire.
 *
 * @author Tom Evans
 */
public class JMXManager {

    private static final Logger Log = LoggerFactory.getLogger(JMXManager.class);

    /**
     * Enables / disables JMX support in Openfire. This option can be
     * configured via the admin console or by setting the following
     * system property:
     * <pre>
     *    xmpp.jmx.enabled=true (default: false)
     * </pre>
     */
    public static final SystemProperty<Boolean> XMPP_JMX_ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.jmx.enabled")
        .setDynamic(false) // This property strictly speaking is somewhat 'dynamic', but generally, it's used only at boot time to see if Beans etc. are to be registered. Reboot is required to have JMX functionality.
        .setDefaultValue(false)
        .build();

    /**
     * Controls if the JMX connector is configured to require
     * Openfire admin credentials. This option can be configured via
     * the admin console or by setting the following system property:
     * <pre>
     *    xmpp.jmx.secure=false (default: true)
     * </pre>
     */
    public static final SystemProperty<Boolean> XMPP_JMX_SECURE = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.jmx.secure")
        .setDynamic(false)
        .setDefaultValue(true)
        .build();

    /**
     * Defines the TCP port number for the JMX connector. This option can
     * be configured via the admin console or by setting the following
     * system property:
     * <pre>
     *    xmpp.jmx.port=[port] (default: 1099)
     * </pre>
     */
    public static final SystemProperty<Integer> XMPP_JMX_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("xmpp.jmx.port")
        .setDynamic(false) // This property strictly speaking is 'dynamic', but generally, it's used only at boot time to see if Beans etc. are to be registered. Reboot is required to have JMX functionality.
        .setDefaultValue(Registry.REGISTRY_PORT)
        .setMinValue(1)
        .setMaxValue(65535)
        .build();

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
        return XMPP_JMX_SECURE.getValue();
    }

    public static void setSecure(boolean secure) {
        XMPP_JMX_SECURE.setValue(secure);
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
        return XMPP_JMX_PORT.getValue();
    }

    public static void setPort(int port) {
        XMPP_JMX_PORT.setValue(port);
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
        return XMPP_JMX_ENABLED.getValue();
    }

    public static void setEnabled(boolean enabled) {
        XMPP_JMX_ENABLED.setValue(enabled);
    }

    public static synchronized JMXManager getInstance() {
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

    /**
     * Registers a MBean in the platform MBean server, replacing any MBean that might have been registered earlier using
     * the same object name.
     *
     * This method will log but otherwise ignore any exception that is thrown when registrating the bean.
     *
     * @param mbean The bean to register
     * @param name The identifier to register the bean under.
     * @return the ObjectName instance used the register the bean.
     */
    public static ObjectName tryRegister(@Nonnull final Object mbean, @Nonnull final String name)
    {
        try {
            final ObjectName objectName = new ObjectName(name);
            tryRegister(mbean, objectName);
            return objectName;
        } catch (Exception e) {
            Log.warn("Failed to register MBean with platform MBean server, using object name: {}", name, e);
            return null;
        }
    }

    /**
     * Registers a MBean in the platform MBean server, replacing any MBean that might have been registered earlier using
     * the same object name.
     *
     * This method will log but otherwise ignore any exception that is thrown when registrating the bean.
     *
     * @param mbean The bean to register
     * @param objectName The identifier to register the bean under.
     */
    public static void tryRegister(@Nonnull final Object mbean, @Nonnull final ObjectName objectName)
    {
        if (!isEnabled()) {
            Log.trace("Not Registering MBean with platform MBean server (using object name: {}) as JMX functionality in Openfire is disabled.", objectName);
            return;
        }

        getInstance(); // Ensure that the instance is started before trying to register anything.

        Log.trace("Registering MBean with platform MBean server, using object name: {}", objectName);
        tryUnregister(objectName);

        try {
            getInstance().getContainer().getMBeanServer().registerMBean(mbean, objectName);
            Log.debug("Successfully registered MBean with platform MBean server, using object name: {}", objectName);
        } catch (Exception e) {
            Log.warn("Failed to register MBean with platform MBean server, using object name: {}", objectName, e);
        }
    }

    /**
     * Unregisters a MBean from the platform MBean server.
     *
     * This method will log but otherwise ignore any exception that is thrown when unregistering the bean.
     *
     * @param name The identifier that was used to register the bean under.
     */
    public static void tryUnregister(@Nonnull final String name) {
        try {
            final ObjectName objectName = new ObjectName(name);
            tryUnregister(objectName);
        } catch (Exception e) {
            Log.warn("Failed to unregister MBean with platform MBean server, using object name: {}", name, e);
        }
    }

    /**
     * Unregisters a MBean from the platform MBean server.
     *
     * This method will log but otherwise ignore any exception that is thrown when unregistering the bean.
     *
     * @param objectName The identifier that was used to register the bean under.
     */
    public static void tryUnregister(@Nonnull final ObjectName objectName) {
        try {
            Log.debug("Unregistering MBean from platform MBean server (if one was registered), using object name: {}", objectName);
            if (getInstance().getContainer().getMBeanServer().isRegistered(objectName)) {
                getInstance().getContainer().getMBeanServer().unregisterMBean(objectName);
                Log.debug("Successfully unregistered MBean from platform MBean server, using object name: {}", objectName.getCanonicalName());
            }
        } catch (Exception e) {
            Log.warn("Failed to unregister MBean with platform MBean server, using object name: {}", objectName, e);
        }
    }
}
