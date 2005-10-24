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

import org.jivesoftware.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Collection;

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
    private final List<URL> list = new ArrayList<URL>();


    /**
     * Constructs a plugin loader for the given plugin directory.
     *
     * @param pluginDir the plugin directory.
     * @throws SecurityException if the created class loader violates
     *                                     existing security constraints.
     */
    public PluginClassLoader(File pluginDir) throws SecurityException {
        addDirectory(pluginDir);
    }

    /**
     * Adds a directory to the class loader. The {@link #initialize()} method should be called
     * after adding the directory to make the change take effect.
     *
     * @param directory the directory.
     */
    public void addDirectory(File directory) {
        try {
            File classesDir = new File(directory, "classes");
            if (classesDir.exists()) {
                list.add(classesDir.toURL());
            }
            File libDir = new File(directory, "lib");
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
        catch (MalformedURLException mue) {
            Log.error(mue);
        }
    }

    public Collection<URL> getURLS(){
        return list;
    }

    /**
     * Adds a URL to the class loader. The {@link #initialize()} method should be called
     * after adding the URL to make the change take effect.
     *
     * @param url the url.
     */
    public void addURL(URL url) {
        list.add(url);
    }

    /**
     * Initializes the class loader with all configured classpath URLs. This method
     * can be called multiple times if the list of URLs changes.
     */
    public void initialize() {
        Iterator urls = list.iterator();
        URL[] urlArray = new URL[list.size()];
        for (int i = 0; urls.hasNext(); i++) {
            urlArray[i] = (URL)urls.next();
        }

        // If the classloader is to be used by a child plugin, we should
        // never use the ContextClassLoader, but only reuse the plugin classloader itself.
        if (classLoader != null) {
            classLoader = new URLClassLoader(urlArray, classLoader);
        }
        else {
            classLoader = new URLClassLoader(urlArray, findParentClassLoader());
        }
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
