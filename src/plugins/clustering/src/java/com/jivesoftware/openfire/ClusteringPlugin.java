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

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import com.tangosol.net.CacheFactory;

/**
 * Clustering Enterprise plugin.
 *
 * @author Matt Tucker
 */
public class ClusteringPlugin implements Plugin {
    private static final String COHERENCE_CONFIG = "tangosol-coherence-override";
    private static final String COHERENCE_CACHE_CONFIG = "coherence-cache-config";

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
        CacheFactory.getClusterConfig();
        ClusterManager.startup();
    }

    public void destroyPlugin() {
        // Shutdown is initiated by XMPPServer before unloading plugins
    	if (!XMPPServer.getInstance().isShuttingDown()) {
    		ClusterManager.shutdown();
    	}
    }
}
