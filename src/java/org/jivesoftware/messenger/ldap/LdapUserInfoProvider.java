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
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.UserInfo;
import org.jivesoftware.messenger.user.UserInfoProvider;
import org.jivesoftware.messenger.user.UserNotFoundException;
import org.jivesoftware.messenger.user.spi.BasicUserInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;

/**
 * LDAP implementation of the UserInfoProvider interface. The LdapUserIDProvider
 * can operate in two modes -- in the pure LDAP mode, all user data is stored in
 * the LDAP store. This mode generally requires modifications to the LDAP schema
 * to accommodate data that Messenger needs. In the mixed mode, data that Messenger
 * needs is stored locally.
 *
 * @author Jim Berrettini
 */
public class LdapUserInfoProvider implements UserInfoProvider {

    private static final String LOAD_USER_BY_ID =
        "SELECT name, nameVisible, email, emailVisible, " +
        "creationDate, modificationDate FROM jiveUser WHERE userID=?";
    private static final String INSERT_USER =
        "INSERT INTO jiveUser (userID, password, name, nameVisible, " +
        "email, emailVisible, creationDate, modificationDate) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String SAVE_USER =
        "UPDATE jiveUser SET name=?, nameVisible=?, email=?," +
        "emailVisible=?, creationDate=?, modificationDate=? WHERE " +
        "userID=?";

    private LdapManager manager;

    /**
     * Constructor initializes the internal LdapManager instance.
     */
    public LdapUserInfoProvider() {
        manager = LdapManager.getInstance();
    }

    /**
     * <p>Obtain the UserInfo of a user. Will retrieve either from LDAP or locally, depending on mode of operation.</p>
     *
     * @param id
     * @return a user info object.
     * @throws UserNotFoundException
     */
    public UserInfo getInfo(long id) throws UserNotFoundException {
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) {
            return getInfoFromLdap(id);
        }
        UserInfo info = null;
        try {
            info = getInfoFromDb(id);
        }
        catch (UserNotFoundException e) {
            info = generateNewUserInfoInDb(id);
        }
        return info;
    }

    /**
     * <p>Sets the user's info. In pure LDAP mode, this is unsupported.</p>
     *
     * @param id   user ID for setting info.
     * @param info to set.
     * @throws UserNotFoundException
     * @throws UnauthorizedException
     * @throws UnsupportedOperationException
     */
    public void setInfo(long id, UserInfo info)
            throws UserNotFoundException, UnauthorizedException, UnsupportedOperationException {
        if (manager.getMode() == LdapManager.ALL_LDAP_MODE) { // can't do this in LDAP
            throw new UnsupportedOperationException("All LDAP mode: Cannot modify data in LDAP.");
        }
        // in mixed mode, update the database.
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SAVE_USER);
            pstmt.setString(1, info.getName());
            pstmt.setInt(2, info.isNameVisible() ? 1 : 0);
            pstmt.setString(3, info.getEmail());
            pstmt.setInt(4, info.isEmailVisible() ? 1 : 0);
            pstmt.setString(5, StringUtils.dateToMillis(info.getCreationDate()));
            pstmt.setString(6, StringUtils.dateToMillis(info.getModificationDate()));
            pstmt.setLong(7, id);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            throw new UnauthorizedException();
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    /**
     * Pure LDAP method for getting info for a given userID.
     *
     * @param id of user.
     * @return UserInfo for that user.
     * @throws UserNotFoundException
     */
    private UserInfo getInfoFromLdap(long id) throws UserNotFoundException {
        BasicUserInfo userInfo = null;
        DirContext ctx = null;
        try {
            String userDN = null;
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
            userDN = ((SearchResult)answer.next()).getName();

            // Load record.
            String[] attributes = new String[]{
                "jiveUserID", manager.getUsernameField(), manager.getNameField(),
                manager.getEmailField(), "jiveNameVisible",
                "jiveEmailVisible", "jiveCDate", "jiveMDate", "jiveProps"
            };
            Attributes attrs = ctx.getAttributes(userDN, attributes);
            id = Long.parseLong((String)attrs.get("jiveUserID").get());
            String username = (String)attrs.get(manager.getUsernameField()).get();
            String name = null;
            String email = null;
            boolean nameVisible = false;
            boolean emailVisible = false;
            Date creationDate, modificationDate;
            Attribute nameField = attrs.get(manager.getNameField());
            if (nameField != null) {
                name = (String)nameField.get();
            }
            Attribute emailField = attrs.get(manager.getEmailField());
            if (emailField != null) {
                email = (String)emailField.get();
            }
            nameVisible = new Boolean((String)attrs.get("jiveNameVisible").get()).booleanValue();
            emailVisible = new Boolean((String)attrs.get("jiveEmailVisible").get()).booleanValue();
            creationDate = new Date(Long.parseLong((String)attrs.get("jiveCDate").get()));
            modificationDate = new Date(Long.parseLong((String)attrs.get("jiveMDate").get()));
            userInfo = new BasicUserInfo(id, name, email, nameVisible, emailVisible, creationDate, modificationDate);
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
        return userInfo;
    }

    /**
     * Mixed mode method for retrieving User Info for a given user ID.
     *
     * @param id for user.
     * @return UserInfo for user.
     * @throws UserNotFoundException
     */
    private UserInfo getInfoFromDb(long id) throws UserNotFoundException {
        BasicUserInfo userInfo = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_USER_BY_ID);
            pstmt.setLong(1, id);

            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException();
            }
            // We trim() the dates before trying to parse them because some
            // databases pad with extra characters when returning the data.
            userInfo = new BasicUserInfo(id,
                    rs.getString(1), // name
                    rs.getString(3), // email
                    rs.getInt(2) == 1, // name visible
                    rs.getInt(4) == 1, // email visible
                    new java.util.Date(Long.parseLong(rs.getString(5).trim())), // creation date
                    new java.util.Date(Long.parseLong(rs.getString(6).trim()))); // modification date

        }
        catch (SQLException e) {
            throw new UserNotFoundException("Failed to read user " + id + " from database.", e);
        }
        catch (NumberFormatException nfe) {
            Log.error("WARNING: There was an error parsing the dates " +
                    "returned from the database. Ensure that they're being stored " +
                    "correctly.");
            throw new UserNotFoundException("User with id "
                    + id + " could not be loaded from the database.");
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return userInfo;
    }

    /**
     * Mixed mode method for creating default UserInfo locally for a new user.
     *
     * @param id
     * @return UserInfo for that user.
     */
    private UserInfo generateNewUserInfoInDb(long id) {
        Connection con = null;
        PreparedStatement pstmt = null;
        Date now = new Date();
        try {
            // Add the user record in jiveUser
            pstmt = con.prepareStatement(INSERT_USER);
            pstmt.setLong(1, id);
            pstmt.setString(2, "");
            pstmt.setString(3, "");
            pstmt.setInt(4, 1); // name visible
            pstmt.setString(5, "");
            pstmt.setInt(6, 0); // email visible
            pstmt.setString(7, StringUtils.dateToMillis(now));
            pstmt.setString(8, StringUtils.dateToMillis(now));
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return new BasicUserInfo(id,
                "", // name
                "", // email
                true, // name visible
                false, // email visible
                now, // creation date
                now);
    }

}