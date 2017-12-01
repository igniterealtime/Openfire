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
package org.jivesoftware.util.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Acts as a proxy for a Cache implementation. The Cache implementation can be switched on the fly,
 * which enables users to hold a reference to a CacheWrapper object, but for the underlying
 * Cache implementation to switch from clustered to local, etc.
 *
 */
public class CacheWrapper<K, V> implements Cache<K, V> {

    private Cache<K, V> cache;

    public CacheWrapper(Cache<K, V> cache) {
        this.cache = cache;
    }

    public Cache<K, V> getWrappedCache() {
        return cache;
    }

    public void setWrappedCache(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public String getName() {
        return cache.getName();
    }

    @Override
    public void setName(String name) {
        cache.setName(name);
    }

    @Override
    public long getMaxCacheSize() {
        return cache.getMaxCacheSize();
    }

    @Override
    public void setMaxCacheSize(int maxSize) {
        cache.setMaxCacheSize(maxSize);
    }

    @Override
    public long getMaxLifetime() {
        return cache.getMaxLifetime();
    }

    @Override
    public void setMaxLifetime(long maxLifetime) {
        cache.setMaxLifetime(maxLifetime);
    }

    @Override
    public int getCacheSize() {
        return cache.getCacheSize();
    }

    @Override
    public long getCacheHits() {
        return cache.getCacheHits();
    }

    @Override
    public long getCacheMisses() {
        return cache.getCacheMisses();
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public boolean isEmpty() {
        return cache.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return cache.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return cache.containsValue(value);
    }

    @Override
    public Collection<V> values() {
        return cache.values();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> t) {
        cache.putAll(t);
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return cache.entrySet();
    }

    @Override
    public Set<K> keySet() {
        return cache.keySet();
    }

    @Override
    public V get(Object key) {
        return cache.get(key);
    }

    @Override
    public V remove(Object key) {
        return cache.remove(key);
    }

    @Override
    public V put(K key, V value) {
        return cache.put(key, value);
    }

}
