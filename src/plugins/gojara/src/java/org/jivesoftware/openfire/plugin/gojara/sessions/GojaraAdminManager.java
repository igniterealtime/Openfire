package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.util.HashSet;
import java.util.Set;

import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Message.Type;

/**
 * This Class sends Ad-Hoc commands to given spectrum2 transports, which will then be intercepted by MainInterceptor,
 * and processed by GojaraAdminProcessor
 * 
 * @author axel.frederik.brand
 * 
 */
public class GojaraAdminManager {
	private static GojaraAdminManager myself;
	private PacketRouter router;
	private static final Logger Log = LoggerFactory.getLogger(GojaraAdminManager.class);
	private JID adminUser;
	private XMPPServer _server;
	private Set<String> unconfiguredGateways;
	private Set<String> configuredGateways;
	private boolean areGatewaysConfigured = true;

	private GojaraAdminManager() {
		_server = XMPPServer.getInstance();
		router = _server.getPacketRouter();
		UserManager userManager = UserManager.getInstance();
		try {
			userManager.createUser("gojaraadmin", "iAmTheLawgiver", "gojaraadmin", null);
			Log.info("gojaraAdmin User created.");
		} catch (UserAlreadyExistsException e) {
			Log.info("gojaraAdmin User already created.");
		}
		adminUser = _server.createJID("gojaraadmin", null);
		unconfiguredGateways = new HashSet<String>();
		configuredGateways = new HashSet<String>();
	}

	public static GojaraAdminManager getInstance() {
		if (myself == null) {
			myself = new GojaraAdminManager();
		}
		return myself;
	}

	public void removeAdminUser() {
		UserManager userManager = UserManager.getInstance();
		try {
			userManager.deleteUser(userManager.getUser("gojaraadmin"));
		} catch (UserNotFoundException e) {
			Log.info("Couldn't remove adminUser.");
		}
	}

	/**
	 * Sends a testmessage to specified gateway and schedules a task to check if there was a response.
	 * 
	 */
	public void testAdminConfiguration(String gateway) {
		areGatewaysConfigured = false;
		unconfiguredGateways.add(gateway);
		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(gateway);
		message.setID("config_check");
		message.setBody("status");
		message.setType(Type.chat);
		router.route(message);
		Log.info("Sent config_check Packet!" + message.toString());
	}

	/**
	 * Gets called from Interceptor to confirm that a Gateway responded to our config_check message.
	 * 
	 * @param gateway
	 */
	public void confirmGatewayConfig(String gateway) {
		unconfiguredGateways.remove(gateway);
		configuredGateways.add(gateway);
		if (unconfiguredGateways.isEmpty())
			areGatewaysConfigured = true;
	}

	/**
	 * If a gateway disconnects we have to check if it was not configured as we may want to alter boolean
	 * areGatewaysConfigured.
	 */
	public void gatewayUnregistered(String gateway) {
			unconfiguredGateways.remove(gateway);
			configuredGateways.remove(gateway);
			if (unconfiguredGateways.isEmpty())
				areGatewaysConfigured = true;
	}

	public boolean areGatewaysConfigured() {
		return areGatewaysConfigured;
	}
	
	public boolean isGatewayConfigured(String gateway) {
		return configuredGateways.contains(gateway);
	}

	/**
	 * Sends the command online_users to specified Spectrum2 transport. We set the ID specific to the command so we can
	 * identify the response. Transport has to be configured for admin_jid = gojaraadmin@domain
	 * 
	 * @param transport
	 */
	public void getOnlineUsersOf(String transport) {
		// no use in sending the message if not configured for gojaraadmin
		if (unconfiguredGateways.contains(transport))
			return;

		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(transport);
		message.setID("online_users");
		message.setBody("online_users");
		message.setType(Type.chat);
		router.route(message);
		Log.info("Sent online_users Packet!" + message.toString());
	}

	/**
	 * Sends the unregister <bare_jid> command to specified Spectrum2 transport. We set the ID specific to the command
	 * so we can identify the response. Transport has to be configured for admin_jid = gojaraadmin@domain
	 * 
	 * @param transport
	 */
	public void unregisterUserFrom(String transport, String user) {
		if (unconfiguredGateways.contains(transport))
			return;

		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(transport);
		message.setID("unregister");
		message.setBody("unregister " + _server.createJID(user, null).toString());
		message.setType(Type.chat);
		router.route(message);
		Log.info("Sent Unregister Packet!" + message.toString());

	}
}
