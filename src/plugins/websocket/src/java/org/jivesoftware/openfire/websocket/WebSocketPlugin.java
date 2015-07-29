/**
 * Copyright (C) 2015 Tom Evans. All rights reserved.
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
package org.jivesoftware.openfire.websocket;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.common.extensions.compress.PerMessageDeflateExtension;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This plugin enables XMPP over WebSocket (RFC 7395) for Openfire.
 * 
 * The Jetty WebSocketServlet serves as a base class and enables easy integration into the
 * BOSH (http-bind) web context. Each WebSocket request received at the "/ws/" URI will be
 * forwarded to this plugin/servlet, which will in turn create a new {@link XmppWebSocket}
 * for each new connection. 
 */
public class WebSocketPlugin extends WebSocketServlet implements Plugin  {

	private static final long serialVersionUID = 7281841492829464603L;
	private static final Logger Log = LoggerFactory.getLogger(WebSocketPlugin.class);

	private ServletContextHandler contextHandler;
	protected PluginClassLoader pluginClassLoader = null;
	

	@Override 
    public void initializePlugin(final PluginManager manager, final File pluginDirectory) {

        if (Boolean.valueOf(JiveGlobals.getBooleanProperty(HttpBindManager.HTTP_BIND_ENABLED, true))) {
            Log.info(String.format("Initializing websocket plugin"));

            try {
    			ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();
    			contextHandler = new ServletContextHandler(contexts, "/ws", ServletContextHandler.SESSIONS);
    			contextHandler.addServlet(new ServletHolder(this), "/*");

    		} catch (Exception e) {
    			Log.error("Failed to start websocket plugin", e);
    		}
        } else {
        	Log.warn("Failed to start websocket plugin; http-bind is disabled");
        }

    }

	@Override 
    public void destroyPlugin() {
    	// terminate any active websocket sessions
    	SessionManager sm = XMPPServer.getInstance().getSessionManager();
    	for (ClientSession session : sm.getSessions()) {
    		if (session instanceof LocalSession) {
    			Object ws = ((LocalSession) session).getSessionData("ws");
    			if (ws != null && (Boolean) ws) {
    				session.close();
    			}
    		}
    	}
		ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();
		contexts.removeHandler(contextHandler);
		contextHandler = null;
		pluginClassLoader = null;
    }

	@Override 
	public void configure(WebSocketServletFactory factory)
	{
		if (XmppWebSocket.isCompressionEnabled()) {
			factory.getExtensionFactory().register("permessage-deflate", PerMessageDeflateExtension.class);
		}
		factory.setCreator(new WebSocketCreator() {
			@Override 
			public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
			{
	        	ClassLoader ccl = Thread.currentThread().getContextClassLoader();
	            try {
	            	ClassLoader pcl = getPluginClassLoader();
	    	    	Thread.currentThread().setContextClassLoader(pcl == null ? ccl : pcl);
					for (String subprotocol : req.getSubProtocols())
					{
						if ("xmpp".equals(subprotocol))
						{
							resp.setAcceptedSubProtocol(subprotocol);
							return new XmppWebSocket();
						}
					}
	            } catch (Exception e) {
	                Log.warn(MessageFormat.format("Unable to load websocket factory: {0} ({1})", e.getClass().getName(), e.getMessage()));
	            } finally {
	    			Thread.currentThread().setContextClassLoader(ccl);
	            }
				Log.warn("Failed to create websocket: " + req);
				return null;
			}
		});
	}

	protected synchronized PluginClassLoader getPluginClassLoader() {
		PluginManager pm = XMPPServer.getInstance().getPluginManager();
		if (pluginClassLoader == null) {
    		pluginClassLoader = pm.getPluginClassloader(this);
		}
		// report error if plugin is unavailable 
		if (pluginClassLoader == null) {
			Log.error("Unable to find class loader for websocket plugin");
		}
		return pluginClassLoader;
	}

}
