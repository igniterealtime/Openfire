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

package org.jivesoftware.openfire.plugin;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.plugin.emailListener.EmailListener;

import java.io.File;

/**
 * Plugin that connects to a mail server using IMAP and sends instant messages
 * to specified users when new email messages are found.
 *
 * @author Gaston Dombiak
 */
public class EmailListenerPlugin implements Plugin {

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        // Start the email listener process
        EmailListener.getInstance().start();
    }

    public void destroyPlugin() {
        // Stop the email listener process
        EmailListener.getInstance().stop();
    }
}
