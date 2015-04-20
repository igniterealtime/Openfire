/**
 * $RCSfile$
 * $Revision: 3001 $
 * $Date: 2005-10-31 05:39:25 -0300 (Mon, 31 Oct 2005) $
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and manages plugins. The <tt>plugins</tt> directory is monitored for any
 * new plugins, and they are dynamically loaded.
 *
 * <p>An instance of this class can be obtained using:</p>
 *
 * <tt>XMPPServer.getInstance().getPluginManager()</tt>
 *
 * @author Matt Tucker
 * @see Plugin
 * @see org.jivesoftware.openfire.XMPPServer#getPluginManager()
 */
public class PluginManager {

	private static final Logger Log = LoggerFactory.getLogger(PluginManager.class);

    private File pluginDirectory;
    private Map<String, Plugin> plugins;
    private Map<Plugin, PluginClassLoader> classloaders;
    private Map<Plugin, File> pluginDirs;
    /**
     * Keep track of plugin names and their unzipped files. This list is updated when plugin
     * is exploded and not when is loaded.
     */
    private Map<String, File> pluginFiles;
    private ScheduledExecutorService executor = null;
    private Map<Plugin, PluginDevEnvironment> pluginDevelopment;
    private Map<Plugin, List<String>> parentPluginMap;
    private Map<Plugin, String> childPluginMap;
    private Set<String> devPlugins;
    private PluginMonitor pluginMonitor;
    private Set<PluginListener> pluginListeners = new CopyOnWriteArraySet<PluginListener>();
    private Set<PluginManagerListener> pluginManagerListeners = new CopyOnWriteArraySet<PluginManagerListener>();

    /**
     * Constructs a new plugin manager.
     *
     * @param pluginDir the plugin directory.
     */
    public PluginManager(File pluginDir) {
        this.pluginDirectory = pluginDir;
        plugins = new ConcurrentHashMap<String, Plugin>();
        pluginDirs = new HashMap<Plugin, File>();
        pluginFiles = new HashMap<String, File>();
        classloaders = new HashMap<Plugin, PluginClassLoader>();
        pluginDevelopment = new HashMap<Plugin, PluginDevEnvironment>();
        parentPluginMap = new HashMap<Plugin, List<String>>();
        childPluginMap = new HashMap<Plugin, String>();
        devPlugins = new HashSet<String>();
        pluginMonitor = new PluginMonitor();
    }

    /**
     * Starts plugins and the plugin monitoring service.
     */
    public void start() {
        executor = new ScheduledThreadPoolExecutor(1);
        // See if we're in development mode. If so, check for new plugins once every 5 seconds.
        // Otherwise, default to every 20 seconds.
        if (Boolean.getBoolean("developmentMode")) {
            executor.scheduleWithFixedDelay(pluginMonitor, 0, 5, TimeUnit.SECONDS);
        }
        else {
            executor.scheduleWithFixedDelay(pluginMonitor, 0, 20, TimeUnit.SECONDS);
        }
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
            try {
                plugin.destroyPlugin();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
        plugins.clear();
        pluginDirs.clear();
        pluginFiles.clear();
        classloaders.clear();
        pluginDevelopment.clear();
        childPluginMap.clear();
        pluginMonitor = null;
    }

    /**
     * Installs or updates an existing plugin.
     *
     * @param in the input stream that contains the new plugin definition.
     * @param pluginFilename the filename of the plugin to create or update.
     * @return true if the plugin was successfully installed or updated.
     */
    public boolean installPlugin(InputStream in, String pluginFilename) {
        if (in == null || pluginFilename == null || pluginFilename.length() < 1) {
            Log.error("Error installing plugin: Input stream was null or pluginFilename was null or had no length.");
            return false;
        }
        try {
            byte[] b = new byte[1024];
            int len;
            // If pluginFilename is a path instead of a simple file name, we only want the file name
            int index = pluginFilename.lastIndexOf(File.separator);
            if (index != -1) {
                pluginFilename = pluginFilename.substring(index+1);
            }
            // Absolute path to the plugin file
            String absolutePath = pluginDirectory + File.separator + pluginFilename;
            // Save input stream contents to a temp file
            OutputStream out = new FileOutputStream(absolutePath + ".part");
            while ((len = in.read(b)) != -1) {
                     //write byte to file
                     out.write(b, 0, len);
            }
            out.close();
            // Delete old .jar (if it exists)
            new File(absolutePath).delete();
            // Rename temp file to .jar
            new File(absolutePath + ".part").renameTo(new File(absolutePath));
            // Ask the plugin monitor to update the plugin immediately.
            pluginMonitor.run();
        }
        catch (IOException e) {
            Log.error("Error installing new version of plugin: " + pluginFilename, e);
            return false;
        }
        return true;
    }

    /**
     * Returns true if the specified filename, that belongs to a plugin, exists.
     *
     * @param pluginFilename the filename of the plugin to create or update.
     * @return true if the specified filename, that belongs to a plugin, exists.
     */
    public boolean isPluginDownloaded(String pluginFilename) {
        return new File(pluginDirectory + File.separator + pluginFilename).exists();
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
     * Returns the JAR or WAR file that created the plugin.
     *
     * @param name the name of the plugin.
     * @return the plugin JAR or WAR file.
     */
    public File getPluginFile(String name) {
        return pluginFiles.get(name);
    }

    /**
     * Returns true if at least one attempt to load plugins has been done. A true value does not mean
     * that available plugins have been loaded nor that plugins to be added in the future are already
     * loaded. :)<p>
     *
     * TODO Current version does not consider child plugins that may be loaded in a second attempt. It either
     * TODO consider plugins that were found but failed to be loaded due to some error.
     *
     * @return true if at least one attempt to load plugins has been done.
     */
    public boolean isExecuted() {
        return pluginMonitor.executed;
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
        if (XMPPServer.getInstance().isSetupMode() && !(pluginDir.getName().equals("admin"))) {
            return;
        }
        Log.debug("PluginManager: Loading plugin " + pluginDir.getName());
        Plugin plugin;
        try {
            File pluginConfig = new File(pluginDir, "plugin.xml");
            if (pluginConfig.exists()) {
                SAXReader saxReader = new SAXReader();
                saxReader.setEncoding("UTF-8");
                Document pluginXML = saxReader.read(pluginConfig);

                // See if the plugin specifies a version of Openfire
                // required to run.
                Element minServerVersion = (Element)pluginXML.selectSingleNode("/plugin/minServerVersion");
                if (minServerVersion != null) {
                    Version requiredVersion = new Version(minServerVersion.getTextTrim());
                    Version currentVersion = XMPPServer.getInstance().getServerInfo().getVersion();
                    if (requiredVersion.isNewerThan(currentVersion)) {
                        String msg = "Ignoring plugin " + pluginDir.getName() + ": requires " +
                            "server version " + requiredVersion;
                        Log.warn(msg);
                        System.out.println(msg);
                        return;
                    }
                }

                PluginClassLoader pluginLoader;

                // Check to see if this is a child plugin of another plugin. If it is, we
                // re-use the parent plugin's class loader so that the plugins can interact.
                Element parentPluginNode = (Element)pluginXML.selectSingleNode("/plugin/parentPlugin");

                String pluginName = pluginDir.getName();
                String webRootKey = pluginName + ".webRoot";
                String classesDirKey = pluginName + ".classes";
                String webRoot = System.getProperty(webRootKey);
                String classesDir = System.getProperty(classesDirKey);

                if (webRoot != null) {
                    final File compilationClassesDir = new File(pluginDir, "classes");
                    if (!compilationClassesDir.exists()) {
                        compilationClassesDir.mkdir();
                    }
                    compilationClassesDir.deleteOnExit();
                }

                if (parentPluginNode != null) {
                    String parentPlugin = parentPluginNode.getTextTrim();
                    // See if the parent is already loaded.
                    if (plugins.containsKey(parentPlugin)) {
                        pluginLoader = classloaders.get(getPlugin(parentPlugin));
                        pluginLoader.addDirectory(pluginDir, classesDir != null);

                    }
                    else {
                        // See if the parent plugin exists but just hasn't been loaded yet.
                        // This can only be the case if this plugin name is alphabetically before
                        // the parent.
                        if (pluginName.compareTo(parentPlugin) < 0) {
                            // See if the parent exists.
                            File file = new File(pluginDir.getParentFile(), parentPlugin + ".jar");
                            if (file.exists()) {
                                // Silently return. The child plugin will get loaded up on the next
                                // plugin load run after the parent.
                                return;
                            }
                            else {
                                file = new File(pluginDir.getParentFile(), parentPlugin + ".war");
                                if (file.exists()) {
                                    // Silently return. The child plugin will get loaded up on the next
                                    // plugin load run after the parent.
                                    return;
                                }
                                else {
                                    String msg = "Ignoring plugin " + pluginName + ": parent plugin " +
                                        parentPlugin + " not present.";
                                    Log.warn(msg);
                                    System.out.println(msg);
                                    return;
                                }
                            }
                        }
                        else {
                            String msg = "Ignoring plugin " + pluginName + ": parent plugin " +
                                parentPlugin + " not present.";
                            Log.warn(msg);
                            System.out.println(msg);
                            return;
                        }
                    }
                }
                // This is not a child plugin, so create a new class loader.
                else {
                    pluginLoader = new PluginClassLoader();
                    pluginLoader.addDirectory(pluginDir, classesDir != null);
                }

                // Check to see if development mode is turned on for the plugin. If it is,
                // configure dev mode.

                PluginDevEnvironment dev = null;
                if (webRoot != null || classesDir != null) {
                    dev = new PluginDevEnvironment();

                    System.out.println("Plugin " + pluginName + " is running in development mode.");
                    Log.info("Plugin " + pluginName + " is running in development mode.");
                    if (webRoot != null) {
                        File webRootDir = new File(webRoot);
                        if (!webRootDir.exists()) {
                            // Ok, let's try it relative from this plugin dir?
                            webRootDir = new File(pluginDir, webRoot);
                        }

                        if (webRootDir.exists()) {
                            dev.setWebRoot(webRootDir);
                        }
                    }

                    if (classesDir != null) {
                        File classes = new File(classesDir);
                        if (!classes.exists()) {
                            // ok, let's try it relative from this plugin dir?
                            classes = new File(pluginDir, classesDir);
                        }

                        if (classes.exists()) {
                            dev.setClassesDir(classes);
                            pluginLoader.addURLFile(classes.getAbsoluteFile().toURI().toURL());
                        }
                    }
                }

                String className = pluginXML.selectSingleNode("/plugin/class").getText().trim();
                plugin = (Plugin)pluginLoader.loadClass(className).newInstance();
                if (parentPluginNode != null) {
                    String parentPlugin = parentPluginNode.getTextTrim();
                    // See if the parent is already loaded.
                    if (plugins.containsKey(parentPlugin)) {
                        pluginLoader = classloaders.get(getPlugin(parentPlugin));
                        classloaders.put(plugin, pluginLoader);
                    }
                }

                plugins.put(pluginName, plugin);
                pluginDirs.put(plugin, pluginDir);

                // If this is a child plugin, register it as such.
                if (parentPluginNode != null) {
                    String parentPlugin = parentPluginNode.getTextTrim();
                    List<String> childrenPlugins = parentPluginMap.get(plugins.get(parentPlugin));
                    if (childrenPlugins == null) {
                        childrenPlugins = new ArrayList<String>();
                        parentPluginMap.put(plugins.get(parentPlugin), childrenPlugins);
                    }
                    childrenPlugins.add(pluginName);
                    // Also register child to parent relationship.
                    childPluginMap.put(plugin, parentPlugin);
                }
                else {
                    // Only register the class loader in the case of this not being
                    // a child plugin.
                    classloaders.put(plugin, pluginLoader);
                }

                // Check the plugin's database schema (if it requires one).
                if (!DbConnectionManager.getSchemaManager().checkPluginSchema(plugin)) {
                    // The schema was not there and auto-upgrade failed.
                    Log.error(pluginName + " - " +
                            LocaleUtils.getLocalizedString("upgrade.database.failure"));
                    System.out.println(pluginName + " - " +
                            LocaleUtils.getLocalizedString("upgrade.database.failure"));
                }

                // Load any JSP's defined by the plugin.
                File webXML = new File(pluginDir, "web" + File.separator + "WEB-INF" +
                    File.separator + "web.xml");
                if (webXML.exists()) {
                    PluginServlet.registerServlets(this, plugin, webXML);
                }
                // Load any custom-defined servlets.
                File customWebXML = new File(pluginDir, "web" + File.separator + "WEB-INF" +
                    File.separator + "web-custom.xml");
                if (customWebXML.exists()) {
                    PluginServlet.registerServlets(this, plugin, customWebXML);
                }

                if (dev != null) {
                    pluginDevelopment.put(plugin, dev);
                }

                // Configure caches of the plugin
                configureCaches(pluginDir, pluginName);

                // Init the plugin.
                ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(pluginLoader);
                plugin.initializePlugin(this, pluginDir);
                Thread.currentThread().setContextClassLoader(oldLoader);

                // If there a <adminconsole> section defined, register it.
                Element adminElement = (Element)pluginXML.selectSingleNode("/plugin/adminconsole");
                if (adminElement != null) {
                    Element appName = (Element)adminElement.selectSingleNode(
                        "/plugin/adminconsole/global/appname");
                    if (appName != null) {
                        // Set the plugin name so that the proper i18n String can be loaded.
                        appName.addAttribute("plugin", pluginName);
                    }
                    // If global images are specified, override their URL.
                    Element imageEl = (Element)adminElement.selectSingleNode(
                        "/plugin/adminconsole/global/logo-image");
                    if (imageEl != null) {
                        imageEl.setText("plugins/" + pluginName + "/" + imageEl.getText());
                        // Set the plugin name so that the proper i18n String can be loaded.
                        imageEl.addAttribute("plugin", pluginName);
                    }
                    imageEl = (Element)adminElement.selectSingleNode("/plugin/adminconsole/global/login-image");
                    if (imageEl != null) {
                        imageEl.setText("plugins/" + pluginName + "/" + imageEl.getText());
                        // Set the plugin name so that the proper i18n String can be loaded.
                        imageEl.addAttribute("plugin", pluginName);
                    }
                    // Modify all the URL's in the XML so that they are passed through
                    // the plugin servlet correctly.
                    List urls = adminElement.selectNodes("//@url");
                    for (Object url : urls) {
                        Attribute attr = (Attribute)url;
                        attr.setValue("plugins/" + pluginName + "/" + attr.getValue());
                    }
                    // In order to internationalize the names and descriptions in the model,
                    // we add a "plugin" attribute to each tab, sidebar, and item so that
                    // the the renderer knows where to load the i18n Strings from.
                    String[] elementNames = new String [] { "tab", "sidebar", "item" };
                    for (String elementName : elementNames) {
                        List values = adminElement.selectNodes("//" + elementName);
                        for (Object value : values) {
                            Element element = (Element) value;
                            // Make sure there's a name or description. Otherwise, no need to
                            // override i18n settings.
                            if (element.attribute("name") != null ||
                                    element.attribute("value") != null) {
                                element.addAttribute("plugin", pluginName);
                            }
                        }
                    }

                    AdminConsole.addModel(pluginName, adminElement);
                }
                firePluginCreatedEvent(pluginName, plugin);
            }
            else {
                Log.warn("Plugin " + pluginDir + " could not be loaded: no plugin.xml file found");
            }
        }
        catch (Throwable e) {
            Log.error("Error loading plugin: " + pluginDir, e);
        }
    }

    private void configureCaches(File pluginDir, String pluginName) {
        File cacheConfig = new File(pluginDir, "cache-config.xml");
        if (cacheConfig.exists()) {
            PluginCacheConfigurator configurator = new PluginCacheConfigurator();
            try {
                configurator.setInputStream(new BufferedInputStream(new FileInputStream(cacheConfig)));
                configurator.configure(pluginName);
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    private void firePluginCreatedEvent(String name, Plugin plugin) {
        for(PluginListener listener : pluginListeners) {
            listener.pluginCreated(name, plugin);
        }
    }

    private void firePluginsMonitored() {
        for(PluginManagerListener listener : pluginManagerListeners) {
            listener.pluginsMonitored();
        }
    }

    /**
     * Unloads a plugin. The {@link Plugin#destroyPlugin()} method will be called and then
     * any resources will be released. The name should be the name of the plugin directory
     * and not the name as given by the plugin meta-data. This method only removes
     * the plugin but does not delete the plugin JAR file. Therefore, if the plugin JAR
     * still exists after this method is called, the plugin will be started again the next
     * time the plugin monitor process runs. This is useful for "restarting" plugins.
     * <p>
     * This method is called automatically when a plugin's JAR file is deleted.
     * </p>
     *
     * @param pluginName the name of the plugin to unload.
     */
    public void unloadPlugin(String pluginName) {
        Log.debug("PluginManager: Unloading plugin " + pluginName);

        Plugin plugin = plugins.get(pluginName);
        if (plugin != null) {
            // Remove from dev mode if it exists.
            pluginDevelopment.remove(plugin);

            // See if any child plugins are defined.
            if (parentPluginMap.containsKey(plugin)) {
                String[] childPlugins =
                        parentPluginMap.get(plugin).toArray(new String[parentPluginMap.get(plugin).size()]);
                parentPluginMap.remove(plugin);
                for (String childPlugin : childPlugins) {
                    Log.debug("Unloading child plugin: " + childPlugin);
                    childPluginMap.remove(plugins.get(childPlugin));
                    unloadPlugin(childPlugin);
                }
            }

            File webXML = new File(pluginDirectory, pluginName + File.separator + "web" + File.separator + "WEB-INF" +
                File.separator + "web.xml");
            if (webXML.exists()) {
                AdminConsole.removeModel(pluginName);
                PluginServlet.unregisterServlets(webXML);
            }
            File customWebXML = new File(pluginDirectory, pluginName + File.separator + "web" + File.separator + "WEB-INF" +
                File.separator + "web-custom.xml");
            if (customWebXML.exists()) {
                PluginServlet.unregisterServlets(customWebXML);
            }

            // Wrap destroying the plugin in a try/catch block. Otherwise, an exception raised
            // in the destroy plugin process will disrupt the whole unloading process. It's still
            // possible that classloader destruction won't work in the case that destroying the plugin
            // fails. In that case, Openfire may need to be restarted to fully cleanup the plugin
            // resources.
            try {
                plugin.destroyPlugin();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }

        // Remove references to the plugin so it can be unloaded from memory
        // If plugin still fails to be removed then we will add references back
        // Anyway, for a few seconds admins may not see the plugin in the admin console
        // and in a subsequent refresh it will appear if failed to be removed
        plugins.remove(pluginName);
        File pluginFile = pluginDirs.remove(plugin);
        PluginClassLoader pluginLoader = classloaders.remove(plugin);

        // try to close the cached jar files from the plugin class loader
        if (pluginLoader != null) {
        	pluginLoader.unloadJarFiles();
        } else {
        	Log.warn("No plugin loader found for " + pluginName);
        }

        // Try to remove the folder where the plugin was exploded. If this works then
        // the plugin was successfully removed. Otherwise, some objects created by the
        // plugin are still in memory.
        File dir = new File(pluginDirectory, pluginName);
        // Give the plugin 2 seconds to unload.
        try {
            Thread.sleep(2000);
            // Ask the system to clean up references.
            System.gc();
            int count = 0;
            while (!deleteDir(dir) && count++ < 5) {
                Log.warn("Error unloading plugin " + pluginName + ". " + "Will attempt again momentarily.");
                Thread.sleep(8000);
                // Ask the system to clean up references.
                System.gc();
            }
        } catch (InterruptedException e) {
            Log.error(e.getMessage(), e);
        }

        if (plugin != null && !dir.exists()) {
            // Unregister plugin caches
            PluginCacheRegistry.getInstance().unregisterCaches(pluginName);

            // See if this is a child plugin. If it is, we should unload
            // the parent plugin as well.
            if (childPluginMap.containsKey(plugin)) {
                String parentPluginName = childPluginMap.get(plugin);
                Plugin parentPlugin = plugins.get(parentPluginName);
                List<String> childrenPlugins = parentPluginMap.get(parentPlugin);

                childrenPlugins.remove(pluginName);
                childPluginMap.remove(plugin);

                // When the parent plugin implements PluginListener, its pluginDestroyed() method
                // isn't called if it dies first before its child. Athough the parent will die anyway,
                // it's proper if the parent "gets informed first" about the dying child when the
                // child is the one being killed first.
                if (parentPlugin instanceof PluginListener) {
                    PluginListener listener;
                    listener = (PluginListener) parentPlugin;
                    listener.pluginDestroyed(pluginName, plugin);
                }
                unloadPlugin(parentPluginName);
            }
            firePluginDestroyedEvent(pluginName, plugin);
        }
        else if (plugin != null) {
            // Restore references since we failed to remove the plugin
            plugins.put(pluginName, plugin);
            pluginDirs.put(plugin, pluginFile);
            classloaders.put(plugin, pluginLoader);
        }
    }

    private void firePluginDestroyedEvent(String name, Plugin plugin) {
        for (PluginListener listener : pluginListeners) {
            listener.pluginDestroyed(name, plugin);
        }
    }

    /**
     * Loads a class from the classloader of a plugin.
     *
     * @param plugin the plugin.
     * @param className the name of the class to load.
     * @return the class.
     * @throws ClassNotFoundException if the class was not found.
     * @throws IllegalAccessException if not allowed to access the class.
     * @throws InstantiationException if the class could not be created.
     */
    public Class loadClass(Plugin plugin, String className) throws ClassNotFoundException,
        IllegalAccessException, InstantiationException {
        PluginClassLoader loader = classloaders.get(plugin);
        return loader.loadClass(className);
    }

    /**
     * Returns a plugin's dev environment if development mode is enabled for
     * the plugin.
     *
     * @param plugin the plugin.
     * @return the plugin dev environment, or <tt>null</tt> if development
     *         mode is not enabled for the plugin.
     */
    public PluginDevEnvironment getDevEnvironment(Plugin plugin) {
        return pluginDevelopment.get(plugin);
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
        String name = getElementValue(plugin, "/plugin/name");
        String pluginName = pluginDirs.get(plugin).getName();
        if (name != null) {
            return AdminConsole.getAdminText(name, pluginName);
        }
        else {
            return pluginName;
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
        String pluginName = pluginDirs.get(plugin).getName();
        return AdminConsole.getAdminText(getElementValue(plugin, "/plugin/description"), pluginName);
    }

    /**
     * Returns the author of a plugin. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's author.
     */
    public String getAuthor(Plugin plugin) {
        return getElementValue(plugin, "/plugin/author");
    }

    /**
     * Returns the version of a plugin. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's version.
     */
    public String getVersion(Plugin plugin) {
        return getElementValue(plugin, "/plugin/version");
    }

     /**
     * Returns the minimum server version this plugin can run within. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's version.
     */
    public String getMinServerVersion(Plugin plugin) {
        return getElementValue(plugin, "/plugin/minServerVersion");
    }

    /**
     * Returns the database schema key of a plugin, if it exists. The value is retrieved
     * from the plugin.xml file of the plugin. If the value could not be found, <tt>null</tt>
     * will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's database schema key or <tt>null</tt> if it doesn't exist.
     */
    public String getDatabaseKey(Plugin plugin) {
        return getElementValue(plugin, "/plugin/databaseKey");
    }

    /**
     * Returns the database schema version of a plugin, if it exists. The value is retrieved
     * from the plugin.xml file of the plugin. If the value could not be found, <tt>-1</tt>
     * will be returned.
     *
     * @param plugin the plugin.
     * @return the plugin's database schema version or <tt>-1</tt> if it doesn't exist.
     */
    public int getDatabaseVersion(Plugin plugin) {
        String versionString = getElementValue(plugin, "/plugin/databaseVersion");
        if (versionString != null) {
            try {
                return Integer.parseInt(versionString.trim());
            }
            catch (NumberFormatException nfe) {
                Log.error(nfe.getMessage(), nfe);
            }
        }
        return -1;
    }

    /**
     * Returns the license agreement type that the plugin is governed by. The value
     * is retrieved from the plugin.xml file of the plugin. If the value could not be
     * found, {@link License#other} is returned.
     *
     * @param plugin the plugin.
     * @return the plugin's license agreement.
     */
    public License getLicense(Plugin plugin) {
        String licenseString = getElementValue(plugin, "/plugin/licenseType");
        if (licenseString != null) {
            try {
                // Attempt to load the get the license type. We lower-case and
                // trim the license type to give plugin author's a break. If the
                // license type is not recognized, we'll log the error and default
                // to "other".
                return License.valueOf(licenseString.toLowerCase().trim());
            }
            catch (IllegalArgumentException iae) {
                Log.error(iae.getMessage(), iae);
            }
        }
        return License.other;
    }

    /**
     * Returns the classloader of a plugin.
     *
     * @param plugin the plugin.
     * @return the classloader of the plugin.
     */
    public PluginClassLoader getPluginClassloader(Plugin plugin) {
        return classloaders.get(plugin);
    }

    /**
     * Returns the value of an element selected via an xpath expression from
     * a Plugin's plugin.xml file.
     *
     * @param plugin the plugin.
     * @param xpath  the xpath expression.
     * @return the value of the element selected by the xpath expression.
     */
    private String getElementValue(Plugin plugin, String xpath) {
        File pluginDir = pluginDirs.get(plugin);
        if (pluginDir == null) {
            return null;
        }
        try {
            File pluginConfig = new File(pluginDir, "plugin.xml");
            if (pluginConfig.exists()) {
                SAXReader saxReader = new SAXReader();
                saxReader.setEncoding("UTF-8");
                Document pluginXML = saxReader.read(pluginConfig);
                Element element = (Element)pluginXML.selectSingleNode(xpath);
                if (element != null) {
                    return element.getTextTrim();
                }
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * An enumberation for plugin license agreement types.
     */
    @SuppressWarnings({"UnnecessarySemicolon"})  // Support for QDox Parser
    public enum License {

        /**
         * The plugin is distributed using a commercial license.
         */
        commercial,

        /**
         * The plugin is distributed using the GNU Public License (GPL).
         */
        gpl,

        /**
         * The plugin is distributed using the Apache license.
         */
        apache,

        /**
         * The plugin is for internal use at an organization only and is not re-distributed.
         */
        internal,

        /**
         * The plugin is distributed under another license agreement not covered by
         * one of the other choices. The license agreement should be detailed in the
         * plugin Readme.
         */
        other;
    }

    /**
     * A service that monitors the plugin directory for plugins. It periodically
     * checks for new plugin JAR files and extracts them if they haven't already
     * been extracted. Then, any new plugin directories are loaded.
     */
    private class PluginMonitor implements Runnable {

        /**
         * Tracks if the monitor is currently running.
         */
        private boolean running = false;

        /**
         * True if the monitor has been executed at least once. After the first iteration in {@link #run}
         * this variable will always be true.
         * */
        private boolean executed = false;

        /**
         * True when it's the first time the plugin monitor process runs. This is helpful for
         * bootstrapping purposes.
         */
        private boolean firstRun = true;

        public void run() {
            // If the task is already running, return.
            synchronized (this) {
                if (running) {
                    return;
                }
                running = true;
            }
            try {
                running = true;
                // Look for extra plugin directories specified as a system property.
                String pluginDirs = System.getProperty("pluginDirs");
                if (pluginDirs != null) {
                    StringTokenizer st = new StringTokenizer(pluginDirs, ", ");
                    while (st.hasMoreTokens()) {
                        String dir = st.nextToken();
                        if (!devPlugins.contains(dir)) {
                            loadPlugin(new File(dir));
                            devPlugins.add(dir);
                        }
                    }
                }

                File[] jars = pluginDirectory.listFiles(new FileFilter() {
                    public boolean accept(File pathname) {
                        String fileName = pathname.getName().toLowerCase();
                        return (fileName.endsWith(".jar") || fileName.endsWith(".war"));
                    }
                });

                if (jars == null) {
                    return;
                }

                for (File jarFile : jars) {
                    String pluginName = jarFile.getName().substring(0,
                        jarFile.getName().length() - 4).toLowerCase();
                    // See if the JAR has already been exploded.
                    File dir = new File(pluginDirectory, pluginName);
                    // Store the JAR/WAR file that created the plugin folder
                    pluginFiles.put(pluginName, jarFile);
                    // If the JAR hasn't been exploded, do so.
                    if (!dir.exists()) {
                        unzipPlugin(pluginName, jarFile, dir);
                    }
                    // See if the JAR is newer than the directory. If so, the plugin
                    // needs to be unloaded and then reloaded.
                    else if (jarFile.lastModified() > dir.lastModified()) {
                        // If this is the first time that the monitor process is running, then
                        // plugins won't be loaded yet. Therefore, just delete the directory.
                        if (firstRun) {
                            int count = 0;
                            // Attempt to delete the folder for up to 5 seconds.
                            while (!deleteDir(dir) && count < 5) {
                                Thread.sleep(1000);
                            }
                        }
                        else {
                            unloadPlugin(pluginName);
                        }
                        // If the delete operation was a success, unzip the plugin.
                        if (!dir.exists()) {
                            unzipPlugin(pluginName, jarFile, dir);
                        }
                    }
                }

                File[] dirs = pluginDirectory.listFiles(new FileFilter() {
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
                        else {
                            return file1.compareTo(file2);
                        }
                    }
                });

                // Turn the list of JAR/WAR files into a set so that we can do lookups.
                Set<String> jarSet = new HashSet<String>();
                for (File file : jars) {
                    jarSet.add(file.getName().toLowerCase());
                }

                // See if any currently running plugins need to be unloaded
                // due to the JAR file being deleted (ignore admin plugin).
                // Build a list of plugins to delete first so that the plugins
                // keyset isn't modified as we're iterating through it.
                List<String> toDelete = new ArrayList<String>();
                for (File pluginDir : dirs) {
                    String pluginName = pluginDir.getName();
                    if (pluginName.equals("admin")) {
                        continue;
                    }
                    if (!jarSet.contains(pluginName + ".jar")) {
                        if (!jarSet.contains(pluginName + ".war")) {
                            toDelete.add(pluginName);
                        }
                    }
                }
                for (String pluginName : toDelete) {
                    unloadPlugin(pluginName);
                }

                // Load all plugins that need to be loaded.
                for (File dirFile : dirs) {
                    // If the plugin hasn't already been started, start it.
                    if (dirFile.exists() && !plugins.containsKey(dirFile.getName())) {
                        loadPlugin(dirFile);
                    }
                }
                // Set that at least one iteration was done. That means that "all available" plugins
                // have been loaded by now.
                if (!XMPPServer.getInstance().isSetupMode()) {
                    executed = true;
                }

                // Trigger event that plugins have been monitored
                firePluginsMonitored();
            }
            catch (Throwable e) {
                Log.error(e.getMessage(), e);
            }
            // Finished running task.
            synchronized (this) {
                running = false;
            }
            // Process finished, so set firstRun to false (setting it multiple times doesn't hurt).
            firstRun = false;
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
                // Set the date of the JAR file to the newly created folder
                dir.setLastModified(file.lastModified());
                Log.debug("PluginManager: Extracting plugin: " + pluginName);
                for (Enumeration e = zipFile.entries(); e.hasMoreElements();) {
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
                        byte[] b = new byte[512];
                        int len;
                        while ((len = zin.read(b)) != -1) {
                            out.write(b, 0, len);
                        }
                        out.flush();
                        out.close();
                        zin.close();
                    }
                }
                zipFile.close();

            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Deletes a directory.
     *
     * @param dir the directory to delete.
     * @return true if the directory was deleted.
     */
    private boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] childDirs = dir.list();
            // Always try to delete JAR files first since that's what will
            // be under contention. We do this by always sorting the lib directory
            // first.
            List<String> children = new ArrayList<String>(Arrays.asList(childDirs));
            Collections.sort(children, new Comparator<String>() {
                public int compare(String o1, String o2) {
                    if (o1.equals("lib")) {
                        return -1;
                    }
                    if (o2.equals("lib")) {
                        return 1;
                    }
                    else {
                        return o1.compareTo(o2);
                    }
                }
            });
            for (String file : children) {
                boolean success = deleteDir(new File(dir, file));
                if (!success) {
                    Log.debug("PluginManager: Plugin removal: could not delete: " + new File(dir, file));
                    return false;
                }
            }
        }
        boolean deleted = !dir.exists() || dir.delete();
        if (deleted) {
            // Remove the JAR/WAR file that created the plugin folder
            pluginFiles.remove(dir.getName());
        }
        return deleted;
    }

    public void addPluginListener(PluginListener listener) {
        pluginListeners.add(listener);
    }

    public void removePluginListener(PluginListener listener) {
        pluginListeners.remove(listener);
    }

    public void addPluginManagerListener(PluginManagerListener listener) {
        pluginManagerListeners.add(listener);
        if (isExecuted()) {
            firePluginsMonitored();
        }
    }

    public void removePluginManagerListener(PluginManagerListener listener) {
        pluginManagerListeners.remove(listener);
    }
}