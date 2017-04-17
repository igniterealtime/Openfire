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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.roster.*;
import org.jivesoftware.openfire.user.*;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.muc.spi.*;
import org.jivesoftware.openfire.forms.spi.*;
import org.jivesoftware.openfire.forms.*;
import org.jivesoftware.openfire.group.*;
import org.jivesoftware.openfire.event.GroupEventDispatcher;


import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.XMPPServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.Certificate;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;

import org.xmpp.packet.Packet;

import java.util.*;
import java.net.UnknownHostException;
import org.xmpp.packet.*;
import java.sql.*;
import java.io.File;
import org.dom4j.Element;

import javax.script.ScriptEngine;

import com.caucho.quercus.script.QuercusScriptEngineFactory;
import com.caucho.quercus.module.AbstractQuercusModule;


public class PHP2Java extends AbstractQuercusModule
{
	public ScriptEngine engine = (new QuercusScriptEngineFactory()).getScriptEngine();
    private static final Logger Log = LoggerFactory.getLogger(PHP2Java.class);


	public PHP2Java()
	{
/*
		try {
			String code = "<?php $foo = strlen('abc'); print $foo; return 'yikes'; ?>";
			Object o = engine.eval(code);
			System.out.println(o);
		} catch (Exception e) {
			Log.error("PHP2Java script error", e);
		}
*/
	}

	public String registerUser(String username)
	{
		try {
			ScriptEngine engine = (new QuercusScriptEngineFactory()).getScriptEngine();

			String code =
						"<?php																		" +
						"header('Access-Control-Allow-Origin: *');									" +
						"header('Access-Control-Allow-Methods: GET, POST');							" +
						"header('Access-Control-Allow-Headers: X-Requested-With');					" +
						"																			" +
						"require_once('wp-blog-header.php');										" +
						"require_once('wp-includes/registration.php');								" +
						"require_once('wp-includes/registration.php');								" +
						"require_once('wp-includes/pluggable.php');									" +
						"																			" +
						"$newusername = htmlspecialchars($_GET['username']);						" +
						"$newpassword = htmlspecialchars($_GET['password']);						" +
						"$newemail = htmlspecialchars($_GET['email']);								" +
						"$name = htmlspecialchars($_GET['name']);									" +
						"																			" +
						"if (!$newpassword) 	$newpassword = $newusername;						" +
						"if (!$name) 		$name = $newusername;									" +
						"if (!$newemail) 	$newemail = $newusername . '@traderlynk.net';			" +
						"																			" +
						"if (!username_exists($newusername) && !email_exists($newemail) )			" +
						"{																			" +
						"	$user_id = wp_create_user( $newusername, $newpassword, $newemail);		" +
						"																			" +
						"	if ( is_int($user_id) )													" +
						"	{																		" +
						"		$wp_user_object = new WP_User($user_id);							" +
						"		$wp_user_object->set_role('subscriber');							" +
						"																			" +
						"		wp_update_user( array( 'ID' => $user_id, 'display_name' => $name ));" +
						"																			" +
						"		echo 'ok';															" +
						"	}																		" +
						"	else {																	" +
						"		echo 'error';														" +
						"	}																		" +
						"}																			" +
						"else {																		" +
						"	echo 'nothing';															" +
						"}																			" +
						"?>																			" +
						"";

			Object o = engine.eval(code);
			System.out.println(o);
			return o.toString();
		} catch (Exception e) {
			Log.error("PHP2Java script error", e);
			return null;
		}

	}

	public String hello_test(String name)
	{
		return "Hello, " + name;
	}

	public void of_logInfo(String text)
	{
		Log.info(text);
	}

	public void of_logError(String text)
	{
		Log.error(text);
	}

	public void of_logDebug(String text)
	{
		Log.debug(text);
	}

	public void of_logWarn(String text)
	{
		Log.warn(text);
	}

	public synchronized void sendEmail(String toAddress, String subject, String body, String htmlBody)
	{
	   try {
		   String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());

		   Log.info( "sendEmail " + toAddress + " " + subject + "\n " + body + "\n " + htmlBody);

		   EmailService.getInstance().sendMessage(null, toAddress, "Inspired Social", "no_reply@" + domainName, subject, body, htmlBody);
	   }
	   catch (Exception e) {
		   Log.error(e.toString());
	   }

	}

	public String of_get_db_username()
	{
		return JiveGlobals.getXMLProperty("database.defaultProvider.username");
	}

	public String of_get_db_password()
	{
		return JiveGlobals.getXMLProperty("database.defaultProvider.password");
	}

	public String of_get_db_name()
	{
		String serverURL = JiveGlobals.getXMLProperty("database.defaultProvider.serverURL");
		String defaultName = "openfire";

		int pos = serverURL.indexOf("3306");

		if (pos > -1) defaultName = serverURL.substring(pos + 5);

		pos = defaultName.indexOf("?");

		if (pos > -1) defaultName = defaultName.substring(0, pos);

		return defaultName;
	}

	public void of_set_user_session(String username)
	{
		Log.debug( "of_set_user_session " + username);

		JID jid = new JID(username + "@" + JiveGlobals.getProperty("xmpp.domain") + "/" + username);

		LocalClientSession session = (LocalClientSession) SessionManager.getInstance().getSession(jid);

		if (session == null)
		{
			Log.info( "of_set_user_session not found session for " + username);

			UserManager userManager = XMPPServer.getInstance().getUserManager();

			try {
				Log.info( "of_set_user_session creating user session for " + username);
				userManager.getUser(username);

				AuthToken authToken = new AuthToken(username);
				session = SessionManager.getInstance().createClientSession( new DummyConnection(), new BasicStreamID("url" + System.currentTimeMillis() ) );
				session.setAuthToken(authToken, username);
			}
			catch (UserNotFoundException e) {
				Log.error("of_set_user_session - user not found " + username);
			}
		}
	}

	public synchronized void createGroupChat(String groupId)
	{
		String roomName = getSQLField("SELECT name FROM wp_bp_groups WHERE id='" + groupId + "'", "name");
		String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());

		Log.info( "createGroupChat " + groupId + " " + roomName);

		try
		{
			if (roomName != null)
			{
				createRoom(removeSpaces(roomName).toLowerCase());
			}

		} catch(Exception e) {

			Log.error("createGroupChat exception " + e);
		}
	}


	private String removeSpaces(String Name)
	{
		String NewName = "";
		for ( int i = 0; i < Name.length(); i++)
		{
			if (Name.charAt(i) != ' ' )
			{
				NewName = NewName + Name.charAt(i);
			}
		}
		return NewName;
	}

	public synchronized String getGroupChats(String userId)
	{
		//Log.info( "getGroupChats " + userId);

		String sql = "SELECT name FROM wp_bp_groups INNER JOIN wp_bp_groups_members ON wp_bp_groups.id = wp_bp_groups_members.group_id WHERE wp_bp_groups_members.user_id ='" + userId + "' AND is_confirmed=1";
		return getSQLGroupNames(sql);
	}

	public synchronized void joinLeaveGroup(String fromUserId, String groupId, String action)
	{
		String groupName = getSQLField("SELECT name FROM wp_bp_groups WHERE id='" + groupId + "'", "name");
		String fromUser = getUserIdByID(fromUserId);

		String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());

		if (groupName != null)
		{
			try
			{
				Group group = GroupManager.getInstance().getGroup(groupName, true);

				if (group != null)
				{
					if (fromUser != null)
					{
						Log.info( "joinLeaveGroup " + action + " " + fromUser + " " + groupName);

						Map<String, Object> params = new HashMap<String, Object>();
						params.put("member", fromUser+"@"+domainName);

						if ("leave".equals(action))
						{
							GroupEventDispatcher.dispatchEvent(group, GroupEventDispatcher.EventType.member_removed, params);

						} else  {
							GroupEventDispatcher.dispatchEvent(group, GroupEventDispatcher.EventType.member_added, params);
						}
					}
				}
			}
			catch(Exception e)
			{
				Log.error("joinGroup exception " + e);
				e.printStackTrace();
			}
		}
	}


	public synchronized void removeFriendship(String fromUserId, String toUserId)
	{
		String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());
		String fromUser = getUserIdByID(fromUserId);
		String toUser = getUserIdByID(toUserId);

		if (fromUser != null && toUser != null)
		{
			Log.info( "removeFriendship " + fromUser + " " + toUser);

			try
			{
				Roster roster = XMPPServer.getInstance().getRosterManager().getRoster(fromUser);

				if (roster != null) {
					RosterItem gwitem = roster.deleteRosterItem(new JID(toUser + "@" + domainName), false);

					if (gwitem != null)
					{
						Presence reply = new Presence();
						reply.setTo(new JID(toUser + "@" + domainName));
						reply.setFrom(new JID(fromUser + "@" + domainName));
						reply.setType(Presence.Type.unavailable);
						XMPPServer.getInstance().getPresenceRouter().route(reply);
					}
				}

				Roster roster2 = XMPPServer.getInstance().getRosterManager().getRoster(toUser);

				if (roster2 != null) {
					RosterItem gwitem = roster2.deleteRosterItem(new JID(fromUser + "@" + domainName), false);

					if (gwitem != null)
					{
						Presence reply = new Presence();
						reply.setTo(new JID(fromUser + "@" + domainName));
						reply.setFrom(new JID(toUser + "@" + domainName));
						reply.setType(Presence.Type.unavailable);
						XMPPServer.getInstance().getPresenceRouter().route(reply);
					}
				}
			}
			catch(Exception e)
			{
				Log.error("removeFriendship exception " + e);
				e.printStackTrace();
			}

		} else Log.warn("cannot delete friendship  " + fromUserId + " " + toUserId);
	}


	public synchronized void createFriendship(String fromUserId, String toUserId, String group)
	{
		String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());
		String fromUser = getUserIdByID(fromUserId);
		String toUser = getUserIdByID(toUserId);
		String Nickname = getUserNameByID(toUserId);
		String Nickname2 = getUserNameByID(fromUserId);

		if (fromUser != null && toUser != null && fromUser != toUser)
		{
			try
			{
				Roster roster = XMPPServer.getInstance().getRosterManager().getRoster(fromUser);

				if (roster != null)
				{
					RosterItem gwitem = roster.createRosterItem(new JID(toUser + "@" + domainName), true, true);

					if (gwitem != null)
					{
						Log.info( "createFriendship " + fromUser + " " + toUser + " " + Nickname);

						gwitem.setSubStatus(RosterItem.SUB_BOTH);
						gwitem.setAskStatus(RosterItem.ASK_NONE);
						gwitem.setNickname(Nickname);

						ArrayList<String> groups = new ArrayList<String>();
						groups.add(group);
						gwitem.setGroups((List<String>)groups);
						roster.updateRosterItem(gwitem);
						roster.broadcast(gwitem, true);

						Presence reply = new Presence();
						reply.setTo(new JID(fromUser + "@" + domainName));
						reply.setFrom(new JID(toUser + "@" + domainName));
						XMPPServer.getInstance().getPresenceRouter().route(reply);

					} else Log.warn("cannot create friendship  " + fromUser + " " + toUser + " " + Nickname);
				}

				Roster roster2 = XMPPServer.getInstance().getRosterManager().getRoster(toUser);

				if (roster2 != null)
				{
					RosterItem gwitem = roster2.createRosterItem(new JID(fromUser + "@" + domainName), true, true);

					if (gwitem != null)
					{
						Log.info( "createFriendship " + toUser + " " + fromUser + " " + Nickname2);

						gwitem.setSubStatus(RosterItem.SUB_BOTH);
						gwitem.setAskStatus(RosterItem.ASK_NONE);
						gwitem.setNickname(Nickname2);

						ArrayList<String> groups = new ArrayList<String>();
						groups.add(group);
						gwitem.setGroups((List<String>)groups);
						roster2.updateRosterItem(gwitem);
						roster2.broadcast(gwitem, true);

						Presence reply2 = new Presence();
						reply2.setTo(new JID(toUser + "@" + domainName));
						reply2.setFrom(new JID(fromUser + "@" + domainName));
						XMPPServer.getInstance().getPresenceRouter().route(reply2);

					} else Log.warn("cannot create friendship  " + toUser + " " + fromUser + " " + Nickname2);
				}

			}
			catch(Exception e)
			{
				Log.error("createFriendship exception " + e);
				e.printStackTrace();
			}

		} else Log.warn("cannot create friendship  " + fromUserId + " " + toUserId + " " + group);
	}

	public String getUserIdByID(String id)
	{
		return getUserByID(id, "user_login");
	}

	public String getUserNameByID(String id)
	{
		return getUserByID(id, "user_nicename");
	}

	private String getUserByID(String id, String field)
	{
		return getSQLField("SELECT " + field + " FROM wp_users WHERE ID='" + id + "'", field);
	}

	private String getSQLField(String sql, String field)
	{
		Connection con = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;
		String fieldValue = null;

		try {
			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			rs = psmt.executeQuery();

			if (rs.next()) {
				fieldValue = rs.getString(field);
			}

		} catch (SQLException e) {
			Log.error("getSQLField exception " + e);

		} finally {
			DbConnectionManager.closeConnection(rs, psmt, con);
		}

		return fieldValue;
	}

	private String getSQLGroupNames(String sql)
	{
		String field = "name";
		Connection con = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;
		String listValue = "";

		try {
			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			rs = psmt.executeQuery();
			boolean first = true;

			while (rs.next()) {

				String fieldValue = removeSpaces(rs.getString(field)).toLowerCase();
				createRoom(fieldValue);

				if (first)
				{
					listValue = "\"" + fieldValue + "\"";
					first = false;

				} else listValue = listValue + ", \"" + fieldValue + "\"";
			}

		} catch (Exception e) {
			Log.error("getSQLList exception " + e);

		} finally {
			DbConnectionManager.closeConnection(rs, psmt, con);
		}

		return listValue;
	}

	public String getOpenfireUsers()
	{
		String sql = "SELECT * FROM ofuser;";
		Connection con = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;
		String listValue = "";

		try {
			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			rs = psmt.executeQuery();
			boolean first = true;

			while (rs.next()) {

				String username = rs.getString("username");
				String name = rs.getString("name");
				String email = rs.getString("email");

				if (first)
				{
					listValue = username + "," + name + "," + email;
					first = false;

				} else listValue = listValue + "|" + username + "," + name + "," + email;
			}

		} catch (Exception e) {
			Log.error("getSQLList exception " + e);

		} finally {
			DbConnectionManager.closeConnection(rs, psmt, con);
		}

		return listValue;
	}

	public void messageOtherRoomMembers(String myName, String roomJID, String msg)
	{
		Log.info( "messageOtherRoomMembers " + roomJID);

		try
		{
			String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());
			String roomName = (new JID(roomJID)).getNode();

			if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").hasChatRoom(roomName))
			{
				MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(roomName);

				for (JID jid : room.getMembers())
				{
					Log.info( "messageOtherRoomMembers memember " + jid);

					String hisName = jid.getNode();

					if (hisName.equals(myName) == false)
					{

					}
				}

			}

		} catch (Exception e) {
			Log.error("messageOtherRoomMembers exception " + e);
		}
	}


	private void createRoom(String roomName)
	{
		//Log.info( "createRoom " + roomName);

		try
		{
			String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());

			if (XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").hasChatRoom(roomName) == false)
			{
				MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(roomName);

				if (room == null)
				{
					room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(roomName, new JID("admin@"+domainName));

					if (room != null)
					{
						configureRoom(room);
					}
				}
			}

		} catch (Exception e) {

			e.printStackTrace();
		}
	}


	private void configureRoom(MUCRoom room )
	{
		Log.info( "configureRoom " + room.getID());

		FormField field;
		XDataFormImpl dataForm = new XDataFormImpl(DataForm.TYPE_SUBMIT);

        field = new XFormFieldImpl("muc#roomconfig_roomdesc");
        field.setType(FormField.TYPE_TEXT_SINGLE);

		String desc = room.getDescription();
		desc = desc == null ? "" : desc;

		//int pos = desc.indexOf(":");

		//if (pos > 0)
		//	desc = desc.substring(pos + 1);


        //field.addValue(String.valueOf(room.getID() + 1000) + ":" + desc);

        field.addValue(desc);
        dataForm.addField(field);

        field = new XFormFieldImpl("muc#roomconfig_roomname");
        field.setType(FormField.TYPE_TEXT_SINGLE);
        field.addValue(room.getName());
        dataForm.addField(field);

		field = new XFormFieldImpl("FORM_TYPE");
		field.setType(FormField.TYPE_HIDDEN);
		field.addValue("http://jabber.org/protocol/muc#roomconfig");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_changesubject");
		field.addValue("1");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_maxusers");
		field.addValue("30");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_presencebroadcast");
		field.addValue("moderator");
		field.addValue("participant");
		field.addValue("visitor");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_publicroom");
		field.addValue("1");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_persistentroom");
		field.addValue("1");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_moderatedroom");
		field.addValue("0");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_membersonly");
		field.addValue("0");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_allowinvites");
		field.addValue("1");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_passwordprotectedroom");
		field.addValue("0");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_whois");
		field.addValue("moderator");
		dataForm.addField(field);

		field = new XFormFieldImpl("muc#roomconfig_enablelogging");
		field.addValue("1");
		dataForm.addField(field);

		field = new XFormFieldImpl("x-muc#roomconfig_canchangenick");
		field.addValue("1");
		dataForm.addField(field);

		field = new XFormFieldImpl("x-muc#roomconfig_registration");
		field.addValue("1");
		dataForm.addField(field);

		// Keep the existing list of admins
		field = new XFormFieldImpl("muc#roomconfig_roomadmins");
		for (JID jid : room.getAdmins()) {
			field.addValue(jid.toString());
		}
		dataForm.addField(field);

		String domainName = JiveGlobals.getProperty("xmpp.domain", XMPPServer.getInstance().getServerInfo().getHostname());
		field = new XFormFieldImpl("muc#roomconfig_roomowners");
		field.addValue("admin@"+domainName);
		dataForm.addField(field);

		// Create an IQ packet and set the dataform as the main fragment
		IQ iq = new IQ(IQ.Type.set);
		Element element = iq.setChildElement("query", "http://jabber.org/protocol/muc#owner");
		element.add(dataForm.asXMLElement());

		try
		{
			// Send the IQ packet that will modify the room's configuration
			room.getIQOwnerHandler().handleIQ(iq, room.getRole());

		} catch (Exception e) {
			Log.error("configureRoom exception " + e);
		}
	}

	public class DummyConnection extends VirtualConnection
	{
		public void closeVirtualConnection()
		{

		}
        public byte[] getAddress() throws UnknownHostException {
            return "0.0.0.0".getBytes();
        }

        public String getHostAddress() throws UnknownHostException {
            return "";
        }

        public String getHostName() throws UnknownHostException {
            return XMPPServer.getInstance().getServerInfo().getHostname();
        }

        public void systemShutdown() {

        }

        public void deliver(Packet packet) throws UnauthorizedException {

        }

        public void deliverRawText(String text) {

        }

		@Override public ConnectionConfiguration getConfiguration()
		{
			// TODO Here we run into an issue with the ConnectionConfiguration introduced in Openfire 4:
			//      it is not extensible in the sense that unforeseen connection types can be added.
			//      For now, null is returned, as this object is likely to be unused (its lifecycle is
			//      not managed by a ConnectionListener instance).
			return null;
		}
	}

	public class BasicStreamID implements StreamID {
		String id;

		public BasicStreamID(String id) {
			this.id = id;
		}

		public String getID() {
			return id;
		}

		public String toString() {
			return id;
		}

		public int hashCode() {
			return id.hashCode();
		}
	}
}