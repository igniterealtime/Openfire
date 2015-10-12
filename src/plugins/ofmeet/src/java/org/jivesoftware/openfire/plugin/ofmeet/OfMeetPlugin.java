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
import java.text.*;
import java.util.regex.*;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.xmpp.packet.*;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.plugin.spark.*;
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
import org.jivesoftware.openfire.group.*;
import org.jivesoftware.openfire.security.SecurityAuditManager;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
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

import net.sf.json.*;


public class OfMeetPlugin implements Plugin, ClusterEventListener  {

    private static final Logger Log = LoggerFactory.getLogger(OfMeetPlugin.class);
    private final ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> sockets = new ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket>();
	private PluginImpl jitsiPlugin;
	private JigasiPlugin jigasiPlugin;
	private JicofoPlugin jicofoPlugin;
	private PluginManager manager;
	public File pluginDirectory;
    private TaskEngine taskEngine = TaskEngine.getInstance();
    private UserManager userManager = XMPPServer.getInstance().getUserManager();
    private ComponentManager componentManager;

    public static OfMeetPlugin self;

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

    public void initializePlugin(final PluginManager manager, final File pluginDirectory)
    {
        componentManager = ComponentManagerFactory.getComponentManager();
		ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();

		this.manager = manager;
		this.pluginDirectory = pluginDirectory;
		self = this;

		try {
			try {
				Log.info("OfMeet Plugin - Initialize jitsi videobridge ");

				jitsiPlugin = new PluginImpl();
				jitsiPlugin.initializePlugin(componentManager, manager, pluginDirectory);
			}
			catch (Exception e1) {
				Log.error("Could NOT Initialize jitsi videobridge", e1);
			}

			try {
				Log.info("OfMeet Plugin - Initialize SIP gateway ");

				jigasiPlugin = new JigasiPlugin();
				jigasiPlugin.initializePlugin(componentManager, manager, pluginDirectory);
			}
			catch (Exception e1) {
				Log.error("Could NOT Initialize jitsi videobridge", e1);
			}

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
					Log.info("OfMeet Plugin - Initialize jitsi conference focus");

     				jicofoPlugin = new JicofoPlugin();
					jicofoPlugin.initializePlugin(componentManager, manager, pluginDirectory);
				}
			}, 5000);

			try {

				boolean clientControl = XMPPServer.getInstance().getPluginManager().getPlugin("clientControl") != null || XMPPServer.getInstance().getPluginManager().getPlugin("clientcontrol") != null;

				if (clientControl)
				{
					new Timer().scheduleAtFixedRate(new TimerTask()
					{
						@Override public void run()
						{
							processMeetingPlanner();
						}

					}, 0,  900000);
				}

			} catch (Exception e) {

				Log.error("Meeting Planner Executor error", e);
			}

			ClusterManager.addListener(this);

			Log.info("OfMeet Plugin - Initialize websockets ");
			ServletContextHandler context = new ServletContextHandler(contexts, "/ofmeetws", ServletContextHandler.SESSIONS);
			context.addServlet(new ServletHolder(new XMPPServlet()),"/server");
			// Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
			final List<ContainerInitializer> initializers = new ArrayList<>();
			initializers.add(new ContainerInitializer(new JettyJasperInitializer(), null));
			context.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
			context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

			WebAppContext context2 = new WebAppContext(contexts, pluginDirectory.getPath(), "/ofmeet");
			context2.setClassLoader(this.getClass().getClassLoader());

			// Ensure the JSP engine is initialized correctly (in order to be able to cope with Tomcat/Jasper precompiled JSPs).
			final List<ContainerInitializer> initializers2 = new ArrayList<>();
			initializers2.add(new ContainerInitializer(new JettyJasperInitializer(), null));
			context2.setAttribute("org.eclipse.jetty.containerInitializers", initializers2);
			context2.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

			context2.setWelcomeFiles(new String[]{"index.html"});

			String securityEnabled = JiveGlobals.getProperty("ofmeet.security.enabled", "true");

			if ("true".equals(securityEnabled))
			{
				Log.info("OfMeet Plugin - Initialize security");
				context2.setSecurityHandler(basicAuth("ofmeet"));
			}

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
		jitsiPlugin.initializePlugin(componentManager, manager, pluginDirectory);
		jigasiPlugin.initializePlugin(componentManager, manager, pluginDirectory);
		jicofoPlugin.initializePlugin(componentManager, manager, pluginDirectory);
	}

	@Override
	public void leftCluster(byte[] arg0)
	{


	}

	@Override
	public void markedAsSeniorClusterMember()
	{
		Log.info("OfMeet Plugin - markedAsSeniorClusterMember");

		jitsiPlugin.initializePlugin(componentManager, manager, pluginDirectory);
		jigasiPlugin.initializePlugin(componentManager, manager, pluginDirectory);
		jicofoPlugin.initializePlugin(componentManager, manager, pluginDirectory);
	}

	public void processMeetingPlanner()
	{
		Log.debug("OfMeet Plugin - processMeetingPlanner");

		final Collection<Bookmark> bookmarks = BookmarkManager.getBookmarks();

		for (Bookmark bookmark : bookmarks)
		{
			String json = bookmark.getProperty("calendar");

			if (json != null)
			{
				bookmark.setProperty("lock", "true");

				JSONArray calendar = new JSONArray(json);
				boolean done = false;

				for(int i = 0; i < calendar.length(); i++)
				{
					JSONObject meeting = calendar.getJSONObject(i);

					boolean processed = meeting.getBoolean("processed");
					long startLong = meeting.getLong("startTime");

					Date rightNow = new Date(System.currentTimeMillis());
					Date actionDate = new Date(startLong + 300000);
					Date warnDate = new Date(startLong - 960000);

					Log.debug("OfMeet Plugin - scanning meeting now " + rightNow + " action " + actionDate + " warn " + warnDate + "\n" + meeting );

					if(rightNow.after(warnDate) && rightNow.before(actionDate))
					{
						for (String user : bookmark.getUsers())
						{
							processMeeting(meeting, user);
						}

						for (String groupName : bookmark.getGroups())
						{
							try {
								Group group = GroupManager.getInstance().getGroup(groupName);

								for (JID memberJID : group.getMembers())
								{
									processMeeting(meeting, memberJID.getNode());
								}

							} catch (GroupNotFoundException e) { }
						}

						meeting.put("processed", true);
						done = true;
					}
				}

				if (done)
				{
					json = calendar.toString();
					bookmark.setProperty("calendar", json);

					Log.debug("OfMeet Plugin - processed meeting\n" + json);
				}

				bookmark.setProperty("lock", "false");
			}
		}
	}

	private void processMeeting(JSONObject meeting, String username)
	{
		Log.info("OfMeet Plugin - processMeeting " + username + " " + meeting);

	   	try {
			User user = userManager.getUser(username);
			Date start = new Date(meeting.getLong("startTime"));
			Date end = new Date(meeting.getLong("startTime"));
			String name = user.getName();
			String email = user.getEmail();
			String description = meeting.getString("description");
			String title = meeting.getString("title");
			String room = meeting.getString("room");
			String videourl = "https://" + XMPPServer.getInstance().getServerInfo().getHostname() + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443") + "/ofmeet/?r=" + room;
			String audiourl = videourl + "&novideo=true";
			String template = JiveGlobals.getProperty("ofmeet.email.template", "Dear [name],\n\nYou have an online meeting from [start] to [end]\n\n[description]\n\nTo join, please click\n[videourl]\nFor audio only with no webcan, please click\n[audiourl]\n\nAdministrator - [domain]");

			HashMap variables = new HashMap<String, String>();

			if (email != null)
			{
				variables.put("name", name);
				variables.put("email", email);
				variables.put("start", start.toString());
				variables.put("end", end.toString());
				variables.put("description", description);
				variables.put("title", title);
				variables.put("room", room);
				variables.put("videourl", videourl);
				variables.put("audiourl", audiourl);
				variables.put("domain", XMPPServer.getInstance().getServerInfo().getXMPPDomain());

				sendEmail(name, email, title, replaceTokens(template, variables), null);
				SecurityAuditManager.getInstance().logEvent(user.getUsername(), "sent email - " + title, description);
			}
	   }
	   catch (Exception e) {
		   Log.error("processMeeting error", e);
	   }
	}

	private void sendEmail(String toName, String toAddress, String subject, String body, String htmlBody)
	{
	   try {
		   String fromAddress = "no_reply@" + JiveGlobals.getProperty("ofmeet.email.domain", XMPPServer.getInstance().getServerInfo().getXMPPDomain());
		   String fromName = JiveGlobals.getProperty("ofmeet.email.fromname", "Openfire Meetings");

		   Log.debug( "sendEmail " + toAddress + " " + subject + "\n " + body + "\n " + htmlBody);
		   EmailService.getInstance().sendMessage(toName, toAddress, fromName, fromAddress, subject, body, htmlBody);
	   }
	   catch (Exception e) {
		   Log.error(e.toString());
	   }

	}

	private String replaceTokens(String text, Map<String, String> replacements)
	{
		Pattern pattern = Pattern.compile("\\[(.+?)\\]");
		Matcher matcher = pattern.matcher(text);
		StringBuffer buffer = new StringBuffer();

		while (matcher.find())
		{
			String replacement = replacements.get(matcher.group(1));

			if (replacement != null)
			{
				matcher.appendReplacement(buffer, "");
				buffer.append(replacement);
			}
		}
		matcher.appendTail(buffer);
		return buffer.toString();
	}
}
