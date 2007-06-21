/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.container.plugin;

import com.google.inject.Inject;

import java.io.File;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Callable;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

import org.jivesoftware.util.Logger;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.PropertyEventDispatcher;

import org.xmpp.component.ComponentManager;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.jivesoftware.openfire.component.ComponentLifecycle;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;

/**
 *
 */
public abstract class AbstractPlugin implements Plugin {

    private final List<FutureTask> destroyTasks = new ArrayList<FutureTask>();

    protected ComponentManager componentManager;
    protected Logger log;

    @Inject
    public void setComponentManager(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    @Inject
    public void setLog(Logger log) {
        this.log = log;
    }

    protected ComponentLifecycle addComponent(String domain, Component component)
            throws ComponentException
    {
        return addComponent(domain, component, null);
    }

    protected ComponentLifecycle addComponent(final String domain, Component component,
                                              String jiveProperty) throws ComponentException
    {
        ComponentLifecycle componentLifecycle
                = componentManager.addComponent(domain, component, jiveProperty);
        destroyTasks.add(new FutureTask<Boolean>(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                componentManager.removeComponent(domain);
                return true;
            }
        }));
        return componentLifecycle;
    }

    protected void addPropertyEventListener(final PropertyEventListener listener) {
        PropertyEventDispatcher.addListener(listener);
        destroyTasks.add(new FutureTask<Boolean>(new Runnable() {
            public void run() {
                PropertyEventDispatcher.removeListener(listener);
            }
        }, Boolean.TRUE));
    }

    public final void initializePlugin(PluginManager manager, File pluginDirectory) {
        initialize();
    }

    public abstract void initialize();

    public final void destroyPlugin() {
        destroy();
        for(FutureTask destroyTask : destroyTasks) {
            try {
            destroyTask.run();
            }
            catch(Exception ex) {
                log.error("Error unloading plugin", ex);
            }
        }
    }

    public void destroy() {

    }
}
