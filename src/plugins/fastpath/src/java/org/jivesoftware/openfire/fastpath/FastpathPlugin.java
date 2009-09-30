/**
 * $Revision: $
 * $Date: $
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

package org.jivesoftware.openfire.fastpath;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.user.UserNameManager;
import org.jivesoftware.openfire.user.UserNameProvider;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.fastpath.util.TaskEngine;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.component.ComponentException;
import org.xmpp.packet.JID;

import java.io.File;
import java.io.FileFilter;

/**
 * Openfire Fastpath plugin.
 *
 * @author Matt Tucker
 */
public class FastpathPlugin implements Plugin, ClusterEventListener {

    /**
     * Keep a reference to Fastpath only when the service is up and running in this JVM.
     */
    private WorkgroupManager workgroupManager;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        System.out.println("Starting Fastpath Server");

        // Check if we Enterprise is installed and stop loading this plugin if found
        File pluginDir = new File(JiveGlobals.getHomeDirectory(), "plugins");
        File[] jars = pluginDir.listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                String fileName = pathname.getName().toLowerCase();
                return (fileName.equalsIgnoreCase("enterprise.jar"));
            }
        });
        if (jars.length > 0) {
            // Do not load this plugin since Enterprise is still installed
            System.out.println("Enterprise plugin found. Stopping Fastpath Plugin");
            throw new IllegalStateException("This plugin cannot run next to the Enterprise plugin");
        }

        // Make sure that the fastpath folder exists under the home directory
        File fastpathDir = new File(JiveGlobals.getHomeDirectory() +
            File.separator + "fastpath");
        if (!fastpathDir.exists()) {
            fastpathDir.mkdirs();
        }

        workgroupManagerStart();

        // Listen to cluster events
        ClusterManager.addListener(this);
    }

    public void destroyPlugin() {
        workgroupManagerStop();

        // Stop listen to cluster events
        ClusterManager.removeListener(this);

        // Dispose pending tasks
        TaskEngine.getInstance().dispose();
    }

    private void workgroupManagerStart() {
        workgroupManager = WorkgroupManager.getInstance();
        // Register Fastpath service
        try {
            ComponentManagerFactory.getComponentManager().addComponent("workgroup", workgroupManager);
        }
        catch (ComponentException e) {
            // Do nothing. Should never happen.
            Log.error(e);
        }
        // Register the provider of workgroup names
        UserNameManager.addUserNameProvider(workgroupManager.getAddress().toString(),
            new UserNameProvider() {
                public String getUserName(JID entity) {
                    try {
                        Workgroup workgroup = workgroupManager.getWorkgroup(entity);
                        return workgroup.getDisplayName();
                    }
                    catch (UserNotFoundException e) {
                        return entity.toString();
                    }
                }

            });
        // Start the Fastpath module
        workgroupManager.start();
    }

    private void workgroupManagerStop() {
        // Unregister workgroup component
        try {
            ComponentManagerFactory.getComponentManager().removeComponent("workgroup");
        }
        catch (ComponentException e) {
            Log.error("Error unregistering workgroup component", e);
        }
        if (workgroupManager != null) {
            // Unregister the provider of workgroup names
            UserNameManager.removeUserNameProvider(workgroupManager.getAddress().toString());
            // Stop the Fastpath module
            workgroupManager.stop();
        }
        // Clean up the reference to the workgroup manager as a way to say that FP is no longer running in this JVM
        workgroupManager = null;
    }

    public void joinedCluster() {
        workgroupManagerStop();
    }

    public void joinedCluster(byte[] nodeID) {
        // Do nothing
    }

    public void leftCluster() {
        workgroupManagerStart();
    }

    public void leftCluster(byte[] nodeID) {
        // Do nothing
    }

    public void markedAsSeniorClusterMember() {
        workgroupManagerStart();
    }
}
