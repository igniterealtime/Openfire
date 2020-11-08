/*
 * Copyright 2016 IgniteRealtime.org
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service that monitors the plugin directory for plugins. It periodically checks for new plugin JAR files and
 * extracts them if they haven't already been extracted. Then, any new plugin directories are loaded, using the
 * PluginManager.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginMonitor implements PropertyEventListener
{
    private static final Logger Log = LoggerFactory.getLogger( PluginMonitor.class );

    private final PluginManager pluginManager;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> monitorTaskScheduledFuture;

    private boolean isTaskRunning = false;

    public PluginMonitor( final PluginManager pluginManager )
    {
        this.pluginManager = pluginManager;
    }

    /**
     * Initialize the monitor.
     */
    public void start()
    {
        if ( executor != null )
        {
            executor.shutdown();
        }

        executor = new ScheduledThreadPoolExecutor( 1 );

        if ( JiveGlobals.getBooleanProperty( "plugins.loading.monitor.enabled", true ) )
        {
            startMonitoring();
        }
        else
        {
            // Upon start, the monitor should execute at least once - otherwise, plugins will not load at all.
            runNow( false );
        }

        PropertyEventDispatcher.addListener( this );
    }

    /**
     * Start periodically checking the plugin directory.
     */
    public void startMonitoring()
    {
        // See if we're in development mode. If so, check for new plugins once every 5 seconds Otherwise, default to every 20 seconds.
        if ( Boolean.getBoolean( "developmentMode" ) )
        {
            monitorTaskScheduledFuture = executor.scheduleWithFixedDelay( new MonitorTask(), 0, 5, TimeUnit.SECONDS );
        }
        else
        {
            monitorTaskScheduledFuture = executor.scheduleWithFixedDelay( new MonitorTask(), 0, JiveGlobals.getIntProperty( "plugins.loading.monitor.interval", 20 ), TimeUnit.SECONDS );
        }
    }

    /**
     * Shutdown the monitor.
     */
    public void stop()
    {
        PropertyEventDispatcher.removeListener( this );

        stopMonitoring();

        if ( executor != null )
        {
            executor.shutdown();
        }
    }

    /**
     * Stop periodically checking the plugin directory.
     */
    public void stopMonitoring()
    {
        if ( monitorTaskScheduledFuture != null && !monitorTaskScheduledFuture.isDone() )
        {
            // Cancel, with an interrupt if this task has been cancelled before.
            monitorTaskScheduledFuture.cancel( monitorTaskScheduledFuture.isCancelled() );
        }
    }

    public boolean isTaskRunning()
    {
        return isTaskRunning;
    }

    /**
     * Immediately run a check of the plugin directory.
     * @param blockUntilDone {code true} to wait until the check is complete, otherwise {@code false}
     */
    public void runNow( boolean blockUntilDone )
    {
        final Future<?> future = executor.submit( new MonitorTask() );
        if ( blockUntilDone )
        {
            try
            {
                future.get();
            }
            catch ( Exception e )
            {
                Log.warn( "An exception occurred while waiting for a check of the plugin directory to complete.", e );
            }
        }
    }

    @Override
    public void propertySet( final String property, final Map<String, Object> params )
    {
        switch ( property )
        {
            case "plugins.loading.monitor.enabled":
                if ( JiveGlobals.getBooleanProperty( "plugins.loading.monitor.enabled", true ) ) {
                    startMonitoring();
                } else {
                    stopMonitoring();
                }
                break;

            case "plugins.loading.monitor.interval":
                // When monitoring, restart the monitor, which will re-read the interval.
                if ( monitorTaskScheduledFuture != null && !monitorTaskScheduledFuture.isDone() ) {
                    stopMonitoring();
                    startMonitoring();
                }
                break;
        }
    }

    @Override
    public void propertyDeleted( final String property, final Map<String, Object> params )
    {
        propertySet( property, params );
    }

    @Override
    public void xmlPropertySet( final String property, final Map<String, Object> params )
    {
        propertySet( property, params );
    }

    @Override
    public void xmlPropertyDeleted( final String property, final Map<String, Object> params )
    {
        propertySet( property, params );
    }

    private class MonitorTask implements Runnable
    {
        @Override
        public void run()
        {
            // Prevent two tasks from running in parallel by using the plugin monitor itself as a mutex.
            synchronized ( PluginMonitor.this )
            {
                isTaskRunning = true;
                try
                {
                    // The directory that contains all plugins.
                    final Path pluginsDirectory = pluginManager.getPluginsDirectory();
                    if ( !Files.isDirectory( pluginsDirectory ) || !Files.isReadable( pluginsDirectory ) )
                    {
                        Log.error( "Unable to process plugins. The plugins directory does not exist (or is no directory): {}", pluginsDirectory );
                        return;
                    }

                    // Turn the list of JAR/WAR files into a set so that we can do lookups.
                    final Set<String> jarSet = new HashSet<>();

                    // Explode all plugin files that have not yet been exploded (or need to be re-exploded).
                    try ( final DirectoryStream<Path> ds = Files.newDirectoryStream( pluginsDirectory, new DirectoryStream.Filter<Path>()
                    {
                        @Override
                        public boolean accept( final Path path ) throws IOException
                        {
                            if ( Files.isDirectory( path ) )
                            {
                                return false;
                            }

                            final String fileName = path.getFileName().toString().toLowerCase();
                            return ( fileName.endsWith( ".jar" ) || fileName.endsWith( ".war" ) );
                        }
                    } ) )
                    {
                        for ( final Path jarFile : ds )
                        {
                            final String fileName = jarFile.getFileName().toString();
                            final String canonicalPluginName = fileName.substring( 0, fileName.length() - 4 ).toLowerCase(); // strip extension.

                            jarSet.add( canonicalPluginName );

                            // See if the JAR has already been exploded.
                            final Path dir = pluginsDirectory.resolve( canonicalPluginName );

                            // See if the JAR is newer than the directory. If so, the plugin needs to be unloaded and then reloaded.
                            if ( Files.exists( dir ) && Files.getLastModifiedTime( jarFile ).toMillis() > Files.getLastModifiedTime( dir ).toMillis() )
                            {
                                // If this is the first time that the monitor process is running, then plugins won't be loaded yet. Therefore, just delete the directory.
                                if ( !pluginManager.isExecuted() )
                                {
                                    int count = 0;
                                    // Attempt to delete the folder for up to 5 seconds.
                                    while ( !PluginManager.deleteDir( dir ) && count++ < 5 )
                                    {
                                        Thread.sleep( 1000 );
                                    }
                                }
                                else
                                {
                                    // Not the first time? Properly unload the plugin.
                                    pluginManager.unloadPlugin( canonicalPluginName );
                                }
                            }

                            // If the JAR needs to be exploded, do so.
                            if ( Files.notExists( dir ) )
                            {
                                if (!unzipPlugin( canonicalPluginName, jarFile, dir ) )
                                {
                                    // the 'continue' statement is strictly unneeded here, as this
                                    // is the last statement at the time of writing. It's left in
                                    // to avoid future additions to this code from progressing
                                    // beyond this point.
                                    continue;
                                }
                            }
                        }
                    }

                    // See if any currently running plugins need to be unloaded due to the JAR file being deleted. Note
                    // that unloading a parent plugin might cause more than one plugin to disappear. Don't reuse the
                    // directory stream afterwards!
                    try ( final DirectoryStream<Path> ds = Files.newDirectoryStream( pluginsDirectory, new DirectoryStream.Filter<Path>()
                    {
                        @Override
                        public boolean accept( final Path path ) throws IOException
                        {
                            if ( !Files.isDirectory( path ) )
                            {
                                return false;
                            }

                            final String pluginName = PluginMetadataHelper.getCanonicalName( path );
                            return !pluginName.equals( "admin" ) && !jarSet.contains( pluginName );
                        }
                    } ) )
                    {
                        for ( final Path path : ds )
                        {
                            final String pluginName = PluginMetadataHelper.getCanonicalName( path );
                            Log.info( "Plugin '{}' was removed from the file system.", pluginName );
                            pluginManager.unloadPlugin( pluginName );
                        }
                    }

                    // Load all plugins that need to be loaded. Make sure that the admin plugin is loaded first (as that
                    // should be available as soon as possible), followed by all other plugins. Ensure that parent plugins
                    // are loaded before their children.
                    try ( final DirectoryStream<Path> ds = Files.newDirectoryStream( pluginsDirectory, new DirectoryStream.Filter<Path>()
                    {
                        @Override
                        public boolean accept( final Path path ) throws IOException
                        {
                            return Files.isDirectory( path );
                        }
                    } ) )
                    {
                        // Look for extra plugin directories specified as a system property.
                        final Set<Path> devPlugins = new HashSet<>();
                        final String devPluginDirs = System.getProperty( "pluginDirs" );
                        if ( devPluginDirs != null )
                        {
                            final StringTokenizer st = new StringTokenizer( devPluginDirs, "," );
                            while ( st.hasMoreTokens() )
                            {
                                try
                                {
                                    final String devPluginDir = st.nextToken().trim();
                                    final Path devPluginPath = Paths.get( devPluginDir );
                                    if ( Files.exists( devPluginPath ) && Files.isDirectory( devPluginPath ) )
                                    {
                                        devPlugins.add( devPluginPath );
                                    }
                                    else
                                    {
                                        Log.error( "Unable to load a dev plugin as its path (as supplied in the 'pluginDirs' system property) does not exist, or is not a directory. Offending path: [{}] (parsed from raw value [{}])", devPluginPath, devPluginDir );
                                    }
                                }
                                catch ( InvalidPathException ex )
                                {
                                    Log.error( "Unable to load a dev plugin as an invalid path was added to the 'pluginDirs' system property.", ex );
                                }
                            }
                        }

                        // Sort the list of directories so that the "admin" plugin is always first in the list, and 'parent'
                        // plugins always precede their children.
                        final Deque<List<Path>> dirs = sortPluginDirs( ds, devPlugins );

                        // Hierarchy processing could be parallel.
                        final Collection<Callable<Integer>> parallelProcesses = new ArrayList<>();
                        for ( final List<Path> hierarchy : dirs )
                        {
                            parallelProcesses.add( new Callable<Integer>()
                            {

                                @Override
                                public Integer call() throws Exception
                                {
                                    int loaded = 0;
                                    for ( final Path path : hierarchy )
                                    {
                                        // If the plugin hasn't already been started, start it.
                                        final String canonicalName = PluginMetadataHelper.getCanonicalName( path );
                                        if ( pluginManager.getPlugin( canonicalName ) == null )
                                        {
                                            if ( pluginManager.loadPlugin( canonicalName, path ) )
                                            {
                                                loaded++;
                                            }
                                        }
                                    }

                                    return loaded;
                                }
                            } );
                        }

                        // Before running any plugin, make sure that the admin plugin is loaded. It is a dependency
                        // of all plugins that attempt to modify the admin panel.
                        if ( pluginManager.getPlugin( "admin" ) == null )
                        {
                            pluginManager.loadPlugin( "admin", dirs.getFirst().get( 0 ) );
                        }

                        // Hierarchies could be processed in parallel. This is likely to be beneficial during the first
                        // execution of this monitor, as during later executions, most plugins will likely already be loaded.
                        final int parallelProcessMax = JiveGlobals.getIntProperty( "plugins.loading.max-parallel", 4 );
                        final int parallelProcessCount = ( pluginManager.isExecuted() ? 1 : parallelProcessMax );

                        final ExecutorService executorService = Executors.newFixedThreadPool( parallelProcessCount );
                        try
                        {
                            // Blocks until ready
                            final List<Future<Integer>> futures = executorService.invokeAll( parallelProcesses );

                            // Unless nothing happened, report that we're done loading plugins.
                            int pluginsLoaded = 0;
                            for ( Future<Integer> future : futures )
                            {
                                pluginsLoaded += future.get();
                            }
                            if ( pluginsLoaded > 0 && !XMPPServer.getInstance().isSetupMode() )
                            {
                                Log.info( "Finished processing all plugins." );
                            }
                        }
                        finally
                        {
                            executorService.shutdown();
                        }

                        // Trigger event that plugins have been monitored
                        pluginManager.firePluginsMonitored();
                    }
                }
                catch ( Throwable e )
                {
                    Log.error( "An unexpected exception occurred:", e );
                }
                finally
                {
                    isTaskRunning = false;
                }
            }
        }

        /**
         * Unzips a plugin from a JAR file into a directory. If the JAR file
         * isn't a plugin, this method will do nothing.
         *
         * @param pluginName the name of the plugin.
         * @param file       the JAR file
         * @param dir        the directory to extract the plugin to.
         * @return A boolean indicating success.
         */
        private boolean unzipPlugin( String pluginName, Path file, Path dir )
        {
            try ( ZipFile zipFile = new JarFile( file.toFile() ) )
            {
                // Ensure that this JAR is a plugin.
                if ( zipFile.getEntry( "plugin.xml" ) == null )
                {
                    return false;
                }

                // Protect against zip-slip (before applying any file-system modifications).
                if ( JiveGlobals.getBooleanProperty( "plugins.loading.zipslipDetection.enabled", true ) )
                {
                    for ( Enumeration e = zipFile.entries(); e.hasMoreElements(); )
                    {
                        JarEntry entry = (JarEntry) e.nextElement();
                        Path entryFile = dir.resolve( entry.getName() );
                        if ( !entryFile.normalize().toAbsolutePath().startsWith( dir.normalize().toAbsolutePath() ) )
                        {
                            throw new RuntimeException( "Plugin contains content that is outside of target plugin directory (possible zipslip attack)" );
                        }
                    }
                }

                Files.createDirectory( dir );
                // OF-1973: Prevent future-timestamped jar files from restarting the installation process.
                if ( Files.getLastModifiedTime( file ).toMillis() > System.currentTimeMillis() )
                {
                    final FileTime now = FileTime.fromMillis(System.currentTimeMillis());
                    Files.setLastModifiedTime(file, now);
                }
                // Set the date of the JAR file to the newly created folder
                Files.setLastModifiedTime( dir, Files.getLastModifiedTime( file ) );
                Log.debug( "Extracting plugin '{}'...", pluginName );
                for ( Enumeration e = zipFile.entries(); e.hasMoreElements(); )
                {
                    JarEntry entry = (JarEntry) e.nextElement();
                    Path entryFile = dir.resolve( entry.getName() );
                    // Ignore any manifest.mf entries.
                    if ( entry.getName().toLowerCase().endsWith( "manifest.mf" ) )
                    {
                        continue;
                    }
                    if ( !entry.isDirectory() )
                    {
                        Files.createDirectories( entryFile.getParent() );
                        try ( InputStream zin = zipFile.getInputStream( entry ) )
                        {
                            Files.copy( zin, entryFile, StandardCopyOption.REPLACE_EXISTING );
                        }
                    }
                }
                Log.debug( "Successfully extracted plugin '{}'.", pluginName );
                return true;
            }
            catch ( Exception e )
            {
                Log.error( "An exception occurred while trying to extract plugin '{}':", pluginName, e );
                return false;
            }
        }

        /**
         * Returns all plugin directories, in a deque of lists with these characteristics:
         * <ol>
         * <li>Every list is a hierarchy of parent/child plugins (or is a list of one element).</li>
         * <li>Every list is ordered to ensure that all parent plugins have a lower index than their children.</li>
         * <li>The first element of every list will be a plugin that has no 'parent' plugin.</li>
         * <li>the first element of the first list will be the 'admin' plugin.</li>
         * </ol>
         *
         * Plugins within the provided argument that refer to non-existing parent plugins will not be part of the returned
         * collection.
         *
         * @param dirs Collections of paths that refer every plugin directory (but not the corresponding .jar/.war files).
         * @return An ordered collection of paths.
         */
        @SafeVarargs
        private final Deque<List<Path>> sortPluginDirs( Iterable<Path>... dirs )
        {
            // Map all plugins to they parent plugin (lower-cased), using a null key for parent-less plugins;
            final Map<String, Set<Path>> byParent = new HashMap<>();
            for ( final Iterable<Path> iterable : dirs )
            {
                for ( final Path dir : iterable )
                {
                    final String parent = PluginMetadataHelper.getParentPlugin( dir );
                    if ( !byParent.containsKey( parent ) )
                    {
                        byParent.put( parent, new HashSet<Path>() );
                    }
                    byParent.get( parent ).add( dir );
                }
            }

            // Transform the map into a tree structure (where the root node is a placeholder without data).
            final Node root = new Node();
            populateTree( root, byParent );

            // byParent should be consumed. Remaining entries are depending on a non-existing parent.
            for ( Map.Entry<String, Set<Path>> entry : byParent.entrySet() )
            {
                if ( !entry.getValue().isEmpty() )
                {
                    for ( final Path path : entry.getValue() )
                    {
                        final String name = PluginMetadataHelper.getCanonicalName( path );
                        Log.warn( "Unable to load plugin '{}' as its defined parent plugin '{}' is not installed.", name, entry.getKey() );
                    }
                }
            }

            // Return a deque of lists, where each list is parent-child chain of plugins (the parents preceding its children).
            final Set<PluginToLoad> pluginsToLoad = new HashSet<>();
            final Deque<List<Path>> result = new ArrayDeque<>();
            for ( final Node noParentPlugin : root.children )
            {
                final List<Path> hierarchy = new ArrayList<>();
                walkTree( noParentPlugin, hierarchy );
                // Strip out duplicates
                final Iterator<Path> iterator = hierarchy.iterator();
                while (iterator.hasNext()) {
                    final PluginToLoad pluginToLoad = new PluginToLoad(iterator.next());
                    if (!pluginsToLoad.add(pluginToLoad)) {
                        Log.warn("Unable to load plugin at '{}' as a different plugin with the same name is present", pluginToLoad.path);
                        iterator.remove();
                    }
                }

                // The admin plugin should go first
                if ( noParentPlugin.getName().equals( "admin" ) )
                {
                    result.addFirst( hierarchy );
                }
                else
                {
                    result.addLast( hierarchy );
                }
            }

            return result;
        }

        private void populateTree( final Node parent, Map<String, Set<Path>> byParent )
        {
            final String parentName = parent.path == null ? null : PluginMetadataHelper.getCanonicalName( parent.path );
            final Set<Path> children = byParent.remove( parentName );
            if ( children != null )
            {
                for ( final Path child : children )
                {
                    final Node node = new Node();
                    node.path = child;
                    if ( !parent.children.add( node ) )
                    {
                        Log.warn( "Detected plugin duplicates for name: '{}'. Only one plugin will be loaded.", node.getName() );
                    }

                    // recurse to find further children.
                    populateTree( node, byParent );
                }
            }
        }

        private void walkTree( final Node node, List<Path> result )
        {
            result.add( node.path );
            if ( node.children != null )
            {
                for ( Node child : node.children )
                {
                    walkTree( child, result );
                }
            }
        }

        class Node
        {
            Path path;
            SortedSet<Node> children = new TreeSet<>( new Comparator<Node>()
            {
                @Override
                public int compare( Node o1, Node o2 )
                {
                    return o1.getName().compareToIgnoreCase( o2.getName() );
                }
            } );

            String getName()
            {
                return PluginMetadataHelper.getCanonicalName( path );
            }
        }
    }

    /**
     * Two plugins are considered "equal" if they share the same canonical name, <strong>or</strong> the same
     * name from the plugin.xml file. This class represents a plugin that could be loaded to encapsulate this concept
     * <p>
     *     Note: this class has a natural ordering that is inconsistent with equals.
     * </p>
     */
    private static final class PluginToLoad implements Comparable<PluginToLoad> {
        private final Path path;
        private final String canonicalName;
        private final String pluginName;

        private PluginToLoad(final Path path) {
            this.path = path;
            this.canonicalName = PluginMetadataHelper.getCanonicalName( path );
            this.pluginName = PluginMetadataHelper.getName( path );
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }

            if (!(o instanceof PluginToLoad)) {
                return false;
            }

            final PluginToLoad that = (PluginToLoad) o;
            return this.canonicalName.equalsIgnoreCase(that.canonicalName)
                || this.pluginName.equalsIgnoreCase(that.pluginName);
        }

        @Override
        public int hashCode() {
            // Note this is sub-optimal, but not an issue for the relatively low number of plugins Openfire will have
            // installed. It is necessary because of the Java equals/hashCode contract - two equals objects must have
            // the same hash code - but we don't know if the objects are equal because the share the same canonical name
            // or the same plugin name.
            return 0;
        }

        @Override
        public int compareTo(final PluginToLoad that) {
            // NB. This violates the Comparable recommendation. Quote:
            // <p>It is strongly recommended, but <i>not</i> strictly required that
            // {@code (x.compareTo(y)==0) == (x.equals(y))}.
            return this.pluginName.compareTo(that.pluginName);
        }
    }
}
