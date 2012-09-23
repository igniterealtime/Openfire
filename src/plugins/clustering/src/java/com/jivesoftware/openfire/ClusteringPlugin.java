/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.openfire;

import com.jivesoftware.openfire.session.RemoteSessionLocator;
import com.jivesoftware.util.cache.CoherenceExternalizableUtil;
import com.jivesoftware.util.cluster.CoherencePacketRouter;
import com.tangosol.net.CacheFactory;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.util.cache.ExternalizableUtilStrategy;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Clustering Enterprise plugin.
 *
 * @author Matt Tucker
 */
public class ClusteringPlugin implements Plugin, PropertyEventListener {
    private static final String COHERENCE_CONFIG = "tangosol-coherence-override";
    private static final String COHERENCE_CACHE_CONFIG = "coherence-cache-config";

    /**
     * Keep serialization strategy the server was using before we set our strategy. We will
     * restore old strategy when plugin is unloaded.
     */
    private ExternalizableUtilStrategy serializationStrategy;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        System.out.println("Starting Clustering Plugin");

        // Check if we Enterprise is installed and stop loading this plugin if found
        File pluginDir = new File(JiveGlobals.getHomeDirectory(), "plugins");
        File[] jars = pluginDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                String fileName = pathname.getName().toLowerCase();
                return (fileName.equalsIgnoreCase("enterprise.jar") || fileName.equalsIgnoreCase("hazelcast.jar"));
            }
        });
        if (jars.length > 0) {
            // Do not load this plugin since Enterprise is still installed
            System.out.println("Conflicting plugin found. Stopping Clustering Plugin");
            throw new IllegalStateException("This plugin cannot run with the Enterprise or Hazelcast plugin");
        }

        // Make sure that the enteprise folder exists under the home directory
        File enterpriseDir = new File(JiveGlobals.getHomeDirectory() +
            File.separator + "enterprise");
        if (!enterpriseDir.exists()) {
            enterpriseDir.mkdirs();
        }

        // Check if Coherence libs are installed and stop loading this plugin if NOT found
//        File libDir = new File(JiveGlobals.getHomeDirectory(), "lib");
//        jars = libDir.listFiles(new FileFilter() {
//            public boolean accept(File pathname) {
//                String fileName = pathname.getName().toLowerCase();
//                return (fileName.equalsIgnoreCase("coherence.jar"));
//            }
//        });
//        if (jars.length == 0) {
//            // Do not load this plugin since Coherence libs are not installed
//            System.out.println("Coherence libs not found. Stopping Clustering Plugin. Copy tangosol.jar, " +
//                    "coherence.jar and coherence-work.jar files to [OPENFIRE_HOME]/lib and restart the server.");
//            throw new IllegalStateException("Coherence libs not found. Stopping Clustering Plugin. Copy " +
//                    "tangosol.jar, coherence.jar and coherence-work.jar files to [OPENFIRE_HOME]/lib and restart the server.");
//        }

        // List for clustering setting events (e.g. enabled/disabled)
        PropertyEventDispatcher.addListener(this);

        // Delete no longer used COHERENCE_CONFIG file. Java system properties should be used
        // to customize coherence
        File configFile = new File(enterpriseDir, COHERENCE_CONFIG + ".xml");
        if (configFile.exists()) {
            configFile.delete();
        }
        // Delete no longer used COHERENCE_CACHE_CONFIG file. Admins should use system properties
        // to override default values. Same system properties will be used when not using enterprise or not
        // using clustering
        configFile = new File(enterpriseDir, COHERENCE_CACHE_CONFIG + ".xml");
        if (configFile.exists()) {
            configFile.delete();
        }

        try {
            // Add openfireHome/enterprise dir to pluginclassloader
            // Add enterprise plugin dir to pluginclassloader
            URL url = new File(pluginDirectory + File.separator).toURL();
            manager.getPluginClassloader(manager.getPlugin(pluginDirectory.getName())).addURLFile(url);
        }
        catch (MalformedURLException e) {
            Log.error("Error adding openfireHome/enterprise to the classpath of the enterprise plugin", e);
        }

        if (ClusterManager.isClusteringEnabled()) {
            initForClustering();

            // Start up or join the cluster and initialize caches
            ClusterManager.startup();
        }

    }

    private void initForClustering() {
        // Set the serialization strategy to use for transmitting objects between node clusters
        serializationStrategy = ExternalizableUtil.getInstance().getStrategy();
        ExternalizableUtil.getInstance().setStrategy(new CoherenceExternalizableUtil());
        // Set session locator to use when in a cluster
        XMPPServer.getInstance().setRemoteSessionLocator(new RemoteSessionLocator());
        // Set packet router to use to deliver packets to remote cluster nodes
        XMPPServer.getInstance().getRoutingTable().setRemotePacketRouter(new CoherencePacketRouter());
        // Initialize the Coherence cluster configuration
        CacheFactory.getClusterConfig();
    }

    /**
     * Returns the date when this release of Openfire Enterprise was released.
     *
     * @return the date when this release of Openfire Enterprise was released.
     */
    public static Date getReleaseDate() {
        try {
            // @DATE@ should be replaced with a date with the following format: Jan 31, 2007
            // Automaticly set by ANT build tasks
            return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US).parse("@DATE@");
        }
        catch (ParseException e) {
            Log.error("Error parsing date", e);
            return null;
        }
    }

    public void destroyPlugin() {
        ClusterManager.shutdown();

        // Set the old serialization strategy was using before enterprise was loaded
        ExternalizableUtil.getInstance().setStrategy(serializationStrategy);

        // Stop listing for clustering setting events (e.g. enabled/disabled)
        PropertyEventDispatcher.removeListener(this);
    }

    public void propertySet(String property, Map<String, Object> params) {
        // Ignore
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        // Ignore
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        if (ClusterManager.CLUSTER_PROPERTY_NAME.equals(property)) {
            if (Boolean.parseBoolean((String) params.get("value"))) {
                // Clustering was enabled
                initForClustering();
            }
            else {
                // Clustering was disabled
            }
        }
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }
}
