/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ClassLoader for plugins. It searches the plugin directory for classes
 * and JAR files, then constructs a class loader for the resources found.
 * Resources are loaded as follows:<ul>
 * <p/>
 * <li>Any JAR files in the <tt>lib</tt> will be added to the classpath.
 * <li>Any files in the classes directory will be added to the classpath.
 * </ul>
 *
 * @author Derek DeMoro
 */
class PluginClassLoader {

    private URLClassLoader classLoader;
    private final List list = new ArrayList();


    /**
     * Constructs a plugin loader for the given plugin directory.
     *
     * @param pluginDir the plugin directory.
     * @throws java.lang.SecurityException    if the created class loader violates
     *                                        existing security constraints.
     * @throws java.net.MalformedURLException if a located resource name cannot be
     *                                        properly converted to a URL.
     */
    public PluginClassLoader(File pluginDir) throws MalformedURLException, SecurityException {
        File classesDir = new File(pluginDir, "classes");
        if (classesDir.exists()) {
            list.add(classesDir.toURL());
        }
        File libDir = new File(pluginDir, "lib");
        File[] jars = libDir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".jar") || name.endsWith(".zip");
            }
        });
        if (jars != null) {
            for (int i = 0; i < jars.length; i++) {
                if (jars[i] != null && jars[i].isFile()) {
                    list.add(jars[i].toURL());
                }
            }
        }
    }

    public void addURL(URL url) {
        list.add(url);
    }

    public void initialize(){
        Iterator urls = list.iterator();
        URL[] urlArray = new URL[list.size()];
        for (int i = 0; urls.hasNext(); i++) {
            urlArray[i] = (URL)urls.next();
        }
        classLoader = new URLClassLoader(urlArray, findParentClassLoader());
    }

    /**
     * Load a class using this plugin class loader.
     *
     * @param name the fully qualified name of the class to load.
     * @return The module object loaded
     * @throws ClassNotFoundException if the class could not be loaded by this class loader.
     * @throws IllegalAccessException if the class constructor was private or protected.
     * @throws InstantiationException if the class could not be instantiated (initialization error).
     * @throws SecurityException      if the custom class loader not allowed.
     */
    public Class loadClass(String name) throws ClassNotFoundException, IllegalAccessException,
            InstantiationException, SecurityException {
        return classLoader.loadClass(name);
    }

    /**
     * Destroys this class loader.
     */
    public void destroy() {
        classLoader = null;
    }

    /**
     * Locates the best parent class loader based on context.
     *
     * @return the best parent classloader to use.
     */
    private ClassLoader findParentClassLoader() {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null) {
            parent = this.getClass().getClassLoader();
        }
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        return parent;
    }
}