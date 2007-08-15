/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;

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
 * To enable this provider, set the following in the XML configuration file:<p/>
 * <pre>
 * &lt;provider&gt;
 *     &lt;user&gt;
 *         &lt;className&gt;org.jivesoftware.openfire.user.JDBCUserProvider&lt;/className&gt;
 *     &lt;/user&gt;
 * &lt;/provider&gt;
 * </pre><p/>
 *
 * Then you need to set your driver, connection string and SQL statements:
 * <p/>
 * <pre>
 * &lt;jdbcProvider&gt;
 *     &lt;driver&gt;com.mysql.jdbc.Driver&lt;/driver&gt;
 *     &lt;connectionString&gt;jdbc:mysql://localhost/dbname?user=username&amp;password=secret&lt;/connectionString&gt;
 * &lt;/jdbcProvider&gt;
 *
 * &lt;jdbcUserProvider&gt;
 *      &lt;loadUserSQL&gt;SELECT name,email FROM myUser WHERE user = ?&lt;/loadUserSQL&gt;
 *      &lt;userCountSQL&gt;SELECT COUNT(*) FROM myUser&lt;/userCountSQL&gt;
 *      &lt;allUsersSQL&gt;SELECT user FROM myUser&lt;/allUsersSQL&gt;
 *      &lt;searchSQL&gt;SELECT user FROM myUser WHERE&lt;/searchSQL&gt;
 *      &lt;usernameField&gt;myUsernameField&lt;/usernameField&gt;
 *      &lt;nameField&gt;myNameField&lt;/nameField&gt;
 *      &lt;emailField&gt;mymailField&lt;/emailField&gt;
 * &lt;/jdbcUserProvider&gt;
 * </pre>
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
		// Load the JDBC driver and connection string.
		String jdbcDriver = JiveGlobals.getXMLProperty("jdbcProvider.driver");
		try {
			Class.forName(jdbcDriver).newInstance();
		}
		catch (Exception e) {
			Log.error("Unable to load JDBC driver: " + jdbcDriver, e);
			return;
		}
		connectionString = JiveGlobals.getXMLProperty("jdbcProvider.connectionString");

        // Load database statements for user data.
        loadUserSQL = JiveGlobals.getXMLProperty("jdbcUserProvider.loadUserSQL");
		userCountSQL = JiveGlobals.getXMLProperty("jdbcUserProvider.userCountSQL");
		allUsersSQL = JiveGlobals.getXMLProperty("jdbcUserProvider.allUsersSQL");
		searchSQL = JiveGlobals.getXMLProperty("jdbcUserProvider.searchSQL");
		usernameField = JiveGlobals.getXMLProperty("jdbcUserProvider.usernameField");
		nameField = JiveGlobals.getXMLProperty("jdbcUserProvider.nameField");
		emailField = JiveGlobals.getXMLProperty("jdbcUserProvider.emailField");
	}

	public User loadUser(String username) throws UserNotFoundException {
        String userDomain = JiveGlobals.getProperty("xmpp.domain");
        if(username.contains("@")) {
            userDomain = username.substring((username.lastIndexOf("@")+1));
            username = username.substring(0,username.lastIndexOf("@"));
        }
        if(!userDomain.equals(JiveGlobals.getProperty("xmpp.domain"))) {
            throw new UserNotFoundException("Unknown domain: "+userDomain);
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
				Log.debug(rs.getString(1));
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
				Log.debug(rs.getString(1));
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
			Log.debug(sql.toString());
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
			Log.debug(sql.toString());
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
}
