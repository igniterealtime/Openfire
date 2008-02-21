/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
