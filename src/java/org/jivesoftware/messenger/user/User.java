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

package org.jivesoftware.messenger.user;

import org.jivesoftware.messenger.roster.CachedRoster;
import org.jivesoftware.messenger.XMPPServer;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.database.DbConnectionManager;

import java.util.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Encapsulates information about a user. New users are created using
 * {@link UserManager#createUser(String, String, String, String)}. All user
 * properties are loaded on demand and are read from the <tt>jiveUserProp</tt>
 * database table. The currently-installed {@link UserProvider} is used for
 * setting all other user data and some operations may not be supported
 * depending on the capabilities of the {@link UserProvider}.
 *
 * @author Matt Tucker
 */
public class User implements Cacheable {

    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM jiveUserProp WHERE username=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM jiveUserProp WHERE username=? AND name=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE jiveUserProp SET propValue=? WHERE name=? AND username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveUserProp (username, name, propValue) VALUES (?, ?, ?)";

    private String username;
    private String name;
    private String email;
    private Date creationDate;
    private Date modificationDate;

    private Map<String,String> properties = null;

    /**
     * Constructs a new user. All arguments can be <tt>null</tt> except the username.
     * Typically, User objects should not be constructed by end-users of the API.
     * Instead, user objects should be retrieved using {@link UserManager#getUser(String)}.
     *
     * @param username the username.
     * @param name the name.
     * @param email the email address.
     * @param creationDate the date the user was created.
     * @param modificationDate the date the user was last modified.
     */
    public User(String username, String name, String email, Date creationDate,
            Date modificationDate)
    {
        if (username == null) {
            throw new NullPointerException("Username cannot be null");
        }
        this.username = username;
        this.name = name;
        this.email = email;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
    }

    /**
     * Returns this user's username.
     *
     * @return the username..
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets a new password for this user.
     *
     * @param password the new password for the user.
     */
    public void setPassword(String password) {
        try {
            UserManager.getUserProvider().setPassword(username, password);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        try {
            UserManager.getUserProvider().setName(username, name);
            this.name = name;
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        try {
            UserManager.getUserProvider().setEmail(username, email);
            this.email = email;
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        try {
            UserManager.getUserProvider().setCreationDate(username, creationDate);
            this.creationDate = creationDate;
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) {
        try {
            UserManager.getUserProvider().setCreationDate(username, modificationDate);
            this.modificationDate = modificationDate;
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    /**
     * Returns an extended property of the user. Each user can have an arbitrary number of extended
     * properties. This lets particular skins or filters provide enhanced functionality that is not
     * part of the base interface.
     *
     * @param name the name of the property to get.
     * @return the value of the property
     */
    public String getProperty(String name) {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        return properties.get(name);
    }

    /**
     * Sets an extended property of the user. Each user can have an arbitrary number of extended
     * properties. This lets particular skins or filters provide enhanced functionality that is not
     * part of the base interface. Property names and values must be valid Strings. If <tt>null</tt>
     * or an empty length String is used, a NullPointerException will be thrown.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     */
    public void setProperty(String name, String value) {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        // Make sure the property name and value aren't null.
        if (name == null || value == null || "".equals(name) || "".equals(value)) {
            throw new NullPointerException("Cannot set property with empty or null value.");
        }
        // See if we need to update a property value or insert a new one.
        if (properties.containsKey(name)) {
            // Only update the value in the database if the property value
            // has changed.
            if (!(value.equals(properties.get(name)))) {
                properties.put(name, value);
                updatePropertyInDb(name, value);
            }
        }
        else {
            properties.put(name, value);
            insertPropertyIntoDb(name, value);
        }
    }

    /**
     * Deletes an extended property. If the property specified by <code>name</code> does not exist,
     * this method will do nothing.
     *
     * @param name the name of the property to delete.
     */
    public void deleteProperty(String name) {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        properties.remove(name);
        deletePropertyFromDb(name);
    }

    /**
     * Returns a Collection of all the names of the extended user properties.
     *
     * @return a Collection of all the property names.
     */
    public Collection<String> getPropertyNames() {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        return Collections.unmodifiableCollection(properties.keySet());
    }

    /**
     * Returns the user's roster. A roster is a list of users that the user wishes to know
     * if they are online. Rosters are similar to buddy groups in popular IM clients.
     *
     * @return the user's roster.
     */
    public CachedRoster getRoster() {
        try {
            return XMPPServer.getInstance().getRosterManager().getRoster(username);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
            return null;
        }
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfLong();                // id
        size += CacheSizes.sizeOfString(username);      // username
        size += CacheSizes.sizeOfString(email);         // email
        size += CacheSizes.sizeOfDate() * 2;            // creationDate and modificationDate
        size += CacheSizes.sizeOfMap(properties);       // properties
        return size;
    }

    public String toString() {
        return username;
    }

    public int hashCode() {
        return username.hashCode();
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof User) {
            return username.equals(((User)object).getUsername());
        }
        else {
            return false;
        }
    }

    private synchronized void loadPropertiesFromDb() {
        properties = new Hashtable<String,String>();
        Connection con = null;
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
    }

    private void insertPropertyIntoDb(String name, String value) {
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

    private void updatePropertyInDb(String name, String value) {
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

    private void deletePropertyFromDb(String name) {
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
