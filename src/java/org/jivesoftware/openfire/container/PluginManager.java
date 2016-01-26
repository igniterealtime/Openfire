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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

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

    private Path pluginDirectory;
    private Map<String, Plugin> plugins;
    private Map<Plugin, PluginClassLoader> classloaders;
    private Map<Plugin, Path> pluginDirs;
    /**
     * Keep track of plugin names and their unzipped files. This list is updated when plugin
     * is exploded and not when is loaded.
     */
    private Map<String, Path> pluginFiles;
    private ScheduledExecutorService executor = null;
    private Map<Plugin, PluginDevEnvironment> pluginDevelopment;
    private Map<Plugin, List<String>> parentPluginMap;
    private Map<Plugin, String> childPluginMap;
    private Set<String> devPlugins;
    private PluginMonitor pluginMonitor;
    private Set<PluginListener> pluginListeners = new CopyOnWriteArraySet<>();
    private Set<PluginManagerListener> pluginManagerListeners = new CopyOnWriteArraySet<>();

    /**
     * Constructs a new plugin manager.
     *
     * @param pluginDir the plugin directory.
     */
    public PluginManager(File pluginDir) {
        this.pluginDirectory = pluginDir.toPath();
        plugins = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        pluginDirs = new HashMap<>();
        pluginFiles = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );
        classloaders = new HashMap<>();
        pluginDevelopment = new HashMap<>();
        parentPluginMap = new HashMap<>();
        childPluginMap = new HashMap<>();
        devPlugins = new TreeSet<>( String.CASE_INSENSITIVE_ORDER );
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
            // If pluginFilename is a path instead of a simple file name, we only want the file name
            int index = pluginFilename.lastIndexOf(File.separator);
            if (index != -1) {
                pluginFilename = pluginFilename.substring(index+1);
            }
            // Absolute path to the plugin file
            Path absolutePath = pluginDirectory.resolve(pluginFilename);
            Path partFile = pluginDirectory.resolve(pluginFilename + ".part");
            // Save input stream contents to a temp file
            Files.copy(in, partFile, StandardCopyOption.REPLACE_EXISTING);

            // Rename temp file to .jar
            Files.move(partFile, absolutePath, StandardCopyOption.REPLACE_EXISTING);
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
        return Files.exists(pluginDirectory.resolve(pluginFilename));
    }

    /**
     * Returns a Collection of all installed plugins.
     *
     * @return a Collection of all installed plugins.
     */
    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableCollection(Arrays.asList( plugins.values().toArray( new Plugin[ plugins.size() ]) ));
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
        return pluginDirs.get(plugin).toFile();
    }

    /**
     * Returns the JAR or WAR file that created the plugin.
     *
     * @param name the name of the plugin.
     * @return the plugin JAR or WAR file.
     */
    public File getPluginFile(String name) {
        return pluginFiles.get(name).toFile();
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
    private void loadPlugin(Path pluginDir) {
        // Only load the admin plugin during setup mode.
        if (XMPPServer.getInstance().isSetupMode() && !(pluginDir.getFileName().toString().equals("admin"))) {
            return;
        }
        Log.debug("PluginManager: Loading plugin " + pluginDir.getFileName().toString());
        Plugin plugin;
        try {
            Path pluginConfig = pluginDir.resolve("plugin.xml");
            if (Files.exists(pluginConfig)) {
                SAXReader saxReader = new SAXReader();
                saxReader.setEncoding("UTF-8");
                Document pluginXML = saxReader.read(pluginConfig.toFile());

                // See if the plugin specifies a version of Openfire
                // required to run.
                Element minServerVersion = (Element)pluginXML.selectSingleNode("/plugin/minServerVersion");
                if (minServerVersion != null) {
                    Version requiredVersion = new Version(minServerVersion.getTextTrim());
                    Version currentVersion = XMPPServer.getInstance().getServerInfo().getVersion();
                    if (requiredVersion.isNewerThan(currentVersion)) {
                        String msg = "Ignoring plugin " + pluginDir.getFileName() + ": requires " +
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

                String pluginName = pluginDir.getFileName().toString();
                String webRootKey = pluginName + ".webRoot";
                String classesDirKey = pluginName + ".classes";
                String webRoot = System.getProperty(webRootKey);
                String classesDir = System.getProperty(classesDirKey);

                if (webRoot != null) {
                    final Path compilationClassesDir = pluginDir.resolve("classes");
                    if (Files.notExists(compilationClassesDir)) {
                        Files.createDirectory(compilationClassesDir);
                    }
                    compilationClassesDir.toFile().deleteOnExit();
                }

                if (parentPluginNode != null) {
                    String parentPlugin = parentPluginNode.getTextTrim();
                    // See if the parent is already loaded.
                    if (plugins.containsKey(parentPlugin)) {
                        pluginLoader = classloaders.get(getPlugin(parentPlugin));
                        pluginLoader.addDirectory(pluginDir.toFile(), classesDir != null);

                    }
                    else {
                        // See if the parent plugin exists but just hasn't been loaded yet.
                        final Set<String> plugins = new TreeSet<>( String.CASE_INSENSITIVE_ORDER );
                        plugins.addAll( Arrays.asList( pluginDir.getParent().toFile().list() ) );
                        if ( plugins.contains( parentPlugin + ".jar" ) || plugins.contains( parentPlugin + ".war" ) ) {
                            // Silently return. The child plugin will get loaded up on the next
                            // plugin load run after the parent.
                            return;
                        } else {
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
                    pluginLoader.addDirectory(pluginDir.toFile(), classesDir != null);
                }

                // Check to see if development mode is turned on for the plugin. If it is,
                // configure dev mode.

                PluginDevEnvironment dev = null;
                if (webRoot != null || classesDir != null) {
                    dev = new PluginDevEnvironment();

                    System.out.println("Plugin " + pluginName + " is running in development mode.");
                    Log.info("Plugin " + pluginName + " is running in development mode.");
                    if (webRoot != null) {
                        Path webRootDir = Paths.get(webRoot);
                        if (Files.notExists(webRootDir)) {
                            // Ok, let's try it relative from this plugin dir?
                            webRootDir = pluginDir.resolve(webRoot);
                        }

                        if (Files.exists(webRootDir)) {
                            dev.setWebRoot(webRootDir.toFile());
                        }
                    }

                    if (classesDir != null) {
                        Path classes = Paths.get(classesDir);
                        if (Files.notExists(classes)) {
                            // ok, let's try it relative from this plugin dir?
                            classes = pluginDir.resolve(classesDir);
                        }

                        if (Files.exists(classes)) {
                            dev.setClassesDir(classes.toFile());
                            pluginLoader.addURLFile(classes.toUri().toURL());
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
                    // The name of the parent plugin as specified in plugin.xml might have incorrect casing. Lookup the correct name.
                    for (Map.Entry<String, Plugin> entry : plugins.entrySet() ) {
                        if ( entry.getKey().equalsIgnoreCase( parentPlugin ) ) {
                            parentPlugin = entry.getKey();
                            break;
                        }
                    }
                    List<String> childrenPlugins = parentPluginMap.get(plugins.get(parentPlugin));
                    if (childrenPlugins == null) {
                        childrenPlugins = new ArrayList<>();
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
                Path webXML = pluginDir.resolve("web").resolve("WEB-INF").resolve("web.xml");
                if (Files.exists(webXML)) {
                    PluginServlet.registerServlets(this, plugin, webXML.toFile());
                }
                // Load any custom-defined servlets.
                Path customWebXML = pluginDir.resolve("web").resolve("WEB-INF").resolve("web-custom.xml");
                if (Files.exists(customWebXML)) {
                    PluginServlet.registerServlets(this, plugin, customWebXML.toFile());
                }

                if (dev != null) {
                    pluginDevelopment.put(plugin, dev);
                }

                // Configure caches of the plugin
                configureCaches(pluginDir, pluginName);

                // Init the plugin.
                ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(pluginLoader);
                plugin.initializePlugin(this, pluginDir.toFile());
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

    private void configureCaches(Path pluginDir, String pluginName) {
        Path cacheConfig = pluginDir.resolve("cache-config.xml");
        if (Files.exists(cacheConfig)) {
            PluginCacheConfigurator configurator = new PluginCacheConfigurator();
            try {
                configurator.setInputStream(new BufferedInputStream(Files.newInputStream(cacheConfig)));
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

            Path webXML = pluginDirectory.resolve(pluginName).resolve("web").resolve("WEB-INF").resolve("web.xml");
            if (Files.exists(webXML)) {
                AdminConsole.removeModel(pluginName);
                PluginServlet.unregisterServlets(webXML.toFile());
            }
            Path customWebXML = pluginDirectory.resolve(pluginName).resolve("web").resolve("WEB-INF").resolve("web-custom.xml");
            if (Files.exists(customWebXML)) {
                PluginServlet.unregisterServlets(customWebXML.toFile());
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
        Path pluginFile = pluginDirs.remove(plugin);
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
        Path dir = pluginDirectory.resolve(pluginName);
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

        if (plugin != null && Files.notExists(dir)) {
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
        String pluginName = pluginDirs.get(plugin).getFileName().toString();
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
        String pluginName = pluginDirs.get(plugin).getFileName().toString();
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
        Path pluginDir = pluginDirs.get(plugin);
        if (pluginDir == null) {
            return null;
        }
        try {
            Path pluginConfig = pluginDir.resolve("plugin.xml");
            if (Files.exists(pluginConfig)) {
                SAXReader saxReader = new SAXReader();
                saxReader.setEncoding("UTF-8");
                Document pluginXML = saxReader.read(pluginConfig.toFile());
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

        @Override
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
                            loadPlugin(Paths.get(dir));
                            devPlugins.add(dir);
                        }
                    }
                }

                // Turn the list of JAR/WAR files into a set so that we can do lookups.
                Set<String> jarSet = new HashSet<String>();

                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pluginDirectory, new DirectoryStream.Filter<Path>() {
                    @Override
                    public boolean accept(Path pathname) throws IOException {
                        String fileName = pathname.getFileName().toString().toLowerCase();
                        return (fileName.endsWith(".jar") || fileName.endsWith(".war"));
                    }
                })) {
                    for (Path jarFile : directoryStream) {
                        jarSet.add(jarFile.getFileName().toString().toLowerCase());
                        String pluginName = jarFile.getFileName().toString().substring(0,
                                jarFile.getFileName().toString().length() - 4).toLowerCase();
                        // See if the JAR has already been exploded.
                        Path dir = pluginDirectory.resolve(pluginName);
                        // Store the JAR/WAR file that created the plugin folder
                        pluginFiles.put(pluginName, jarFile);
                        // If the JAR hasn't been exploded, do so.
                        if (Files.notExists(dir)) {
                            unzipPlugin(pluginName, jarFile, dir);
                        }
                        // See if the JAR is newer than the directory. If so, the plugin
                        // needs to be unloaded and then reloaded.
                        else if (Files.getLastModifiedTime(jarFile).toMillis() > Files.getLastModifiedTime(dir).toMillis()) {
                            // If this is the first time that the monitor process is running, then
                            // plugins won't be loaded yet. Therefore, just delete the directory.
                            if (firstRun) {
                                int count = 0;
                                // Attempt to delete the folder for up to 5 seconds.
                                while (!deleteDir(dir) && count++ < 5) {
                                    Thread.sleep(1000);
                                }
                            }
                            else {
                                unloadPlugin(pluginName);
                            }
                            // If the delete operation was a success, unzip the plugin.
                            if (Files.notExists(dir)) {
                                unzipPlugin(pluginName, jarFile, dir);
                            }
                        }
                    }
                }

                List<Path> dirs = new ArrayList<>();

                try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(pluginDirectory, new DirectoryStream.Filter<Path>() {
                    @Override
                    public boolean accept(Path pathname) throws IOException {
                        return Files.isDirectory(pathname);
                    }
                })) {
                    for (Path path : directoryStream) {
                        dirs.add(path);
                    }
                }

                // Sort the list of directories so that the "admin" plugin is always
                // first in the list.
                Collections.sort(dirs, new Comparator<Path>() {
                    public int compare(Path file1, Path file2) {
                        if (file1.getFileName().toString().equals("admin")) {
                            return -1;
                        } else if (file2.getFileName().toString().equals("admin")) {
                            return 1;
                        } else {
                            return file1.compareTo(file2);
                        }
                    }
                });

                // See if any currently running plugins need to be unloaded
                // due to the JAR file being deleted (ignore admin plugin).
                // Build a list of plugins to delete first so that the plugins
                // keyset isn't modified as we're iterating through it.
                List<String> toDelete = new ArrayList<String>();
                for (Path pluginDir : dirs) {
                    String pluginName = pluginDir.getFileName().toString();
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
                for (Path dirFile : dirs) {
                    // If the plugin hasn't already been started, start it.
                    if (Files.exists(dirFile) && !plugins.containsKey(dirFile.getFileName().toString())) {
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
        private void unzipPlugin(String pluginName, Path file, Path dir) {
            try (ZipFile zipFile = new JarFile(file.toFile())) {
                // Ensure that this JAR is a plugin.
                if (zipFile.getEntry("plugin.xml") == null) {
                    return;
                }
                Files.createDirectory(dir);
                // Set the date of the JAR file to the newly created folder
                Files.setLastModifiedTime(dir, Files.getLastModifiedTime(file));
                Log.debug("PluginManager: Extracting plugin: " + pluginName);
                for (Enumeration e = zipFile.entries(); e.hasMoreElements();) {
                    JarEntry entry = (JarEntry)e.nextElement();
                    Path entryFile = dir.resolve(entry.getName());
                    // Ignore any manifest.mf entries.
                    if (entry.getName().toLowerCase().endsWith("manifest.mf")) {
                        continue;
                    }
                    if (!entry.isDirectory()) {
                        Files.createDirectories(entryFile.getParent());
                        try (InputStream zin = zipFile.getInputStream(entry)) {
                            Files.copy(zin, entryFile, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
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
    private boolean deleteDir(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException e) {
                            Log.debug("PluginManager: Plugin removal: could not delete: " + file);
                            throw e;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        try {
                            Files.deleteIfExists(dir);
                        } catch (IOException e) {
                            Log.debug("PluginManager: Plugin removal: could not delete: " + dir);
                            throw e;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            boolean deleted = Files.notExists(dir) || Files.deleteIfExists(dir);
            if (deleted) {
                // Remove the JAR/WAR file that created the plugin folder
                pluginFiles.remove(dir.getFileName().toString());
            }
            return deleted;
        } catch (IOException e) {
            return Files.notExists(dir);
        }
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