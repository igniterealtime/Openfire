/**
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.plugin.spark.SparkManager;
import org.jivesoftware.openfire.plugin.spark.BookmarkInterceptor;
import org.jivesoftware.openfire.plugin.spark.TaskEngine;
import org.jivesoftware.openfire.plugin.spark.manager.SparkVersionManager;
import org.jivesoftware.openfire.plugin.spark.manager.FileTransferFilterManager;

import java.io.File;

/**
 * Client control plugin.
 *
 * @author Matt Tucker
 */
public class ClientControlPlugin implements Plugin {

    private PluginManager pluginManager;

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
        pluginManager = manager;

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
        pluginManager = null;

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