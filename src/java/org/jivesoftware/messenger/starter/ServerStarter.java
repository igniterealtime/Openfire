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

import java.io.File;

/**
 * Starts the core XMPP server. A ootstrap class that configures classloaders
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
 * @author Iain Shigeoka
 */
public class ServerStarter {

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
            File libDir = new File("../lib");
            ClassLoader loader = new JiveClassLoader(parent, libDir);
           
            Thread.currentThread().setContextClassLoader(loader);
            Class containerClass = loader.loadClass(
                    "org.jivesoftware.messenger.XMPPBootContainer");
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