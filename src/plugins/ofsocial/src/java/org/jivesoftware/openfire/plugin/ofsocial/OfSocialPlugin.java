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

package org.jivesoftware.openfire.plugin.ofsocial;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;

import org.ifsoft.websockets.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;


public class OfSocialPlugin implements Plugin, ClusterEventListener  {

    private static final Logger Log = LoggerFactory.getLogger(OfSocialPlugin.class);
    private final ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> sockets = new ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket>();

    public static OfSocialPlugin self;

    public String getName() {
        return "ofsocial";
    }

    public String getDescription() {
        return "OfSocial Plugin";
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {
		ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();

		self = this;

		try {

			ClusterManager.addListener(this);

			setupWordPress();

			Log.info("OfMeet Plugin - Initialize websockets ");

			// Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
			final List<ContainerInitializer> initializers = new ArrayList<>();

			ServletContextHandler context = new ServletContextHandler(contexts, "/ofsocialws", ServletContextHandler.SESSIONS);
			context.addServlet(new ServletHolder(new XMPPServlet()),"/server");
			initializers.add(new ContainerInitializer(new JettyJasperInitializer(), null));
			context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
			context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

			Log.info("OfSocial Plugin - Initialize PHP");

			// Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
			final List<ContainerInitializer> initializers2 = new ArrayList<>();
			initializers2.add(new ContainerInitializer(new JettyJasperInitializer(), null));

			WebAppContext context2 = new WebAppContext(contexts, pluginDirectory.getPath(), "/ofsocial");
			context2.setClassLoader(this.getClass().getClassLoader());
			context2.setAttribute("org.eclipse.jetty.containerInitializers", initializers2);
			context2.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
			context2.setWelcomeFiles(new String[]{"index.php"});

		} catch (Exception e) {
			Log.error("Could NOT start open fire meetings", e);
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

			cleanupWordPress();
        	ClusterManager.removeListener(this);

			sockets.clear();


        } catch (Exception e) {

        }
    }

	public ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> getSockets()
	{
		return sockets;
	}

	@Override
	public void joinedCluster()
	{
		Log.info("OfSocial Plugin - joinedCluster");
	}

	@Override
	public void joinedCluster(byte[] arg0)
	{


	}

	@Override
	public void leftCluster()
	{
		Log.info("OfSocial Plugin - leftCluster");
	}

	@Override
	public void leftCluster(byte[] arg0)
	{


	}

	@Override
	public void markedAsSeniorClusterMember()
	{
		Log.info("OfSocial Plugin - markedAsSeniorClusterMember");
	}

	private void setupWordPress()
	{
		Log.info("Setting WordPress as new auth Provider");

		JiveGlobals.setProperty("jdbcAuthProvider.passwordSQL", "SELECT user_pass FROM wp_users WHERE user_nicename=?");
		JiveGlobals.setProperty("jdbcAuthProvider.setPasswordSQL", "");
		JiveGlobals.setProperty("jdbcAuthProvider.allowUpdate", "false");
		JiveGlobals.setProperty("jdbcAuthProvider.passwordType", "md5");
		JiveGlobals.setProperty("jdbcAuthProvider.useConnectionProvider", "true");

		JiveGlobals.setProperty("provider.auth.className",  "org.jivesoftware.openfire.auth.JDBCAuthProvider");

		Log.info("Setting WordPress as user Provider");

		JiveGlobals.setProperty("jdbcUserProvider.loadUserSQL", "SELECT user_nicename, user_email FROM wp_users WHERE user_nicename=?");
		JiveGlobals.setProperty("jdbcUserProvider.userCountSQL", "SELECT COUNT(*) FROM wp_users");
		JiveGlobals.setProperty("jdbcUserProvider.allUsersSQL", "SELECT user_nicename FROM wp_users");
		JiveGlobals.setProperty("jdbcUserProvider.searchSQL", "SELECT user_nicename FROM wp_users WHERE");
		JiveGlobals.setProperty("jdbcUserProvider.user_loginField", "user_nicename");
		JiveGlobals.setProperty("jdbcUserProvider.nameField", "display_name");
		JiveGlobals.setProperty("jdbcUserProvider.emailField", "user_email");
		JiveGlobals.setProperty("jdbcUserProvider.useConnectionProvider", "true");

		JiveGlobals.setProperty("provider.user.className",  "org.jivesoftware.openfire.user.JDBCUserProvider");

		Log.info("Setting WordPress as group Provider");

		JiveGlobals.setProperty("jdbcGroupProvider.groupCountSQL", "SELECT count(*) FROM wp_bp_groups");
		JiveGlobals.setProperty("jdbcGroupProvider.allGroupsSQL", "SELECT name FROM wp_bp_groups");
		JiveGlobals.setProperty("jdbcGroupProvider.userGroupsSQL", "SELECT name FROM wp_bp_groups INNER JOIN wp_bp_groups_members ON wp_bp_groups.id = wp_bp_groups_members.group_id WHERE wp_bp_groups_members.user_id IN (SELECT ID FROM wp_users WHERE user_nicename=?) AND is_confirmed=1");
		JiveGlobals.setProperty("jdbcGroupProvider.descriptionSQL", "SELECT description FROM wp_bp_groups WHERE name=?");
		JiveGlobals.setProperty("jdbcGroupProvider.loadMembersSQL", "SELECT user_nicename FROM wp_users INNER JOIN wp_bp_groups_members ON wp_users.ID = wp_bp_groups_members.user_id WHERE wp_bp_groups_members.group_id IN (SELECT id FROM wp_bp_groups WHERE name=?) AND user_nicename<>'admin' AND is_confirmed=1");
		JiveGlobals.setProperty("jdbcGroupProvider.loadAdminsSQL", "SELECT user_nicename FROM wp_users INNER JOIN wp_bp_groups_members ON wp_users.ID = wp_bp_groups_members.user_id WHERE wp_bp_groups_members.group_id IN (SELECT id FROM wp_bp_groups WHERE name=?) AND user_nicename='admin' AND is_confirmed=1");
		JiveGlobals.setProperty("jdbcGroupProvider.useConnectionProvider", "true");

		JiveGlobals.setProperty("provider.group.className",  "org.jivesoftware.openfire.group.JDBCGroupProvider");

		JiveGlobals.setProperty("cache.groupMeta.maxLifetime", "60000");
		JiveGlobals.setProperty("cache.group.maxLifetime", "60000");
		JiveGlobals.setProperty("cache.userCache.maxLifetime", "60000");
	}

	private void cleanupWordPress()
	{
		Log.info("Cleanup WordPress as new auth Provider");

		JiveGlobals.deleteProperty("jdbcAuthProvider.passwordSQL");
		JiveGlobals.deleteProperty("jdbcAuthProvider.setPasswordSQL");
		JiveGlobals.deleteProperty("jdbcAuthProvider.allowUpdate");
		JiveGlobals.deleteProperty("jdbcAuthProvider.passwordType");
		JiveGlobals.deleteProperty("jdbcAuthProvider.useConnectionProvider");

		JiveGlobals.setProperty("provider.auth.className",  "org.jivesoftware.openfire.auth.DefaultAuthProvider");

		Log.info("Cleanup WordPress as user Provider");

		JiveGlobals.deleteProperty("jdbcUserProvider.loadUserSQL");
		JiveGlobals.deleteProperty("jdbcUserProvider.userCountSQL");
		JiveGlobals.deleteProperty("jdbcUserProvider.allUsersSQL");
		JiveGlobals.deleteProperty("jdbcUserProvider.searchSQL");
		JiveGlobals.deleteProperty("jdbcUserProvider.user_loginField");
		JiveGlobals.deleteProperty("jdbcUserProvider.nameField");
		JiveGlobals.deleteProperty("jdbcUserProvider.emailField");
		JiveGlobals.deleteProperty("jdbcUserProvider.useConnectionProvider");

		JiveGlobals.setProperty("provider.user.className",  "org.jivesoftware.openfire.user.DefaultUserProvider");

		Log.info("Cleanup WordPress as group Provider");

		JiveGlobals.deleteProperty("jdbcGroupProvider.groupCountSQL");
		JiveGlobals.deleteProperty("jdbcGroupProvider.allGroupsSQL");
		JiveGlobals.deleteProperty("jdbcGroupProvider.userGroupsSQL");
		JiveGlobals.deleteProperty("jdbcGroupProvider.descriptionSQL");
		JiveGlobals.deleteProperty("jdbcGroupProvider.loadMembersSQL");
		JiveGlobals.deleteProperty("jdbcGroupProvider.loadAdminsSQL");
		JiveGlobals.deleteProperty("jdbcGroupProvider.useConnectionProvider");

		JiveGlobals.setProperty("provider.group.className",  "org.jivesoftware.openfire.group.DefaultGroupProvider");

		JiveGlobals.deleteProperty("cache.groupMeta.maxLifetime");
		JiveGlobals.deleteProperty("cache.group.maxLifetime");
		JiveGlobals.deleteProperty("cache.userCache.maxLifetime");
	}
}
