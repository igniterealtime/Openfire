package org.jivesoftware.openfire.plugin.gojara.sessions;

import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

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
	private static final Logger Log = LoggerFactory.getLogger(TransportSessionManager.class);
	private JID  adminUser;
	private XMPPServer _server;

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
	 * Sends the command online_users to specified Spectrum2 transport
	 * @param transport
	 */
	public void getOnlineUsersOf(String transport) {
		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(transport);
		message.setBody("online_users");
		router.route(message);
		Log.info("Sent following Packet!" + message.toString());
	}
	
	/**
	 * Sends the unregister <bare_jid> command to specified Spectrum2 transport
	 * @param transport
	 */
	public void unregisterUserFrom(String transport, String user) {
		Message message = new Message();
		message.setFrom(adminUser);
		message.setTo(transport);
		message.setBody("unregister " + _server.createJID(user, null).toString());
		router.route(message);
		Log.info("Sent following Packet!" + message.toString());
		
	}
}
