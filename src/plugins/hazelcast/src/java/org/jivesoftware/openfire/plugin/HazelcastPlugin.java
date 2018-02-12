/*
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

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginManagerListener;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Hazelcast clustering plugin. This implementation is based upon
 * (and borrows heavily from) the original Openfire clustering plugin.
 * See this plugin's README file for more information.
 *
 * @author Tom Evans
 * @author Matt Tucker
 */
public class HazelcastPlugin implements Plugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(HazelcastPlugin.class);

    @Override
    public void initializePlugin(final PluginManager manager, final File pluginDirectory) {
        LOGGER.info("Waiting for other plugins to initialize before initializing clustering");
        manager.addPluginManagerListener(new PluginManagerListener() {
            @Override
            public void pluginsMonitored() {
                manager.removePluginManagerListener(this);
                initializeClustering(pluginDirectory);
            }
        });
    }

    private void initializeClustering(final File hazelcastPluginDirectory) {
        LOGGER.info("All plugins have initialized; initializing clustering");
        // Check if another cluster is installed and stop loading this plugin if found
        final String openfireHome = JiveGlobals.getHomeDirectory();
        File pluginDir = new File(openfireHome, "plugins");
        File[] jars = pluginDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                String fileName = pathname.getName().toLowerCase();
                return (fileName.equalsIgnoreCase("enterprise.jar") || 
                        fileName.equalsIgnoreCase("coherence.jar"));
            }
        });
        if (jars != null && jars.length > 0) {
            // Do not load this plugin if a conflicting implementation exists
            LOGGER.warn("Conflicting clustering plugins found; remove Coherence and/or Enterprise jar files");
            return;
        }

        try {
            final Path pathToLocalHazelcastConfig = Paths.get(openfireHome, "conf/hazelcast-local-config.xml");
            if (!Files.exists(pathToLocalHazelcastConfig)) {
                Files.copy(Paths.get(hazelcastPluginDirectory.getAbsolutePath(), "classes/hazelcast-local-config.xml.template"), pathToLocalHazelcastConfig);
            }
            ClusterManager.startup();
        } catch (final IOException e) {
            LOGGER.warn("Unable to create local Hazelcast configuration file from template; clustering will not start", e);
        }
    }

    @Override
    public void destroyPlugin() {
        // Shutdown is initiated by XMPPServer before unloading plugins
        if (!XMPPServer.getInstance().isShuttingDown()) {
            ClusterManager.shutdown();
        }
    }

}
