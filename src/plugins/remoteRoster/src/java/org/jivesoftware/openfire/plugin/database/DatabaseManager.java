package org.jivesoftware.openfire.plugin.database;

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

public class DatabaseManager {

	private static Logger Log = Logger.getLogger(DatabaseManager.class);
	private static volatile DatabaseManager _myself;
	private static final String COUNT_LOG_ENTRIES = "SELECT count(*) FROM ofGojaraStatistics";
	private static final String COUNT_PACKAGES_ODLER = "SELECT count(*) FROM `ofGojaraStatistics` WHERE messageType like ? AND component = ? AND messageDate > ?";
	private static final String GET_ALL_LOGS = "SELECT * FROM ofGojaraStatistics ORDER BY logID desc";
//	private static final String MOST_ACTIVE = "SELECT toJID, count(logID) AS counter FROM `ofGojaraStatistics` GROUP by toJID ORDER BY counter DESC";
	private static final String ADD_NEW_LOG = "INSERT INTO ofGojaraStatistics(messageDate, messageType, fromJID, toJId, component) VALUES(?,?,?,?,?)";
	private static final String CLEAN_OLD_DATA = "DELETE FROM `ofGojaraStatistics` WHERE messageDate < ?";
	private static final String GET_LOGS_DATE_LIMIT_COMPONENT = "SELECT * FROM `ofGojaraStatistics` WHERE messageDate > ? AND component LIKE ? ORDER BY messageDate DESC LIMIT ?";

	private DatabaseManager() {
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
	 * @return Collection of LogEntry
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

	public void cleanOldLogEntries() {
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(CLEAN_OLD_DATA);
			pstmt.setLong(1, System.currentTimeMillis() - 60 * 60 * 1000);
			int rows = pstmt.executeUpdate();
			Log.debug("Cleaned statistic database. Affected rows: " + rows);
			pstmt.close();
		} catch (SQLException sqle) {
			Log.error(sqle);
		} finally {
			DbConnectionManager.closeConnection(pstmt, con);
		}
	}

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
		return 0;
	}

	public int getPacketCount(String component, Class packetClass) {
		return getPacketCountOlderThan(component, packetClass, 60);
	}

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
		return 0;
	}

}
