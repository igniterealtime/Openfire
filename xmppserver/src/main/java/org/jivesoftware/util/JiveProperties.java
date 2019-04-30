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

package org.jivesoftware.util;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retrieves and stores Jive properties. Properties are stored in the database.
 *
 * @author Matt Tucker
 */
public class JiveProperties implements Map<String, String> {

    private static final Logger Log = LoggerFactory.getLogger(JiveProperties.class);

    private static final String LOAD_PROPERTIES = "SELECT name, propValue, encrypted, iv FROM ofProperty";
    private static final String INSERT_PROPERTY = "INSERT INTO ofProperty(name, propValue, encrypted, iv) VALUES(?,?,?,?)";
    private static final String UPDATE_PROPERTY = "UPDATE ofProperty SET propValue=?, encrypted=?, iv=? WHERE name=?";
    private static final String DELETE_PROPERTY = "DELETE FROM ofProperty WHERE name LIKE ?";

    private static JiveProperties instance = null;

    // The map of property keys to their values
    private Map<String, String> properties;
    // The map of property keys to a boolean indicating if they are encrypted or not
    private Map<String, Boolean> encrypted;

    /**
     * Returns a singleton instance of JiveProperties.
     *
     * @return an instance of JiveProperties.
     */
    public synchronized static JiveProperties getInstance() {
        if (instance == null) {
            JiveProperties props = new JiveProperties();
            props.init();
            instance = props;
        }
        return instance;
    }
    private JiveProperties() { }

    /**
     * For internal use only. This method allows for the reloading of all properties from the
     * values in the database. This is required since it's quite possible during the setup
     * process that a database connection will not be available till after this class is
     * initialized. Thus, if there are existing properties in the database we will want to reload
     * this class after the setup process has been completed.
     */
    public void init() {
        if (properties == null) {
            properties = new ConcurrentHashMap<>();
            encrypted = new ConcurrentHashMap<>();
        }
        else {
            properties.clear();
            encrypted.clear();
        }

        loadProperties();
    }

    @Override
    public int size() {
        return properties.size();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return properties.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return properties.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return properties.containsValue(value);
    }

    @Override
    public Collection<String> values() {
        return Collections.unmodifiableCollection(properties.values());
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> t) {
        for (Map.Entry<? extends String, ? extends String> entry : t.entrySet() ) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public Set<Map.Entry<String, String>> entrySet() {
        return Collections.unmodifiableSet(properties.entrySet());
    }

    @Override
    public Set<String> keySet() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    @Override
    public String get(Object key) {
        return properties.get(key);
    }

    /**
     * Indicates the encryption status for the given property.
     * 
     * @param name
     *            The name of the property
     * @return {@code true} if the property exists and is encrypted, otherwise {@code false}
     */
    boolean isEncrypted(final String name) {
        if (name == null) {
            return false;
        }
        final Boolean isEncrypted = encrypted.get(name);
        return isEncrypted != null && isEncrypted;
    }

    /**
     * Set the encryption status for the given property.
     *
     * @param name
     *            The name of the property
     * @param encrypt
     *            True to encrypt the property, false to decrypt
     * @return {@code true} if the property's encryption status changed, otherwise {@code false}
     */
    boolean setPropertyEncrypted(String name, boolean encrypt) {
        final boolean encryptionWasChanged = name != null && properties.containsKey(name) && isEncrypted(name) != encrypt;
        if (encryptionWasChanged) {
            final String value = get(name);
            put(name, value, encrypt);
        }
        return encryptionWasChanged;
    }
    
    /**
     * Return all children property names of a parent property as a Collection
     * of String objects. For example, given the properties {@code X.Y.A},
     * {@code X.Y.B}, and {@code X.Y.C}, then the child properties of
     * {@code X.Y} are {@code X.Y.A}, {@code X.Y.B}, and {@code X.Y.C}. The method
     * is not recursive; ie, it does not return children of children.
     *
     * @param parentKey the name of the parent property.
     * @return all child property names for the given parent.
     */
    public Collection<String> getChildrenNames(String parentKey) {
        Collection<String> results = new HashSet<>();
        for (String key : properties.keySet()) {
            if (key.startsWith(parentKey + ".")) {
                if (key.equals(parentKey)) {
                    continue;
                }
                int dotIndex = key.indexOf(".", parentKey.length()+1);
                if (dotIndex < 1) {
                    if (!results.contains(key)) {
                        results.add(key);
                    }
                }
                else {
                    String name = parentKey + key.substring(parentKey.length(), dotIndex);
                    results.add(name);
                }
            }
        }
        return results;
    }

    /**
     * Returns all property names as a Collection of String values.
     *
     * @return all property names.
     */
    public Collection<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public String remove(Object key) {
        String value;
        synchronized (this) {
            value = properties.remove(key);
            // Also remove any children.
            Collection<String> propNames = getPropertyNames();
            for (String name : propNames) {
                if (name.startsWith((String)key)) {
                    properties.remove(name);
                }
            }
            deleteProperty((String)key);
        }

        // Generate event.
        Map<String, Object> params = Collections.emptyMap();
        PropertyEventDispatcher.dispatchEvent((String)key, PropertyEventDispatcher.EventType.property_deleted, params);

        // Send update to other cluster members.
        CacheFactory.doClusterTask(PropertyClusterEventTask.createDeleteTask((String) key));

        return value;
    }

    void localRemove(String key) {
        properties.remove(key);
        // Also remove any children.
        Collection<String> propNames = getPropertyNames();
        for (String name : propNames) {
            if (name.startsWith(key)) {
                properties.remove(name);
            }
        }

        // Generate event.
        Map<String, Object> params = Collections.emptyMap();
        PropertyEventDispatcher.dispatchEvent(key, PropertyEventDispatcher.EventType.property_deleted, params);
    }

    /**
     * Saves a property, optionally encrypting it
     * 
     * @param key
     *            The name of the property
     * @param value
     *            The value of the property
     * @param isEncrypted
     *            {@code true} to encrypt the property, {@code true} to leave in plain text
     * @return The previous value associated with {@code key}, or {@code null} if there was no mapping for
     *         {@code key}.
     */
    public String put(String key, String value, boolean isEncrypted) {
        if (value == null) {
            // This is the same as deleting, so remove it.
            return remove(key);
        }
        if (key == null) {
            throw new NullPointerException("Key cannot be null. Key=" +
                    key + ", value=" + value);
        }
        if (key.endsWith(".")) {
            key = key.substring(0, key.length()-1);
        }
        key = key.trim();
        String result;
        synchronized (this) {
            if (properties.containsKey(key)) {
                updateProperty(key, value, isEncrypted);
            }
            else {
                insertProperty(key, value, isEncrypted);
            }

            result = properties.put(key, value);
            encrypted.put(key, isEncrypted);
            // We now know the database is correct - so we can remove the entry from security.conf
            JiveGlobals.clearXMLPropertyEncryptionEntry(key);
        }

        // Generate event.
        Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        PropertyEventDispatcher.dispatchEvent(key, PropertyEventDispatcher.EventType.property_set, params);

        // Send update to other cluster members.
        CacheFactory.doClusterTask(PropertyClusterEventTask.createPutTask(key, value, isEncrypted));

        return result;
    }

    @Override
    public String put(String key, String value) {
        return put(key, value, isEncrypted(key));
    }

    void localPut(String key, String value, boolean isEncrypted) {
        properties.put(key, value);
        encrypted.put(key, isEncrypted);
        // We now know the database is correct - so we can remove the entry from security.conf
        JiveGlobals.clearXMLPropertyEncryptionEntry(key);

        // Generate event.
        Map<String, Object> params = new HashMap<>();
        params.put("value", value);
        PropertyEventDispatcher.dispatchEvent(key, PropertyEventDispatcher.EventType.property_set, params);
    }

    public String getProperty(String name, String defaultValue) {
        String value = properties.get(name);
        if (value != null) {
            return value;
        }
        else {
            return defaultValue;
        }
    }

    public boolean getBooleanProperty(String name) {
        return Boolean.valueOf(get(name));
    }

    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String value = get(name);
        if (value != null) {
            return Boolean.valueOf(value);
        }
        else {
            return defaultValue;
        }
    }

    private void insertProperty(String name, String value, boolean isEncrypted) {
        Encryptor encryptor = getEncryptor(true);
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            final String valueToSave;
            final String ivString;
            if (isEncrypted) {
                final byte[] iv = new byte[16];
                new SecureRandom().nextBytes(iv);
                ivString = java.util.Base64.getEncoder().encodeToString(iv);
                valueToSave = encryptor.encrypt(value, iv);
            } else {
                ivString = null;
                valueToSave = value;
            }
            pstmt.setString(1, name);
            pstmt.setString(2, valueToSave);
            pstmt.setInt(3, isEncrypted ? 1 : 0);
            pstmt.setString(4, ivString);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void updateProperty(String name, String value, boolean isEncrypted) {
        Encryptor encryptor = getEncryptor(true);
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            final String valueToSave;
            final String ivString;
            if (isEncrypted) {
                final byte[] iv = new byte[16];
                new SecureRandom().nextBytes(iv);
                ivString = java.util.Base64.getEncoder().encodeToString(iv);
                valueToSave = encryptor.encrypt(value, iv);
            } else {
                ivString = null;
                valueToSave = value;
            }
            pstmt.setString(1, valueToSave);
            pstmt.setInt(2, isEncrypted ? 1 : 0);
            pstmt.setString(3, ivString);
            pstmt.setString(4, name);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void deleteProperty(String name) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setString(1, name + "%");
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void loadProperties() {
        Encryptor encryptor = getEncryptor();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                String ivString = rs.getString(4);
                byte[] iv = null;
                if (ivString != null) {
                    try {
                        iv = java.util.Base64.getDecoder().decode(ivString);
                        if (iv.length != 16) {
                            Log.error("Unable to correctly decode iv from string " + ivString);
                            iv = null;
                        }
                    } catch (final IllegalArgumentException e) {
                        Log.error("Unable to decode iv from string " + ivString, e);
                    }
                }

                boolean isEncrypted = rs.getInt(3) == 1 || JiveGlobals.isXMLPropertyEncrypted(name);
                if (isEncrypted) {
                    try { 
                        value = encryptor.decrypt(value, iv);
                    } catch (Exception ex) {
                        Log.error("Failed to load encrypted property value for " + name, ex);
                        value = null;
                    }
                }
                if (value != null) { 
                    properties.put(name, value);
                    encrypted.put(name, isEncrypted);
                }
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }
    
    private Encryptor getEncryptor(boolean useNewEncryptor) {
        return JiveGlobals.getPropertyEncryptor(useNewEncryptor);
    }
    
    private Encryptor getEncryptor() {
        return getEncryptor(false);
    }
}
