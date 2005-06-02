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

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.Version;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * Loads and manages plugins. The <tt>plugins</tt> directory is monitored for any
 * new plugins, and they are dynamically loaded.<p>
 * <p/>
 * An instance of this class can be obtained using:
 * <p/>
 * <tt>XMPPServer.getInstance().getPluginManager()</tt>
 *
 * @author Matt Tucker
 * @see Plugin
 * @see org.jivesoftware.messenger.XMPPServer#getPluginManager()
 */
public class PluginManager {

    private File pluginDirectory;
    private Map<String , Plugin> plugins;
    private Map<Plugin , PluginClassLoader> classloaders;
    private Map<Plugin , File> pluginDirs;
    private boolean setupMode = !(Boolean.valueOf(JiveGlobals.getXMLProperty("setup")).booleanValue());
    private ScheduledExecutorService executor = null;
    private Map<Plugin , PluginDevEnvironment> pluginDevelopment;

    /**
     * Constructs a new plugin manager.
     *
     * @param pluginDir the plugin directory.
     */
    public PluginManager(File pluginDir) {
        this.pluginDirectory = pluginDir;
        plugins = new HashMap<String , Plugin>();
        pluginDirs = new HashMap<Plugin , File>();
        classloaders = new HashMap<Plugin , PluginClassLoader>();
        pluginDevelopment = new HashMap<Plugin , PluginDevEnvironment>();
    }

    /**
     * Starts plugins and the plugin monitoring service.
     */
    public void start() {
        executor = new ScheduledThreadPoolExecutor(1);
        executor.scheduleWithFixedDelay(new PluginMonitor() , 0 , 10 , TimeUnit.SECONDS);
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
            plugin.destroyPlugin();
        }
        plugins.clear();
        pluginDirs.clear();
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
     * Returns a plugin by name or <tt>null</tt> if a plugin with that name does not
     * exist. The name is the name of the directory that the plugin is in such as
     * "broadcast".
     *
     * @param name the name of the plugin.
     * @return the plugin.
     */
    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    /**
     * Returns the plugin's directory.
     *
     * @param plugin the plugin.
     * @return the plugin's directory.
     */
    public File getPluginDirectory(Plugin plugin) {
        return pluginDirs.get(plugin);
    }

    /**
     * Loads a plug-in module into the container. Loading consists of the
     * following steps:<ul>
     * <p/>
     * <li>Add all jars in the <tt>lib</tt> dir (if it exists) to the class loader</li>
     * <li>Add all files in <tt>classes</tt> dir (if it exists) to the class loader</li>
     * <li>Locate and load <tt>module.xml</tt> into the context</li>
     * <li>For each jive.module entry, load the given class as a module and start it</li>
     * <p/>
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
            File pluginConfig = new File(pluginDir , "plugin.xml");
            if (pluginConfig.exists()) {
                SAXReader saxReader = new SAXReader();
                Document pluginXML = saxReader.read(pluginConfig);
                // See if the plugin specifies a version of Jive Messenger
                // required to run.
                Element minServerVersion = (Element) pluginXML.selectSingleNode("/plugin/minServerVersion");


                if (minServerVersion != null) {
                    String requiredVersion = minServerVersion.getTextTrim();
                    Version version = XMPPServer.getInstance().getServerInfo().getVersion();
                    String hasVersion = version.getMajor() + "." + version.getMinor() + "." +
                        version.getMicro();
                    if (hasVersion.compareTo(requiredVersion) < 0) {
                        String msg = "Ignoring plugin " + pluginDir.getName() + ": requires " +
                            "server version " + requiredVersion;
                        Log.warn(msg);
                        System.out.println(msg);
                        return;
                    }
                }
                PluginClassLoader pluginLoader = new PluginClassLoader(pluginDir);
                String className = pluginXML.selectSingleNode("/plugin/class").getText();
                plugin = (Plugin) pluginLoader.loadClass(className).newInstance();
                plugin.initializePlugin(this , pluginDir);
                plugins.put(pluginDir.getName() , plugin);
                pluginDirs.put(plugin , pluginDir);
                classloaders.put(plugin , pluginLoader);
                // Load any JSP's defined by the plugin.
                File webXML = new File(pluginDir , "web" + File.separator + "WEB-INF" +
                    File.separator + "web.xml");
                if (webXML.exists()) {
                    PluginServlet.registerServlets(this , plugin , webXML);
                }
                // Load any custom-defined servlets.
                File customWebXML = new File(pluginDir , "web" + File.separator + "WEB-INF" +
                    File.separator + "web-custom.xml");
                if (customWebXML.exists()) {
                    PluginServlet.registerServlets(this , plugin , customWebXML);
                }

                Element developmentNode = (Element) pluginXML.selectSingleNode("/plugin/development");
                if (developmentNode != null) {
                    Element webRoot = (Element) developmentNode.selectSingleNode("/plugin/development/webRoot");
                    Element classesDir = (Element) developmentNode.selectSingleNode("/plugin/development/classesDir");

                    PluginDevEnvironment dev = new PluginDevEnvironment();

                    String wrd = webRoot.getTextTrim();
                    File webRootDir = new File(wrd);
                    if (webRootDir.exists()) {
                        dev.setWebRoot(webRootDir);
                    }

                    String cd = classesDir.getTextTrim();
                    File classes = new File(cd);
                    if (classes.exists()) {
                        dev.setClassesDir(classes);
                    }

                    pluginDevelopment.put(plugin , dev);
                }

                // If there a <adminconsole> section defined, register it.
                Element adminElement = (Element) pluginXML.selectSingleNode("/plugin/adminconsole");
                if (adminElement != null) {
                    // If global images are specified, override their URL.
                    Element imageEl = (Element) adminElement.selectSingleNode("/plugin/adminconsole/global/logo-image");
                    if (imageEl != null) {
                        imageEl.setText("plugins/" + pluginDir.getName() + "/" + imageEl.getText());
                    }
                    imageEl = (Element) adminElement.selectSingleNode("/plugin/adminconsole/global/login-image");
                    if (imageEl != null) {
                        imageEl.setText("plugins/" + pluginDir.getName() + "/" + imageEl.getText());
                    }
                    // Modify all the URL's in the XML so that they are passed through
                    // the plugin servlet correctly.
                    List urls = adminElement.selectNodes("//@url");
                    for (int i = 0;i < urls.size();i++) {
                        Attribute attr = (Attribute) urls.get(i);
                        attr.setValue("plugins/" + pluginDir.getName() + "/" + attr.getValue());
                    }
                    AdminConsole.addModel(pluginDir.getName() , adminElement);
                }
            }
            else {
                Log.warn("Plugin " + pluginDir + " could not be loaded: no plugin.xml file found");
            }
        }
        catch (Exception e) {
            Log.error("Error loading plugin" , e);
        }
    }

    /**
     * Unloads a plugin. The {@link Plugin#destroyPlugin()} method will be called and then
     * any resources will be released. The name should be the name of the plugin directory
     * and not the name as given by the plugin meta-data. This method only removes
     * the plugin but does not delete the plugin JAR file. Therefore, if the plugin JAR
     * still exists after this method is called, the plugin will be started again the next
     * time the plugin monitor process runs. This is useful for "restarting" plugins.<p>
     * <p/>
     * This method is called automatically when a plugin's JAR file is deleted.
     *
     * @param pluginName the name of the plugin to unload.
     */
    public void unloadPlugin(String pluginName) {
        Log.debug("Unloading plugin " + pluginName);
        Plugin plugin = plugins.get(pluginName);
        if (plugin == null) {
            return;
        }
        File webXML = new File(pluginDirectory , "web" + File.separator + "WEB-INF" +
            File.separator + "web.xml");
        if (webXML.exists()) {
            AdminConsole.removeModel(pluginName);
            PluginServlet.unregisterServlets(webXML);
        }
        File customWebXML = new File(pluginDirectory , "web" + File.separator + "WEB-INF" +
            File.separator + "web-custom.xml");
        if (customWebXML.exists()) {
            PluginServlet.unregisterServlets(customWebXML);
        }

        PluginClassLoader classLoader = classloaders.get(plugin);
        plugin.destroyPlugin();
        classLoader.destroy();
        plugins.remove(pluginName);
        pluginDirs.remove(plugin);
        classloaders.remove(plugin);
    }

    public Class loadClass(String className , Plugin plugin) throws ClassNotFoundException ,
        IllegalAccessException , InstantiationException {
        PluginClassLoader loader = classloaders.get(plugin);
        return loader.loadClass(className);
    }

    /**
     * Returns the name of a plugin. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     * Note that this value is distinct from the name of the plugin directory.
     *
     * @param plugin the plugin.
     * @return the plugin's name.
     */
    public String getName(Plugin plugin) {
        String name = getElementValue(plugin , "/plugin/name");
        if (name != null) {
            return name;
        }
        else {
            return pluginDirs.get(plugin).getName();
        }
    }

    /**
     * Returns the description of a plugin. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's description.
     */
    public String getDescription(Plugin plugin) {
        return getElementValue(plugin , "/plugin/description");
    }

    /**
     * Returns the author of a plugin. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's author.
     */
    public String getAuthor(Plugin plugin) {
        return getElementValue(plugin , "/plugin/author");
    }

    /**
     * Returns the version of a plugin. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's version.
     */
    public String getVersion(Plugin plugin) {
        return getElementValue(plugin , "/plugin/version");
    }

    /**
     * Returns the value of an element selected via an xpath expression from
     * a Plugin's plugin.xml file.
     *
     * @param plugin the plugin.
     * @param xpath  the xpath expression.
     * @return the value of the element selected by the xpath expression.
     */
    private String getElementValue(Plugin plugin , String xpath) {
        File pluginDir = pluginDirs.get(plugin);
        if (pluginDir == null) {
            return null;
        }
        try {
            File pluginConfig = new File(pluginDir , "plugin.xml");
            if (pluginConfig.exists()) {
                SAXReader saxReader = new SAXReader();
                Document pluginXML = saxReader.read(pluginConfig);
                Element element = (Element) pluginXML.selectSingleNode(xpath);
                if (element != null) {
                    return element.getTextTrim();
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        return null;
    }

    /**
     * A service that monitors the plugin directory for plugins. It periodically
     * checks for new plugin JAR files and extracts them if they haven't already
     * been extracted. Then, any new plugin directories are loaded.
     */
    private class PluginMonitor implements Runnable {

        public void run() {
            try {
                File[] jars = pluginDirectory.listFiles(new FileFilter() {
                    public boolean accept(File pathname) {
                        String fileName = pathname.getName().toLowerCase();
                        return (fileName.endsWith(".jar") || fileName.endsWith(".war"));
                    }
                });

                for (int i = 0;i < jars.length;i++) {
                    File jarFile = jars[i];
                    String pluginName = jarFile.getName().substring(0 , jarFile.getName().length() - 4).toLowerCase();
                    // See if the JAR has already been exploded.
                    File dir = new File(pluginDirectory , pluginName);
                    // If the JAR hasn't been exploded, do so.
                    if (!dir.exists()) {
                        unzipPlugin(pluginName , jarFile , dir);
                    }
                    // See if the JAR is newer than the directory. If so, the plugin
                    // needs to be unloaded and then reloaded.
                    else if (jarFile.lastModified() > dir.lastModified()) {
                        unloadPlugin(pluginName);
                        // Ask the system to clean up references.
                        System.gc();
                        while (!deleteDir(dir)) {
                            Log.error("Error unloading plugin " + pluginName + ". " +
                                "Will attempt again momentarily.");
                            Thread.sleep(5000);
                        }
                        // Now unzip the plugin.
                        unzipPlugin(pluginName , jarFile , dir);
                    }
                }

                File[] dirs = pluginDirectory.listFiles(new FileFilter() {
                    public boolean accept(File pathname) {
                        return pathname.isDirectory();
                    }
                });

                // Sort the list of directories so that the "admin" plugin is always
                // first in the list.
                Arrays.sort(dirs , new Comparator<File>() {
                    public int compare(File file1 , File file2) {
                        if (file1.getName().equals("admin")) {
                            return -1;
                        }
                        else if (file2.getName().equals("admin")) {
                            return 1;
                        }
                        else
                            return file1.compareTo(file2);
                    }
                });

                for (int i = 0;i < dirs.length;i++) {
                    File dirFile = dirs[i];
                    // If the plugin hasn't already been started, start it.
                    if (!plugins.containsKey(dirFile.getName())) {
                        loadPlugin(dirFile);
                    }
                }

                // See if any currently running plugins need to be unloaded
                // due to its JAR file being deleted (ignore admin plugin).
                if (plugins.size() > jars.length + 1) {
                    // Build a list of plugins to delete first so that the plugins
                    // keyset is modified as we're iterating through it.
                    List<String> toDelete = new ArrayList<String>();
                    for (String pluginName : plugins.keySet()) {
                        if (pluginName.equals("admin")) {
                            continue;
                        }
                        File file = new File(pluginDirectory , pluginName + ".jar");
                        if (!file.exists()) {
                            toDelete.add(pluginName);
                        }
                    }
                    for (String pluginName : toDelete) {
                        unloadPlugin(pluginName);
                        System.gc();
                        while (!deleteDir(new File(pluginDirectory , pluginName))) {
                            Log.error("Error unloading plugin " + pluginName + ". " +
                                "Will attempt again momentarily.");
                            Thread.sleep(5000);
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
         * @param file       the JAR file
         * @param dir        the directory to extract the plugin to.
         */
        private void unzipPlugin(String pluginName , File file , File dir) {
            try {
                ZipFile zipFile = new JarFile(file);
                // Ensure that this JAR is a plugin.
                if (zipFile.getEntry("plugin.xml") == null) {
                    return;
                }
                dir.mkdir();
                Log.debug("Extracting plugin: " + pluginName);
                for (Enumeration e = zipFile.entries();e.hasMoreElements();) {
                    JarEntry entry = (JarEntry) e.nextElement();
                    File entryFile = new File(dir , entry.getName());
                    // Ignore any manifest.mf entries.
                    if (entry.getName().toLowerCase().endsWith("manifest.mf")) {
                        continue;
                    }
                    if (!entry.isDirectory()) {
                        entryFile.getParentFile().mkdirs();
                        FileOutputStream out = new FileOutputStream(entryFile);
                        InputStream zin = zipFile.getInputStream(entry);
                        byte[] b = new byte[512];
                        int len = 0;
                        while ((len = zin.read(b)) != -1) {
                            out.write(b , 0 , len);
                        }
                        out.flush();
                        out.close();
                        zin.close();
                    }
                }
                zipFile.close();
                zipFile = null;
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
                for (int i = 0;i < children.length;i++) {
                    boolean success = deleteDir(new File(dir , children[i]));
                    if (!success) {
                        return false;
                    }
                }
            }
            return dir.delete();
        }
    }

    public PluginDevEnvironment getDevEnvironment(Plugin plugin) {
        return pluginDevelopment.get(plugin);
    }
}