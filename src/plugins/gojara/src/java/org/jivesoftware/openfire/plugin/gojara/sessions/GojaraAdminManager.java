package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

	private Map<String, Map<String, Integer>> gatewayStatisticsMap;
	private long refreshCooldown = 0;

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
		setGatewayStatisticsMap(new HashMap<String, Map<String, Integer>>());
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
		unconfiguredGateways.add(gateway);
		// we add these here and not in confirmGateway so its more clear that these are not being gathered when viewing
		// Spectrum2 Stats jsp
		getGatewayStatisticsMap().put(gateway, new HashMap<String, Integer>());

		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(gateway);
		message.setID("config_check");
		message.setBody("status");
		message.setType(Type.chat);
		router.route(message);
		Log.info("Checking for admin configuration on " + gateway);
	}

	/**
	 * Gets called from Interceptor to confirm that a Gateway responded to our config_check message.
	 * 
	 * @param gateway
	 */
	public void confirmGatewayConfig(String gateway) {
		unconfiguredGateways.remove(gateway);
		configuredGateways.add(gateway);
	}

	/**
	 * If a gateway disconnects we have to check if it was not configured as we may want to alter boolean
	 * areGatewaysConfigured.
	 */
	public void gatewayUnregistered(String gateway) {
		unconfiguredGateways.remove(gateway);
		configuredGateways.remove(gateway);
		getGatewayStatisticsMap().remove(gateway);
	}

	public boolean areGatewaysConfigured() {
		return unconfiguredGateways.isEmpty();
	}
	
	public boolean isGatewayConfigured(String gateway) {
		return configuredGateways.contains(gateway);
	}

	/**
	 * Generates a basic ad-hoc command with From,To, Type, ID & Body configured. Body might need to be reconfigured
	 * when additional info has to be specified, like unregister
	 * 
	 * @param transport
	 * @param command
	 * @return
	 */
	private Message generateCommand(String transport, String command) {
		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(transport);
		message.setID(command);
		message.setBody(command);
		message.setType(Type.chat);
		return message;
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

		Message message = generateCommand(transport, "online_users");
		router.route(message);
		Log.debug("Sent online_users Packet!" + message.toString());
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

		Message message = generateCommand(transport, "unregister");
		message.setBody("unregister " + _server.createJID(user, null).toString());
		router.route(message);
		Log.debug("Sent Unregister Packet!" + message.toString());

	}

	public Map<String, Map<String, Integer>> getGatewayStatisticsMap() {
		return gatewayStatisticsMap;
	}

	public void setGatewayStatisticsMap(Map<String, Map<String, Integer>> gatewayStatisticsMap) {
		this.gatewayStatisticsMap = gatewayStatisticsMap;
	}

	public void gatherGatewayStatistics() {
		if (refreshCooldown == 0) {
			refreshCooldown = System.currentTimeMillis();
		} else {
			// once a minute max
			if ((System.currentTimeMillis() - refreshCooldown) < 60000)
				return;
		}

		refreshCooldown = System.currentTimeMillis();

		Log.info("Gathering Gateway-Statistics!");
		for (String gateway : configuredGateways) {
			uptime(gateway);
			messagesFrom(gateway);
			messagesTo(gateway);
			usedMemoryOf(gateway);
			averageMemoryOfUser(gateway);
		}
	}

	private void uptime(String transport) {
		Message message = generateCommand(transport, "uptime");
		router.route(message);
	}

	private void messagesFrom(String transport) {
		Message message = generateCommand(transport, "messages_from_xmpp");
		router.route(message);
	}

	private void messagesTo(String transport) {
		Message message = generateCommand(transport, "messages_to_xmpp");
		router.route(message);
	}

	private void usedMemoryOf(String transport) {
		Message message = generateCommand(transport, "used_memory");
		router.route(message);
	}

	private void averageMemoryOfUser(String transport) {
		Message message = generateCommand(transport, "average_memory_per_user");
		router.route(message);
	}
	
	/**
	 * Preps the specified stat for nicer output which is used in tables.
	 * @param gateway
	 * @param stat
	 * @return
	 */
	public String getStatisticsPresentationString(String gateway, String stat) {
		if (gatewayStatisticsMap.containsKey(gateway)) {
			if (stat.equals("uptime")) {
				if (gatewayStatisticsMap.get(gateway).get("uptime") != null) {
					int time = gatewayStatisticsMap.get(gateway).get("uptime");
					long diffSeconds = time % 60;
					long diffMinutes = time / 60 % 60;
					long diffHours = time / (60 * 60) % 24;
					long diffDays = time / (24 * 60 * 60);
					return "" + diffSeconds+ " Sec " + diffMinutes + " Min " + diffHours + " Hours " + diffDays + " Days";
				}
			} else if (stat.equals("messages_from_xmpp")) {
				if (gatewayStatisticsMap.get(gateway).get("messages_from_xmpp") != null)
					return "" + gatewayStatisticsMap.get(gateway).get("messages_from_xmpp");
			} else if (stat.equals("messages_to_xmpp")) {
				if (gatewayStatisticsMap.get(gateway).get("messages_to_xmpp") != null)
					return "" + gatewayStatisticsMap.get(gateway).get("messages_to_xmpp");
			} else if (stat.equals("used_memory")) {
				if (gatewayStatisticsMap.get(gateway).get("used_memory") != null) {
					DecimalFormat f = new DecimalFormat("#0.00");
					double mb = gatewayStatisticsMap.get(gateway).get("used_memory") / 1024.0;
					
					return ""  + f.format(mb) + " MB";
				}
			} else if (stat.equals("average_memory_per_user")) {
				if (gatewayStatisticsMap.get(gateway).get("average_memory_per_user") != null) {
					return "" + gatewayStatisticsMap.get(gateway).get("average_memory_per_user") + " KB";
				}
			}
		}
		return "-";
	}
}
