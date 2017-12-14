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
package org.jivesoftware.openfire.container;

/**
 * Allows for notifications that a plugin has been either created or destroyed.
 *
 * @author Alexander Wenckus
 */
public interface PluginListener {

    /**
     * Called when a plugin has been created.
     *
     * @param pluginName the name of the created plugin.
     * @param plugin the plugin that was created.
     */
    void pluginCreated(String pluginName, Plugin plugin);

    /**
     * Called when a plugin has been destroyed.
     *
     * @param pluginName the name of the destroyed plugin.
     * @param plugin the plugin that was destroyed.
     */
    void pluginDestroyed(String pluginName, Plugin plugin);
}
