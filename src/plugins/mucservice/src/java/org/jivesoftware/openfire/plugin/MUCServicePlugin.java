/*
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

import java.io.File;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

/**
 * The Class MUCServicePlugin.
 */
public class MUCServicePlugin implements Plugin {

    /**
     * Instantiates a new mUC service plugin.
     */
    public MUCServicePlugin() {
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.jivesoftware.openfire.container.Plugin#initializePlugin(org.jivesoftware
     * .openfire.container.PluginManager, java.io.File)
     */
    public void initializePlugin(PluginManager manager, File pluginDirectory) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.openfire.container.Plugin#destroyPlugin()
     */
    public void destroyPlugin() {
    }

}
