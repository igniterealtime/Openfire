/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.sip.calllog;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 *
 * Database persistence for CallLog class and database methods for call log store
 *
 * @author Thiago Rocha Camargo
 */
public class CallLogDAO {

    /**
     *
     * Return every stored calls that matches to the SQLCondition in the interval between startIndex and endIndex
     *
     * @param SQLCondition the content of a SQL "Where" clause.
     * @param startIndex
     * @param numResults
     * @return Collection<CallLog>;
     */
    public static Collection<CallLog> getCalls(String SQLCondition,
			int startIndex, int numResults) {

		String sql = "SELECT * FROM sipPhoneLog";

		sql = SQLCondition != null && !SQLCondition.equals("") ? sql
				+ " WHERE " + SQLCondition : sql;

		sql += " ORDER BY datetime DESC";

		List<CallLog> calls = new ArrayList<CallLog>(numResults);
		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = DbConnectionManager.createScrollablePreparedStatement(con,
					sql);
			ResultSet rs = pstmt.executeQuery();
			DbConnectionManager.setFetchSize(rs, startIndex + numResults);
			DbConnectionManager.scrollResultSet(rs, startIndex);
			int count = 0;
			while (rs.next() && count < numResults) {
				calls.add(read(rs));
				count++;
			}
			rs.close();
		} catch (SQLException e) {
			Log.error(e);
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (Exception e) {
				Log.error(e);
			}
			try {
				if (con != null) {
					con.close();
				}
			} catch (Exception e) {
				Log.error(e);
			}
		}
		return calls;
	}


    /**
     *
     * Read a callLog result set and return a CallLog instance with the information of the resultSet
     *
     * @param rs ResultSet
     * @return CallLog
     */
    private static CallLog read(ResultSet rs) {
		CallLog callLog = null;
		try {

			String username = rs.getString("username");
			String numA = rs.getString("addressFrom");
			String numB = rs.getString("addressTo");
			long dateTime = rs.getLong("datetime");
			int duration = rs.getInt("duration");
            String callType = rs.getString("calltype");
            if ("loss".equals(callType)) {
                // Backwards compatibility change
                callType = "missed";
            }
            CallLog.Type type = CallLog.Type.valueOf(callType);

			callLog = new CallLog(username);

			callLog.setNumA(numA);
			callLog.setNumB(numB);
			callLog.setDateTime(dateTime);
			callLog.setDuration(duration);
			callLog.setType(type);

		} catch (SQLException e) {
			Log.error(e.getMessage(), e);
		}
		return callLog;
	}

    /**
     *
     * Insert a new CallLog into the database
     *
     * @param callLog
     * @throws SQLException
     */
    public static void insert(CallLog callLog) throws SQLException {

		String sql = "INSERT INTO sipPhoneLog (username, addressFrom, addressTo, datetime, duration, calltype) "
				+ " values  (?, ?, ?, ?, ?, ?)";

		Connection con = null;
		PreparedStatement psmt = null;
		ResultSet rs = null;

		try {
			con = DbConnectionManager.getConnection();
			psmt = con.prepareStatement(sql);
			psmt.setString(1, callLog.getUsername());
			psmt.setString(2, callLog.getNumA());
			psmt.setString(3, callLog.getNumB());
			psmt.setLong(4, callLog.getDateTime());
			psmt.setInt(5, callLog.getDuration());
			psmt.setString(6, callLog.getType().name());

			psmt.executeUpdate();

		} catch (SQLException e) {
			Log.error(e.getMessage(), e);
			throw new SQLException(e.getMessage());
		} finally {
			DbConnectionManager.closeConnection(rs, psmt, con);
		}

	}

    /**
     * Gets all calls in database for the given range
     * @param startIndex
     * @param numResults
     * @return Collection<CallLog>
     */
    public static Collection<CallLog> getCalls(int startIndex, int numResults) {
		return getCalls("", startIndex, numResults);
	}

    /**
     * Return the number of callLog stored
     * @return int number
     */
    public static int getLogCount() {
		return getLogCount("");
	}

    /**
     *
     * Return the number of store callLogs for the given SQLCondition
     *
     * @param SQLCondition
     * @return  int number
     */
    public static int getLogCount(String SQLCondition) {
		int count = 0;

		String sql = "SELECT count(*) FROM sipPhoneLog";

		sql = SQLCondition != null && !SQLCondition.equals("") ? sql + " WHERE " + SQLCondition
				: sql;

		Connection con = null;
		PreparedStatement pstmt = null;
		try {
			con = DbConnectionManager.getConnection();
			pstmt = con.prepareStatement(sql);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getInt(1);
			}
			rs.close();
		} catch (SQLException e) {
			Log.error(e);
		} finally {
			try {
				if (pstmt != null) {
					pstmt.close();
				}
			} catch (Exception e) {
				Log.error(e);
			}
			try {
				if (con != null) {
					con.close();
				}
			} catch (Exception e) {
				Log.error(e);
			}
		}
		return count;
	}

    /**
     * Create a SQLFilter ( SQL Condition ) for CallLog entries
     *
     * @param username
     * @param numa
     * @param numb
     * @param callType
     * @param fromDate
     * @param uptoDate
     * @return String
     */
    public static String createSQLFilter(String username, String numa, String numb,
			String callType, Date fromDate, Date uptoDate) {

		ArrayList<String> conditions = new ArrayList<String>(10);

		if (username != null && !username.trim().equals(""))
			conditions.add(" username = '" + username.trim() + "' ");

		if (numa != null && !numa.trim().equals(""))
			conditions.add(" addressFrom = '" + numa.trim() + "' ");

		if (numb != null && !numb.trim().equals(""))
			conditions.add(" addressTo = '" + numb.trim() + "' ");

		if (fromDate != null)
			conditions.add(" datetime >= '" + fromDate.getTime() + "' ");

		if (uptoDate != null)
			conditions.add(" datetime <= '" + uptoDate.getTime() + "' ");

		if (callType != null && !callType.trim().equals("") && !callType.trim().equals("all"))
			conditions.add(" calltype = '" + callType.trim() + "' ");

		StringBuilder str = new StringBuilder();
		for (String aux : conditions) {
			if (str.length() > 0)
				str.append("AND");
			str.append(aux);
		}

		return str.toString();
	}

}

