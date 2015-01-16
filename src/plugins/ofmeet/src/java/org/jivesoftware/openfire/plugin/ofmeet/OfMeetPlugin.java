/**
 * $Revision $
 * $Date $
 *
 * Copyright (C) 2005-2010 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.ofmeet;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

import org.xmpp.packet.*;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.muc.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.util.security.*;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.*;

import org.ifsoft.websockets.*;

import org.jitsi.videobridge.openfire.PluginImpl;
import org.jitsi.jigasi.openfire.JigasiPlugin;
import org.jitsi.jicofo.openfire.JicofoPlugin;

public class OfMeetPlugin implements Plugin, ClusterEventListener  {

    private static final Logger Log = LoggerFactory.getLogger(OfMeetPlugin.class);
    private final ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> sockets = new ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket>();
	private PluginImpl jitsiPlugin;
	private JigasiPlugin jigasiPlugin;
	private JicofoPlugin jicofoPlugin;
	private PluginManager manager;
	public File pluginDirectory;

	public String sipRegisterStatus = "";

    public String getName() {
        return "ofmeet";
    }

    public String getDescription() {
        return "OfMeet Plugin";
    }

	public PluginImpl getPlugin()
	{
		return jitsiPlugin;
	}

    public void initializePlugin(final PluginManager manager, final File pluginDirectory) {

		ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();

		this.manager = manager;
		this.pluginDirectory = pluginDirectory;

		try {
			Log.info("OfMeet Plugin - Initialize jitsi videobridge ");

			jitsiPlugin = new PluginImpl();
			jitsiPlugin.initializePlugin(manager, pluginDirectory);

			Log.info("OfMeet Plugin - Initialize SIP gateway ");

			jigasiPlugin = new JigasiPlugin();
			jigasiPlugin.initializePlugin(manager, pluginDirectory);

			Log.info("OfMeet Plugin - Initialize jitsi conference focus");

			UserManager userManager = XMPPServer.getInstance().getUserManager();
			String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
			String userName = "focus";
			String focusUserJid = userName + "@" + domain;

			try {
				userManager.getUser(userName);
			}
			catch (UserNotFoundException e) {

				Log.info("OfMeet Plugin - Setup focus user " + focusUserJid);

				String focusUserPassword = "focus-password-" + System.currentTimeMillis();

				try {
					userManager.createUser(userName, focusUserPassword, "Openfire Meetings Focus User", focusUserJid);

					JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.focus.user.jid", focusUserJid);
					JiveGlobals.setProperty("org.jitsi.videobridge.ofmeet.focus.user.password", focusUserPassword);

					MultiUserChatService mucService = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference");
					List<JID> allowedJIDs = new ArrayList<JID>();
					allowedJIDs.add(new JID(focusUserJid));
					mucService.addSysadmins(allowedJIDs);
				}
				catch (Exception e1) {

					Log.error("Could NOT create focus user", e1);
				}
			}

			new Timer().schedule(new TimerTask()
			{
				@Override public void run()
				{
     				jicofoPlugin = new JicofoPlugin();
					jicofoPlugin.initializePlugin(manager, pluginDirectory);
				}
			}, 5000);


			ClusterManager.addListener(this);

			Log.info("OfMeet Plugin - Initialize websockets ");
			ServletContextHandler context = new ServletContextHandler(contexts, "/ofmeetws", ServletContextHandler.SESSIONS);
			context.addServlet(new ServletHolder(new XMPPServlet()),"/server");

			WebAppContext context2 = new WebAppContext(contexts, pluginDirectory.getPath(), "/ofmeet");
			context2.setWelcomeFiles(new String[]{"index.html"});

			String securityEnabled = JiveGlobals.getProperty("ofmeet.security.enabled", "true");

			if ("true".equals(securityEnabled))
			{
				Log.info("OfMeet Plugin - Initialize security");
				context2.setSecurityHandler(basicAuth("ofmeet"));
			}

		} catch (Exception e) {
			Log.error("Could NOT start open fire meetings");
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

			jitsiPlugin.destroyPlugin();
			jigasiPlugin.destroyPlugin();
			jicofoPlugin.destroyPlugin();

        	ClusterManager.removeListener(this);

        } catch (Exception e) {

        }
    }

    private static final SecurityHandler basicAuth(String realm) {

    	OpenfireLoginService l = new OpenfireLoginService();
        l.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"ofmeet"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName(realm);
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }

	public ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> getSockets()
	{
		return sockets;
	}

	@Override
	public void joinedCluster()
	{
		Log.info("OfMeet Plugin - joinedCluster");
		jitsiPlugin.destroyPlugin();
		jigasiPlugin.destroyPlugin();
		jicofoPlugin.destroyPlugin();
	}

	@Override
	public void joinedCluster(byte[] arg0)
	{


	}

	@Override
	public void leftCluster()
	{
		Log.info("OfMeet Plugin - leftCluster");
		jitsiPlugin.initializePlugin(manager, pluginDirectory);
		jigasiPlugin.initializePlugin(manager, pluginDirectory);
		jicofoPlugin.initializePlugin(manager, pluginDirectory);
	}

	@Override
	public void leftCluster(byte[] arg0)
	{


	}

	@Override
	public void markedAsSeniorClusterMember()
	{
		Log.info("OfMeet Plugin - markedAsSeniorClusterMember");
		jitsiPlugin.initializePlugin(manager, pluginDirectory);
		jigasiPlugin.initializePlugin(manager, pluginDirectory);
		jicofoPlugin.initializePlugin(manager, pluginDirectory);
	}
}
