package org.jivesoftware.openfire.plugin;

import java.io.File;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteRosterPlugin implements Plugin {

	private static final Logger Log = LoggerFactory.getLogger(RemoteRosterPlugin.class);
	
    private static PluginManager pluginManager;

    public RemoteRosterPlugin() {
    }

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        pluginManager = manager;

    }

    public void destroyPlugin() {
    }


    public String getName() {
        return "remoteRoster";
        
    }
    public static PluginManager getPluginManager() {
        return pluginManager;
    }
}
