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

package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.messenger.container.Module;
import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * <p>This class extends the current classpath with plug-in jars and classes
 * and makes it simple to load up modules from that extended classpath.</p>
 * <p>This tool will add any jars in the <tt>lib</tt> directory
 * (if it exists), and the <tt>classes</tt> directory (if it exists) to
 * the classpath.</p>
 * <p>JiveModuleLoader also sets the ccontext classloader for loaded
 * modules to a classloader with extended classpath and sets the parent
 * classloader to the 'best' available according to the following ranked
 * choices:</p>
 * <ol>
 * <li>The current thread's current context class loader</li>
 * <li>The class loader for this class</li>
 * <li>The system class loader</li>
 * </ol>
 *
 * @author Iain Shigeoka
 * @author Derek DeMoro
 */
public class JiveModuleLoader {

    /**
     * The class loader used by this pseudo loader *
     */
    private URLClassLoader classLoader;

    /**
     * Create a classloader for the given root directory.
     * We locate all the relevant URLs then create a standard URLClassLoader.
     *
     * @param dir The directory to search for other classes
     * @throws java.lang.SecurityException    If the created class loader violates existing security constraints
     * @throws java.net.MalformedURLException If a located resource name doesn't properly convert to a URL
     */
    public JiveModuleLoader(String dir) throws MalformedURLException, SecurityException {
        final List list = new ArrayList();
        File classes = new File(dir + File.separator + "classes" + File.separator);
        if (classes.exists()) {
            list.add(classes.toURL());
        }
        File lib = new File(dir + File.separator + "lib" + File.separator);
        File[] jars = lib.listFiles(new FilenameFilter() {
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
        Iterator urls = list.iterator();
        URL[] urlArray = new URL[list.size()];
        for (int i = 0; urls.hasNext(); i++) {
            urlArray[i] = (URL)urls.next();
        }
        classLoader = new URLClassLoader(urlArray, findParentClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    /**
     * Load a class using this class loader.
     *
     * @param className The fully qualified name of the class to load
     * @return The module object loaded
     * @throws ClassNotFoundException if the class could not be loaded by this class loader.
     * @throws IllegalAccessException if the class constructor was private or protected.
     * @throws InstantiationException if the class could not be instantiated (initialization error).
     * @throws SecurityException if the custom class loader not allowed.
     */
    public Module loadModule(String className) throws ClassNotFoundException, IllegalAccessException,
            InstantiationException, SecurityException
    {
        Class moduleClass = classLoader.loadClass(className);
        Module mod = (Module)moduleClass.newInstance();
        return mod;
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
        }
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        return parent;
    }
}