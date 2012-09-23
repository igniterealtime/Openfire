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
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimerTask;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.util.cache.ExternalizableUtilStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jivesoftware.openfire.session.RemoteSessionLocator;
import com.jivesoftware.util.cache.ClusterExternalizableUtil;
import com.jivesoftware.util.cluster.ClusterPacketRouter;

/**
 * Hazelcast clustering plugin. This implementation is based upon
 * (and borrows heavily from) the original Openfire clustering plugin.
 * See this plugin's README file for more information.
 *
 * @author Tom Evans
 * @author Matt Tucker
 */
public class HazelcastPlugin extends TimerTask implements Plugin, PropertyEventListener {

    private static Logger logger = LoggerFactory.getLogger(HazelcastPlugin.class);

    private static final long CLUSTER_STARTUP_DELAY_TIME = 
    		JiveGlobals.getLongProperty("hazelcast.startup.delay.seconds", 5);
    
    /**
     * Keep serialization strategy the server was using before we set our strategy. We will
     * restore old strategy when plugin is unloaded.
     */
    private ExternalizableUtilStrategy serializationStrategy;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
    	// start cluster using a separate thread after a short delay
    	// this will allow other plugins to initialize during startup
    	TaskEngine.getInstance().schedule(this, CLUSTER_STARTUP_DELAY_TIME*1000);
    }

	@Override
	public void run() {
        System.out.println("Starting Hazelcast Clustering Plugin");

        // Check if another cluster is installed and stop loading this plugin if found
        File pluginDir = new File(JiveGlobals.getHomeDirectory(), "plugins");
        File[] jars = pluginDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                String fileName = pathname.getName().toLowerCase();
                return (fileName.equalsIgnoreCase("enterprise.jar") || 
                		fileName.equalsIgnoreCase("coherence.jar"));
            }
        });
        if (jars.length > 0) {
            // Do not load this plugin if a conflicting implementation exists
            logger.warn("Conflicting clustering plugins found; remove Coherence and/or Enterprise jar files");
            throw new IllegalStateException("Clustering plugin configuration conflict (Coherence)");
        }

        // List for clustering setting events (e.g. enabled/disabled)
        PropertyEventDispatcher.addListener(this);

        if (ClusterManager.isClusteringEnabled()) {
            initForClustering();

            // Start up or join the cluster and initialize caches
            ClusterManager.startup();
        }
	}

	private void initForClustering() {
        // Set the serialization strategy to use for transmitting objects between node clusters
        serializationStrategy = ExternalizableUtil.getInstance().getStrategy();
        ExternalizableUtil.getInstance().setStrategy(new ClusterExternalizableUtil());
        // Set session locator to use when in a cluster
        XMPPServer.getInstance().setRemoteSessionLocator(new RemoteSessionLocator());
        // Set packet router to use to deliver packets to remote cluster nodes
        XMPPServer.getInstance().getRoutingTable().setRemotePacketRouter(new ClusterPacketRouter());
    }

    /**
     * Returns the date when this release of Openfire clustering plugin was released.
     *
     * @return the date when this release of Openfire clustering plugin was released.
     */
    public static Date getReleaseDate() {
        try {
            // @DATE@ should be replaced with a date with the following format: Jan 31, 2007
            // Automatically set by ANT build tasks
            return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.US).parse("@DATE@");
        }
        catch (ParseException e) {
            logger.error("Error parsing date", e);
            return null;
        }
    }

    public void destroyPlugin() {
        ClusterManager.shutdown();

        // Set the old serialization strategy was using before clustering was loaded
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
