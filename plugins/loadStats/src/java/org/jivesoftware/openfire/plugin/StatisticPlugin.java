/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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
