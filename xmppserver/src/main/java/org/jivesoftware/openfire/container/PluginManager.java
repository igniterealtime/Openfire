/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.spi.LoggerContext;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JavaSpecVersion;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import javax.annotation.concurrent.GuardedBy;

/**
 * Manages plugins.
 *
 * The {@code plugins} directory is monitored for any new plugins, and they are dynamically loaded.
 *
 * An instance of this class can be obtained using: {@code XMPPServer.getInstance().getPluginManager()}
 *
 * These states are defined for plugin management:
 * <ul>
 *     <li><em>installed</em> - the plugin archive file is present in the {@code plugins} directory.</li>
 *     <li><em>extracted</em> - the plugin archive file has been extracted.</li>
 *     <li><em>loaded</em> - the plugin has (successfully) been initialized.</li>
 * </ul>
 *
 * Note that an <em>installed</em> plugin is not per definition an <em>extracted</em> plugin, and an extracted
 * plugin is not per definition a <em>loaded</em> plugin.  A plugin that's extracted might, for instance, fail to
 * load, due to restrictions imposed by its {@code minServerVersion} definition.
 *
 * @author Matt Tucker
 * @see Plugin
 * @see org.jivesoftware.openfire.XMPPServer#getPluginManager()
 */
public class PluginManager
{
    private static final Logger Log = LoggerFactory.getLogger( PluginManager.class );

    private final Path pluginDirectory;

    /**
     * Plugins that are loaded, mapped by their canonical name.
     */
    @GuardedBy("this")
    private final Map<String, Plugin> pluginsLoaded = new TreeMap<>( String.CASE_INSENSITIVE_ORDER );

    /**
     * The plugin classloader for each loaded plugin.
     */
    @GuardedBy("this")
    private final Map<Plugin, PluginClassLoader> classloaders = new HashMap<>();

    /**
     * The directory in which a plugin is extracted, mapped by canonical name. This collection contains loaded plugins,
     * as well as extracted (but not loaded) plugins.
     *
     * Note that typically these directories are subdirectories of {@code plugins}, but a 'dev-plugin' could live
     * elsewhere.
     */
    @GuardedBy("this")
    private final Map<String, Path> pluginDirs = new HashMap<>();

    /**
     * Plugin metadata for all extracted plugins, mapped by canonical name.
     */
    @GuardedBy("this")
    private final Map<String, PluginMetadata> pluginMetadata = Collections.synchronizedMap(new TreeMap<>(String.CASE_INSENSITIVE_ORDER));

    @GuardedBy("this")
    private final Map<Plugin, PluginDevEnvironment> pluginDevelopment = new HashMap<>();

    @GuardedBy("this")
    private final Map<Plugin, List<String>> parentPluginMap = new HashMap<>();

    @GuardedBy("this")
    private final Map<Plugin, String> childPluginMap = new HashMap<>();

    // CopyOnWriteArraySet is thread safe
    private final Set<PluginListener> pluginListeners = new CopyOnWriteArraySet<>();

    // CopyOnWriteArraySet is thread safe
    private final Set<PluginManagerListener> pluginManagerListeners = new CopyOnWriteArraySet<>();

    @GuardedBy("this")
    private final Map<String, Integer> failureToLoadCount = new HashMap<>();

    private final PluginMonitor pluginMonitor;
    private boolean executed = false;

    /**
     * Constructs a new plugin manager.
     *
     * @param pluginDir the directory containing all Openfire plugins, typically OPENFIRE_HOME/plugins/
     */
    public PluginManager( File pluginDir )
    {
        this.pluginDirectory = pluginDir.toPath();
        pluginMonitor = new PluginMonitor( this );
    }

    /**
     * Starts plugins and the plugin monitoring service.
     */
    public synchronized void start()
    {
        pluginMonitor.start();
    }

    /**
     * Shuts down all running plugins.
     */
    public synchronized void shutdown()
    {
        Log.info( "Shutting down. Unloading all loaded plugins..." );

        // Stop the plugin monitoring service.
        pluginMonitor.stop();

        // Shutdown all loaded plugins.
        for ( Map.Entry<String, Plugin> plugin : pluginsLoaded.entrySet() )
        {
            try
            {
                plugin.getValue().destroyPlugin();
                Log.info( "Unloaded plugin '{}'.", plugin.getKey() );
            }
            catch ( Exception e )
            {
                Log.error( "An exception occurred while trying to unload plugin '{}':", plugin.getKey(), e );
            }
        }
        pluginsLoaded.clear();
        pluginDirs.clear();
        pluginMetadata.clear();
        classloaders.clear();
        pluginDevelopment.clear();
        childPluginMap.clear();
        failureToLoadCount.clear();
    }

    /**
     * Returns the directory that contains all plugins. This typically is OPENFIRE_HOME/plugins.
     *
     * @return The directory that contains all plugins.
     */
    public Path getPluginsDirectory()
    {
        return pluginDirectory;
    }

    /**
     * Installs or updates an existing plugin.
     *
     * @param in the input stream that contains the new plugin definition.
     * @param pluginFilename the filename of the plugin to create or update.
     * @return true if the plugin was successfully installed or updated.
     */
    public boolean installPlugin( InputStream in, String pluginFilename )
    {
        if ( pluginFilename == null || pluginFilename.isEmpty() )
        {
            Log.error( "Error installing plugin: pluginFilename was null or empty." );
            return false;
        }
        if ( in == null )
        {
            Log.error( "Error installing plugin '{}': Input stream was null.", pluginFilename );
            return false;
        }

        try ( final BufferedInputStream bin = new BufferedInputStream( in ) )
        {
            // Check magic bytes to ensure this is a JAR file.
            final boolean magicNumberCheckEnabled = JiveGlobals.getBooleanProperty("plugins.upload.magic-number-check.enabled", true);
            if ( magicNumberCheckEnabled && ! validMagicNumbers( bin ) )
            {
                Log.error( "Error installing plugin '{}': This does not appear to be a JAR file (unable to find a magic byte match).", pluginFilename );
                return false;
            }

            // If pluginFilename is a path instead of a simple file name, we only want the file name
            pluginFilename = Paths.get(pluginFilename).getFileName().toString();
            // Absolute path to the plugin file
            Path absolutePath = pluginDirectory.resolve( pluginFilename );
            Path partFile = pluginDirectory.resolve( pluginFilename + ".part" );
            // Save input stream contents to a temp file
            Files.copy( bin, partFile, StandardCopyOption.REPLACE_EXISTING );

            // Check if zip file, else ZipException caught below.
            try (JarFile pluginJar = new JarFile(partFile.toFile())) {
                final boolean pluginXMLCheckEnabled = JiveGlobals.getBooleanProperty("plugins.upload.pluginxml-check.enabled", true);
                // Check if the zip file contains a plugin.xml file.
                if ( pluginXMLCheckEnabled && pluginJar.getEntry( "plugin.xml" ) == null ) {
                    Log.error( "Error installing plugin '{}': Unable to find 'plugin.xml' in archive.", pluginFilename );
                    Files.deleteIfExists( partFile );
                    return false;
                }
            } catch (ZipException e) {
                Log.error( "Error installing plugin '{}': Cannot parse file into a JAR format.", pluginFilename, e);
                Files.deleteIfExists(partFile);
                return false;
            }

            // Rename temp file to .jar
            Files.move( partFile, absolutePath, StandardCopyOption.REPLACE_EXISTING );
            // Ask the plugin monitor to update the plugin immediately.
            pluginMonitor.runNow( true );
        }
        catch ( IOException e )
        {
            Log.error( "An exception occurred while installing new version of plugin '{}':", pluginFilename, e );
            return false;
        }
        return true;
    }

    /**
     * Returns true if the plugin by the specified name is installed. Specifically, this checks if the plugin
     * archive file is present in the {@code plugins} directory.
     *
     * Note that an <em>installed</em> plugin is not per definition an <em>extracted</em> plugin, and an extracted
     * plugin is not per definition a <em>loaded</em> plugin.  A plugin that's extracted might, for instance, fail to
     * load, due to restrictions imposed by its {@code minServerVersion} definition.
     *
     * @param canonicalName the canonical filename of the plugin (cannot be null).
     * @return true if the plugin is installed, otherwise false.
     */
    public boolean isInstalled( final String canonicalName )
    {
        final DirectoryStream.Filter<Path> filter = new DirectoryStream.Filter<Path>()
        {
            @Override
            public boolean accept( Path entry ) {
                final String name = entry.getFileName().toString();
                return Files.exists( entry ) && !Files.isDirectory( entry ) &&
                        ( name.equalsIgnoreCase( canonicalName + ".jar" ) || name.equalsIgnoreCase( canonicalName + ".war" ) );
            }
        };

        try ( final DirectoryStream<Path> paths = Files.newDirectoryStream( pluginDirectory, filter ) )
        {
            return paths.iterator().hasNext();
        }
        catch ( IOException e )
        {
            Log.error( "Unable to determine if plugin '{}' is installed.", canonicalName, e );

            // return the next best guess
            synchronized ( this )
            {
                return pluginsLoaded.containsKey(canonicalName);
            }
        }
    }

    /**
     * Returns true if the plugin by the specified name is extracted. Specifically, this checks if the {@code plugins}
     * directory contains a subdirectory that matches the canonical name of the plugin.
     *
     * Note that an <em>installed</em> plugin is not per definition an <em>extracted</em> plugin, and an extracted
     * plugin is not per definition a <em>loaded</em> plugin.  A plugin that's extracted might, for instance, fail to
     * load, due to restrictions imposed by its {@code minServerVersion} definition.
     *
     * @param canonicalName the canonical filename of the plugin (cannot be null).
     * @return true if the plugin is extracted, otherwise false.
     */
    public synchronized boolean isExtracted( final String canonicalName )
    {
        return pluginMetadata.containsKey( canonicalName );
    }

    /**
     * Returns true if the plugin by the specified name is loaded. Specifically, this checks if an instance was created
     * for the plugin class file.
     *
     * Note that an <em>installed</em> plugin is not per definition an <em>extracted</em> plugin, and an extracted
     * plugin is not per definition a <em>loaded</em> plugin.  A plugin that's extracted might, for instance, fail to
     * load, due to restrictions imposed by its {@code minServerVersion} definition.
     *
     * @param canonicalName the canonical filename of the plugin (cannot be null).
     * @return true if the plugin is extracted, otherwise false.
     */
    public synchronized boolean isLoaded( final String canonicalName )
    {
        return pluginsLoaded.containsKey( canonicalName );
    }

    /**
     * Returns metadata for all extracted plugins, mapped by their canonical name.
     *
     * The collection is alphabetically sorted, by plugin name.
     *
     * Note that an <em>installed</em> plugin is not per definition an <em>extracted</em> plugin, and an extracted
     * plugin is not per definition a <em>loaded</em> plugin.  A plugin that's extracted might, for instance, fail to
     * load, due to restrictions imposed by its {@code minServerVersion} definition.
     *
     * @return A collection of metadata (possibly empty, never null).
     */
    public synchronized Map<String, PluginMetadata> getMetadataExtractedPlugins()
    {
        return Collections.unmodifiableMap(new TreeMap<>(this.pluginMetadata));
    }

    /**
     * Returns metadata for an extracted plugin, or null when the plugin is extracted nor loaded.
     *
     * Note that an <em>installed</em> plugin is not per definition an <em>extracted</em> plugin, and an extracted
     * plugin is not per definition a <em>loaded</em> plugin.  A plugin that's extracted might, for instance, fail to
     * load, due to restrictions imposed by its {@code minServerVersion} definition.
     *
     * @param canonicalName the canonical name (lower case JAR/WAR file without exception) of the plugin
     * @return A collection of metadata (possibly empty, never null).
     */
    public synchronized PluginMetadata getMetadata( String canonicalName )
    {
        return this.pluginMetadata.get( canonicalName );
    }

    /**
     * Returns a Collection of all loaded plugins.
     *
     * The returned collection will not include plugins that have been downloaded, but not loaded.
     *
     * @return a Collection of all loaded plugins.
     */
    public Collection<Plugin> getPlugins()
    {
        final List<Plugin> plugins;
        synchronized ( this )
        {
            plugins = Arrays.asList(pluginsLoaded.values().toArray(new Plugin[0]));
        }
        return Collections.unmodifiableCollection( plugins );
    }

    /**
     * Returns the canonical name for a loaded plugin.
     *
     * @param plugin A plugin (cannot be null).
     * @return The canonical name for the plugin (never null).
     */
    public synchronized String getCanonicalName( Plugin plugin )
    {
        for ( Map.Entry<String, Plugin> entry : pluginsLoaded.entrySet() )
        {
            if ( entry.getValue().equals( plugin ) )
            {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns a loaded plugin by its canonical name or {@code null} if a plugin with that name does not exist. The
     * canonical name is the lowercase-name of the plugin archive, without the file extension. For example: "broadcast".
     *
     * @deprecated in Openfire 4.4 in favour of {@link #getPluginByName(String)}
     * @param canonicalName the name of the plugin.
     * @return the plugin.
     */
    // TODO: (2019-03-26) Remove with Openfire 5.0
    @Deprecated
    public synchronized Plugin getPlugin( String canonicalName )
    {
        return pluginsLoaded.get( canonicalName.toLowerCase() );
    }

    /**
     * Returns a loaded plugin by the name contained in the plugin.xml &lt;name/&gt; tag, ignoring case.
     * For example: "broadcast".
     *
     * @param pluginName the name of the plugin.
     * @return the plugin, if found
     * @since Openfire 4.4
     */
    public synchronized Optional<Plugin> getPluginByName(final String pluginName) {
        return pluginMetadata.values().stream()
            // Find the matching metadata
            .filter(pluginMetadata -> pluginName.equalsIgnoreCase(pluginMetadata.getName()))
            .findAny()
            // Find the canonical name for this plugin
            .map(PluginMetadata::getCanonicalName)
            // Finally, find the plugin
            .flatMap(canonicalName -> Optional.ofNullable(pluginsLoaded.get(canonicalName)));
    }

    /**
     * @deprecated Use #getPluginPath() instead.
     * @param plugin the plugin to get the directory for
     * @return the plugin's directory
     */
    @Deprecated
    public File getPluginDirectory( Plugin plugin )
    {
        return getPluginPath( plugin ).toFile();
    }

    /**
     * Returns the plugin's directory.
     *
     * @param plugin the plugin.
     * @return the plugin's directory.
     * @since Openfire 4.1
     */
    public synchronized Path getPluginPath( Plugin plugin )
    {
        final String canonicalName = getCanonicalName( plugin );
        if ( canonicalName != null )
        {
            return pluginDirs.get( canonicalName );
        }
        return null;
    }

    /**
     * Returns true if at least one attempt to load plugins has been done. A true value does not mean
     * that available plugins have been loaded nor that plugins to be added in the future are already
     * loaded. :)<p>
     *
     * @return true if at least one attempt to load plugins has been done.
     */
    public boolean isExecuted()
    {
        return executed;
    }

    /**
     * Loads a plugin.
     *
     * @param pluginDir the plugin directory.
     */
    synchronized boolean loadPlugin( String canonicalName, Path pluginDir )
    {
        final PluginMetadata metadata = PluginMetadata.getInstance( pluginDir );
        pluginMetadata.put( canonicalName, metadata );

        // Only load the admin plugin during setup mode.
        if ( XMPPServer.getInstance().isSetupMode() && !( canonicalName.equals( "admin" ) ) )
        {
            return false;
        }

        if ( failureToLoadCount.containsKey( canonicalName ) && failureToLoadCount.get( canonicalName ) > JiveGlobals.getIntProperty( "plugins.loading.retries", 5 ) )
        {
            Log.debug( "The unloaded file for plugin '{}' is silently ignored, as it has failed to load repeatedly.", canonicalName );
            return false;
        }

        Log.debug( "Loading plugin '{}'...", canonicalName );
        try
        {
            final Path pluginConfig = pluginDir.resolve( "plugin.xml" );
            if ( !Files.exists( pluginConfig ) )
            {
                Log.warn( "Plugin '{}' could not be loaded: no plugin.xml file found.", canonicalName );
                failureToLoadCount.put( canonicalName, Integer.MAX_VALUE ); // Don't retry - this cannot be recovered from.
                return false;
            }

            final Version currentServerVersion = XMPPServer.getInstance().getServerInfo().getVersion();

            // See if the plugin specifies a minimum version of Openfire required to run.
            if ( metadata.getMinServerVersion() != null )
            {
                // OF-1338: Ignore release status when comparing minimum server version requirement.
                if (metadata.getMinServerVersion().isNewerThan(currentServerVersion.ignoringReleaseStatus())) {
                    Log.warn( "Ignoring plugin '{}': requires server version {}. Current server version is {}.", canonicalName, metadata.getMinServerVersion(), currentServerVersion );
                    failureToLoadCount.put( canonicalName, Integer.MAX_VALUE ); // Don't retry - this cannot be recovered from.
                    return false;
                }
            }

            // See if the plugin specifies a maximum version of Openfire required to run.
            if ( metadata.getPriorToServerVersion() != null )
            {
                // OF-1338: Ignore release status when comparing maximum server version requirement.
                final Version compareVersion = new Version( currentServerVersion.getMajor(), currentServerVersion.getMinor(), currentServerVersion.getMicro(), null, -1 );
                if ( !metadata.getPriorToServerVersion().isNewerThan( compareVersion ) )
                {
                    Log.warn( "Ignoring plugin '{}': compatible with server versions up to but excluding {}. Current server version is {}.", canonicalName, metadata.getPriorToServerVersion(), currentServerVersion );
                    failureToLoadCount.put( canonicalName, Integer.MAX_VALUE ); // Don't retry - this cannot be recovered from.
                    return false;
                }
            }

            // See if the plugin specifies a minimum version of Java required to run.
            if ( metadata.getMinJavaVersion() != null )
            {
                final JavaSpecVersion runtimeVersion = new JavaSpecVersion( System.getProperty( "java.specification.version" ) );
                if ( metadata.getMinJavaVersion().isNewerThan( runtimeVersion ) )
                {
                    Log.warn( "Ignoring plugin '{}': requires Java specification version {}. Openfire is currently running in Java {}.", canonicalName, metadata.getMinJavaVersion(), System.getProperty( "java.specification.version" ) );
                    failureToLoadCount.put( canonicalName, Integer.MAX_VALUE ); // Don't retry - this cannot be recovered from.
                    return false;
                }
            }

            // Properties to be used to load external resources. When set, plugin is considered to run in DEV mode.
            final String devModeClassesDir = System.getProperty( canonicalName + ".classes" );
            final String devModewebRoot = System.getProperty( canonicalName + ".webRoot" );
            final boolean devMode = devModewebRoot != null || devModeClassesDir != null;
            final PluginDevEnvironment dev = ( devMode ? configurePluginDevEnvironment( pluginDir, devModeClassesDir, devModewebRoot ) : null );

            // Initialize the plugin class loader, which is either a new instance, or a the loader from a parent plugin.
            final PluginClassLoader pluginLoader;

            // Check to see if this is a child plugin of another plugin. If it is, we re-use the parent plugin's class
            // loader so that the plugins can interact.
            String parentPluginName = null;
            Plugin parentPlugin = null;

            final String parentCanonicalName = PluginMetadataHelper.getParentPlugin( pluginDir );
            if ( parentCanonicalName != null )
            {
                // The name of the parent plugin as specified in plugin.xml might have incorrect casing. Lookup the correct name.
                for ( final Map.Entry<String, Plugin> entry : pluginsLoaded.entrySet() )
                {
                    if ( entry.getKey().equalsIgnoreCase( parentCanonicalName ) )
                    {
                        parentPluginName = entry.getKey();
                        parentPlugin = entry.getValue();
                        break;
                    }
                }

                // See if the parent is loaded.
                if ( parentPlugin == null )
                {
                    Log.info( "Unable to load plugin '{}': parent plugin '{}' has not been loaded.", canonicalName, parentCanonicalName );
                    Integer count = failureToLoadCount.get( canonicalName );
                    if ( count == null ) {
                        count = 0;
                    }
                    failureToLoadCount.put( canonicalName, ++count );
                    return false;
                }
                pluginLoader = classloaders.get( parentPlugin );
            }
            else
            {
                // This is not a child plugin, so create a new class loader.
                pluginLoader = new PluginClassLoader();
            }

            // Add the plugin sources to the classloaded.
            pluginLoader.addDirectory( pluginDir.toFile(), devMode );

            // When running in DEV mode, add optional other sources too.
            if ( dev != null && dev.getClassesDir() != null )
            {
                pluginLoader.addURLFile( dev.getClassesDir().toURI().toURL() );
            }

            // Initialise a logging context, if necessary
            final Path path = pluginDir.resolve("classes/log4j2.xml");
            if (Files.isRegularFile(path)) {
                synchronized (PluginManager.class) {
                    final LoggerContext loggerContext = LogManager.getContext(pluginLoader, false, path.toUri());
                    loggerContext.getLogger("To avoid LOG4J2-1094");
                }
            }

            // Instantiate the plugin!
            final SAXReader saxReader = setupSAXReader();
            final Document pluginXML = saxReader.read( pluginConfig.toFile() );

            final String className = pluginXML.selectSingleNode( "/plugin/class" ).getText().trim();
            final Plugin plugin;
            final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(pluginLoader);
                plugin = (Plugin) pluginLoader.loadClass(className).newInstance();
            } finally {
                Thread.currentThread().setContextClassLoader(originalClassLoader);
            }
            // Bookkeeping!
            classloaders.put( plugin, pluginLoader );
            pluginsLoaded.put( canonicalName, plugin );
            pluginDirs.put( canonicalName, pluginDir );
            if ( dev != null )
            {
                pluginDevelopment.put( plugin, dev );
            }

            // If this is a child plugin, register it as such.
            if ( parentPlugin != null )
            {
                List<String> childrenPlugins = parentPluginMap.get( parentPlugin );
                if ( childrenPlugins == null )
                {
                    childrenPlugins = new ArrayList<>();
                    parentPluginMap.put( parentPlugin, childrenPlugins );
                }
                childrenPlugins.add( canonicalName );

                // Also register child to parent relationship.
                childPluginMap.put( plugin, parentPluginName );
            }

            // Check the plugin's database schema (if it requires one).
            if ( !DbConnectionManager.getSchemaManager().checkPluginSchema( plugin ) )
            {
                // The schema was not there and auto-upgrade failed.
                Log.error( "Error while loading plugin '{}': {}", canonicalName, LocaleUtils.getLocalizedString( "upgrade.database.failure" ) );
            }

            // Load any JSP's defined by the plugin.
            final Path webXML = pluginDir.resolve( "web" ).resolve( "WEB-INF" ).resolve( "web.xml" );
            if ( Files.exists( webXML ) )
            {
                PluginServlet.registerServlets( this, plugin, webXML.toFile() );
            }

            // Load any custom-defined servlets.
            final Path customWebXML = pluginDir.resolve( "web" ).resolve( "WEB-INF" ).resolve( "web-custom.xml" );
            if ( Files.exists( customWebXML ) )
            {
                PluginServlet.registerServlets( this, plugin, customWebXML.toFile() );
            }

            // Configure caches of the plugin
            configureCaches( pluginDir, canonicalName );

            // Initialze the plugin.
            final ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader( pluginLoader );
            plugin.initializePlugin( this, pluginDir.toFile() );
            Log.debug( "Initialized plugin '{}'.", canonicalName );
            Thread.currentThread().setContextClassLoader( oldLoader );

            // If there a <adminconsole> section defined, register it.
            final Element adminElement = (Element) pluginXML.selectSingleNode( "/plugin/adminconsole" );
            if ( adminElement != null )
            {
                final Element appName = (Element) adminElement.selectSingleNode( "/plugin/adminconsole/global/appname" );
                if ( appName != null )
                {
                    // Set the plugin name so that the proper i18n String can be loaded.
                    appName.addAttribute( "plugin", canonicalName );
                }

                // If global images are specified, override their URL.
                Element imageEl = (Element) adminElement.selectSingleNode( "/plugin/adminconsole/global/logo-image" );
                if ( imageEl != null )
                {
                    imageEl.setText( "plugins/" + canonicalName + "/" + imageEl.getText() );
                    imageEl.addAttribute( "plugin", canonicalName ); // Set the plugin name so that the proper i18n String can be loaded.
                }
                imageEl = (Element) adminElement.selectSingleNode( "/plugin/adminconsole/global/login-image" );
                if ( imageEl != null )
                {
                    imageEl.setText( "plugins/" + canonicalName + "/" + imageEl.getText() );
                    imageEl.addAttribute( "plugin", canonicalName ); // Set the plugin name so that the proper i18n String can be loaded.
                }

                // Modify all the URL's in the XML so that they are passed through the plugin servlet correctly.
                final List urls = adminElement.selectNodes( "//@url" );
                for ( final Object url : urls )
                {
                    final Attribute attr = (Attribute) url;
                    attr.setValue( "plugins/" + canonicalName + "/" + attr.getValue() );
                }

                // In order to internationalize the names and descriptions in the model, we add a "plugin" attribute to
                // each tab, sidebar, and item so that the the renderer knows where to load the i18n Strings from.
                final String[] elementNames = new String[]{ "tab", "sidebar", "item" };
                for ( final String elementName : elementNames )
                {
                    final List values = adminElement.selectNodes( "//" + elementName );
                    for ( final Object value : values )
                    {
                        final Element element = (Element) value;
                        // Make sure there's a name or description. Otherwise, no need to i18n settings.
                        if ( element.attribute( "name" ) != null || element.attribute( "value" ) != null )
                        {
                            element.addAttribute( "plugin", canonicalName );
                        }
                    }
                }

                AdminConsole.addModel( canonicalName, adminElement );
            }
            firePluginCreatedEvent( canonicalName, plugin );
            Log.info( "Successfully loaded plugin '{}'.", canonicalName );
            return true;
        }
        catch ( Throwable e )
        {
            Log.error( "An exception occurred while loading plugin '{}':", canonicalName, e );
            Integer count = failureToLoadCount.get( canonicalName );
            if ( count == null ) {
                count = 0;
            }
            failureToLoadCount.put( canonicalName, ++count );
            return false;
        }
    }

    private SAXReader setupSAXReader() throws SAXException {
        final SAXReader saxReader = new SAXReader();
        saxReader.setEncoding( "UTF-8" );
        saxReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        saxReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        saxReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return saxReader;
    }

    private PluginDevEnvironment configurePluginDevEnvironment( final Path pluginDir, String classesDir, String webRoot ) throws IOException
    {
        final String pluginName = pluginDir.getFileName().toString();

        final Path compilationClassesDir = pluginDir.resolve( "classes" );
        if ( Files.notExists( compilationClassesDir ) )
        {
            Files.createDirectory( compilationClassesDir );
        }
        compilationClassesDir.toFile().deleteOnExit();

        final PluginDevEnvironment dev = new PluginDevEnvironment();
        Log.info( "Plugin '{}' is running in development mode.", pluginName );
        if ( webRoot != null )
        {
            Path webRootDir = Paths.get( webRoot );
            if ( Files.notExists( webRootDir ) )
            {
                // Ok, let's try it relative from this plugin dir?
                webRootDir = pluginDir.resolve( webRoot );
            }

            if ( Files.exists( webRootDir ) )
            {
                dev.setWebRoot( webRootDir.toFile() );
            }
        }

        if ( classesDir != null )
        {
            Path classes = Paths.get( classesDir );
            if ( Files.notExists( classes ) )
            {
                // ok, let's try it relative from this plugin dir?
                classes = pluginDir.resolve( classesDir );
            }

            if ( Files.exists( classes ) )
            {
                dev.setClassesDir( classes.toFile() );
            }
        }

        return dev;
    }

    private void configureCaches( Path pluginDir, String pluginName )
    {
        Path cacheConfig = pluginDir.resolve( "cache-config.xml" );
        if ( Files.exists( cacheConfig ) )
        {
            PluginCacheConfigurator configurator = new PluginCacheConfigurator();
            try
            {
                configurator.setInputStream( new BufferedInputStream( Files.newInputStream( cacheConfig ) ) );
                configurator.configure( pluginName );
            }
            catch ( Exception e )
            {
                Log.error( "An exception occurred while trying to configure caches for plugin '{}':", pluginName, e );
            }
        }
    }

    /**
     * Delete a plugin, which removes the plugin.jar/war file after which the plugin is unloaded.
     * @param pluginName the plugin to delete
     */
    public void deletePlugin( final String pluginName )
    {
        Log.debug( "Deleting plugin '{}'...", pluginName );

        try ( final DirectoryStream<Path> ds = Files.newDirectoryStream( getPluginsDirectory(), new DirectoryStream.Filter<Path>()
        {
            @Override
            public boolean accept( final Path path ) {
                if ( Files.isDirectory( path ) )
                {
                    return false;
                }

                final String fileName = path.getFileName().toString().toLowerCase();
                return ( fileName.equals( pluginName + ".jar" ) || fileName.equals( pluginName + ".war" ) );
            }
        } ) )
        {
            for ( final Path pluginFile : ds )
            {
                try
                {
                    Files.delete( pluginFile );
                    pluginMonitor.runNow( true ); // trigger unload by running the monitor (which is more thread-safe than calling unloadPlugin directly).
                }
                catch ( IOException ex )
                {
                    Log.warn( "Unable to delete plugin '{}', as the plugin jar/war file cannot be deleted. File path: {}", pluginName, pluginFile, ex );
                }
            }
        }
        catch ( Throwable e )
        {
            Log.error( "An unexpected exception occurred while deleting plugin '{}'.", pluginName, e );
        }
    }

    public boolean reloadPlugin( String pluginName )
    {
        Log.debug( "Reloading plugin '{}'...", pluginName );

        final Plugin plugin = getPlugin( pluginName );
        if ( plugin == null )
        {
            Log.warn( "Unable to reload plugin '{}'. No such plugin loaded.", pluginName );
            return false;
        }

        final Path path = getPluginPath( plugin );
        if ( path == null )
        {
            // When there's a plugin, there should be a path. If there isn't, our code is buggy.
            throw new IllegalStateException( "Unable to determine installation path of plugin: " + pluginName );
        }

        try
        {
            Files.setLastModifiedTime( path, FileTime.fromMillis( 0 ) );
        }
        catch ( IOException e )
        {
            Log.warn( "Unable to reload plugin '{}'. Unable to reset the 'last modified time' of the plugin path. Try removing and restoring the plugin jar file manually.", pluginName );
            return false;
        }

        pluginMonitor.runNow( false );
        return true;
    }

    /**
     * Unloads a plugin. The {@link Plugin#destroyPlugin()} method will be called and then any resources will be
     * released. The name should be the canonical name of the plugin (based on the plugin directory name) and not the
     * human readable name as given by the plugin meta-data.
     *
     * This method only removes the plugin but does not delete the plugin JAR file. Therefore, if the plugin JAR still
     * exists after this method is called, the plugin will be started again the next  time the plugin monitor process
     * runs. This is useful for "restarting" plugins. To completely remove the plugin, use {@link #deletePlugin(String)}
     * instead.
     *
     * This method is called automatically when a plugin's JAR file is deleted.
     *
     * @param canonicalName the canonical name of the plugin to unload.
     */
    synchronized void unloadPlugin( String canonicalName )
    {
        Log.debug( "Unloading plugin '{}'...", canonicalName );

        failureToLoadCount.remove( canonicalName );

        Plugin plugin = pluginsLoaded.get( canonicalName );
        if ( plugin != null )
        {
            // Remove from dev mode if it exists.
            pluginDevelopment.remove( plugin );

            // See if any child plugins are defined.
            if ( parentPluginMap.containsKey( plugin ) )
            {
                String[] childPlugins = parentPluginMap.get( plugin ).toArray(new String[0]);
                for ( String childPlugin : childPlugins )
                {
                    Log.debug( "Unloading child plugin: '{}'.", childPlugin );
                    childPluginMap.remove( pluginsLoaded.get( childPlugin ) );
                    unloadPlugin( childPlugin );
                }
                parentPluginMap.remove( plugin );
            }

            Path webXML = pluginDirectory.resolve( canonicalName ).resolve( "web" ).resolve( "WEB-INF" ).resolve( "web.xml" );
            if ( Files.exists( webXML ) )
            {
                AdminConsole.removeModel( canonicalName );
                PluginServlet.unregisterServlets( webXML.toFile() );
            }
            Path customWebXML = pluginDirectory.resolve( canonicalName ).resolve( "web" ).resolve( "WEB-INF" ).resolve( "web-custom.xml" );
            if ( Files.exists( customWebXML ) )
            {
                PluginServlet.unregisterServlets( customWebXML.toFile() );
            }

            // Wrap destroying the plugin in a try/catch block. Otherwise, an exception raised
            // in the destroy plugin process will disrupt the whole unloading process. It's still
            // possible that classloader destruction won't work in the case that destroying the plugin
            // fails. In that case, Openfire may need to be restarted to fully cleanup the plugin
            // resources.
            try
            {
                plugin.destroyPlugin();
                Log.debug( "Destroyed plugin '{}'.", canonicalName );
            }
            catch ( Exception e )
            {
                Log.error( "An exception occurred while unloading plugin '{}':", canonicalName, e );
            }
        }

        // Remove references to the plugin so it can be unloaded from memory
        // If plugin still fails to be removed then we will add references back
        // Anyway, for a few seconds admins may not see the plugin in the admin console
        // and in a subsequent refresh it will appear if failed to be removed
        pluginsLoaded.remove( canonicalName );
        final String pluginName = getMetadata(canonicalName).getName();
        Log.info("Removing all System Properties for the plugin '{}'", pluginName);
        SystemProperty.removePropertiesForPlugin(pluginName);
        Path pluginFile = pluginDirs.remove( canonicalName );
        PluginClassLoader pluginLoader = classloaders.remove( plugin );
        PluginMetadata metadata = pluginMetadata.remove( canonicalName );

        // try to close the cached jar files from the plugin class loader
        if ( pluginLoader != null )
        {
            pluginLoader.unloadJarFiles();
        }
        else
        {
            Log.warn( "No plugin loader found for '{}'.", canonicalName );
        }

        // Try to remove the folder where the plugin was exploded. If this works then
        // the plugin was successfully removed. Otherwise, some objects created by the
        // plugin are still in memory.
        Path dir = pluginDirectory.resolve( canonicalName );
        // Give the plugin 2 seconds to unload.
        try
        {
            Thread.sleep( 2000 );
            // Ask the system to clean up references.
            System.gc();
            int count = 0;
            while ( !deleteDir( dir ) && count++ < 5 )
            {
                Log.warn( "Error unloading plugin '{}'. Will attempt again momentarily.", canonicalName );
                Thread.sleep( 8000 );
                // Ask the system to clean up references.
                System.gc();
            }
        }
        catch ( InterruptedException e )
        {
            Log.debug( "Stopped waiting for plugin '{}' to be fully unloaded.", canonicalName, e );
        }

        if ( plugin != null && Files.notExists( dir ) )
        {
            // Unregister plugin caches
            PluginCacheRegistry.getInstance().unregisterCaches( canonicalName );

            // See if this is a child plugin. If it is, we should unload
            // the parent plugin as well.
            if ( childPluginMap.containsKey( plugin ) )
            {
                String parentPluginName = childPluginMap.get( plugin );
                Plugin parentPlugin = pluginsLoaded.get( parentPluginName );
                List<String> childrenPlugins = parentPluginMap.get( parentPlugin );

                childrenPlugins.remove( canonicalName );
                childPluginMap.remove( plugin );

                // When the parent plugin implements PluginListener, its pluginDestroyed() method
                // isn't called if it dies first before its child. Athough the parent will die anyway,
                // it's proper if the parent "gets informed first" about the dying child when the
                // child is the one being killed first.
                if ( parentPlugin instanceof PluginListener )
                {
                    PluginListener listener;
                    listener = (PluginListener) parentPlugin;
                    listener.pluginDestroyed( canonicalName, plugin );
                }
                unloadPlugin( parentPluginName );
            }
            firePluginDestroyedEvent( canonicalName, plugin );
            Log.info( "Successfully unloaded plugin '{}'.", canonicalName );
        }
        else if ( plugin != null )
        {
            Log.info( "Restore references since we failed to remove the plugin '{}'.", canonicalName );
            pluginsLoaded.put( canonicalName, plugin );
            pluginDirs.put( canonicalName, pluginFile );
            classloaders.put( plugin, pluginLoader );
            pluginMetadata.put( canonicalName, metadata );
        }
    }

    /**
     * Loads a class from the classloader of a plugin.
     *
     * @param plugin the plugin.
     * @param className the name of the class to load.
     * @return the class.
     * @throws ClassNotFoundException if the class was not found.
     */
    public Class loadClass( Plugin plugin, String className ) throws ClassNotFoundException {
        final PluginClassLoader loader;
        synchronized ( this ) {
            loader = classloaders.get( plugin );
        }
        return loader.loadClass( className );
    }

    /**
     * Returns a plugin's dev environment if development mode is enabled for
     * the plugin.
     *
     * @param plugin the plugin.
     * @return the plugin dev environment, or {@code null} if development
     *         mode is not enabled for the plugin.
     */
    public synchronized PluginDevEnvironment getDevEnvironment( Plugin plugin )
    {
        return pluginDevelopment.get( plugin );
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getName(Plugin)}.
     * @param plugin the plugin to get the name for
     * @return the name of the plugin, as defined in the plugin.xml
     */
    @Deprecated
    public String getName( Plugin plugin )
    {
        return PluginMetadataHelper.getName( plugin );
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getDescription(Plugin)}.
     * @param plugin the plugin to get the description for
     * @return the description of the plugin
     */
    @Deprecated
    public String getDescription( Plugin plugin )
    {
        return PluginMetadataHelper.getDescription( plugin );
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getAuthor(Plugin)}.
     * @param plugin the plugin to get the author for
     * @return the author of the plugin
     */
    @Deprecated
    public String getAuthor( Plugin plugin )
    {
        return PluginMetadataHelper.getAuthor( plugin );
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getVersion(Plugin)}.
     * @param plugin the plugin to get the version for
     * @return the version of the plugin
     */
    @Deprecated
    public String getVersion( Plugin plugin )
    {
        return PluginMetadataHelper.getVersion( plugin ).getVersionString();
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getMinServerVersion(Plugin)}.
     * @param plugin the plugin to get the minimum server version for
     * @return the minimum server version for the plugin
     */
    @Deprecated
    public String getMinServerVersion( Plugin plugin )
    {
        return PluginMetadataHelper.getMinServerVersion( plugin ).getVersionString();
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getDatabaseKey(Plugin)}.
     * @param plugin the plugin to get the database key for
     * @return the database key for the plugin
     */
    @Deprecated
    public String getDatabaseKey( Plugin plugin )
    {
        return PluginMetadataHelper.getDatabaseKey( plugin );
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getDatabaseVersion(Plugin)}.
     * @param plugin the plugin to get the database version for
     * @return the database version for the plugin
     */
    @Deprecated
    public int getDatabaseVersion( Plugin plugin )
    {
        return PluginMetadataHelper.getDatabaseVersion( plugin );
    }

    /**
     * @deprecated Moved to {@link PluginMetadataHelper#getLicense(Plugin)}.
     * @param plugin the plugin to get the licence for
     * @return the licence for the plugin
     */
    @Deprecated
    public String getLicense( Plugin plugin )
    {
        return PluginMetadataHelper.getLicense( plugin );
    }

    /**
     * Returns the classloader of a plugin.
     *
     * @param plugin the plugin.
     * @return the classloader of the plugin.
     */
    public synchronized PluginClassLoader getPluginClassloader( Plugin plugin )
    {
        return classloaders.get( plugin );
    }

    /**
     * Verifies that the first few bytes of the input stream correspond to any of the known 'magic numbers' that
     * are known to represent a JAR archive.
     *
     * This method uses the mark/reset functionality of InputStream. This ensures that the input stream is reset
     * back to its original position after execution of this method.
     *
     * @param bin The input to read (cannot be null).
     * @return true if the stream first few bytes are equal to any of the known magic number sequences, otherwise false.
     */
    public static boolean validMagicNumbers( final BufferedInputStream bin ) throws IOException
    {
        final List<String> validMagicBytesCollection = JiveGlobals.getListProperty( "plugins.upload.magic-number.values.expected-value", Arrays.asList( "504B0304", "504B0506", "504B0708" ) );
        for ( final String entry : validMagicBytesCollection )
        {
            final byte[] validMagicBytes = StringUtils.decodeHex( entry );
            bin.mark( validMagicBytes.length );
            try
            {
                final byte[] magicBytes = new byte[validMagicBytes.length];
                final int bytesRead = IOUtils.read( bin, magicBytes );
                if ( bytesRead == validMagicBytes.length && Arrays.equals( validMagicBytes, magicBytes ) )
                {
                    return true;
                }
            }
            finally
            {
                bin.reset();
            }
        }

        return false;
    }

    /**
     * Deletes a directory.
     *
     * @param dir the directory to delete.
     * @return true if the directory was deleted.
     */
    static boolean deleteDir( Path dir )
    {
        try
        {
            if ( Files.isDirectory( dir ) )
            {
                Files.walkFileTree( dir, new SimpleFileVisitor<Path>()
                {
                    @Override
                    public FileVisitResult visitFile( Path file, BasicFileAttributes attrs ) throws IOException
                    {
                        try
                        {
                            Files.deleteIfExists( file );
                        }
                        catch ( IOException e )
                        {
                            Log.debug( "Plugin removal: could not delete: {}", file );
                            throw e;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory( Path dir, IOException exc ) throws IOException
                    {
                        try
                        {
                            Files.deleteIfExists( dir );
                        }
                        catch ( IOException e )
                        {
                            Log.debug( "Plugin removal: could not delete: {}", dir );
                            throw e;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                } );
            }
            return Files.notExists( dir ) || Files.deleteIfExists( dir );
        }
        catch ( IOException e )
        {
            return Files.notExists( dir );
        }
    }

    /**
     * Registers a PluginListener, which will now start receiving events regarding plugin creation and destruction.
     *
     * When the listener was already registered, this method will have no effect.
     *
     * @param listener the listener to be notified (cannot be null).
     */
    public void addPluginListener( PluginListener listener )
    {
        pluginListeners.add( listener );
    }

    /**
     * Deregisters a PluginListener, which will no longer receive events.
     *
     * When the listener was never added, this method will have no effect.
     *
     * @param listener the listener to be removed (cannot be null).
     */
    public void removePluginListener( PluginListener listener )
    {
        pluginListeners.remove( listener );
    }

    /**
     * Registers a PluginManagerListener, which will now start receiving events regarding plugin management.
     *
     * @param listener the listener to be notified (cannot be null).
     */
    public void addPluginManagerListener( PluginManagerListener listener )
    {
        pluginManagerListeners.add( listener );
        if ( isExecuted() )
        {
            firePluginsMonitored(listener);
        }
    }

    /**
     * Deregisters a PluginManagerListener, which will no longer receive events.
     *
     * When the listener was never added, this method will have no effect.
     *
     * @param listener the listener to be notified (cannot be null).
     */
    public void removePluginManagerListener( PluginManagerListener listener )
    {
        pluginManagerListeners.remove( listener );
    }

    /**
     * Notifies all registered PluginListener instances that a new plugin was created.
     *
     * @param name The name of the plugin
     * @param plugin the plugin.
     */
    void firePluginCreatedEvent( String name, Plugin plugin )
    {
        for ( final PluginListener listener : pluginListeners )
        {
            try
            {
                listener.pluginCreated( name, plugin );
            }
            catch ( Exception ex )
            {
                Log.warn( "An exception was thrown when one of the pluginManagerListeners was notified of a 'created' event for plugin '{}'!", name, ex );
            }
        }
    }

    /**
     * Notifies all registered PluginListener instances that a plugin was destroyed.
     *
     * @param name The name of the plugin
     * @param plugin the plugin.
     */
    void firePluginDestroyedEvent( String name, Plugin plugin )
    {
        for ( final PluginListener listener : pluginListeners )
        {
            try
            {
                listener.pluginDestroyed( name, plugin );
            }
            catch ( Exception ex )
            {
                Log.warn( "An exception was thrown when one of the pluginManagerListeners was notified of a 'destroyed' event for plugin '{}'!", name, ex );
            }
        }
    }

    /**
     * Notifies all registered PluginManagerListener instances that the service monitoring for plugin changes completed a
     * periodic check.
     */
    void firePluginsMonitored()
    {
        // Set that at least one iteration was done. That means that "all available" plugins
        // have been loaded by now.
        if ( !XMPPServer.getInstance().isSetupMode() )
        {
            executed = true;
        }

        for ( final PluginManagerListener listener : pluginManagerListeners )
        {
            firePluginsMonitored(listener);
        }
    }

    private void firePluginsMonitored(final PluginManagerListener listener) {
        try
        {
            listener.pluginsMonitored();
        }
        catch ( Exception ex )
        {
            Log.warn( "An exception was thrown when one of the pluginManagerListeners was notified of a 'monitored' event!", ex );
        }
    }

    public boolean isMonitorTaskRunning()
    {
        return pluginMonitor.isTaskRunning();
    }
}
