/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.starter;

import org.jivesoftware.util.Log;

import java.io.File;

/**
 * Starts the core XMPP server. A bootstrap class that configures classloaders
 * to ensure easy, dynamic server startup.
 *
 * This class should be for standalone mode only. Jive Messenger servers launched
 * through a J2EE container (servlet/EJB) will use those environment's
 * classloading facilities to ensure proper startup.<p>
 *
 * Tasks:<ul>
 *      <li>Add all jars in the lib directory to the classpath.</li>
 *      <li>Add the config directory to the classpath for loadResource()</li>
 *      <li>Start the server</li>
 * </ul>
 *
 * Note: if the enviroment property <tt>messenger.lib.directory</tt> is specified
 * ServerStarter will attempt to use this value as the value for messenger's lib
 * directory. If the property is not specified the default value of ../lib will be used.
 *
 * @author Iain Shigeoka
 */
public class ServerStarter {

    /**
     * Default to this location if one has not been specified
     */
    private static final String DEFAULT_LIB_DIR = "../lib";

    public static void main(String [] args) {
        new ServerStarter().start();
    }

    /**
     * Starts the server by loading and instantiating the bootstrap
     * container. Once the start method is called, the server is
     * started and the server starter should not be used again.
     */
    private void start() {
        // setup the classpath using JiveClassLoader
        try {
            // Load up the bootstrap container
            final ClassLoader parent = findParentClassLoader();

            String libDirString = System.getProperty("messenger.lib.dir");

            File libDir;
            if (libDirString != null) {
                // If the lib directory property has been specified and it actually
                // exists use it, else use the default
                libDir = new File(libDirString);
                if (!libDir.exists()) {
                    Log.warn("Lib directory " + libDirString +
                            " does not exist. Using default " + DEFAULT_LIB_DIR);
                    libDir = new File(DEFAULT_LIB_DIR);
                }
            }
            else {
                libDir = new File(DEFAULT_LIB_DIR);
            }

            ClassLoader loader = new JiveClassLoader(parent, libDir);
           
            Thread.currentThread().setContextClassLoader(loader);
            Class containerClass = loader.loadClass(
                    "org.jivesoftware.messenger.XMPPServer");
            containerClass.newInstance();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Locates the best class loader based on context (see class description).
     *
     * @return The best parent classloader to use
     */
    private ClassLoader findParentClassLoader() {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = this.getClass().getClassLoader();
            if (parent == null) {
                parent = ClassLoader.getSystemClassLoader();
            }
        }
        return parent;
    }
}