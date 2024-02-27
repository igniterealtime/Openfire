/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.group;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.PersistableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of a writable {@link Map} to manage group properties.
 * Updates made to the elements in this map will also be applied to the database.
 * Note this implementation assumes group property changes will be relatively
 * infrequent and therefore does not try to optimize database I/O for performance.
 * Each call to a {@link Map} mutator method (direct or indirect via {@link Iterator})
 * will result in a corresponding synchronous update to the database.
 * 
 * @param <K> Property key
 * @param <V> Property value
 */

public class DefaultGroupPropertyMap<K,V> extends PersistableMap<K,V> {

    private static final long serialVersionUID = 3128889631577167040L;
    private static final Logger logger = LoggerFactory.getLogger(DefaultGroupPropertyMap.class);

    // moved from {@link Group} as these are specific to the default provider
    private static final String DELETE_PROPERTY =
            "DELETE FROM ofGroupProp WHERE groupName=? AND name=?";
    private static final String DELETE_ALL_PROPERTIES =
            "DELETE FROM ofGroupProp WHERE groupName=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE ofGroupProp SET propValue=? WHERE name=? AND groupName=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO ofGroupProp (groupName, name, propValue) VALUES (?, ?, ?)";

    private final Group group;
    
    /**
     * Group properties map constructor; requires an associated {@link Group} instance
     * @param group The group that owns these properties
     */
    public DefaultGroupPropertyMap(Group group) {
        this.group = group;
    }
    
    /**
     * Custom method to put properties into the map, optionally without
     * triggering persistence. This is used when the map is being 
     * initially loaded from the database.
     * 
     * @param key The property name
     * @param value The property value
     * @param persist True if the changes should be persisted to the database
     * @return The original value or null if the property did not exist
     */
    @Override
    public V put(K key, V value, boolean persist) {
        V originalValue = super.put(key, value);
        // we only support persistence for <String, String>
        if (persist && key instanceof String && value instanceof String) {
            if (logger.isDebugEnabled())
                logger.debug("Persisting group property [" + key + "]: " + value);
            if (originalValue instanceof String) { // existing property		
                updateProperty((String)key, (String)value, (String)originalValue);
            } else {
                insertProperty((String)key, (String)value);
            }
        }
        return originalValue;
    }

    @Override
    public V put(K key, V value) {
        if (value == null) { // treat null value as "remove"
            return remove(key);
        } else {
            return put(key, value, true);
        }
    }
    
    @Override
    public V remove(Object key) {
        V result = super.remove(key);
        if (key instanceof String) {
            deleteProperty((String)key, (String)result);
        }
        return result;
    }

    @Override
    public void clear() {
        final Map<K,V> originalMap = new HashMap<>(this); // copy to be used by event handling.
        super.clear();

        // Create a copy of all to-be-deleted string values (to be sent to event listeners).
        final Map<String, String> map = originalMap.entrySet().stream()
            .filter(entry -> entry.getValue() instanceof String && entry.getKey() instanceof String)
            .collect(Collectors.toMap(entry -> (String)entry.getKey(), entry -> (String)entry.getValue()));

        deleteAllProperties(map);
    }

    /**
     * Be aware that removing a property through the iterator of the returned key set might cause unexpected behavior,
     * as not all event handlers will be notified with all data required for cache updates to be processed.
     */
    @Override
    public Set<K> keySet() {
        // custom class needed here to handle key.remove()
        return new PersistenceAwareKeySet<K>(super.keySet());
    }

    @Override
    public Collection<V> values() {
        // custom class needed here to suppress value.remove()
        return Collections.unmodifiableCollection(super.values());
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        // custom class needed here to handle entrySet mutators
        return new PersistenceAwareEntrySet<Entry<K,V>>(super.entrySet());
    }

    /**
     * Persistence-aware {@link Set} for group property keys. This class returns
     * a custom iterator that can handle property removal.
     */
    private class PersistenceAwareKeySet<E> extends AbstractSet<K> {

        private final Set<K> delegate;
        
        /**
         * Sole constructor; requires a wrapped {@link Set} for delegation
         * @param delegate A collection of keys from the map
         */
        public PersistenceAwareKeySet(Set<K> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Iterator<K> iterator() {
            return new KeyIterator<E>(delegate.iterator());
        }

        @Override
        public int size() {
            return delegate.size();
        }
    }

    /**
     * This iterator updates the database when a property key is removed.
     */
    private class KeyIterator<E> implements Iterator<K> {

        private final Iterator<K> delegate;
        private K current;
        
        /**
         * Sole constructor; requires a wrapped {@link Iterator} for delegation
         * @param delegate An iterator for all the keys from the map
         */
        public KeyIterator(Iterator<K> delegate) {
            this.delegate = delegate;
        }
        
        /**
         * Delegated to corresponding method in the backing {@link Iterator}
         */
        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        /**
         * Delegated to corresponding method in the backing {@link Iterator}
         */
        @Override
        public K next() {
            current = delegate.next();
            return current;
        }

        /**
         * Removes the property corresponding to the current key from
         * the underlying map. Also applies update to the database.
         */
        @Override
        public void remove() {
            delegate.remove();
            if (current instanceof String) {
                deleteProperty((String)current, null); // FIXME OF-2430 by not providing the original value, some event handlers may not be able to handle this.
            }
            current = null;
        }
    }
    
    /**
     * Persistence-aware {@link Set} for group properties (as {@link Map.Entry})
     */
    private class PersistenceAwareEntrySet<E> implements Set<Entry<K, V>> {

        private final Set<Entry<K, V>> delegate;
        
        /**
         * Sole constructor; requires a wrapped {@link Set} for delegation
         * @param delegate A collection of entries ({@link Map.Entry}) from the map
         */
        public PersistenceAwareEntrySet(Set<Entry<K, V>> delegate) {
            this.delegate = delegate;
        }

        /**
         * Returns a custom iterator for the entries in the backing map
         */
        @Override
        public Iterator<Entry<K, V>> iterator() {
            return new EntryIterator<Entry<K,V>>(delegate.iterator());
        }

        /**
         * Removes the given key from the backing map, and applies the
         * corresponding update to the database.
         * 
         * @param o A {@link Map.Entry} within this set
         * @return True if the set contained the given key
         */
        @Override
        public boolean remove(Object o) {
            boolean propertyExists = delegate.remove(o);
            if (propertyExists) {
                deleteProperty((String)((Entry<K,V>)o).getKey(), (String)((Entry<K,V>)o).getValue());
            }
            return propertyExists;
        }

        /**
         * Removes all the elements in the set, and applies the
         * corresponding update to the database.
         */
        @Override
        public void clear() {
            final Map<String,String> originalMap = new HashMap<>(); // copy to be used by event handling.
            for (Entry<K,V> entry : delegate) {
                if (entry.getKey() instanceof String && entry.getValue() instanceof String) {
                    originalMap.put((String) entry.getKey(), (String) entry.getValue());
                }
            }
            delegate.clear();
            deleteAllProperties(originalMap);
        }

        // these methods are problematic (and not really necessary),
        // so they are not implemented
        
        /**
         * @throws UnsupportedOperationException Always thrown, as this implementation does not support the optional functionality.
         */
        @Override
        public boolean removeAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        /**
         * @throws UnsupportedOperationException Always thrown, as this implementation does not support the optional functionality.
         */
        @Override
        public boolean retainAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }
        
        // per docs for {@link Map.entrySet}, these methods are not supported

        /**
         * @throws UnsupportedOperationException Always thrown, as this implementation does not support the optional functionality.
         */
        @Override
        public boolean add(Entry<K, V> o) {
            return delegate.add(o);
        }

        /**
         * @throws UnsupportedOperationException Always thrown, as this implementation does not support the optional functionality.
         */
        @Override
        public boolean addAll(Collection<? extends Entry<K, V>> c) {
            return delegate.addAll(c);
        }

        // remaining {@link Set} methods can be delegated safely
        
        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        @Override
        public int size() {
            return delegate.size();
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.toArray(a);
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        public boolean equals(Object o) {
            return delegate.equals(o);
        }

        /**
         * Delegated to corresponding method in the backing {@link Set}
         */
        public int hashCode() {
            return delegate.hashCode();
        }
    }

    /**
     * Remove group property from the database when the {@link Iterator#remove}
     * method is invoked via the {@link Map#entrySet} set
     */
    private class EntryIterator<E> implements Iterator<Entry<K, V>> {

        private final Iterator<Entry<K,V>> delegate;
        private EntryWrapper<E> current;
        
        /**
         * Sole constructor; requires a wrapped {@link Iterator} for delegation
         * @param delegate An iterator for all the keys from the map
         */
        public EntryIterator(Iterator<Entry<K,V>> delegate) {
            this.delegate = delegate;
        }
        /**
         * Delegated to corresponding method in the backing {@link Iterator}
         */
        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        /**
         * Delegated to corresponding method in the backing {@link Iterator}
         */
        @Override
        public Entry<K,V> next() {
            current = new EntryWrapper<>(delegate.next());
            return current;
        }

        /**
         * Removes the property corresponding to the current key from
         * the underlying map. Also applies update to the database.
         */
        @Override
        public void remove() {
            delegate.remove();
            K key = current.getKey();
            V value = current.getValue();
            if (key instanceof String) {
                deleteProperty((String)key, value instanceof String ? (String)value : null);
            }
            current = null;
        }
    }
    
    /**
     * Update the database when a group property is updated via {@link Map.Entry#setValue}
     */
    private class EntryWrapper<E> implements Entry<K,V> {
        private final Entry<K,V> delegate;

        /**
         * Sole constructor; requires a wrapped {@link Map.Entry} for delegation
         * @param delegate The corresponding entry from the map
         */
        public EntryWrapper(Entry<K,V> delegate) {
            this.delegate = delegate;
        }
        
        /**
         * Delegated to corresponding method in the backing {@link Map.Entry}
         */
        @Override
        public K getKey() {
            return delegate.getKey();
        }
        
        /**
         * Delegated to corresponding method in the backing {@link Map.Entry}
         */
        @Override
        public V getValue() {
            return delegate.getValue();
        }
        
        /**
         * Set the value of the property corresponding to this entry. This
         * method also updates the database as needed depending on the new
         * property value. A null value will cause the property to be deleted
         * from the database.
         * 
         * @param value The new property value
         * @return The old value of the corresponding property
         */
        @Override
        public V setValue(V value) {
            V oldValue = delegate.setValue(value);
            K key = delegate.getKey();
            if (key instanceof String) {
                if (value instanceof String) {
                    if (oldValue == null) {
                        insertProperty((String) key, (String) value);
                    } else if (!value.equals(oldValue)) {
                        updateProperty((String)key,(String)value, (String)oldValue);
                    }
                } else {
                    deleteProperty((String)key, (oldValue instanceof String) ? (String)oldValue : null);
                }
            }
            return oldValue;
        }
    }

    /**
     * Persist a new group property to the database for the current group
     * 
     * @param key Property name
     * @param value Property value
     */
    private synchronized void insertProperty(String key, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, group.getName());
            pstmt.setString(2, key);
            pstmt.setString(3, value);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // Clean up caches.
        DefaultGroupProvider.sharedGroupMetaCache.clear();
        GroupManager.getInstance().propertyAddedPostProcess(group, key);
    }

    /**
     * Update the value of an existing group property for the current group
     * 
     * @param key Property name
     * @param value Property value
     * @param originalValue Original property value
     */
    private synchronized void updateProperty(String key, String value, String originalValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, value);
            pstmt.setString(2, key);
            pstmt.setString(3, group.getName());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // Clean up caches.
        DefaultGroupProvider.sharedGroupMetaCache.clear();
        GroupManager.getInstance().propertyModifiedPostProcess(group, key, originalValue);
    }

    /**
     * Delete a group property from the database for the current group
     * 
     * @param key Property name
     */
    private synchronized void deleteProperty(String key, String originalValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setString(1, group.getName());
            pstmt.setString(2, key);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // Clean up caches.
        DefaultGroupProvider.sharedGroupMetaCache.clear();
        GroupManager.getInstance().propertyDeletedPostProcess(group, key, originalValue);
    }

    /**
     * Delete all properties from the database for the current group
     *
     * @param originalMap The properties of the group prior to the removal.
     */
    private synchronized void deleteAllProperties(final Map<String,String> originalMap) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_ALL_PROPERTIES);
            pstmt.setString(1, group.getName());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            logger.error(e.getMessage(), e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        // Clean up caches.
        DefaultGroupProvider.sharedGroupMetaCache.clear();
        GroupManager.getInstance().propertiesDeletedPostProcess(group, originalMap);
    }
}
