/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

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
