package org.jivesoftware.openfire.plugin.gojara.messagefilter.processors;

import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.plugin.gojara.sessions.GojaraAdminManager;
import org.jivesoftware.openfire.plugin.gojara.sessions.TransportSessionManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

public class GojaraAdminProcessor extends AbstractRemoteRosterProcessor {
	private TransportSessionManager transportSessionManager = TransportSessionManager.getInstance();
	private GojaraAdminManager gojaraAdminManager = GojaraAdminManager.getInstance();

	public GojaraAdminProcessor() {
		Log.info("Created GojaraAdminProcessor");
	}

	/**
	 * Here we process the response of the remote command sent to Spectrum. We have to identify what kind of response it
	 * is, as no tag for the command being responded is being sent. Currently these commands are used in Gojara
	 * TransportSessionManager: online_users ( Chatmsg of online users for specific transport), usernames seperated by
	 * newlines
	 */
	@Override
	public void process(Packet packet, String subdomain, String to, String from) throws PacketRejectedException {
		Message message = (Message) packet;

		// handle different commands
		Log.info("Intercepted something: " + message.toString());
		String command = packet.getID();
		if (command.equals("online_users")) {
			handleOnlineUsers(message, subdomain);
		} else if (command.equals("unregister")) {
			handleUnregister(message);
		} else if (command.equals("config_check")) {
			handleConfigCheck(subdomain);
		} else if (command.equals("uptime")) {
			handleStatistic(message, subdomain, "uptime");
		} else if (command.equals("messages_from_xmpp")) {
			handleStatistic(message, subdomain, "messages_from_xmpp");
		} else if (command.equals("messages_to_xmpp")) {
			handleStatistic(message, subdomain, "messages_to_xmpp");
		} else if (command.equals("used_memory")) {
			handleStatistic(message, subdomain, "used_memory");
		} else if (command.equals("average_memory_per_user")) {
			handleStatistic(message, subdomain, "average_memory_per_user");
		}
	}

	private void handleOnlineUsers(Message message, String subdomain) {
		Log.info("Found online_users command!");
		if (message.getBody().equals("0"))
			return;
		String[] content = message.getBody().split("\\r?\\n");
		for (String user : content) {
			JID userjid = new JID(user);
			transportSessionManager.connectUserTo(subdomain, userjid.getNode());
		}
	}

	private void handleUnregister(Message message) {
		Log.info("Found unregister command! ");
	}

	private void handleConfigCheck(String subdomain) {
		gojaraAdminManager.confirmGatewayConfig(subdomain);
		Log.info("Confirm config_check for " + subdomain);
	}

	private void handleStatistic(Message message, String subdomain, String statistic) {
		int result;
		try {
			result = Integer.parseInt(message.getBody());
		} catch (Exception e) {
			e.printStackTrace();
			result = 0;
		}

		gojaraAdminManager.getGatewayStatisticsMap().get(subdomain).put(statistic, result);
	}
}