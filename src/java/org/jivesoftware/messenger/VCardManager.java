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

package org.jivesoftware.messenger;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.Cache;
import org.jivesoftware.util.CacheManager;

import java.util.*;
import java.sql.*;
import java.sql.Connection;

/**
 * Manages VCard information for users.
 *
 * @author Matt Tucker
 */
public class VCardManager {

    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM jiveVCard WHERE username=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM jiveVCard WHERE username=? AND name=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE jiveVCard SET propValue=? WHERE name=? AND username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveVCard (username, name, propValue) VALUES (?, ?, ?)";


    private static VCardManager instance = new VCardManager();

    public static VCardManager getInstance() {
        return instance;
    }

    private Cache vcardCache;

    private VCardManager() {
        CacheManager.initializeCache("vcardCache", 512 * 1024);
        vcardCache = CacheManager.getCache("vcardCache");
    }

    /**
     * Obtains the user's vCard information for a given vcard property name.
     * Advanced user systems can use vCard
     * information to link to user directory information or store other
     * relevant user information.
     *
     * @param name The name of the vcard property to retrieve
     * @return The vCard value found
     */
    public String getVCardProperty(String username, String name) {
        Map<String,String> properties = (Map<String,String>)vcardCache.get(username);
        if (properties == null) {
            properties = loadPropertiesFromDb(username);
            vcardCache.put(username, properties);
        }
        return properties.get(name);
    }

    /**
     * Sets the user's vCard information. Advanced user systems can use vCard
     * information to link to user directory information or store other
     * relevant user information.
     *
     * @param name  The name of the vcard property
     * @param value The value of the vcard property
     */
    public void setVCardProperty(String username, String name, String value) {
        Map<String,String> properties = (Map<String,String>)vcardCache.get(username);
        if (properties == null) {
            properties = loadPropertiesFromDb(username);
            vcardCache.put(username, properties);
        }
        // See if we need to update a property value or insert a new one.
        if (properties.containsKey(name)) {
            // Only update the value in the database if the property value
            // has changed.
            if (!(value.equals(properties.get(name)))) {
                properties.put(name, value);
                updatePropertyInDb(username, name, value);
            }
        }
        else {
            properties.put(name, value);
            insertPropertyIntoDb(username, name, value);
        }
    }

    /**
     * Deletes a given vCard property from the user account.
     *
     * @param name The name of the vcard property to remove
     */
    public void deleteVCardProperty(String username, String name) {
        Map<String,String> properties = (Map<String,String>)vcardCache.get(username);
        if (properties == null) {
            properties = loadPropertiesFromDb(username);
            vcardCache.put(username, properties);
        }
        properties.remove(name);
        deletePropertyFromDb(username, name);
    }

    /**
     * Obtain an iterator for all vcard property names.
     *
     * @return the iterator over all vcard property names.
     */
    public Collection<String> getVCardPropertyNames(String username) {
        Map<String,String> properties = (Map<String,String>)vcardCache.get(username);
        if (properties == null) {
            properties = loadPropertiesFromDb(username);
            vcardCache.put(username, properties);
        }
        return Collections.unmodifiableCollection(properties.keySet());
    }

    private Map<String,String> loadPropertiesFromDb(String username) {
        synchronized (username.intern()) {
            Map<String,String> properties = new Hashtable<String,String>();
            java.sql.Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_PROPERTIES);
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    properties.put(rs.getString(1), rs.getString(2));
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
            return properties;
        }
    }

    private void insertPropertyIntoDb(String username, String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;

        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
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

    private void updatePropertyInDb(String username, String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
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

    private void deletePropertyFromDb(String username, String name) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
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
}