/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.util.*;
import org.jivesoftware.messenger.NodePrep;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.*;
import org.jivesoftware.database.DbConnectionManager;

import java.util.Iterator;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Database implementation of the UserManager interface.
 * It uses the DbUser class along with the jiveUser database
 * table to store and manipulate user information.<p>
 * <p/>
 * This UserManager implementation uses two caches to vastly improve speed:
 * <ul>
 * <li> id2userCache
 * <li> name2idCache
 * </ul><p>
 * <p/>
 * If making your own UserManager implementation, it's
 * highly recommended that you also use these caches.
 *
 * @author Iain Shigeoka
 */
public class UserManagerImpl extends BasicModule implements UserManager {

    private static final String USER_COUNT = "SELECT count(*) FROM jiveUser";
    private static final String ALL_USERS = "SELECT username from jiveUser";
    private static final String INSERT_USER =
        "INSERT INTO jiveUser (username,password,name,email,creationDate,modificationDate) " +
        "VALUES (?,?,?,?,?,?)";
    private static final String DELETE_USER_GROUPS =
        "DELETE FROM jiveGroupUser WHERE username=?";
    private static final String DELETE_USER_PROPS =
        "DELETE FROM jiveUserProp WHERE username=?";
    private static final String DELETE_VCARD_PROPS =
        "DELETE FROM jiveVCard WHERE username=?";
    private static final String DELETE_USER =
        "DELETE FROM jiveUser WHERE username=?";

    private Cache userCache;

    public UserManagerImpl() {
        super("User Manager");
    }

    private void initializeCaches() {
        CacheManager.initializeCache("userCache", 512*1024);
        CacheManager.initializeCache("username2roster", 512*1024);
        userCache = CacheManager.getCache("userCache");
    }

    public User createUser(String username, String password, String email)
            throws UserAlreadyExistsException, UnauthorizedException {
        User newUser = null;
        // Strip extra or invisible characters from the username so that existing
        // usernames can't be forged.
        username = NodePrep.prep(username);
        username = StringUtils.replace(username, "&nbsp;", "");
        try {
            getUser(username);

            // The user already exists since no exception, so:
            throw new UserAlreadyExistsException();
        }
        catch (UserNotFoundException unfe) {
            // The user doesn't already exist so we can create a new user
            try {
                if (email == null || email.length() == 0) {
                    email = " ";
                }
                Connection con = null;
                PreparedStatement pstmt = null;
                boolean abortTransaction = false;
                try {
                    con = DbConnectionManager.getTransactionConnection();
                    pstmt = con.prepareStatement(INSERT_USER);
                    pstmt.setString(1, username);
                    pstmt.setString(2, password);
                    pstmt.setString(3, "");
                    pstmt.setString(5, email);
                    Date now = new Date();
                    pstmt.setString(7, StringUtils.dateToMillis(now));
                    pstmt.setString(8, StringUtils.dateToMillis(now));
                    pstmt.execute();
                }
                catch (Exception e) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
                    abortTransaction = true;
                }
                finally {
                    try { if (pstmt != null) { pstmt.close(); } }
                    catch (Exception e) { Log.error(e); }
                    DbConnectionManager.closeTransactionConnection(con, abortTransaction);
                }
                newUser = getUser(username);
            }
            catch (UserNotFoundException e) {
                throw new UnauthorizedException("Created an account but could not load it. username: "
                        + username + " pass: " + password + " email: " + email);
            }
        }
        return newUser;
    }

    public User getUser(String username) throws UserNotFoundException {
        if (username == null) {
            throw new UserNotFoundException("Username with null value is not valid.");
        }
        username = NodePrep.prep(username);
        User user = (User)userCache.get(username);
        if (user == null) {
            user = loadUser(username);
        }
        else {
            user = (User)userCache.get(username);
        }
        if (user == null) {
            throw new UserNotFoundException();
        }

        return user;
    }

    private User loadUser(String username) {
        User user = new UserImpl(username);
        userCache.put(username, user);
        return user;
    }

    public void deleteUser(User user) throws UnauthorizedException {
        String username = user.getUsername();
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            // Remove user from all groups
            pstmt = con.prepareStatement(DELETE_USER_GROUPS);
            pstmt.setString(1, username);
            pstmt.execute();
            pstmt.close();
            // Delete all of the users's extended properties
            pstmt = con.prepareStatement(DELETE_USER_PROPS);
            pstmt.setString(1, username);
            pstmt.execute();
            pstmt.close();
            // Delete all of the users's vcard properties
            pstmt = con.prepareStatement(DELETE_VCARD_PROPS);
            pstmt.setString(1, username);
            pstmt.execute();
            pstmt.close();
            // Delete the actual user entry
            pstmt = con.prepareStatement(DELETE_USER);
            pstmt.setString(1, username);
            pstmt.execute();
        }
        catch (Exception e) {
            Log.error(e);
            abortTransaction = true;
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        // Expire user cache.
        userCache.remove(user.getUsername());
    }

    public int getUserCount() {
        int count = 0;
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(USER_COUNT);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return count;
    }

    public Iterator users() throws UnauthorizedException {
        List<String> users = new ArrayList<String>(500);
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            // Set the fetch size. This will prevent some JDBC drivers from trying
            // to load the entire result set into memory.
            DbConnectionManager.setFetchSize(rs, 500);
            while (rs.next()) {
                users.add(rs.getString(1));
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return new UserIterator((String [])users.toArray(new String[users.size()]));
    }

    public Iterator users(int startIndex, int numResults) throws UnauthorizedException {
        List<String> users = new ArrayList<String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(ALL_USERS);
            ResultSet rs = pstmt.executeQuery();
            DbConnectionManager.setFetchSize(rs, startIndex + numResults);
            // Move to start of index
            for (int i = 0; i < startIndex; i++) {
                rs.next();
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (rs.next()) {
                    users.add(rs.getString(1));
                }
                else {
                    break;
                }
            }
            rs.close();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return new UserIterator((String [])users.toArray(new String[users.size()]));
    }

    // #####################################################################
    // Module management
    // #####################################################################

    public void initialize(Container container) {
        super.initialize(container);
        initializeCaches();
    }
}