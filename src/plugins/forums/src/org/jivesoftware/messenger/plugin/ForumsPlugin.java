package org.jivesoftware.messenger.plugin;

import org.jivesoftware.messenger.container.Plugin;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.auth.AuthFactory;
import org.jivesoftware.util.JiveGlobals;

import java.io.File;

/**
 * User: patrick
 * Date: Jul 21, 2005
 * Time: 3:38:41 PM
 */
public class ForumsPlugin implements Plugin {

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        JiveGlobals.setXMLProperty("provider.user.className", ForumsUserProvider.class.getName());
        JiveGlobals.setXMLProperty("provider.auth.className", ForumsUserProvider.class.getName());
    }

    public void destroyPlugin() {
    }
}
