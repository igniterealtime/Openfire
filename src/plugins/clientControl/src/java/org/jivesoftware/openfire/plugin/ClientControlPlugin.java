/**
 * Copyright (C) 2008 Jive Software. All rights reserved.
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

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.plugin.spark.SparkManager;
import org.jivesoftware.openfire.plugin.spark.BookmarkInterceptor;
import org.jivesoftware.openfire.plugin.spark.TaskEngine;
import org.jivesoftware.openfire.plugin.spark.manager.SparkVersionManager;
import org.jivesoftware.openfire.plugin.spark.manager.FileTransferFilterManager;
import org.jivesoftware.util.JiveGlobals;

import java.io.File;
import java.io.FileFilter;

/**
 * Client control plugin.
 *
 * @author Matt Tucker
 */
public class ClientControlPlugin implements Plugin {

    private SparkManager sparkManager;
    private BookmarkInterceptor bookmarkInterceptor;
    private SparkVersionManager sparkVersionManager;
    private FileTransferFilterManager fileTransferFilterManager;
    private TaskEngine taskEngine;

    /**
     * Constructs a new client control plugin.
     */
    public ClientControlPlugin() {
    }

    // Plugin Interface

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        System.out.println("Starting Client Control Plugin");

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
            System.out.println("Enterprise plugin found. Stopping Client Control Plugin");
            throw new IllegalStateException("This plugin cannot run next to the Enterprise plugin");
        }

        taskEngine = TaskEngine.getInstance();
        sparkManager = new SparkManager(taskEngine);
        sparkManager.start();
        // Create and start the bookmark interceptor, which adds server-managed bookmarks when
        // a user requests their bookmark list.
        bookmarkInterceptor = new BookmarkInterceptor();
        bookmarkInterceptor.start();
        // Create and start the Spark version manager
        sparkVersionManager = new SparkVersionManager();
        sparkVersionManager.start();

        fileTransferFilterManager = new FileTransferFilterManager();
        fileTransferFilterManager.start();

    }

    public FileTransferFilterManager getFileTransferFilterManager() {
        return fileTransferFilterManager;
    }

    public void destroyPlugin() {
        if (sparkManager != null) {
            sparkManager.stop();
            sparkManager.shutdown();
            sparkManager = null;
        }

        if (bookmarkInterceptor != null) {
            bookmarkInterceptor.stop();
            bookmarkInterceptor = null;
        }

        if (sparkVersionManager != null) {
            sparkVersionManager.shutdown();
            sparkVersionManager = null;
        }

        if (fileTransferFilterManager != null) {
            fileTransferFilterManager.stop();
            fileTransferFilterManager = null;
        }

        taskEngine.shutdown();
    }

}
