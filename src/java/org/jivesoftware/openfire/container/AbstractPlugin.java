/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.container;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;

import java.io.File;

import org.xmpp.component.ComponentManager;

/**
 *
 */
public abstract class AbstractPlugin implements Plugin {
    protected ComponentManager componentManager;

    @Inject
    public void setComponentManager(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        initializePlugin();
    }

    public abstract void initializePlugin();

    public void destroyPlugin() {
    }
}
