/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.plugin;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;

import java.io.File;

/**
 * Plugins that prints usage information of the database connection pool, thread pool
 * used for processing incoming traffic and the NIO networking layer.
 *
 * @author Gaston Dombiak
 */
public class StatisticPlugin implements Plugin {

    private StatCollector task;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        task = new StatCollector(JiveGlobals.getIntProperty("statistic.frequency", 5000));
        // Run the task
        task.start();
    }

    public void destroyPlugin() {
        if (task != null) {
            task.stop();
            task = null;
        }
    }
}
