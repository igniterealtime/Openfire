/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.user;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * The JDBC user provider allows you to use an external database to define the users.
 * It is best used with the JDBCAuthProvider & JDBCGroupProvider to provide integration
 * between your external system and Openfire. All data is treated as read-only so any
 * set operations will result in an exception.<p/>
 *
 * For the seach facility, the SQL will be constructed from the SQL in the <i>search</i>
 * section below, as well as the <i>usernameField</i>, the <i>nameField</i> and the
 * <i>emailField</i>.<p/>
 *
 * To enable this provider, set the following in the system properties:<p/>
 *
 * <ul>
 * <li><tt>provider.user.className = org.jivesoftware.openfire.user.JDBCUserProvider</tt></li>
 * </ul>
 *
 * Then you need to set your driver, connection string and SQL statements:
 * <p/>
 * <ul>
 * <li><tt>jdbcProvider.driver = com.mysql.jdbc.Driver</tt></li>
 * <li><tt>jdbcProvider.connectionString = jdbc:mysql://localhost/dbname?user=username&amp;password=secret</tt></li>
 * <li><tt>jdbcUserProvider.loadUserSQL = SELECT name,email FROM myUser WHERE user = ?</tt></li>
 * <li><tt>jdbcUserProvider.userCountSQL = SELECT COUNT(*) FROM myUser</tt></li>
 * <li><tt>jdbcUserProvider.allUsersSQL = SELECT user FROM myUser</tt></li>
 * <li><tt>jdbcUserProvider.searchSQL = SELECT user FROM myUser WHERE</tt></li>
 * <li><tt>jdbcUserProvider.usernameField = myUsernameField</tt></li>
 * <li><tt>jdbcUserProvider.nameField = myNameField</tt></li>
 * <li><tt>jdbcUserProvider.emailField = mymailField</tt></li>
 * </ul>
 *
 * @author Huw Richards huw.richards@gmail.com
 */
public class JDBCUserProvider implements UserProvider {

	private String connectionString;

	private String loadUserSQL;
	private String userCountSQL;
	private String allUsersSQL;
	private String searchSQL;
	private String usernameField;
	private String nameField;
	private String emailField;

    /**
     * Constructs a new JDBC user provider.
     */
    public JDBCUserProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("jdbcProvider.driver");
        JiveGlobals.migrateProperty("jdbcProvider.connectionString");
        JiveGlobals.migrateProperty("jdbcUserProvider.loadUserSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.userCountSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.allUsersSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.searchSQL");
        JiveGlobals.migrateProperty("jdbcUserProvider.usernameField");
        JiveGlobals.migrateProperty("jdbcUserProvider.nameField");
        JiveGlobals.migrateProperty("jdbcUserProvider.emailField");

        // Load the JDBC driver and connection string.
		String jdbcDriver = JiveGlobals.getProperty("jdbcProvider.driver");
		try {
			Class.forName(jdbcDriver).newInstance();
		}
		catch (Exception e) {
			Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
			return;
		}
		connectionString = JiveGlobals.getProperty("jdbcProvider.connectionString");

        // Load database statements for user data.
        loadUserSQL = JiveGlobals.getProperty("jdbcUserProvider.loadUserSQL");
		userCountSQL = JiveGlobals.getProperty("jdbcUserProvider.userCountSQL");
		allUsersSQL = JiveGlobals.getProperty("jdbcUserProvider.allUsersSQL");
		searchSQL = JiveGlobals.getProperty("jdbcUserProvider.searchSQL");
		usernameField = JiveGlobals.getProperty("jdbcUserProvider.usernameField");
		nameField = JiveGlobals.getProperty("jdbcUserProvider.nameField");
		emailField = JiveGlobals.getProperty("jdbcUserProvider.emailField");
	}

	public User loadUser(String username) throws UserNotFoundException {
        if(username.contains("@")) {
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0,username.lastIndexOf("@"));
        }
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
        try {
			con = DriverManager.getConnection(connectionString);
			pstmt = con.prepareStatement(loadUserSQL);
			pstmt.setString(1, username);
			rs = pstmt.executeQuery();
			if (!rs.next()) {
				throw new UserNotFoundException();
			}
			String name = rs.getString(1);
			String email = rs.getString(2);

			return new User(username, name, email, new Date(), new Date());
		}
		catch (Exception e) {
			throw new UserNotFoundException(e);
		}
		finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
	}

	public int getUserCount() {
		int count = 0;
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
        try {
			con = DriverManager.getConnection(connectionString);
			pstmt = con.prepareStatement(userCountSQL);
			rs = pstmt.executeQuery();
			if (rs.next()) {
				count = rs.getInt(1);
			}
		}
		catch (SQLException e) {
			Log.error(e);
		}
		finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
		return count;
	}

	public Collection<User> getUsers() {
		Collection<String> usernames = getUsernames();
		return new UserCollection(usernames.toArray(new String[usernames.size()]));
	}

	public Collection<String> getUsernames() {
		List<String> usernames = new ArrayList<String>(500);
		Connection con = null;
		PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
			con = DriverManager.getConnection(connectionString);
			pstmt = con.prepareStatement(allUsersSQL);
			rs = pstmt.executeQuery();
			// Set the fetch size. This will prevent some JDBC drivers from trying
			// to load the entire result set into memory.
			DbConnectionManager.setFetchSize(rs, 500);
			while (rs.next()) {
				Log.debug("JDBCUserProvider: "+rs.getString(1));
				usernames.add(rs.getString(1));
			}
		}
		catch (SQLException e) {
			Log.error(e);
		}
		finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
		return usernames;
	}

	public Collection<User> getUsers(int startIndex, int numResults) {
		List<String> usernames = new ArrayList<String>(numResults);
		Connection con = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
        try {
			con = DriverManager.getConnection(connectionString);
			pstmt = DbConnectionManager.createScrollablePreparedStatement(con, allUsersSQL);
			rs = pstmt.executeQuery();
			DbConnectionManager.setFetchSize(rs, startIndex + numResults);
			DbConnectionManager.scrollResultSet(rs, startIndex);
			int count = 0;
			while (rs.next() && count < numResults) {
				Log.debug("JDBCUserProvider: "+rs.getString(1));
				usernames.add(rs.getString(1));
				count++;
			}
		}
		catch (SQLException e) {
			Log.error(e);
		}
		finally {
			DbConnectionManager.closeConnection(rs, pstmt, con);
		}
		return new UserCollection(usernames.toArray(new String[usernames.size()]));
	}

	public User createUser(String username, String password, String name, String email)
			throws UserAlreadyExistsException {
		// Reject the operation since the provider is read-only
		throw new UnsupportedOperationException();
	}

	public void deleteUser(String username) {
		// Reject the operation since the provider is read-only
		throw new UnsupportedOperationException();
	}

	public void setName(String username, String name) throws UserNotFoundException {
		// Reject the operation since the provider is read-only
		throw new UnsupportedOperationException();
	}

	public void setEmail(String username, String email) throws UserNotFoundException {
		// Reject the operation since the provider is read-only
		throw new UnsupportedOperationException();
	}

	public void setCreationDate(String username, Date creationDate) throws UserNotFoundException {
		// Reject the operation since the provider is read-only
		throw new UnsupportedOperationException();
	}

	public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException {
		// Reject the operation since the provider is read-only
		throw new UnsupportedOperationException();
	}

	public Collection<User> findUsers(Set<String> fields, String query)
			throws UnsupportedOperationException
    {
		if (searchSQL == null) {
            throw new UnsupportedOperationException();
        }
        if (fields.isEmpty()) {
			return Collections.emptyList();
		}
		if (!getSearchFields().containsAll(fields)) {
			throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
		}
		if (query == null || "".equals(query)) {
			return Collections.emptyList();
		}
		// SQL LIKE queries don't map directly into a keyword/wildcard search like we want.
		// Therefore, we do a best approximiation by replacing '*' with '%' and then
		// surrounding the whole query with two '%'. This will return more data than desired,
		// but is better than returning less data than desired.
		query = "%" + query.replace('*', '%') + "%";
		if (query.endsWith("%%")) {
			query = query.substring(0, query.length() - 1);
		}

		List<String> usernames = new ArrayList<String>(50);
		Connection con = null;
		Statement stmt = null;
		ResultSet rs = null;
        try {
			con = DriverManager.getConnection(connectionString);
			stmt = con.createStatement();
			StringBuilder sql = new StringBuilder();
			sql.append(searchSQL);
			boolean first = true;
			if (fields.contains("Username")) {
				sql.append(" ")
						.append(usernameField)
						.append(" LIKE '")
						.append(StringUtils.escapeForSQL(query)).append("'");
				first = false;
			}
			if (fields.contains("Name")) {
				if (!first) {
					sql.append(" AND ");
				}
				sql.append(" ")
						.append(nameField)
						.append(" LIKE '")
						.append(StringUtils.escapeForSQL(query)).append("'");
				first = false;
			}
			if (fields.contains("Email")) {
				if (!first) {
					sql.append(" AND ");
				}
				sql.append(" ")
						.append(emailField)
						.append(" LIKE '")
						.append(StringUtils.escapeForSQL(query)).append("'");
			}
			Log.debug("JDBCUserProvider: "+sql.toString());
			rs = stmt.executeQuery(sql.toString());
			while (rs.next()) {
				usernames.add(rs.getString(1));
			}
		}
		catch (SQLException e) {
			Log.error(e);
		}
		finally {
			DbConnectionManager.closeConnection(rs, stmt, con);
		}
		return new UserCollection(usernames.toArray(new String[usernames.size()]));
	}

	public Collection<User> findUsers(Set<String> fields, String query, int startIndex,
            int numResults) throws UnsupportedOperationException
    {
		if (searchSQL == null) {
            throw new UnsupportedOperationException();
        }
        if (fields.isEmpty()) {
			return Collections.emptyList();
		}
		if (!getSearchFields().containsAll(fields)) {
			throw new IllegalArgumentException("Search fields " + fields + " are not valid.");
		}
		if (query == null || "".equals(query)) {
			return Collections.emptyList();
		}
		// SQL LIKE queries don't map directly into a keyword/wildcard search like we want.
		// Therefore, we do a best approximiation by replacing '*' with '%' and then
		// surrounding the whole query with two '%'. This will return more data than desired,
		// but is better than returning less data than desired.
		query = "%" + query.replace('*', '%') + "%";
		if (query.endsWith("%%")) {
			query = query.substring(0, query.length() - 1);
		}

		List<String> usernames = new ArrayList<String>(50);
		Connection con = null;
		Statement stmt = null;
        ResultSet rs = null;
        try {
			con = DriverManager.getConnection(connectionString);
			stmt = con.createStatement();
			StringBuilder sql = new StringBuilder();
			sql.append(searchSQL);
			boolean first = true;
			if (fields.contains("Username")) {
				sql.append(" ")
						.append(usernameField)
						.append(" LIKE '")
						.append(StringUtils.escapeForSQL(query)).append("'");
				first = false;
			}
			if (fields.contains("Name")) {
				if (!first) {
					sql.append(" AND ");
				}
				sql.append(" ")
						.append(nameField)
						.append("LIKE '")
						.append(StringUtils.escapeForSQL(query)).append("'");
				first = false;
			}
			if (fields.contains("Email")) {
				if (!first) {
					sql.append(" AND ");
				}
				sql.append(" ")
						.append(emailField)
						.append(" LIKE '")
						.append(StringUtils.escapeForSQL(query)).append("'");
			}
			Log.debug("JDBCUserProvider: "+sql.toString());
			rs = stmt.executeQuery(sql.toString());
			// Scroll to the start index.
			DbConnectionManager.scrollResultSet(rs, startIndex);
			while (rs.next()) {
				usernames.add(rs.getString(1));
			}
		}
		catch (SQLException e) {
			Log.error(e);
		}
		finally {
			DbConnectionManager.closeConnection(rs, stmt, con);
		}
		return new UserCollection(usernames.toArray(new String[usernames.size()]));
	}

	public Set<String> getSearchFields() throws UnsupportedOperationException {
        if (searchSQL == null) {
            throw new UnsupportedOperationException();
        }
        return new LinkedHashSet<String>(Arrays.asList("Username", "Name", "Email"));
	}

	public boolean isReadOnly() {
		return true;
	}

    public boolean isNameRequired() {
        return false;
    }

    public boolean isEmailRequired() {
        return false;
    }
}
