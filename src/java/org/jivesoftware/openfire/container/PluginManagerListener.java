/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.container;

/**
 * Interface to listen for plugin manager events. Use the
 * {@link PluginManager#addPluginManagerListener(PluginManagerListener)}
 * method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface PluginManagerListener {

    /**
     * Event indicating that the PluginManager has finished an attemp to load new plugins and unload
     * old plugins.
     */
    void pluginsMonitored();
}
