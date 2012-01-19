package org.jivesoftware.openfire.plugin.gojara.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveGlobals;

/**
 * @author Holger Bergunde This class is used to store logs in the database. A
 *         log entry is representated by {@link LogEntry}
 */
public class DatabaseManager {

	private static Logger Log = Logger.getLogger(DatabaseManager.class);

	private static volatile DatabaseManager _myself;
	private static final String COUNT_LOG_ENTRIES = "SELECT count(*) FROM ofGojaraStatistics";
	private static final String COUNT_PACKAGES_ODLER = "SELECT count(*) FROM ofGojaraStatistics WHERE messageType like ? AND component = ? AND messageDate > ?";
	private static final String GET_ALL_LOGS = "SELECT * FROM ofGojaraStatistics ORDER BY logID desc LIMIT 100";
	// private static final String MOST_ACTIVE =
	// "SELECT toJID, count(logID) AS counter FROM `ofGojaraStatistics` GROUP by toJID ORDER BY counter DESC";
	private static final String ADD_NEW_LOG = "INSERT INTO ofGojaraStatistics(messageDate, messageType, fromJID, toJId, component) VALUES(?,?,?,?,?)";
	private static final String CLEAN_OLD_DATA = "DELETE FROM ofGojaraStatistics WHERE messageDate < ?";
	private static final String GET_LOGS_DATE_LIMIT_COMPONENT = "SELECT * FROM ofGojaraStatistics WHERE messageDate > ? AND component LIKE ? ORDER BY messageDate DESC LIMIT ?";
	private final int _dbCleanMinutes;

	private DatabaseManager() {

		/*
		 * Load time from globals if it is set. It represents the minutes the
		 * log entries stay in database until they will get deleted
		 */
		// TODO: Use PropertyEventListener to check if cleaner.minutes have
		// changed
		_dbCleanMinutes = JiveGlobals.getIntProperty("plugin.remoteroster.log.cleaner.minutes", 60);

		startDatabaseCleanLoop();
	}

	private void startDatabaseCleanLoop() {
		/*
		 * Database Cleaner thread and check for old log entries every 2 minute
		 */
		TimerTask task = new TimerTask() {
			@Override
			public void run() {
				cleanOldLogEntries();
			}
		};
		Timer timer = new Timer();
		timer.schedule(task, 2 * 60 * 1000, 2 * 60 * 1000);
	}

	/**
	 * Singleton for Databasemanager, because we only need one.
	 * 
	 * @return the Databasemanager
	 */
	public static DatabaseManager getInstance() {
		if (_myself == null) {
			synchronized (DatabaseManager.class) {
				if (_myself == null)
					_myself = new DatabaseManager();
			}
		}
		return _myself;
	}

	/**
	 * Returns a list of LogEntry's ordered by date desc
	 * 
	 * @param olderThan
	 *            unix timestamp in ms
	 * @param limit
	 *            num of rows max
	 * @param component
	 *            the specified subdomain of the logged component
	 * @return Collection of {@link LogEntry}
	 */
	public Collection<LogEntry> getLogsByDateAndLimit(long olderThan, int limit, String component) {
		List<LogEntry> result = new ArrayList<LogEntry>();
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(GET_LOGS_DATE_LIMIT_COMPONENT);
			pstmt.setLong(1, olderThan);
			pstmt.setString(2, component);
			pstmt.setInt(3, limit);

			ResultSet rs = pstmt.executeQuery();
			while (rs.next()) {
				String from = rs.getString(4);
				String to = rs.getString(5);
				String type = rs.getString(3);
				long date = rs.getLong(2);
				LogEntry res = new LogEntry(from, to, type, date);
				result.add(res);
			}

			pstmt.close();
		} catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}
		return result;
	}

	/*
	 * Cleans log entries older than 60 minutes if
	 * plugin.remoteroster.log.cleaner.minutes is not set
	 */
	private void cleanOldLogEntries() {
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(CLEAN_OLD_DATA);
			pstmt.setLong(1, System.currentTimeMillis() - _dbCleanMinutes * 60 * 1000);
			int rows = pstmt.executeUpdate();
			Log.debug("Cleaned statistic database. Affected rows: " + rows);
			pstmt.close();
		} catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}
	}

	/**
	 * Adds new log entry for specified component.
	 * 
	 * @param component
	 *            subdomain of the external component. e.g.
	 *            icq.myjabberserver.com
	 * @param type
	 *            string representation of the class. normaly it is like
	 *            {@link org.xmpp.packet}
	 * @param from
	 *            full qualified JID of user or component this packet was from
	 * @param to
	 *            full qualified JID of user or component this packet was
	 *            adressed to
	 */
	public void addNewLogEntry(String component, String type, String from, String to) {
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();

			pstmt = con.prepareStatement(ADD_NEW_LOG);
			pstmt.setLong(1, System.currentTimeMillis());
			pstmt.setString(2, type);
			pstmt.setString(3, from);
			pstmt.setString(4, to);
			pstmt.setString(5, component);
			pstmt.executeUpdate();
			pstmt.close();
		} catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}
	}

	/**
	 * This method return the last 100 log entries. Every entry is one string
	 * and added to a ArrayList
	 * 
	 * @return each log as string in a list
	 * */
	public List<String> getAllLogs() {
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		List<String> _result = new ArrayList<String>();

		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(GET_ALL_LOGS);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				String from = rs.getString(4);
				String to = rs.getString(5);
				String type = rs.getString(3);
				String component = rs.getString(6);
				Timestamp date = rs.getTimestamp(2);
				String res = "From: " + from + " To: " + to + " Type: " + type + " Timestamp: " + date.toString()
						+ "Component: " + component;
				_result.add(res);
			}
		}

		catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
		return _result;
	}

	/**
	 * Returns the size of the ofGoJaraStatistics table
	 * 
	 * @return number rows in database as int or -1 if an error occurred
	 */
	public int getLogSize() {
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(COUNT_LOG_ENTRIES);
			rs = pstmt.executeQuery();
			rs.next();
			return rs.getInt(1);
		} catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
		return -1;
	}

	/**
	 * Counts the number of log entries in the databse that are older than
	 * specified value
	 * 
	 * @param component
	 *            subdomain of the component the packages were flown by
	 * @param packetClass
	 *            the class the packet was instance of
	 * @param minutes
	 *            the log entry should not be older than (timestamp should be
	 *            smaller than currentTime - minutes)
	 * @return number of rows found in database or -1 if there was an error
	 */
	public int getPacketCountOlderThan(String component, Class packetClass, int minutes) {
		String classname = packetClass.getName();
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(COUNT_PACKAGES_ODLER);
			pstmt.setString(1, "%" + classname + "");
			pstmt.setString(2, component);
			pstmt.setLong(3, System.currentTimeMillis() - minutes * 60 * 1000);
			rs = pstmt.executeQuery();
			rs.next();
			return rs.getInt(1);
		} catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
		return -1;
	}

}
