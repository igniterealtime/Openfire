/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.user;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.ConnectionException;
import org.jivesoftware.openfire.auth.InternalUnauthenticatedException;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.resultsetmanagement.Result;

/**
 * Encapsulates information about a user. New users are created using
 * {@link UserManager#createUser(String, String, String, String)}. All user
 * properties are loaded on demand and are read from the <tt>ofUserProp</tt>
 * database table. The currently-installed {@link UserProvider} is used for
 * setting all other user data and some operations may not be supported
 * depending on the capabilities of the {@link UserProvider}.
 *
 * @author Matt Tucker
 */
public class User implements Cacheable, Externalizable, Result {

	private static final Logger Log = LoggerFactory.getLogger(User.class);

    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM ofUserProp WHERE username=?";
    private static final String LOAD_PROPERTY =
        "SELECT propValue FROM ofUserProp WHERE username=? AND name=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM ofUserProp WHERE username=? AND name=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE ofUserProp SET propValue=? WHERE name=? AND username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO ofUserProp (username, name, propValue) VALUES (?, ?, ?)";

    // The name of the name visible property
    private static final String NAME_VISIBLE_PROPERTY = "name.visible";
    // The name of the email visible property
    private static final String EMAIL_VISIBLE_PROPERTY = "email.visible";

    private String username;
    private String salt;
    private String storedKey;
    private String serverKey;
    private int iterations;
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
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, propertyName);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                propertyValue = rs.getString(1);
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return propertyValue;
    }

    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public User() {
    }

    /**
     * Constructs a new user. Normally, all arguments can be <tt>null</tt> except the username.
     * However, a UserProvider -may- require a name or email address.  In those cases, the
     * isNameRequired or isEmailRequired UserProvider tests indicate whether <tt>null</tt> is allowed.
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
        if (UserManager.getUserProvider().isNameRequired() && (name == null || "".equals(name.trim()))) {
            throw new IllegalArgumentException("Invalid or empty name specified with provider that requires name");
        }
        this.name = name;
        if (UserManager.getUserProvider().isEmailRequired() && (email == null || "".equals(email.trim()))) {
            throw new IllegalArgumentException("Empty email address specified with provider that requires email address. User: "
                                                + username + " Email: " + email);
        }
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
     * @throws UnsupportedOperationException exception
     */
    public void setPassword(String password) throws UnsupportedOperationException {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        try {
            AuthFactory.setPassword(username, password);

            // Fire event.
            Map<String,Object> params = new HashMap<>();
            params.put("type", "passwordModified");
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException | ConnectionException | InternalUnauthenticatedException e) {
            Log.error(e.getMessage(), e);
        }
    }
    
    public String getStoredKey() {
    	return storedKey;
    }
    
    public void setStoredKey(String storedKey) {
    	this.storedKey = storedKey;
    }
    
    public String getServerKey() {
    	return serverKey;
    }
    
    public void setServerKey(String serverKey) {
    	this.serverKey = serverKey;
    }
    
    public String getSalt() {
    	return salt;
    }
    
    public void setSalt(String salt) {
    	this.salt = salt;
    }
    
    public int getIterations() {
    	return iterations;
    }
    
    public void setIterations(int iterations) {
    	this.iterations = iterations;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        if (name != null && name.matches("\\s*")) {
        	name = null;
        }

        if (name == null && UserManager.getUserProvider().isNameRequired()) {
            throw new IllegalArgumentException("User provider requires name.");
        }

        try {
            String originalName = this.name;
            UserManager.getUserProvider().setName(username, name);
            this.name = name;

            // Fire event.
            Map<String,Object> params = new HashMap<>();
            params.put("type", "nameModified");
            params.put("originalValue", originalName);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe.getMessage(), unfe);
        }
    }

    /**
     * Returns true if name is visible to everyone or not.
     *
     * @return true if name is visible to everyone, false if not.
     */
    public boolean isNameVisible() {
        return !getProperties().containsKey(NAME_VISIBLE_PROPERTY) || Boolean.valueOf(getProperties().get(NAME_VISIBLE_PROPERTY));
    }

    /**
     * Sets if name is visible to everyone or not.
     *
     * @param visible true if name is visible, false if not.
     */
    public void setNameVisible(boolean visible) {
        getProperties().put(NAME_VISIBLE_PROPERTY, String.valueOf(visible));
    }

    /**
     * Returns the email address of the user or <tt>null</tt> if none is defined.
     *
     * @return the email address of the user or null if none is defined.
     */
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (UserManager.getUserProvider().isReadOnly()) {
            throw new UnsupportedOperationException("User provider is read-only.");
        }

        if (email != null && email.matches("\\s*")) {
        	email = null;
        }

        if (UserManager.getUserProvider().isEmailRequired() && !StringUtils.isValidEmailAddress(email)) {
            throw new IllegalArgumentException("User provider requires email address.");
        }

        try {
            String originalEmail= this.email;
            UserManager.getUserProvider().setEmail(username, email);
            this.email = email;
            // Fire event.
            Map<String,Object> params = new HashMap<>();
            params.put("type", "emailModified");
            params.put("originalValue", originalEmail);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe.getMessage(), unfe);
        }
    }

    /**
     * Returns true if email is visible to everyone or not.
     *
     * @return true if email is visible to everyone, false if not.
     */
    public boolean isEmailVisible() {
        return !getProperties().containsKey(EMAIL_VISIBLE_PROPERTY) || Boolean.valueOf(getProperties().get(EMAIL_VISIBLE_PROPERTY));
    }

    /**
     * Sets if the email is visible to everyone or not.
     *
     * @param visible true if the email is visible, false if not.
     */
    public void setEmailVisible(boolean visible) {
        getProperties().put(EMAIL_VISIBLE_PROPERTY, String.valueOf(visible));
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
            Map<String,Object> params = new HashMap<>();
            params.put("type", "creationDateModified");
            params.put("originalValue", originalCreationDate);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe.getMessage(), unfe);
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
            Map<String,Object> params = new HashMap<>();
            params.put("type", "nameModified");
            params.put("originalValue", originalModificationDate);
            UserEventDispatcher.dispatchEvent(this, UserEventDispatcher.EventType.user_modified,
                    params);
        }
        catch (UserNotFoundException unfe) {
            Log.error(unfe.getMessage(), unfe);
        }
    }

    /**
     * Returns all extended properties of the user. Users have an arbitrary
     * number of extended properties. The returned collection can be modified
     * to add new properties or remove existing ones.
     *
     * @return the extended properties.
     */
    public Map<String,String> getProperties() {
        synchronized (this) {
            if (properties == null) {
                properties = new ConcurrentHashMap<>();
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
            Log.error(unfe.getMessage(), unfe);
            return null;
        }
    }

    @Override
    public int getCachedSize()
            throws CannotCalculateSizeException {
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

    @Override
	public String toString() {
        return username;
    }

    @Override
	public int hashCode() {
        return username.hashCode();
    }

    @Override
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
    private class PropertiesMap extends AbstractMap<String, String> {

        @Override
		public String put(String key, String value) {
            Map<String,Object> eventParams = new HashMap<>();
            String answer;
            String keyString = key;

            synchronized (getName() + keyString.intern()) {
                if (properties.containsKey(keyString)) {
                    String originalValue = properties.get(keyString);
                    answer = properties.put(keyString, value);
                    updateProperty(keyString, value);
                    // Configure event.
                    eventParams.put("type", "propertyModified");
                    eventParams.put("propertyKey", key);
                    eventParams.put("originalValue", originalValue);
                }
                else {
                    answer = properties.put(keyString, value);
                    insertProperty(keyString, value);
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

        @Override
		public Set<Entry<String, String>> entrySet() {
            return new PropertiesEntrySet();
        }
    }

    /**
     * Set implementation that updates the database when properties are deleted.
     */
    private class PropertiesEntrySet extends AbstractSet<Map.Entry<String, String>> {

        @Override
        public int size() {
            return properties.entrySet().size();
        }

        @Override
		public Iterator<Map.Entry<String, String>> iterator() {
            return new Iterator<Map.Entry<String, String>>() {

                Iterator<Map.Entry<String, String>> iter = properties.entrySet().iterator();
                Map.Entry<String,String> current = null;

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Map.Entry<String, String> next() {
                    current = iter.next();
                    return current;
                }

                @Override
                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    String key = current.getKey();
                    deleteProperty(key);
                    iter.remove();
                    // Fire event.
                    Map<String,Object> params = new HashMap<>();
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
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                properties.put(rs.getString(1), rs.getString(2));
            }
        }
        catch (SQLException sqle) {
            Log.error(sqle.getMessage(), sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
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
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
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
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
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
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, username);
        ExternalizableUtil.getInstance().writeSafeUTF(out, getName());
        ExternalizableUtil.getInstance().writeBoolean(out, email != null);
        if (email != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, email);
        }
        ExternalizableUtil.getInstance().writeLong(out, creationDate.getTime());
        ExternalizableUtil.getInstance().writeLong(out, modificationDate.getTime());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        username = ExternalizableUtil.getInstance().readSafeUTF(in);
        name = ExternalizableUtil.getInstance().readSafeUTF(in);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            email = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        creationDate = new Date(ExternalizableUtil.getInstance().readLong(in));
        modificationDate = new Date(ExternalizableUtil.getInstance().readLong(in));
    }

    /*
     * (non-Javadoc)
     * @see org.jivesoftware.util.resultsetmanager.Result#getUID()
     */
	@Override
	public String getUID()
	{
		return username;
	}
}
