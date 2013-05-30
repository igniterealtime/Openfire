package org.jivesoftware.openfire.plugin.gojara.permissions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.plugin.gojara.database.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

public class TransportSessionManager {
	private static TransportSessionManager myself;
	private DatabaseManager db;
	private Map<String, Map<String, Date>> transportSessions = new ConcurrentHashMap<String, Map<String, Date>>();
	private static final Logger Log = LoggerFactory.getLogger(TransportSessionManager.class);

	private TransportSessionManager() {
		db = DatabaseManager.getInstance();
		Log.info(" Created TransportSessionManager");

	}

	public static TransportSessionManager getInstance() {
		if (myself == null) {
			synchronized (TransportSessionManager.class) {
				if (myself == null)
					myself = new TransportSessionManager();
			}
		}
		return myself;
	}

	public void addTransport(String subdomain) {
		transportSessions.put(subdomain, new ConcurrentHashMap<String, Date>());
		Log.info("Added key to transportSessionMap: " + subdomain);
	}

	public void removeTransport(String subdomain) {
		Map<String, Date> disconnectedUsers = transportSessions.remove(subdomain);
		Log.info("Removed " + subdomain + "from TransportSessionMap " + disconnectedUsers.toString());

	}

	public boolean isTransportActive(String subdomain) {
		return transportSessions.containsKey(subdomain) ? true : false;
	}
	public void registerUserTo(String transport, String user) {
		//This ofc is not a session yet, so we just keep the registration in db to track eventual unsuccessful registers
		db.insertOrUpdateSession(transport, user, 0);
	}
	
	public boolean connectUserTo(String transport, String user) {
		if (transportSessions.get(transport) != null) {
			long millis = System.currentTimeMillis();
			Timestamp stamp = new Timestamp(millis);
			Date date = new Date(stamp.getTime());
			transportSessions.get(transport).put(user, date);
			db.insertOrUpdateSession(transport, user, millis);
			return true;
		}
		return false;
	}

	public boolean disconnectUserFrom(String transport, String user) {
		Log.info("Trying to remove User " + JID.nodeprep(user) + " from Session for Transport " + transport);
		if (isUserConnectedTo(transport, user)) {
			transportSessions.get(transport).remove(user);
			return true;
		}
		return false;
	}

	public boolean isUserConnectedTo(String transport, String user) {
		return transportSessions.get(transport).containsKey(user);
	}

	public String removeRegistrationOfUser(String transport, String user) {
		int result = db.removeSessionEntry(transport, user);
		if (result == 0) {
			return "Did not remove entry for user: " + user + " and transport: " + transport + "\n";
		} else if (result == 1) {
			return "Successfully removed entry for user: " + user + " and transport: " + transport + " \n";
		} else {
			return "What is happening ???: " + result;
		}
	}

	public final Map<String, Map<String, Date>> getSessions() {
		return transportSessions;
	}

	public int getNumberOfActiveSessions() {
		int result = 0;
		for (String key : transportSessions.keySet()) {
			result += transportSessions.get(key).size();
		}
		return result;
	}

	public final Map<String, Date> getConnectionsFor(String username) {
		// Maybe i do too much with hashes
		Map<String, Date> userconnections = null;
		for (String transport : transportSessions.keySet()) {
			if (transportSessions.get(transport).containsKey(username)) {
				if (userconnections == null)
					userconnections = new HashMap<String, Date>();
				userconnections.put(transport, transportSessions.get(transport).get(username));
			}
		}
		return userconnections;
	}

	public ArrayList<SessionEntry> getRegistrationsFor(String username) {
		return db.getSessionEntriesFor(username);
	}

	/**
	 * Validation for correctness of attributes is done in DB Manager, returns default sorting: username ASC when not
	 * correct
	 */
	public ArrayList<SessionEntry> getAllRegistrations(String orderAttr, String order) {
		return db.getAllSessionEntries(orderAttr, order);
	}

}
