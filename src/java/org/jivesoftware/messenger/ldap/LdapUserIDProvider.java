/**
 *
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2003 JiveSoftware. All rights reserved.
 *
 * This software is the proprietary information of Jive Software.
 * Use is subject to license terms.
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
import org.jivesoftware.database.DbConnectionManager;

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
 * <p>Ldap implementation of the UserIDProvider interface.</p>
 * <p>The LdapUserIDProvider can operate in two modes -- in the pure LDAP mode, all user data is stored in the LDAP
 * store. This mode generally requires modifications to the LDAP schema to accommodate data that Messenger needs.</p>
 * <p>In the mixed mode, data that Messenger needs is stored locally.</p>
 * * @author Jim Berrettini
 */


public class LdapUserIDProvider implements UserIDProvider {
    private LdapManager manager;
    /**
     * <p>Object type for users.</p>
     */
    public static final int USER_TYPE = 0;
    /**
     * <p>Object type for chatbots.</p>
     */
    public static final int CHATBOT_TYPE = 1;
    /**
     * <p>The default domain id - the messenger domain.</p>
     */
    public static final long DEFAULT_DOMAIN = 1;

    private static final String GET_USERID = "SELECT objectID FROM jiveUserID WHERE username=? AND domainID=? AND objectType=?";
    private static final String GET_USERNAME = "SELECT username FROM jiveUserID WHERE objectID=? AND domainID=? AND objectType=?";
    private static final String USER_COUNT = "SELECT count(*) FROM jiveUser";
    private static final String INSERT_USERID = "INSERT INTO jiveUserID (username,domainID,objectType,objectID) VALUES (?,?,?,?)";
    private static final String ALL_USERS = "SELECT userID from jiveUser";

    public LdapUserIDProvider() {
        manager = LdapManager.getInstance();
    }

    /**
     * <p>Obtain the user's username from their ID.</p>
     *
     * @param id the userID of the user
     * @return the name of the user with the given userID
     * @throws UserNotFoundException if no such user exists
     */
    public String getUsername(long id) throws UserNotFoundException {
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) {
            // Find userDN.
            DirContext ctx = null;
            try {
                ctx = manager.getContext();
                // Search for the dn based on the username.
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
                // Make sure there are no more search results. If there are, then
                // the userID isn't unique on the LDAP server (a perfectly possible
                // scenario since only fully qualified dn's need to be unqiue).
                // There really isn't a way to handle this, so throw an exception.
                // The baseDN must be set correctly so that this doesn't happen.
                if (answer.hasMoreElements()) {
                    throw new UserNotFoundException("LDAP username lookup matched multiple entries.");
                }
            }
            catch (Exception e) {
                throw new UserNotFoundException(e);
            }
            finally {
                try {
                    ctx.close();
                }
                catch (Exception e) {
                }
            }
            return getUsernameFromLdap(id);
        }
        else {
            return getUsernameFromDb(id);
        }
    }


    /**
     * <p>Obtain the user's username from their ID.</p>
     *
     * @param username the username to look up
     * @return the userID corrresponding to the given username
     * @throws UserNotFoundException
     */
    public long getUserID(String username) throws UserNotFoundException {
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) {
            return getUserIDFromLdap(username);
        }

        long id = 0L;
        try {
            id = getUserIDLocally(username);
        }
        catch (UserNotFoundException e) {
            id = generateNewUserIDLocally(username);
        }
        return id;
    }

    /**
     * <p>Obtain the total number of users on the system.</p>
     *
     * @return total number of users on the system.
     */
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
                try {
                    ctx.close();
                }
                catch (Exception e) {
                }
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
            }
            catch (SQLException e) {
                Log.error(e);
            }
            finally {
                try {
                    if (pstmt != null) {
                        pstmt.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
                try {
                    if (con != null) {
                        con.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }
        return count;
    }

    /**
     * <p>Obtain a list all user IDs on the system.</p>
     *
     * @return LongList of user ID's
     */
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
            }
            catch (SQLException e) {
                Log.error(e);
            }
            finally {
                try {
                    if (pstmt != null) {
                        pstmt.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
                try {
                    if (con != null) {
                        con.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
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

            while (answer.hasMoreElements()) {
                // Get the next userID.
                users.add(Long.parseLong((String)(((SearchResult)answer.next()).getAttributes().get("jiveUserID")).get()));
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                ctx.close();
            }
            catch (Exception e) {
            }
        }
        return users;
    }

    /**
     * Get paginated sublist of userID's
     *
     * @param startIndex index to begin sublist with.
     * @param numResults maximum number of results to return.
     * @return sublist of userID's.
     */
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
                try {
                    if (pstmt != null) {
                        pstmt.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
                try {
                    if (con != null) {
                        con.close();
                    }
                }
                catch (Exception e) {
                    Log.error(e);
                }
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
            try {
                ctx.close();
            }
            catch (Exception e) {
            }
        }
        return users;
    }

    /**
     * This is used when operating in mixed mode -- generate a new user ID for a user stored in our database.
     *
     * @param username
     * @return id corresponding to that username
     * @throws UserNotFoundException
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
            pstmt.execute();
        }
        catch (SQLException e) {
            Log.error(e);
            throw new UserNotFoundException(e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
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
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (id == -1) {
            throw new UserNotFoundException();
        }
        return id;
    }

    /**
     * This method is used when operating in pure LDAP mode. Get user ID from the LDAP store.
     *
     * @param username
     * @return user id corresponding to that username.
     * @throws UserNotFoundException
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
            try {
                ctx.close();
            }
            catch (Exception e) {
            }
        }

    }

    /**
     * This method is used in mixed mode. Get the username that corresponds to a given ID.
     *
     * @param id
     * @return username for that user id.
     * @throws UserNotFoundException
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
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try {
                if (pstmt != null) {
                    pstmt.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
            try {
                if (con != null) {
                    con.close();
                }
            }
            catch (Exception e) {
                Log.error(e);
            }
        }
        if (name == null) {
            throw new UserNotFoundException();
        }
        return name;
    }

    /**
     * This method is used when operating in pure LDAP mode. Get the username that corresponds to a given ID.
     *
     * @param id
     * @return
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
            try {
                ctx.close();
            }
            catch (Exception e) {
            }
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
            try {
                ctx.close();
            }
            catch (Exception e) {
            }
        }

    }
}
