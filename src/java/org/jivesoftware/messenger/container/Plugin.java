/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import java.io.File;

/**
 * Plugin interface.
 *
 * @author Matt Tucker
 */
public interface Plugin {

    /**
     * Returns the name of this plugin.
     *
     * @return the plugin's name.
     */
    public String getName();

    /**
     * Returns the description of the plugin or <tt>null</tt> if there is no description.
     *
     * @return this plugin's description.
     */
    public String getDescription();

    /**
     * Returns the author of this plugin.
     *
     * @return the plugin's author.
     */
    public String getAuthor();

    /**
     * The plugin's version.
     *
     * @return the version of the plugin.
     */
    public String getVersion();

    /**
     * Initializes the plugin.
     *
     * @param manager the plugin manager.
     * @param pluginDirectory the directory where the plugin is located.
     */
    public void initialize(PluginManager manager, File pluginDirectory);

    /**
     * Destroys the plugin.
     */
    public void destroy();

}