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
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.util.zip.ZipFile;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

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
        plugins = new ConcurrentHashMap<String,Plugin>();
        classloaders = new HashMap<Plugin,PluginClassLoader>();
    }

    /**
     * Starts plugins and the plugin monitoring service.
     */
    public void start() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(new PluginMonitor(), 0, 10, TimeUnit.SECONDS);
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
        classloaders.clear();
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
                    // If global images are specified, override their URL.
                    Element imageEl = (Element)adminElement.selectSingleNode(
                            "/plugin/adminconsole/global/logo-image");
                    if (imageEl != null) {
                        imageEl.setText("plugins/" + pluginDir.getName() + "/" + imageEl.getText());
                    }
                    imageEl = (Element)adminElement.selectSingleNode(
                            "/plugin/adminconsole/global/login-image");
                    if (imageEl != null) {
                        imageEl.setText("plugins/" + pluginDir.getName() + "/" + imageEl.getText());
                    }
                    // Modify all the URL's in the XML so that they are passed through
                    // the plugin servlet correctly.
                    List urls = adminElement.selectNodes("//@url");
                    for (int i=0; i<urls.size(); i++) {
                        Attribute attr = (Attribute)urls.get(i);
                        attr.setValue("plugins/" + pluginDir.getName() + "/" + attr.getValue());
                    }
                    AdminConsole.addModel(pluginDir.getName(), adminElement);
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

    /**
     * Unloads a plugin. The {@link Plugin#destroy()} method will be called and then
     * any resources will be released.
     *
     * @param pluginName the name of the plugin to unload.
     */
    public void unloadPlugin(String pluginName) {
        Log.debug("Unloading plugin " + pluginName);
        Plugin plugin = plugins.get(pluginName);
        if (plugin == null) {
            return;
        }
        File webXML = new File(pluginDirectory + File.separator + pluginName +
                File.separator + "web" + File.separator + "web.xml");
        if (webXML.exists()) {
            AdminConsole.removeModel(pluginName);
            PluginServlet.unregisterServlets(webXML);
        }

        PluginClassLoader classLoader = classloaders.get(plugin);
        plugin.destroy();
        classLoader.destroy();
        plugins.remove(pluginName);
        classloaders.remove(plugin);
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
                    String pluginName = jarFile.getName().substring(
                            0, jarFile.getName().length()-4).toLowerCase();
                    // See if the JAR has already been exploded.
                    File dir = new File(pluginDirectory, pluginName);
                    // If the JAR hasn't been exploded, do so.
                    if (!dir.exists()) {
                        unzipPlugin(pluginName, jarFile, dir);
                    }
                    // See if the JAR is newer than the directory. If so, the plugin
                    // needs to be unloaded and then reloaded.
                    else if (jarFile.lastModified() > dir.lastModified()) {
                        unloadPlugin(pluginName);
                        if (!deleteDir(dir)) {
                            Log.error("Error unloading plugin " + pluginName + ". " +
                                    "You must manually delete the plugin directory.");
                            continue;
                        }
                        // Now unzip the plugin.
                        unzipPlugin(pluginName, jarFile, dir);
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

                // Finally see if any currently running plugins need to be unloaded
                // due to its JAR file being deleted (ignore admin plugin).
                if (plugins.size() > jars.length + 1) {
                    for (String pluginName : plugins.keySet()) {
                        if (pluginName.equals("admin")) {
                            continue;
                        }
                        File file = new File(pluginDirectory, pluginName + ".jar");
                        if (!file.exists()) {
                            unloadPlugin(pluginName);
                            if (!deleteDir(new File(pluginDirectory, pluginName))) {
                                Log.error("Error unloading plugin " + pluginName + ". " +
                                        "You must manually delete the plugin directory.");
                                continue;
                            }
                        }
                    }
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        /**
         * Unzips a plugin from a JAR file into a directory. If the JAR file
         * isn't a plugin, this method will do nothing.
         *
         * @param pluginName the name of the plugin.
         * @param file the JAR file
         * @param dir the directory to extract the plugin to. 
         */
        private void unzipPlugin(String pluginName, File file, File dir) {
            try {
                ZipFile zipFile = new JarFile(file);
                // Ensure that this JAR is a plugin.
                if (zipFile.getEntry("plugin.xml") == null) {
                    return;
                }
                dir.mkdir();
                Log.debug("Extracting plugin: " + pluginName);
                for (Enumeration e=zipFile.entries(); e.hasMoreElements(); ) {
                    JarEntry entry = (JarEntry)e.nextElement();
                    File entryFile = new File(dir, entry.getName());
                    // Ignore any manifest.mf entries.
                    if (entry.getName().toLowerCase().endsWith("manifest.mf")) {
                        continue;
                    }
                    if (!entry.isDirectory()) {
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

        /**
         * Deletes a directory.
         */
         public boolean deleteDir(File dir) {
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (int i=0; i<children.length; i++) {
                    boolean success = deleteDir(new File(dir, children[i]));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        }
    }
}