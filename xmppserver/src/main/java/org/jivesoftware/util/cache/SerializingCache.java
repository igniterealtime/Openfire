/*
 * Copyright (C) 2021-2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util.cache;

import org.jivesoftware.openfire.cluster.ClusteredCacheEntryListener;
import org.jivesoftware.openfire.cluster.NodeID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A Cache implementation that stores data in serialized form.
 *
 * The primary benefit of usage of this cache is that the cached data is stored without any references to their classes.
 * This allows cache content to remain usable after the classes that instantiate the data get reloaded. This is of
 * particular interest when the cache is used to store data provided by Openfire plugins (as these classes get loaded
 * by a class loader that is replaced when a plugin gets reloaded or upgraded).
 *
 * Before usage, a cache needs to be configured with the class definitions of the data that is stored in the cache. This
 * is done using the #registerClasses method. As stated above, these classes can be provided by an Openfire plugin. When
 * the providing plugin gets removed (potentially replaced), it is important that the registered classes are no longer
 * referenced by the cache (as that would make it impossible for the Plugin Class Loader to be destroyed, which in turn
 * will cause ClassCastExceptions when a new version of the plugin interacts with the cache). To dereference classes,
 * the #deregisterClasses method can be used.
 *
 * As compared to other caches, usage of this cache will require more system resources, as the serialized representation
 * of an object typically is (much) larger than its original (unserialized) form.
 *
 * An instance is backed by a backing Cache that is used as a delegate. This implementation serializes data before
 * storing it in this delegate, and deserializes it when it is retrieved.
 *
 * This implementation makes use of JAXB to serialize data. Instances stored in the cache (both for keys and values)
 * therefor <em>must</em> be JAXB serializable, with this exception: when the class used for a key and/or value is in
 * the list below, then serialization does not use JAXB. Instead, a String representation of the instance is used.
 *
 * <ul>
 *     <li>java.lang.String</li>
 *     <li>org.xmpp.packet.JID</li>
 * </ul>
 *
 * If problems occur during serialization or deserializing of data, the various methods in this class will throw runtime
 * exceptions.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2239">Issue OF-2239: Make it easier to cache plugin class instances</a>
 */
public class SerializingCache<K extends Serializable, V extends Serializable> implements Cache<K, V>
{
    private static final Logger Log = LoggerFactory.getLogger(SerializingCache.class);

    private final Cache<String, String> delegate;
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;
    private Class<K> keyClass;
    private Class<V> valueClass;

    /**
     * Creates a new serializing cache backed by the provided delegate.
     *
     * Before this cache is usable, the class definitions of the data that is stored in the cache need to be provided
     * using the #registerClasses method.
     *
     * @param delegate The cache used to store data in serialized form.
     */
    SerializingCache(@Nonnull final Cache<String, String> delegate)
    {
        this.delegate = delegate;
    }

    /**
     * The cache will store (in serialized form) entries of which the keys are instances of the provided keyClass
     * argument. The values are correspondingly instances of the provided valueClass argument.
     *
     * When this method is invoked more than once, the definitions provided by the last invocation will be used.
     *
     * @param keyClass The class of instances used as keys.
     * @param valueClass The class of instances used as values.
     * @throws IllegalArgumentException when keyClass and/or valueClass cannot be used to serialize and deserialize instances.
     */
    public void registerClasses(@Nonnull final Class<K> keyClass, @Nonnull final Class<V> valueClass) {
        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(keyClass, valueClass);
            marshaller = jaxbContext.createMarshaller();
            unmarshaller = jaxbContext.createUnmarshaller();
            this.keyClass = keyClass;
            this.valueClass = valueClass;
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Unable to create a cache using classes " + keyClass + " and " + valueClass, e);
        }
    }

    /**
     * Removes the registration of key and value class definitions. This is typically useful when the provided classes
     * are provided by a plugin, and that plugin is being unloaded/replaced. When this method is not used, a reference
     * to the classes from the original PluginClassLoader will be retained, which will cause ClassCastExceptions when
     * interacting with the cache.
     *
     * An invocation of this method does not affect the data stored in the cache. After registering the class types
     * again (using #registerClasses) that data can be stored in and retrieved from the cache again.
     */
    public void deregisterClasses() {
        marshaller = null;
        unmarshaller = null;
        keyClass = null;
        valueClass = null;
    }

    @Nonnull
    protected String marshall(@Nullable final Serializable object, @Nonnull final Class<?> typeClass)
    {
        if (object == null) {
            return "";
        }

        if (typeClass == String.class) {
            return (String) object;
        }
        if (typeClass == JID.class) {
            return object.toString();
        }

        return marshall(object);
    }

    @Nonnull
    private String marshall(@Nonnull final Serializable object)
    {
        checkIsUsable();

        final Writer writer = new StringWriter();
        try {
            marshaller.marshal(object, writer);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Object could not be marshalled into an XML format: " + object, e);
        }
        return writer.toString();
    }

    @Nullable
    protected Serializable unmarshall(@Nullable final String object, @Nonnull final Class<?> typeClass)
    {
        if (object == null || object.isEmpty()) {
            return null;
        }

        if (typeClass == String.class) {
            return object;
        }
        if (typeClass == JID.class) {
            return new JID(object);
        }

        return unmarshall(object);
    }

    @Nonnull
    private Serializable unmarshall(@Nonnull final String object)
    {
        checkIsUsable();
        final Reader reader = new StringReader(object);
        try {
            return (Serializable) unmarshaller.unmarshal(reader);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("XML value could not be unmarshalled into an object: " + object);
        }
    }

    @Nonnull
    public Class<K> getKeyClass()
    {
        checkIsUsable();
        return keyClass;
    }

    @Nonnull
    public Class<V> getValueClass()
    {
        checkIsUsable();
        return valueClass;
    }

    @Override
    @Nullable
    public synchronized V put(@Nullable final K key, @Nullable final V value)
    {
        checkIsUsable();
        checkNotNull(key, DefaultCache.NULL_KEY_IS_NOT_ALLOWED);
        checkNotNull(value, DefaultCache.NULL_VALUE_IS_NOT_ALLOWED);

        final String marshalledKey = marshall(key, keyClass);
        final String marshalledValue = marshall(value, valueClass);

        final String oldValue = delegate.put(marshalledKey, marshalledValue);

        @SuppressWarnings("unchecked")
        final V result = (V) unmarshall(oldValue, valueClass);
        return result;
    }

    @Override
    @Nullable
    public synchronized V get(@Nullable final Object key)
    {
        checkIsUsable();
        checkNotNull(key, DefaultCache.NULL_KEY_IS_NOT_ALLOWED);

        if (!keyClass.isInstance(key)) {
            return null;
        }

        final String marshalledKey = marshall((K)key, keyClass);
        final String marshalledValue = delegate.get(marshalledKey);

        @SuppressWarnings("unchecked")
        final V result = (V) unmarshall(marshalledValue, valueClass);
        return result;
    }

    @Override
    @Nullable
    public synchronized V remove(@Nullable final Object key)
    {
        checkIsUsable();
        checkNotNull(key, DefaultCache.NULL_KEY_IS_NOT_ALLOWED);

        if (!keyClass.isInstance(key)) {
            return null;
        }

        final String marshalledKey = marshall((K)key, keyClass);
        final String marshalledValue = delegate.remove(marshalledKey);

        @SuppressWarnings("unchecked")
        final V result = (V) unmarshall(marshalledValue, valueClass);
        return result;
    }

    @Override
    public synchronized void clear() {
        delegate.clear();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    @Nonnull
    public Collection<V> values()
    {
        checkIsUsable();
        final Collection<String> marshalledValues = delegate.values();
        return marshalledValues.stream()
            .map(marshalledValue -> {
                @SuppressWarnings("unchecked")
                final V result = (V) unmarshall(marshalledValue, valueClass);
                return result;
            })
            .collect(Collectors.toList());
    }

    @Override
    public boolean containsKey(@Nullable final Object key)
    {
        checkIsUsable();
        checkNotNull(key, DefaultCache.NULL_KEY_IS_NOT_ALLOWED);

        if (!keyClass.isInstance(key)) {
            return false;
        }

        final String marshalledKey = marshall((K)key, keyClass);
        return delegate.containsKey(marshalledKey);
    }

    @Override
    public void putAll(@Nonnull final Map<? extends K, ? extends V> map)
    {
        checkIsUsable();
        final Map<String, String> marshalledMap = map.entrySet().stream()
            .collect(Collectors.toMap(
                e -> marshall(e.getKey(), keyClass),
                e -> marshall(e.getValue(), valueClass)
            ));

        delegate.putAll(marshalledMap);
    }

    @Override
    public boolean containsValue(@Nullable final Object value)
    {
        checkIsUsable();
        checkNotNull(value, DefaultCache.NULL_VALUE_IS_NOT_ALLOWED);

        if (!valueClass.isInstance(value)) {
            return false;
        }

        final String marshalledValue = marshall((V)value, valueClass);
        return delegate.containsValue(marshalledValue);
    }

    @Override
    @Nonnull
    public Set<Entry<K, V>> entrySet()
    {
        checkIsUsable();
        final Set<Entry<String, String>> marshalledEntrySet = delegate.entrySet();
        return marshalledEntrySet.stream()
                .collect(Collectors.toMap(
                    entry -> {
                        @SuppressWarnings("unchecked")
                        final K key = (K) unmarshall(entry.getKey(), keyClass);
                        return key;
                    },
                    entry -> {
                        @SuppressWarnings("unchecked")
                        final V value = (V) unmarshall(entry.getValue(), valueClass);
                        return value;
                    })
                )
                .entrySet();
    }

    @Override
    @Nonnull
    public Set<K> keySet()
    {
        checkIsUsable();
        final Set<String> marshalledKeySet = delegate.keySet();
        return marshalledKeySet.stream()
            .map(key -> {
                @SuppressWarnings("unchecked")
                final K result = (K) unmarshall(key, keyClass);
                return result;
            })
            .collect(Collectors.toSet());
    }

    @Override
    public CapacityUnit getCapacityUnit() {
        return delegate.getCapacityUnit();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void setName(final String name) {
        delegate.setName(name);
    }

    @Override
    public long getCacheHits() {
        return delegate.getCacheHits();
    }

    @Override
    public long getCacheMisses() {
        return delegate.getCacheMisses();
    }

    public long getLongCacheSize(){
        return delegate.getLongCacheSize();
    }

    @Override
    public String getCacheSizeRemark() {
        return delegate.getCacheSizeRemark();
    }

    @Override
    public long getMaxCacheSize() {
        return delegate.getMaxCacheSize();
    }

    @Override
    public void setMaxCacheSize(long maxSize){
        delegate.setMaxCacheSize(maxSize);
    }

    @Override
    public String getMaxCacheSizeRemark() {
        return delegate.getMaxCacheSizeRemark();
    }

    @Override
    public long getMaxLifetime() {
        return delegate.getMaxLifetime();
    }

    @Override
    public void setMaxLifetime(final long maxLifetime) {
        delegate.setMaxLifetime(maxLifetime);
    }

    public Cache<String, String> getDelegate() {
        return delegate;
    }

    private void checkNotNull(@Nullable final Object argument, @Nullable final String message) {
        try {
            if (argument == null) {
                throw new NullPointerException(message);
            }
        } catch (final NullPointerException e) {
            if (DefaultCache.allowNull) {
                Log.debug("Allowing storage of null within Cache: ", e); // Gives us a trace for debugging.
            } else {
                throw e;
            }
        }
    }

    private void checkIsUsable() {
        if (marshaller == null) {
            throw new IllegalStateException("Type definitions are not available (they may not have been registered, or may have been deregistered since). Please use #registerClasses before interacting with this cache.");
        }
    }

    @Override
    public String addClusteredCacheEntryListener(@Nonnull final ClusteredCacheEntryListener<K, V> listener, final boolean includeValues, final boolean includeEventsFromLocalNode) {
        return delegate.addClusteredCacheEntryListener(new ClusteredCacheEntryListener<>()
        {
            @Override
            public void entryAdded(@Nonnull String key, @Nullable String newValue, @Nonnull NodeID nodeID)
            {
                @SuppressWarnings("unchecked") final K unmarshalledKey = (K) unmarshall(key, keyClass);
                @SuppressWarnings("unchecked") final V unmarshalledNewValue = (V) unmarshall(newValue, valueClass);
                listener.entryAdded(unmarshalledKey, unmarshalledNewValue, nodeID);
            }

            @Override
            public void entryRemoved(@Nonnull String key, @Nullable String oldValue, @Nonnull NodeID nodeID)
            {
                @SuppressWarnings("unchecked") final K unmarshalledKey = (K) unmarshall(key, keyClass);
                @SuppressWarnings("unchecked") final V unmarshalledOldValue = (V) unmarshall(oldValue, valueClass);

                listener.entryRemoved(unmarshalledKey, unmarshalledOldValue, nodeID);
            }

            @Override
            public void entryUpdated(@Nonnull String key, @Nullable String oldValue, @Nullable String newValue, @Nonnull NodeID nodeID)
            {
                @SuppressWarnings("unchecked") final K unmarshalledKey = (K) unmarshall(key, keyClass);
                @SuppressWarnings("unchecked") final V unmarshalledNewValue = (V) unmarshall(newValue, valueClass);
                @SuppressWarnings("unchecked") final V unmarshalledOldValue = (V) unmarshall(oldValue, valueClass);
                listener.entryUpdated(unmarshalledKey, unmarshalledOldValue, unmarshalledNewValue, nodeID);
            }

            @Override
            public void entryEvicted(@Nonnull String key, @Nullable String oldValue, @Nonnull NodeID nodeID)
            {
                @SuppressWarnings("unchecked") final K unmarshalledKey = (K) unmarshall(key, keyClass);
                @SuppressWarnings("unchecked") final V unmarshalledOldValue = (V) unmarshall(oldValue, valueClass);

                listener.entryEvicted(unmarshalledKey, unmarshalledOldValue, nodeID);
            }

            @Override
            public void mapCleared(@Nonnull NodeID nodeID)
            {
                listener.mapCleared(nodeID);
            }

            @Override
            public void mapEvicted(@Nonnull NodeID nodeID)
            {
                listener.mapEvicted(nodeID);
            }
        }, includeValues, includeEventsFromLocalNode);
    }

    @Override
    public void removeClusteredCacheEntryListener(@Nonnull final String listenerId) {
        delegate.removeClusteredCacheEntryListener(listenerId);
    }
}
