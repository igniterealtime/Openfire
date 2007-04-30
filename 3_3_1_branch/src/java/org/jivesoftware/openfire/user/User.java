/**
 * $RCSfile$
 * $Revision: 1321 $
 * $Date: 2005-05-05 15:31:03 -0300 (Thu, 05 May 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.user;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.roster.Roster;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final String LOAD_PROPERTY =
        "SELECT propValue FROM jiveUserProp WHERE username=? AND name=?";
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
     * Returns the value of the specified property for the given username. This method is
     * an optimization to avoid loading a user to get a specific property.
     *
     * @param username the username of the user to get a specific property value.
     * @param propertyName the name of the property to return its value.
     * @return the value of the specified property for the given username.
     */
    public static String getPropertyValue(String username, String propertyName) {
        String propertyValue = null;
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, propertyName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                propertyValue = rs.getString(1);
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
        return propertyValue;
    }

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
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        try {
            AuthFactory.getAuthProvider().setPassword(username, password);

            // Fire event.
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("type", "passwordModified");
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UnsupportedOperationException uoe) {
            Log.error(uoe);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        try {
            String originalName = this.name;
            UserManager.getUserProvider().setName(username, name);
            this.name = name;

            // Fire event.
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("type", "nameModified");
            params.put("originalValue", originalName);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    /**
     * Returns the email address of the user or <tt>null</tt> if none is defined.
     *
     * @return the email address of the user or nullif none is defined.
     */
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        try {
            String originalEmail= this.email;
            UserManager.getUserProvider().setEmail(username, email);
            this.email = email;
            // Fire event.
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("type", "emailModified");
            params.put("originalValue", originalEmail);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        try {
            Date originalCreationDate = this.creationDate;
            UserManager.getUserProvider().setCreationDate(username, creationDate);
            this.creationDate = creationDate;

            // Fire event.
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("type", "creationDateModified");
            params.put("originalValue", originalCreationDate);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    public Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(Date modificationDate) {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        try {
            Date originalModificationDate = this.modificationDate;
            UserManager.getUserProvider().setCreationDate(username, modificationDate);
            this.modificationDate = modificationDate;

            // Fire event.
            Map<String,Object> params = new HashMap<String,Object>();
            params.put("type", "nameModified");
            params.put("originalValue", originalModificationDate);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe);
        }
    }

    /**
     * Returns all extended properties of the group. Groups
     * have an arbitrary number of extended properties.
     *
     * @return the extended properties.
     */
    public Map<String,String> getProperties() {
        synchronized (this) {
            if (properties == null) {
                properties = new ConcurrentHashMap<String, String>();
                loadProperties();
            }
        }
        // Return a wrapper that will intercept add and remove commands.
        return new PropertiesMap();
    }

    /**
     * Returns the user's roster. A roster is a list of users that the user wishes to know
     * if they are online. Rosters are similar to buddy groups in popular IM clients.
     *
     * @return the user's roster.
     */
    public Roster getRoster() {
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
        size += CacheSizes.sizeOfString(name);          // name
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

    /**
     * Map implementation that updates the database when properties are modified.
     */
    private class PropertiesMap extends AbstractMap {

        public Object put(Object key, Object value) {
            Map<String,Object> eventParams = new HashMap<String,Object>();
            Object answer;
            String keyString = (String) key;
            synchronized (keyString.intern()) {
                if (properties.containsKey(keyString)) {
                    String originalValue = properties.get(keyString);
                    answer = properties.put(keyString, (String)value);
                    updateProperty(keyString, (String)value);
                    // Configure event.
                    eventParams.put("type", "propertyModified");
                    eventParams.put("propertyKey", key);
                    eventParams.put("originalValue", originalValue);
                }
                else {
                    answer = properties.put(keyString, (String)value);
                    insertProperty(keyString, (String)value);
                    // Configure event.
                    eventParams.put("type", "propertyAdded");
                    eventParams.put("propertyKey", key);
                }
            }
            // Fire event.
            UserEventDispatcher.dispatchEvent(User.this,
                    UserEventDispatcher.EventType.user_modified, eventParams);
            return answer;
        }

        public Set<Entry> entrySet() {
            return new PropertiesEntrySet();
        }
    }

    /**
     * Set implementation that updates the database when properties are deleted.
     */
    private class PropertiesEntrySet extends AbstractSet {

        public int size() {
            return properties.entrySet().size();
        }

        public Iterator iterator() {
            return new Iterator() {

                Iterator iter = properties.entrySet().iterator();
                Map.Entry current = null;

                public boolean hasNext() {
                    return iter.hasNext();
                }

                public Object next() {
                    current = (Map.Entry)iter.next();
                    return current;
                }

                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    String key = (String)current.getKey();
                    deleteProperty(key);
                    iter.remove();
                    // Fire event.
                    Map<String,Object> params = new HashMap<String,Object>();
                    params.put("type", "propertyDeleted");
                    params.put("propertyKey", key);
                    UserEventDispatcher.dispatchEvent(User.this,
                        UserEventDispatcher.EventType.user_modified, params);
                }
            };
        }
    }

    private void loadProperties() {
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
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    private void insertProperty(String propName, String propValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, propName);
            pstmt.setString(3, propValue);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    private void updateProperty(String propName, String propValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, propValue);
            pstmt.setString(2, propName);
            pstmt.setString(3, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    private void deleteProperty(String propName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, propName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }
}