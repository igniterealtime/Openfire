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
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.admin.AdminConsole;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Attribute;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Loads and manages plugins. The <tt>plugins</tt> directory is monitored for any
 * new plugins, and they are dynamically loaded.
 *
 * @see Plugin
 * @author Matt Tucker
 */
public class PluginManager {

    private File pluginDirectory;
    private Map<String,Plugin> plugins;
    private Map<Plugin,PluginClassLoader> classloaders;
    private boolean setupMode = !(Boolean.valueOf(JiveGlobals.getXMLProperty("setup")).booleanValue());
    private ScheduledExecutorService executor = null;

    /**
     * Constructs a new plugin manager.
     *
     * @param pluginDir the plugin directory.
     */
    public PluginManager(File pluginDir) {
        this.pluginDirectory = pluginDir;
        plugins = new HashMap<String,Plugin>();
        classloaders = new HashMap<Plugin,PluginClassLoader>();
    }

    /**
     * Starts plugins and the plugin monitoring service.
     */
    public void start() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(new PluginMonitor(), 0, 30, TimeUnit.SECONDS);
    }

    /**
     * Shuts down all running plugins.
     */
    public void shutdown() {
        // Stop the plugin monitoring service.
        if (executor != null) {
            executor.shutdown();
        }
        // Shutdown all installed plugins.
        for (Plugin plugin : plugins.values()) {
            plugin.destroy();
        }
        plugins.clear();
    }

    /**
     * Returns a Collection of all installed plugins.
     *
     * @return a Collection of all installed plugins.
     */
    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    /**
     * Loads a plug-in module into the container. Loading consists of the
     * following steps:<ul>
     *
     *      <li>Add all jars in the <tt>lib</tt> dir (if it exists) to the class loader</li>
     *      <li>Add all files in <tt>classes</tt> dir (if it exists) to the class loader</li>
     *      <li>Locate and load <tt>module.xml</tt> into the context</li>
     *      <li>For each jive.module entry, load the given class as a module and start it</li>
     *
     * </ul>
     *
     * @param pluginDir the plugin directory.
     */
    private void loadPlugin(File pluginDir) {
        // Only load the admin plugin during setup mode.
        if (setupMode && !(pluginDir.getName().equals("admin"))) {
            return;
        }
        Log.debug("Loading plugin " + pluginDir.getName());
        Plugin plugin = null;
        try {
            File pluginConfig = new File(pluginDir, "plugin.xml");
            if (pluginConfig.exists()) {
                SAXReader saxReader = new SAXReader();
                Document pluginXML = saxReader.read(pluginConfig);
                PluginClassLoader pluginLoader = new PluginClassLoader(pluginDir);
                String className = pluginXML.selectSingleNode("/plugin/class").getText();
                plugin = (Plugin)pluginLoader.loadClass(className).newInstance();
                plugin.initialize(this, pluginDir);
                plugins.put(pluginDir.getName(), plugin);
                classloaders.put(plugin, pluginLoader);
                // Load any JSP's defined by the plugin.
                File webXML = new File(pluginDir, "web" + File.separator + "web.xml");
                if (webXML.exists()) {
                    PluginServlet.registerServlets(this, plugin, webXML);
                }
                // If there a <adminconsole> section defined, register it.
                Element adminElement = (Element)pluginXML.selectSingleNode("/plugin/adminconsole");
                if (adminElement != null) {
                    // Modify all the URL's in the XML so that they are passed through
                    // the plugin servlet correctly.
                    List urls = adminElement.selectNodes("//@url");
                    for (int i=0; i<urls.size(); i++) {
                        Attribute attr = (Attribute)urls.get(i);
                        attr.setValue("/plugins/" + pluginDir.getName() + "/" + attr.getValue());
                    }
                    AdminConsole.addXMLSource(adminElement);
                }
            }
            else {
                Log.warn("Plugin " + pluginDir + " could not be loaded: no plugin.xml file found");
            }
        }
        catch (Exception e) {
            Log.error("Error loading plugin", e);
        }
    }

    public Class loadClass(String className, Plugin plugin) throws ClassNotFoundException,
            IllegalAccessException, InstantiationException
    {
        PluginClassLoader loader = classloaders.get(plugin);
        return loader.loadClass(className);
    }

    /**
     * A service that monitors the plugin directory for plugins. It periodically
     * checks for new plugin JAR files and extracts them if they haven't already
     * been extracted. Then, any new plugin directories are loaded.
     */
    private class PluginMonitor implements Runnable {

        public void run() {
            try {
                File [] jars = pluginDirectory.listFiles(new FileFilter() {
                    public boolean accept(File pathname) {
                        return pathname.getName().toLowerCase().endsWith(".jar");
                    }
                });

                for (int i=0; i<jars.length; i++) {
                    File jarFile = jars[i];
                    String jarName = jarFile.getName().substring(
                            0, jarFile.getName().length()-4).toLowerCase();
                    // See if the JAR has already been exploded.
                    File dir = new File(pluginDirectory, jarName);
                    // If the JAR hasn't been exploded, do so.
                    if (!dir.exists()) {
                        try {
                            ZipFile zipFile = new ZipFile(jarFile);
                            // Ensure that this JAR is a plugin.
                            if (zipFile.getEntry("plugin.xml") == null) {
                                continue;
                            }
                            dir.mkdir();
                            Log.debug("Extracting plugin: " + jarName);
                            for (Enumeration e=zipFile.entries(); e.hasMoreElements(); ) {
                                ZipEntry entry = (ZipEntry)e.nextElement();
                                File entryFile = new File(dir, entry.getName());
                                if (!entryFile.isDirectory()) {
                                    entryFile.getParentFile().mkdirs();
                                    FileOutputStream out = new FileOutputStream(entryFile);
                                    InputStream zin = zipFile.getInputStream(entry);
                                    byte [] b = new byte[512];
                                    int len = 0;
                                    while ( (len=zin.read(b))!= -1 ) {
                                        out.write(b,0,len);
                                    }
                                    out.flush();
                                    out.close();
                                    zin.close();
                                }
                            }
                        }
                        catch (Exception e) {
                            Log.error(e);
                        }
                    }
                }

                File [] dirs = pluginDirectory.listFiles(new FileFilter() {
                    public boolean accept(File pathname) {
                        return pathname.isDirectory();
                    }
                });

                // Sort the list of directories so that the "admin" plugin is always
                // first in the list.
                Arrays.sort(dirs, new Comparator<File>() {
                    public int compare(File file1, File file2) {
                        if (file1.getName().equals("admin")) {
                            return -1;
                        }
                        else if (file2.getName().equals("admin")) {
                            return 1;
                        }
                        else return file1.compareTo(file2);
                    }
                });

                for (int i=0; i<dirs.length; i++) {
                    File dirFile = dirs[i];
                    // If the plugin hasn't already been started, start it.
                    if (!plugins.containsKey(dirFile.getName())) {
                        loadPlugin(dirFile);
                    }
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
    }
}