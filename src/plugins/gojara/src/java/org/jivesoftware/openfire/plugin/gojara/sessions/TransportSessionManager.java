package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;
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
	private Map<String, Map<String, Long>> transportSessions = new ConcurrentHashMap<String, Map<String, Long>>(16, 0.75f, 1);
	private static final Logger Log = LoggerFactory.getLogger(TransportSessionManager.class);

	private TransportSessionManager() {
		db = DatabaseManager.getInstance();
		adminManager = GojaraAdminManager.getInstance();
		Log.info(" Created TransportSessionManager");
	}

	public static synchronized TransportSessionManager getInstance() {
		if (myself == null) {
			myself = new TransportSessionManager();
		}
		return myself;
	}

	/**
	 * adds a subdomain to watched transports
	 * 
	 * @param subdomain
	 */
	public void addTransport(String subdomain) {
		transportSessions.put(subdomain, new ConcurrentHashMap<String, Long>(64, 0.75f, 1));
		Log.debug("Added key to transportSessionMap: " + subdomain);
	}

	/**
	 * removes subdomain from watched transports
	 * 
	 * @param subdomain
	 */
	public void removeTransport(String subdomain) {
		Map<String, Long> disconnectedUsers = transportSessions.remove(subdomain);
		Log.debug("Removed " + subdomain + "from TransportSessionMap " + disconnectedUsers.toString());

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
		//dont update if user is already present, else lots of away to online changes might be spammed
		if (transportSessions.get(transport) != null && transportSessions.get(transport).get(user) == null) {
			long millis = System.currentTimeMillis();
			transportSessions.get(transport).put(user, millis);
			db.insertOrUpdateSession(transport, user, millis);
			return true;
		}
		return false;
	}

	public boolean disconnectUserFrom(String transport, String user) {
//		Log.debug("Trying to remove User " + JID.nodeprep(user) + " from Session for Transport " + transport);
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
	 * combination Also it will be removed from our db. For this to happen the transport has to be active.
	 * 
	 * @param transport
	 * @param user
	 * @return String that describes what happened.
	 */
	public String removeRegistrationOfUser(String transport, String user) {
			adminManager.unregisterUserFrom(transport, user);
			int result = db.removeSessionEntry(transport, user);
			if (result == 0) {
				return "Did not remove entry for user: " + user + " and transport: " + transport + "\n";
			} else if (result == 1) {
				return "Successfully removed entry for user: " + user + " and transport: " + transport + " \n";
			} else {
				return "What is happening ???: " + result;
			}
	}

	/**
	 * Initializes Sessions through adminmanager, ofc needs to be called at a point where there are Transports registered in
	 * transportSessions
	 */
	public void initializeSessions() {
		Log.info("Initializing Sessions.");
		for (String transport : transportSessions.keySet()) {
			adminManager.getOnlineUsersOf(transport);
		}
	}
	
	/**
	 * @return Set of currently active Gateways
	 */
	public final Set<String>getActiveGateways() {
		return transportSessions.keySet();
	}
	
	public final Map<String, Map<String, Long>> getSessions() {
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
		ArrayList<GatewaySession> result = new ArrayList<GatewaySession>(getNumberOfActiveSessions());
		for (Map.Entry<String, Map<String,Long>> gateway : transportSessions.entrySet()) {
			for (Map.Entry<String, Long> entry : gateway.getValue().entrySet()) {
				Timestamp stamp = new Timestamp(entry.getValue());
				Date date = new Date(stamp.getTime());
				result.add(new GatewaySession(entry.getKey(), gateway.getKey(), date));
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
		for (Map.Entry<String, Map<String,Long>> gateway : transportSessions.entrySet()) {
			result += gateway.getValue().size();
		}
		return result;
	}
	
	/**
	 * Returns number of active Sessions for specified transport or 0 if not valid transport.
	 * @param transport
	 * @return
	 */
	public int getNumberOfActiveSessionsFor(String transport) {
		if (transportSessions.containsKey(transport))
			return transportSessions.get(transport).size();
		return 0;
	}

	/**
	 * Searches for Sessions with given Username and returns them as ArrList
	 * 
	 * @param username
	 * @return
	 */
	public ArrayList<GatewaySession> getConnectionsFor(String username) {
		ArrayList<GatewaySession> userconnections = new ArrayList<GatewaySession>();
		for (Map.Entry<String, Map<String, Long>> transport : transportSessions.entrySet()) {
			if (transport.getValue().containsKey(username)) {
				Timestamp stamp = new Timestamp(transport.getValue().get(username));
				Date date = new Date(stamp.getTime());
				userconnections.add(new GatewaySession(username, transport.getKey(), date));
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
	
	public int getNumberOfRegistrationsForTransport(String transport) {
		if (transportSessions.containsKey(transport)) {
			return db.getNumberOfRegistrationsForTransport(transport);
		}
		return 0;
	}
}
