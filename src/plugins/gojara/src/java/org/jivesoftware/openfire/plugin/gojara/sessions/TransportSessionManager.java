package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.openfire.plugin.gojara.database.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * This is the central point for doing anything Gojara GatewaySession related. We keep track of Users connected to
 * Transports, stats about these and provide some methods for Presentation in JSP Pages.
 * 
 * @author axel.frederik.brand
 */
public class TransportSessionManager {
	private static TransportSessionManager myself;
	private DatabaseManager db;
	private GojaraAdminManager adminManager;
	private Map<String, Map<String, Date>> transportSessions = new ConcurrentHashMap<String, Map<String, Date>>();
	private static final Logger Log = LoggerFactory.getLogger(TransportSessionManager.class);

	private TransportSessionManager() {
		db = DatabaseManager.getInstance();
		adminManager = GojaraAdminManager.getInstance();
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

	/**
	 * adds a subdomain to watched transports
	 * 
	 * @param subdomain
	 */
	public void addTransport(String subdomain) {
		transportSessions.put(subdomain, new ConcurrentHashMap<String, Date>());
		Log.info("Added key to transportSessionMap: " + subdomain);
	}

	/**
	 * removes subdomain from watched transports
	 * 
	 * @param subdomain
	 */
	public void removeTransport(String subdomain) {
		Map<String, Date> disconnectedUsers = transportSessions.remove(subdomain);
		Log.info("Removed " + subdomain + "from TransportSessionMap " + disconnectedUsers.toString());

	}

	public boolean isTransportActive(String subdomain) {
		return transportSessions.containsKey(subdomain) ? true : false;
	}

	/**
	 * register is seperate because a user may register to transport but not connect to it, e.g. with wrong credentials.
	 * we still want to keep track of those registrations so we know they happened and we can reset them
	 * 
	 * @param transport
	 * @param user
	 */
	public void registerUserTo(String transport, String user) {
		db.insertOrUpdateSession(transport, user, 0);
	}

	/**
	 * Add the session of user that connected to gateway and update or create timestamp for session
	 * 
	 * @param transport
	 * @param user
	 * @return
	 */
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

	/**
	 * 
	 * Removing a registration will cause a unregister msg being sent to Spectrum2 for this specific User/Gateway
	 * combination Also it will be removed from our db.
	 * For this to happen the transport has to be active.
	 * 
	 * @param transport
	 * @param user
	 * @return String that describes what happened.
	 */
	public String removeRegistrationOfUser(String transport, String user) {
		if (transportSessions.containsKey(transport)) {
			adminManager.unregisterUserFrom(transport, user);
			int result = db.removeSessionEntry(transport, user);
			if (result == 0) {
				return "Did not remove entry for user: " + user + " and transport: " + transport + "\n";
			} else if (result == 1) {
				return "Successfully removed entry for user: " + user + " and transport: " + transport + " \n";
			} else {
				return "What is happening ???: " + result;
			}
		} else {
			return "Cannot Unregister user " + user + " from " + transport + " when it's inactive.";
		}
		
	}

	/**
	 * Initializes Sessions, ofc needs to be called at a point where there are Transports registered in
	 * transportSessions
	 */
	public void initializeSessions() {
		Log.info("initializing Sessions.");
		for (String transport : transportSessions.keySet()) {
			adminManager.getOnlineUsersOf(transport);
		}
	}

	public final Map<String, Map<String, Date>> getSessions() {
		return transportSessions;
	}

	/**
	 * Puts all Sessions into an ArrayList of GatewaySession Objects, and sorts it according to specified sorting
	 * attributes.
	 * 
	 * @param sortby
	 *            username, transport or LoginTime
	 * @param sortorder
	 *            ASC or DESC
	 * @return Sorted/Unsorted ArrayList of GatewaySession Objects
	 */
	public ArrayList<GatewaySession> getSessionsSorted(String sortby, String sortorder) {
		ArrayList<GatewaySession> result = new ArrayList<GatewaySession>();
		for (String key : transportSessions.keySet()) {
			for (String user : transportSessions.get(key).keySet()) {
				result.add(new GatewaySession(user, key, transportSessions.get(key).get(user)));
			}
		}

		if (sortby.equals("username")) {
			Collections.sort(result, new SortUserName());
		} else if (sortby.equals("transport")) {
			Collections.sort(result, new SortTransport());
		} else if (sortby.equals("loginTime")) {
			Collections.sort(result, new SortLastActivity());
		}
		if (sortorder.equals("DESC")) {
			Collections.reverse(result);
		}
		return result;
	}

	/**
	 * @return count of recognized active Sessions
	 */
	public int getNumberOfActiveSessions() {
		int result = 0;
		for (String key : transportSessions.keySet()) {
			result += transportSessions.get(key).size();
		}
		return result;
	}

	/**
	 * Searches for Sessions with given Username and returns them as ArrList
	 * 
	 * @param username
	 * @return
	 */
	public ArrayList<GatewaySession> getConnectionsFor(String username) {
		ArrayList<GatewaySession> userconnections = null;
		for (String transport : transportSessions.keySet()) {
			if (transportSessions.get(transport).containsKey(username)) {
				if (userconnections == null)
					userconnections = new ArrayList<GatewaySession>();
				userconnections.add(new GatewaySession(username, transport, transportSessions.get(transport).get(username)));
			}
		}
		return userconnections;
	}

	/**
	 * Returns Registrations associated with Username
	 * 
	 * @param username
	 * @return ArrayList of SessionEntries
	 */
	public ArrayList<SessionEntry> getRegistrationsFor(String username) {
		return db.getSessionEntriesFor(username);
	}

	/**
	 * Get all registrations sorted by attribute and order. Validation for correctness of attributes is done in DB
	 * Manager, returns default sorting: username ASC when not valid
	 */
	public ArrayList<SessionEntry> getAllRegistrations(String orderAttr, String order) {
		return db.getAllSessionEntries(orderAttr, order);
	}

	public int getNumberOfRegistrations() {
		return db.getNumberOfRegistrations();
	}
}
