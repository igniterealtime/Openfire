/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jigasi.openfire;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.*;
import java.security.cert.Certificate;

import javax.media.*;
import javax.media.protocol.*;
import javax.media.format.*;

import org.jivesoftware.util.*;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.database.DbConnectionManager;
import java.sql.*;

import org.slf4j.*;
import org.slf4j.Logger;

import net.java.sip.communicator.impl.protocol.jabber.extensions.rayo.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.colibri.*;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.*;
import net.java.sip.communicator.util.*;

import net.sf.fmj.media.rtp.*;

import org.dom4j.*;
import org.jitsi.jigasi.*;
import org.osgi.framework.*;

import org.xmpp.component.*;
import org.xmpp.packet.*;

import org.jitsi.jigasi.xmpp.*;
import org.jitsi.videobridge.xmpp.*;
import org.jitsi.videobridge.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.conference.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;
import org.jitsi.videobridge.openfire.PluginImpl;

import org.ifsoft.*;
import org.ifsoft.sip.*;
import net.sf.fmj.media.rtp.*;
import org.ifsoft.rtp.*;
import uk.nominet.DDDS.*;


public class CallControlComponent extends AbstractComponent
{
    private static final Logger Log = LoggerFactory.getLogger(JigasiPlugin.class);
	public ConcurrentHashMap<String, CallSession> callSessions = new ConcurrentHashMap<String, CallSession>();
	public ConcurrentHashMap<String, String> conferences = new ConcurrentHashMap<String, String>();
	public ConcurrentHashMap<String, String> registrations = new ConcurrentHashMap<String, String>();

	public static CallControlComponent self;
	private SipService sipService = null;
	private MultiUserChatManager mucManager = XMPPServer.getInstance().getMultiUserChatManager();
	private String hostName;

	private Videobridge getVideobridge()
	{
		return PluginImpl.component.getVideobridge();
	}

	public CallControlComponent(File pluginDirectory)
	{
		Log.info("CallControlComponent " + pluginDirectory);
		self = this;

		Properties properties = new Properties();
		hostName = JiveGlobals.getProperty("org.jitsi.videobridge.nat.harvester.public.address", XMPPServer.getInstance().getServerInfo().getHostname());

		try {
			hostName = InetAddress.getByName(hostName).getHostAddress();
		} catch (Exception e) {

		}

		String logDir = pluginDirectory.getAbsolutePath() + File.separator + ".." + File.separator + ".." + File.separator + "logs" + File.separator;
		String port = JiveGlobals.getProperty("org.jitsi.videobridge.sip.port.number", "5060");

		properties.setProperty("com.voxbone.kelpie.hostname", hostName);
		properties.setProperty("com.voxbone.kelpie.ip", hostName);
		properties.setProperty("com.voxbone.kelpie.sip_port", port);

		properties.setProperty("javax.sip.IP_ADDRESS", hostName);

		properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "99");
		properties.setProperty("gov.nist.javax.sip.SERVER_LOG", logDir + "sip_server.log");
		properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", logDir + "sip_debug.log");

		if (JiveGlobals.getBooleanProperty("org.jitsi.videobridge.ofmeet.sip.enabled", false))
		{
			Log.info("CallControlComponent - enabling SIP gateway ");

			sipService = new SipService(properties);

		} else {
			Log.info("CallControlComponent - disabling SIP gateway");
		}
	}

	@Override protected String[] discoInfoFeatureNamespaces()
	{
		return
			new String[]
				{
					"http://jitsi.org/protocol/jigasi",
					"urn:xmpp:rayo:0"
				};
	}

	@Override public String getDescription()
	{
		return "Call control component";
	}

	@Override public String getName()
	{
		return "Call control";
	}

	public void stop()
	{
		for (CallSession callSession : callSessions.values())
		{
			callSession.mediaStream.stop();
			callSession.mediaStream.close();
		}

		callSessions.clear();

		if (sipService != null) sipService.stop();
	}

	public void recordCall(Conference conference, String token, String state)
	{
		String focusJid = conference.getFocus();
		String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

		Log.info("CallControlComponent - recordCall " + token + " " + state + " " + focusJid);

		IQ iq = new IQ(IQ.Type.set);
		iq.setTo("ofmeet-jitsi-videobridge."+domain);
		iq.setFrom(focusJid);

		Element colibri = iq.setChildElement("conference", "http://jitsi.org/protocol/colibri");
		colibri.addAttribute("id", conference.getID());
		colibri.addElement("recording").addAttribute("state", state).addAttribute("token", token);

		sendPacket(iq);
	}

	private void makeCall(Conference conference, String confJid, String to, String callId, String username, long startTimestamp)
	{
		Log.info("CallControlComponent - makeCall " + confJid + " " + to + " " + callId);

		try {
			String callerId = (new JID(confJid)).getNode();
			String focusJid = conference.getFocus();

			CallSession cs = new CallSession(conference, hostName, this, callId, focusJid, confJid);
			callSessions.put(callId, cs);

			boolean toSip = to.indexOf("sip:") == 0 ;
			boolean toPhone = to.indexOf("tel:") == 0;
			boolean toMulticast = to.indexOf("mrtp:") == 0;

			String from = "sip:" + callerId + "@" + hostName;

			if (!toSip && !toPhone && !toMulticast)
			{
				String sipUri = null;

				if (to.length() == 8)
				{
					toSip = true;
					to = "sip:8835100" + to + "@81.201.82.25";

				} else {

					if (to.indexOf("+") != 0) to = "+" + to;

					Log.info("CallControlComponent - makeCall looking up "  + to);

					ENUM mEnum = new ENUM("e164.arpa");
					Rule[] rules = mEnum.lookup(to);

					for (Rule rule: rules)
					{
						String temp = rule.evaluate();
						Log.info("CallControlComponent - makeCall found "  + temp);
						if (temp.indexOf("sip:") == 0) sipUri = temp;
					}

					if (sipUri != null)
					{
						toSip = true;
						to = sipUri;

					} else {
						to = "tel:" + to;
						toPhone = true;
					}
				}
			}

			if (toSip)
			{
				from = "sip:" + callerId + "@" + hostName;

				Log.info("CallControlComponent - makeCall with direct sip "  + to + " " + from);

			} else if (toPhone) {

				to = to.substring(4);

				if (registrations.containsKey(to))
				{
					to = registrations.get(to);
					from = "sip:" + callerId + "@" + hostName;

					Log.info("CallControlComponent - makeCall with registration "  + to + " " + from);

				} else {

					String outboundProxy = JiveGlobals.getProperty("voicebridge.default.proxy.outboundproxy", null);
					String sipUsername = JiveGlobals.getProperty("voicebridge.default.proxy.sipauthuser", null);

					if (outboundProxy != null && sipUsername != null && !"".equals(outboundProxy.trim()) && !"".equals(sipUsername.trim()))
					{
						to = "sip:" + to + "@" + outboundProxy;
						from = "sip:" + sipUsername + "@" + outboundProxy;

						Log.info("CallControlComponent - makeCall with outbound proxy "  + to + " " + from);

					} else {
						Log.error("SIP proxy not setup with voicebridge.default.proxy.outboundproxy and voicebridge.default.proxy.sipauthuser");
						return;
					}
				}

			} else if (toMulticast) {
				String params[] = to.split(":");

				InetAddress remoteAddr = InetAddress.getByName(params[1]);
				cs.mediaStream.setTarget(new MediaStreamTarget(new InetSocketAddress(remoteAddr, Integer.parseInt(params[2])), null));
				cs.mediaStream.setDirection(MediaDirection.SENDONLY);
				cs.mediaStream.start();
			}


			cs.jabberLocal = from;
			cs.username = username;
			cs.startTimestamp = startTimestamp;

			if (!toMulticast)
			{
				cs.jabberRemote = to;
				SipService.sendInvite(cs);
			} else {
				cs.jabberRemote = "multicast";
				inviteEvent(true, callId);
			}

		} catch (Exception e) {

			Log.error("CallControlComponent makeCall", e);
		}
	}

	public void hangupCall(String callId)
	{
		Log.info("hangupCall " + callId);

		CallSession cs = callSessions.get(callId);

		if (cs != null)
		{
			if (cs.jabberRemote != null && cs.jabberRemote.startsWith("multicast"))
			{
				cs.sendBye();

			} else {
				SipService.sendBye(cs);
			}

			updateCallRecord(cs.startTimestamp, (int)(System.currentTimeMillis() - cs.startTimestamp));

		} else {
			Log.error("CallControlComponent hangup. cannot fine callid " + callId);
		}
	}

	public CallSession findCreateSession(String from, String to, String destination)
	{
		Log.info("CallControlComponent - findCreateSession " + from + " " + to + " " + destination);

		CallSession session = null;
		String callerId = to;
		String confJID = null;
		Conference conference =  null;

		boolean allowDirectSIP = "true".equals(JiveGlobals.getProperty("org.jitsi.videobridge.ofmeet.allow.direct.sip", "false"));

		if (!allowDirectSIP && !registrations.containsKey(from))
		{
			Log.warn("CallControlComponent - call rejected from " + from + " " + to);
			return null;	// only accept calls from registered SIP user endpoint
		}

		if (callerId.indexOf("+") == 0) callerId = callerId.substring(1);

		for (MultiUserChatService service : mucManager.getMultiUserChatServices())
		{
			for (MUCRoom room : service.getChatRooms())
			{
				if (!room.isPasswordProtected() && (room.getDescription().indexOf(callerId) > -1 || room.getJID().getNode().equals(callerId)))
				{
					confJID = room.getJID().toString();					// description has telephone no or room name is sip url target
					break;												// and not password protected
				}
			}

			if (confJID != null) break;
		}

		Log.info("CallControlComponent - findCreateSession conference looking for id " + confJID);

		if (confJID != null && conferences.containsKey(confJID))
		{
			String confId = conferences.get(confJID);

			Log.info("CallControlComponent - findCreateSession conference id " + confJID + " " + confId);

			for (Conference conf : getVideobridge().getConferences())
			{
				if (conf.getID().equals(confId))
				{
					conference = conf;
					break;
				}
			}
		}

		if (conference != null)
		{
			Log.info("CallControlComponent - findCreateSession conference " + conference.getFocus());

			try
			{
				String callId = Long.toHexString(System.currentTimeMillis());
				session = new CallSession(conference, hostName, this, callId, conference.getFocus(), confJID);

				session.jabberRemote = from;
				session.jabberLocal = to;

				callSessions.put(callId, session);

			}
			catch (Exception e)
			{
				Log.error("CallControlComponent findCreateSession", e);
			}
		} else {
			Log.warn("conferennce not found " + confJID);
		}

		if (session != null)
		{
			long startTimestamp = System.currentTimeMillis();
			session.startTimestamp = startTimestamp;
			createCallRecord("admin", from, confJID, startTimestamp, 0, "received");
		}
		return session;
	}

	public void inviteEvent(boolean accepted, String callId)
	{
		Log.info("CallControlComponent - inviteEvent " + accepted + " " + callId);

		if (callSessions.containsKey(callId))
		{
			CallSession session = callSessions.get(callId);

			try {
				JID confJID = new JID(session.roomJID);
				String conferenceId = confJID.getNode();

				MultiUserChatService service = mucManager.getMultiUserChatService(confJID);

				if (service != null)
				{
					if (service.hasChatRoom(conferenceId))
					{
						MUCRoom room = service.getChatRoom(conferenceId);

						if (room != null)
						{
							for (MUCRole role : room.getOccupants())
							{
								Presence presence = new Presence();
								presence.setFrom(callId + "@" + getJID());
								presence.setTo(role.getUserAddress());

								if (accepted)
								{
									Element answered = presence.addChildElement("answered", "urn:xmpp:rayo:1");
									answered.addElement("header").addAttribute("name", "caller_id").addAttribute("value", session.jabberRemote);
									answered.addElement("header").addAttribute("name", "called_id").addAttribute("value", session.jabberLocal);

								} else {
									Element hangup = presence.addChildElement("hangup", "urn:xmpp:rayo:1");
									hangup.addElement("header").addAttribute("name", "caller_id").addAttribute("value", session.jabberRemote);
									hangup.addElement("header").addAttribute("name", "called_id").addAttribute("value", session.jabberLocal);

									callSessions.remove(callId);
								}

								sendPacket(presence);
							}

							return;
						}
					}
				}

				// no valid muc service or room. send events to requestor

				String username = (new JID(session.focusJID)).getNode();

				Collection<ClientSession> sessions = SessionManager.getInstance().getSessions(username);

				for (ClientSession clientSession : sessions)
				{
					Presence presence = new Presence();
					presence.setFrom(callId + "@" + getJID());
					presence.setTo(clientSession.getAddress());

					if (accepted)
					{
						Element answered = presence.addChildElement("answered", "urn:xmpp:rayo:1");
						answered.addElement("header").addAttribute("name", "caller_id").addAttribute("value", session.jabberRemote);
						answered.addElement("header").addAttribute("name", "called_id").addAttribute("value", session.jabberLocal);

					} else {
						Element hangup = presence.addChildElement("hangup", "urn:xmpp:rayo:1");
						hangup.addElement("header").addAttribute("name", "caller_id").addAttribute("value", session.jabberRemote);
						hangup.addElement("header").addAttribute("name", "called_id").addAttribute("value", session.jabberLocal);

						CallSession cs = callSessions.remove(callId);

						if (cs != null)
						{
							updateCallRecord(cs.startTimestamp, (int)(System.currentTimeMillis() - cs.startTimestamp));
						}
					}

					sendPacket(presence);
				}

			} catch (Exception e) {
				Log.error("CallControlComponent inviteEvent. error" + session.roomJID, e);
			}

			if (!accepted) callSessions.remove(callId);

		} else {
			Log.error("CallControlComponent inviteEvent. cannot find callid " + callId);
		}
	}

	@Override public IQ handleIQSet(IQ iq)	throws Exception
	{
		IQ reply = IQ.createResultIQ(iq);
		String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();

		try
		{
			Log.info("CallControlComponent - handleIQSet\n" + iq);

			Element element = iq.getChildElement();
			String namespace = element.getNamespaceURI();
			String request = element.getName();

			String confJid = null;
			String confId = null;

			if ("dial".equals(request) && "urn:xmpp:rayo:1".equals(namespace))
			{
				Log.info("CallControlComponent - Dial");

				String from = element.attributeValue("from");
				String to = element.attributeValue("to");

				for ( Iterator i = element.elementIterator( "header" ); i.hasNext(); )
				{
					Element header = (Element) i.next();
					String name = header.attributeValue("name");
					String value = header.attributeValue("value");

					if ("JvbRoomId".equals(name)) confId = value;
					if ("JvbRoomName".equals(name)) confJid = value;
				}

				if (confJid == null && confId == null)
				{
					reply.setError(PacketError.Condition.item_not_found);
					Log.error("No JvbRoomName or JvbRoomId header found");

				} else {

					if (confId == null)
					{
						if (conferences.containsKey(confJid))
						{
							confId = conferences.get(confJid);
						}
					}

					if (confJid == null)
					{
						confJid = confId + "@conference." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
					}

					if (confId != null)
					{
						String callId = Long.toHexString(System.currentTimeMillis());

						Log.info("Got dial request " + from + " -> " + to + " confId " + confId + " callId " + callId);

						String callResource = "xmpp:" + callId + "@" + getJID();

						final Element childElement = reply.setChildElement("ref", "urn:xmpp:rayo:1");
						childElement.addAttribute("uri", (String) "xmpp:" + callId + "@" + getJID());
						childElement.addAttribute("id", (String)  callId);

						Conference conference = null;

						for (Conference conf : getVideobridge().getConferences())
						{
							if (conf.getID().equals(confId))
							{
								conference = conf;
								break;
							}
						}

						if (conference != null)
						{
							String username = iq.getFrom().getNode();
							long startTimestamp = System.currentTimeMillis();

							makeCall(conference, confJid, to, callId, username, startTimestamp);
							createCallRecord(username, confJid, to, startTimestamp, 0, "dialed");

						} else {
							Log.error("CallControlComponent - can't find conference " + confId);
							reply.setError(PacketError.Condition.item_not_found);
						}

					} else {
						Log.error("CallControlComponent - focus not ready " + confJid);
						reply.setError(PacketError.Condition.item_not_found);
					}
				}
			}

			else if ("accept".equals(request) && "urn:xmpp:rayo:1".equals(namespace))
			{
				Log.info("CallControlComponent - Accept");

				String confName = null;

				for ( Iterator i = element.elementIterator( "header" ); i.hasNext(); )
				{
					Element header = (Element) i.next();
					String name = header.attributeValue("name");
					String value = header.attributeValue("value");

					if ("JvbRoomId".equals(name)) confId = value;
					if ("JvbRoomName".equals(name)) confName = value;
				}

				if (confId != null && confName != null)
				{
					Log.info("CallControlComponent - Accept register " + confId + " " + confName);
					conferences.put(confName, confId);

				} else {
					reply.setError(PacketError.Condition.item_not_found);
					Log.error("No JvbRoomName or JvbRoomId header found");
				}
			}

			else if ("hangup".equals(request) && "urn:xmpp:rayo:1".equals(namespace))
			{
				Log.info("CallControlComponent - HangUp");
				String callId = iq.getTo().getNode();
				hangupCall(callId);
			}

			else if ("record".equals(request) && "urn:xmpp:rayo:record:1".equals(namespace))
			{
				Log.info("CallControlComponent - Record");

				String token = null;
				String state = null;
				String confName = null;

				for ( Iterator i = element.elementIterator( "hint" ); i.hasNext(); )
				{
					Element hint = (Element) i.next();
					String name = hint.attributeValue("name");
					String value = hint.attributeValue("value");

					if ("JvbToken".equals(name)) token = value;
					if ("JvbState".equals(name)) state = value;
					if ("JvbRoomName".equals(name)) confName = value;
				}

				if (token != null && state != null && confName != null)
				{
					if (conferences.containsKey(confName))
					{
						confId = conferences.get(confName);

						Conference conference = null;

						for (Conference conf : getVideobridge().getConferences())
						{
							if (conf.getID().equals(confId))
							{
								conference = conf;
								break;
							}
						}

						if (conference != null)
						{
							recordCall(conference, token, state);

						} else {
							Log.error("CallControlComponent - can't find conference " + confId);
							reply.setError(PacketError.Condition.item_not_found);
						}
					} else {
						Log.error("CallControlComponent - focus not ready " + confName);
						reply.setError(PacketError.Condition.item_not_found);
					}
				} else {
					reply.setError(PacketError.Condition.item_not_found);
					Log.error("No JvbRoomName, JvbToken or JvbState headers found");
				}
			}

			else
			{
				Log.warn("CallControlComponent - Unknown");
				reply.setError(PacketError.Condition.item_not_found);
			}
		}
		catch (Exception e)
		{
			Log.error("CallControlComponent handleIQSet", e);
			reply.setError(PacketError.Condition.internal_server_error);
		}

		return reply;
	}

	private void sendPacket(Packet packet)
	{
		try {
			ComponentManagerFactory.getComponentManager().sendPacket(this, packet);
		} catch (Exception e) {

			Log.error("CallControlComponent sendPacket ", e);
		}
	}

   private void createCallRecord(String username, String addressFrom, String addressTo, long datetime, int duration, String calltype)
   {
		boolean sipPlugin = XMPPServer.getInstance().getPluginManager().getPlugin("sip") != null;

		if (sipPlugin)
		{
			Log.info("createCallRecord " + username + " " + addressFrom + " " + addressTo + " " + datetime);

			String sql = "INSERT INTO ofSipPhoneLog (username, addressFrom, addressTo, datetime, duration, calltype) values  (?, ?, ?, ?, ?, ?)";

			Connection con = null;
			PreparedStatement psmt = null;
			ResultSet rs = null;

			try {
				con = DbConnectionManager.getConnection();
				psmt = con.prepareStatement(sql);
				psmt.setString(1, username);
				psmt.setString(2, addressFrom);
				psmt.setString(3, addressTo);
				psmt.setLong(4, datetime);
				psmt.setInt(5, duration);
				psmt.setString(6, calltype);

				psmt.executeUpdate();

			} catch (SQLException e) {
				Log.error(e.getMessage(), e);
			} finally {
				DbConnectionManager.closeConnection(rs, psmt, con);
			}
		}
    }

	private void updateCallRecord(long datetime, int duration)
	{
		boolean sipPlugin = XMPPServer.getInstance().getPluginManager().getPlugin("sip") != null;

		if (sipPlugin)
		{
			Log.info("updateCallRecord " + datetime + " " + duration);

			String sql = "UPDATE ofSipPhoneLog SET duration = ? WHERE datetime = ?";

			Connection con = null;
			PreparedStatement psmt = null;

			try {

				con = DbConnectionManager.getConnection();
				psmt = con.prepareStatement(sql);

				psmt.setInt(1, duration);
				psmt.setLong(2, (datetime / 1000));
				psmt.executeUpdate();


			} catch (SQLException e) {
				Log.error(e.getMessage(), e);

			} finally {
				DbConnectionManager.closeConnection(psmt, con);
			}
		}
	}
}
