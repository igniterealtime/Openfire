/**
 * $RCSfile: $
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.SessionManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import com.ifsoft.websockets.servlet.*;

/**
 *
 */
public class StompPlugin implements Plugin, PropertyEventListener {

	private static Logger Log = LoggerFactory.getLogger("StompPlugin");

	private PluginManager manager;
    private File pluginDirectory;

    private ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> sockets = new ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket>();


    public void initializePlugin(PluginManager manager, File pluginDirectory)
    {
        if (JiveGlobals.getBooleanProperty("plugin.stomp.enabled", true))
        {
			try {
				Log.info(" initialize stomp websockets");

				ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();
				ServletContextHandler context = new ServletContextHandler(contexts, "/stomp", ServletContextHandler.SESSIONS);
				context.addServlet(new ServletHolder(new XMPPServlet()),"/server");
			}
			catch (Exception e) {
				Log.error("Error initializing WebSockets Plugin", e);
			}
        }
    }

    public void destroyPlugin() {

		try {

			for (XMPPServlet.XMPPWebSocket socket : sockets.values())
			{
				try {
					LocalClientSession session = socket.getSession();
					session.close();
					SessionManager.getInstance().removeSession( session );
					session = null;

				} catch ( Exception e ) { }
			}

			sockets.clear();

		}
		catch (Exception e) {
			Log.error(" destroyPlugin exception ", e);
		}
    }

    public void propertySet(String property, Map<String, Object> params)
    {

    }

    public void propertyDeleted(String property, Map<String, Object> params)
    {
        if (property.equals("plugin.stomp.enabled")) {

        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }

	public ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> getSockets() {
		return sockets;
	}
}
