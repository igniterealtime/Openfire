/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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
