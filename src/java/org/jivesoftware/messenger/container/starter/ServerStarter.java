/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container.starter;

import java.io.File;

/**
 * A bootstrap class that configures classloaders to ensure easy,
 * dynamic server startup.
 * <p/>
 * <p>Extend this class and implement a main method that calls the start()
 * method on this class in order to start the server.</p>
 * <p/>
 * <p>This class should be for standalone
 * mode only. Jive servers launched through a J2EE container
 * (servlet/EJB) will use those environment's classloading facilities
 * to ensure proper startup.
 * <p/>
 * Tasks:
 * </p>
 * <ul>
 * <li>Add all jars in the lib directory to the classpath.</li>
 * <li>Add the config directory to the classpath for loadResource()</li>
 * <li>Start the server</li>
 * </ul>
 *
 * @author Iain Shigeoka
 */
abstract public class ServerStarter {

    /**
     * <p>Obtain the name of the class for the bootstrap container.
     * The class name should be the fully qualified name that can be
     * loaded by a classloader using loadClass() and the implementation
     * should be on the default application lib classpath.</p>
     *
     * @return The class name of the bootstrap container to use
     */
    abstract protected String getBootContainerClassName();

    /**
     * <p>Starts the server by loading and instantiating the bootstrap
     * container. Once the start method is called, the server is
     * started and the server starter should not be used again.</p>
     */
    protected void start() {
        // setup the classpath using JiveClassLoader
        try {
            // Load up the bootstrap container
            final ClassLoader parent = findParentClassLoader();
            // TODO: Possibly load this lib dir as a java property?
            File libDir = new File("../lib");
            ClassLoader loader = new JiveClassLoader(parent, libDir);
           
            Thread.currentThread().setContextClassLoader(loader);
            Class containerClass = loader.loadClass(getBootContainerClassName());
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
