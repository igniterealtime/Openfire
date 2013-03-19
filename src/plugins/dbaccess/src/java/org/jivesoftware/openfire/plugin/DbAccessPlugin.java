/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Daniel Henninger. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

import java.io.File;

/**
 * This is a stub for now, really does nothing for the plugin what-so-ever.
 * 
 * @author Daniel Henninger
 */
public class DbAccessPlugin implements Plugin {

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        // Nothing to do
    }

    public void destroyPlugin() {
        // Nothing to do
    }

}
