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

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserPropertiesProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Map;

/**
 * <p>Database implementation of the UserPropertiesProvider interface.</p>
 *
 * @author Matt Tucker
 * @author Bruce Ritchie
 * @author Iain Shigeoka
 *
 * @see User
 */
public class DbUserPropertiesProvider implements UserPropertiesProvider {

    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM jiveUserProp WHERE username=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM jiveUserProp WHERE username=? AND name=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE jiveUserProp SET propValue=? WHERE name=? AND username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveUserProp (username, name, propValue) VALUES (?, ?, ?)";
    private static final String LOAD_VPROPERTIES =
        "SELECT name, propValue FROM jiveVCard WHERE username=?";
    private static final String DELETE_VPROPERTY =
        "DELETE FROM jiveVCard WHERE username=? AND name=?";
    private static final String UPDATE_VPROPERTY =
        "UPDATE jiveVCard SET propValue=? WHERE name=? AND username=?";
    private static final String INSERT_VPROPERTY =
        "INSERT INTO jiveVCard (username, name, propValue) VALUES (?, ?, ?)";

    /**
     * Create a new DbUserPropertiesProvider.
     */
    public DbUserPropertiesProvider() {
    }

    /**
     * Loads properties from the database.
     */
    private synchronized Map loadPropertiesFromDb(String username, boolean isVcard) {
        Map props = new Hashtable();
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            if (isVcard) {
                pstmt = con.prepareStatement(LOAD_VPROPERTIES);
            }
            else {
                pstmt = con.prepareStatement(LOAD_PROPERTIES);
            }
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                props.put(rs.getString(1), rs.getString(2));
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
        return props;
    }

    /**
     * Inserts a new property into the datatabase.
     */
    private void insertPropertyIntoDb(String username, String name, String value, boolean isVcard) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            if (isVcard) {
                pstmt = con.prepareStatement(INSERT_VPROPERTY);
            }
            else {
                pstmt = con.prepareStatement(INSERT_PROPERTY);
            }
            pstmt.setString(1, username);
            pstmt.setString(2, name);
            pstmt.setString(3, value);
            pstmt.executeUpdate();
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

    /**
     * Updates a property value in the database.
     */
    private void updatePropertyInDb(String username, String name, String value, boolean isVcard) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (isVcard) {
                pstmt = con.prepareStatement(UPDATE_VPROPERTY);
            }
            else {
                pstmt = con.prepareStatement(UPDATE_PROPERTY);
            }
            pstmt.setString(1, value);
            pstmt.setString(2, name);
            pstmt.setString(3, username);
            pstmt.executeUpdate();
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

    /**
     * Deletes a property from the db.
     */
    private void deletePropertyFromDb(String username, String name, boolean isVcard) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            if (isVcard) {
                pstmt = con.prepareStatement(DELETE_VPROPERTY);
            }
            else {
                pstmt = con.prepareStatement(DELETE_PROPERTY);
            }
            pstmt.setString(1, username);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
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

    public void deleteVcardProperty(String username, String name) {
        deletePropertyFromDb(username, name, true);
    }

    public void deleteUserProperty(String username, String name) {
        deletePropertyFromDb(username, name, false);
    }

    public void insertVcardProperty(String username, String name, String value) {
        insertPropertyIntoDb(username, name, value, true);
    }

    public void insertUserProperty(String username, String name, String value) {
        insertPropertyIntoDb(username, name, value, false);
    }

    public void updateVcardProperty(String username, String name, String value) {
        updatePropertyInDb(username, name, value, true);
    }

    public void updateUserProperty(String username, String name, String value) {
        updatePropertyInDb(username, name, value, false);
    }

    public Map getVcardProperties(String username) {
        return loadPropertiesFromDb(username, true);
    }

    public Map getUserProperties(String username) {
        return loadPropertiesFromDb(username, false);
    }
}
