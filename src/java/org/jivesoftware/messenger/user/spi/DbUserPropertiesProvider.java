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
import org.jivesoftware.database.DbConnectionManager;

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
            "SELECT name, propValue FROM jiveUserProp WHERE userID=?";
    private static final String DELETE_PROPERTY =
            "DELETE FROM jiveUserProp WHERE userID=? AND name=?";
    private static final String UPDATE_PROPERTY =
            "UPDATE jiveUserProp SET propValue=? WHERE name=? AND userID=?";
    private static final String INSERT_PROPERTY =
            "INSERT INTO jiveUserProp (userID, name, propValue) VALUES (?, ?, ?)";
    private static final String LOAD_VPROPERTIES =
            "SELECT name, propValue FROM jiveVCard WHERE userID=?";
    private static final String DELETE_VPROPERTY =
            "DELETE FROM jiveVCard WHERE userID=? AND name=?";
    private static final String UPDATE_VPROPERTY =
            "UPDATE jiveVCard SET propValue=? WHERE name=? AND userID=?";
    private static final String INSERT_VPROPERTY =
            "INSERT INTO jiveVCard (userID, name, propValue) VALUES (?, ?, ?)";

    /**
     * Create a new DbUserPropertiesProvider.
     */
    public DbUserPropertiesProvider() {
    }

    /**
     * Loads properties from the database.
     */
    private synchronized Map loadPropertiesFromDb(long id, boolean isVcard) {
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
            pstmt.setLong(1, id);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                props.put(rs.getString(1), rs.getString(2));
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
        return props;
    }

    /**
     * Inserts a new property into the datatabase.
     */
    private void insertPropertyIntoDb(long id, String name, String value, boolean isVcard) {
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
            pstmt.setLong(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, value);
            pstmt.executeUpdate();
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

    /**
     * Updates a property value in the database.
     */
    private void updatePropertyInDb(long id, String name, String value, boolean isVcard) {
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
            pstmt.setLong(3, id);
            pstmt.executeUpdate();
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

    /**
     * Deletes a property from the db.
     */
    private void deletePropertyFromDb(long id, String name, boolean isVcard) {
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
            pstmt.setLong(1, id);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
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

    public void deleteVcardProperty(long id, String name) {
        deletePropertyFromDb(id, name, true);
    }

    public void deleteUserProperty(long id, String name) {
        deletePropertyFromDb(id, name, false);
    }

    public void insertVcardProperty(long id, String name, String value) {
        insertPropertyIntoDb(id, name, value, true);
    }

    public void insertUserProperty(long id, String name, String value) {
        insertPropertyIntoDb(id, name, value, false);
    }

    public void updateVcardProperty(long id, String name, String value) {
        updatePropertyInDb(id, name, value, true);
    }

    public void updateUserProperty(long id, String name, String value) {
        updatePropertyInDb(id, name, value, false);
    }

    public Map getVcardProperties(long id) {
        return loadPropertiesFromDb(id, true);
    }

    public Map getUserProperties(long id) {
        return loadPropertiesFromDb(id, false);
    }
}
