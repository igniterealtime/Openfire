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

package org.jivesoftware.messenger.ldap;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.LongList;
import org.jivesoftware.messenger.user.UserIDProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.spi.DbUserIDProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

/**
 * LDAP implementation of the UserIDProvider interface.<p>
 *
 * The LdapUserIDProvider can operate in two modes -- in the pure LDAP mode,
 * all user data is stored in the LDAP store. This mode generally requires
 * modifications to the LDAP schema to accommodate data that Messenger needs.
 * In the mixed mode, data that Messenger needs is stored locally.
 *
 * @author Jim Berrettini
 */
public class LdapUserIDProvider implements UserIDProvider {

    private LdapManager manager;
    /**
     * Object type for users.
     */
    public static final int USER_TYPE = 0;
    /**
     * Object type for chatbots.
     */
    public static final int CHATBOT_TYPE = 1;
    /**
     * The default domain id - the messenger domain.
     */
    public static final long DEFAULT_DOMAIN = 1;

    private static final String GET_USERID =
        "SELECT objectID FROM jiveUserID WHERE username=? AND domainID=? AND objectType=?";
    private static final String GET_USERNAME =
        "SELECT username FROM jiveUserID WHERE objectID=? AND domainID=? AND objectType=?";
    private static final String USER_COUNT =
        "SELECT count(*) FROM jiveUser";
    private static final String INSERT_USERID =
        "INSERT INTO jiveUserID (username,domainID,objectType,objectID) VALUES (?,?,?,?)";
    private static final String ALL_USERS =
        "SELECT userID from jiveUser";

    public LdapUserIDProvider() {
        manager = LdapManager.getInstance();
    }

    public String getUsername(long id) throws UserNotFoundException {
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) {
            return getUsernameFromLdap(id);
        }
        else {
            return getUsernameFromDb(id);
        }
    }

    public long getUserID(String username) throws UserNotFoundException {
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) {
            return getUserIDFromLdap(username);
        }
        else {
            long id = 0L;
            try {
                id = getUserIDLocally(username);
            }
            catch (UserNotFoundException e) {
                id = generateNewUserIDLocally(username);
            }
            return id;
        }
    }

    public int getUserCount() {
        int count = 0;
        // If using the pure LDAP mode.
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) {
            // Note: the performance of this check may suffer badly for very large
            // numbers of users since we manually iterate through results to get
            // a count.
            DirContext ctx = null;
            try {
                ctx = manager.getContext();
                // Search for the dn based on the username.
                SearchControls constraints = new SearchControls();
                constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
                constraints.setReturningAttributes(new String[]{"jiveUserID"});
                String filter = "(jiveUserID=*)";
                NamingEnumeration answer = ctx.search("", filter, constraints);
                while (answer.hasMoreElements()) {
                    count++;
                    answer.nextElement();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            finally {
                try { if (ctx != null) { ctx.close(); } }
                catch (Exception e) { Log.error(e); }
            }
        }
        // Otherwise, we're using the mixed LDAP mode.
        else {
            Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(USER_COUNT);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    count = rs.getInt(1);
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
        }
        return count;
    }

    public LongList getUserIDs() {
        LongList users = new LongList(500);

        if (manager.getMode() == LdapManager.LDAP_DB_MODE) { // if in mixed mode, get id's from DB.
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
                    users.add(rs.getLong(1));
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
            return users;
        }
        // Otherwise, in LDAP-only mode.
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{"jiveUserID"});
            String filter = "(jiveUserID=*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);

            while (answer.hasMoreElements()) {
                // Get the next userID.
                users.add(Long.parseLong((String)(((SearchResult)answer.next()).getAttributes().get("jiveUserID")).get()));
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return users;
    }

    public LongList getUserIDs(int startIndex, int numResults) {
        LongList users = new LongList();
        if (manager.getMode() == LdapManager.LDAP_DB_MODE) { // if in mixed mode, get id's from DB.
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
                        users.add(rs.getLong(1));
                    }
                    else {
                        break;
                    }
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
            return users;
        }
        // else, in LDAP-only mode
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the username.
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{"jiveUserID"});
            String filter = "(jiveUserID=*)";
            NamingEnumeration answer = ctx.search("", filter, constraints);
            for (int i = 0; i < startIndex; i++) {
                answer.next();
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next userID.
                    users.add(Long.parseLong((String)(((SearchResult)answer.next()).getAttributes().get("jiveUserID")).get()));
                }
                else {
                    break;
                }
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return users;
    }

    /**
     * This is used when operating in mixed mode -- generate a new user ID for a
     * user stored in our database.
     *
     * @param username the username.
     * @return id corresponding to that username
     * @throws UserNotFoundException if an error occured generating the user ID.
     */
    private long generateNewUserIDLocally(String username) throws UserNotFoundException {
        long id = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            id = SequenceManager.nextID(JiveConstants.USER);
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_USERID);
            pstmt.setString(1, username);
            pstmt.setLong(2, DbUserIDProvider.DEFAULT_DOMAIN);
            pstmt.setLong(3, DbUserIDProvider.USER_TYPE);
            pstmt.setLong(4, id);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
            throw new UserNotFoundException(e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        return id;
    }

    /**
     * This is used when operating in mixed mode -- get user ID for a user stored locally.
     *
     * @param username
     * @return user id corresponding to that username.
     * @throws UserNotFoundException
     */
    private long getUserIDLocally(String username) throws UserNotFoundException {
        long id = -1;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USERID);
            pstmt.setString(1, username);
            pstmt.setLong(2, DEFAULT_DOMAIN);
            pstmt.setLong(3, USER_TYPE);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                id = rs.getLong(1);
            }
            rs.close();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        if (id == -1) {
            throw new UserNotFoundException();
        }
        return id;
    }

    /**
     * This method is used when operating in pure LDAP mode. Get user ID from the LDAP store.
     *
     * @param username the username.
     * @return user id corresponding to that username.
     * @throws UserNotFoundException if ther was an error loading the username
     *      from LDAP.
     */
    private long getUserIDFromLdap(String username) throws UserNotFoundException {
        DirContext ctx = null;
        try {
            String userDN = manager.findUserDN(username);
            ctx = manager.getContext();
            String[] attributes = new String[]{"jiveUserID"};
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            return Long.parseLong((String)attrs.get("jiveUserID").get());
        }
        catch (Exception e) {
            Log.error(e);
            throw new UserNotFoundException(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * This method is used in mixed mode. Get the username that corresponds to a given ID.
     *
     * @param id the user ID.
     * @return username for that user id.
     * @throws UserNotFoundException if there was an error loading the username.
     */
    private String getUsernameFromDb(long id) throws UserNotFoundException {
        String name = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_USERNAME);
            pstmt.setLong(1, id);
            pstmt.setLong(2, DEFAULT_DOMAIN);
            pstmt.setLong(3, USER_TYPE);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                name = rs.getString(1);
            }
            rs.close();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
        if (name == null) {
            throw new UserNotFoundException();
        }
        return name;
    }

    /**
     * This method is used when operating in pure LDAP mode. Get the username
     * that corresponds to a given ID.
     *
     * @param id the userID.
     * @return ther username.
     * @throws UserNotFoundException
     */
    private String getUsernameFromLdap(long id) throws UserNotFoundException {
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            SearchControls constraints = new SearchControls();
            constraints.setSearchScope(SearchControls.SUBTREE_SCOPE);
            constraints.setReturningAttributes(new String[]{"jiveUserID"});
            StringBuffer filter = new StringBuffer();
            filter.append("(").append("jiveUserID").append("=");
            filter.append(id).append(")");
            NamingEnumeration answer = ctx.search("", filter.toString(), constraints);
            if (answer == null || !answer.hasMoreElements()) {
                throw new UserNotFoundException("User not found: " + id);
            }
            String userDN = ((SearchResult)answer.next()).getName();
            return getUsernameFromUserDN(userDN);
        }
        catch (NamingException e) {
            Log.error(e);
            throw new UserNotFoundException(e);
        }
        catch (UserNotFoundException e) {
            Log.error(e);
            throw new UserNotFoundException(e);
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Helper function to retrieve username from userDN.
     *
     * @param userDN
     * @return username
     * @throws NamingException
     */
    private String getUsernameFromUserDN(String userDN) throws NamingException {
        DirContext ctx = null;
        try {
            ctx = manager.getContext();
            // Load record.
            String[] attributes = new String[]{manager.getUsernameField()};
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            return (String)attrs.get(manager.getUsernameField()).get();
        }
        finally {
            try { if (ctx != null) { ctx.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }
}