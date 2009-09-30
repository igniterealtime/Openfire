/**
 * $RCSfile$
 * $Revision: 2993 $
 * $Date: 2005-10-24 18:11:33 -0300 (Mon, 24 Oct 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.container;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

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
public class PluginClassLoader extends URLClassLoader {

    public PluginClassLoader() {
        super(new URL[] {}, findParentClassLoader());
    }

    /**
     * Adds a directory to the class loader.
     *
     * @param directory the directory.
     * @param developmentMode true if the plugin is running in development mode. This
     *      resolves classloader conflicts between the deployed plugin
     * and development classes.
     */
    public void addDirectory(File directory, boolean developmentMode) {
        try {
            // Add classes directory to classpath.
            File classesDir = new File(directory, "classes");
            if (classesDir.exists()) {
                addURL(classesDir.toURL());
            }

            // Add i18n directory to classpath.
            File databaseDir = new File(directory, "database");
            if(databaseDir.exists()){
                addURL(databaseDir.toURL());
            }

            // Add i18n directory to classpath.
            File i18nDir = new File(directory, "i18n");
            if(i18nDir.exists()){
                addURL(i18nDir.toURL());
            }

            // Add web directory to classpath.
            File webDir = new File(directory, "web");
            if(webDir.exists()){
                addURL(webDir.toURL());
            }

            // Add lib directory to classpath.
            File libDir = new File(directory, "lib");
            File[] jars = libDir.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar") || name.endsWith(".zip");
                }
            });
            if (jars != null) {
                for (int i = 0; i < jars.length; i++) {
                    if (jars[i] != null && jars[i].isFile()) {
                        if (developmentMode) {
                            // Do not add plugin-pluginName.jar to classpath.
                            if (!jars[i].getName().equals("plugin-" + directory.getName() + ".jar")) {
                                addURL(jars[i].toURL());
                            }
                        }
                        else {
                            addURL(jars[i].toURL());
                        }
                    }
                }
            }
        }
        catch (MalformedURLException mue) {
            Log.error(mue);
        }
    }

    public void addURLFile(URL file) {
        addURL(file);
    }

    /**
     * Locates the best parent class loader based on context.
     *
     * @return the best parent classloader to use.
     */
    private static ClassLoader findParentClassLoader() {
        ClassLoader parent = XMPPServer.class.getClassLoader();
        if (parent == null) {
            parent = PluginClassLoader.class.getClassLoader();
        }
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        return parent;
    }
}
