/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2009 Jive Software. All rights reserved.
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
package com.jivesoftware.util.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.EntryListener;
import com.hazelcast.core.IMap;
import com.hazelcast.monitor.LocalMapStats;

/**
 * Clustered implementation of the Cache interface using Hazelcast.
 *
 */
public class ClusteredCache implements Cache {

    private static Logger logger = LoggerFactory.getLogger(ClusteredCache.class);

    /**
     * The map is used for distributed operations such as get, put, etc.
     */
    protected IMap map;
    private String name;
    private long numberOfGets = 0;

    /**
     * Create a new cache using the supplied named cache as the actual cache implementation
     *
     * @param name a name for the cache, which should be unique per vm.
     * @param cache the cache implementation
     */
    protected ClusteredCache(String name, IMap cache) {
        map = cache;
        setName(name);
    }

    public void addEntryListener(EntryListener listener, boolean includeValue) {
        map.addEntryListener(listener, includeValue);
    }

    public void removeEntryListener(EntryListener listener) {
        map.removeEntryListener(listener);
    }

    // Cache Interface

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object put(Object key, Object object) {
    	if (object == null) { return null; }
        return map.put(key, object);
    }

    public Object get(Object key) {
    	numberOfGets++;
        return map.get(key);
    }

    public Object remove(Object key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
    	LocalMapStats stats = map.getLocalMapStats();
    	return (int) (stats.getOwnedEntryCount() + stats.getBackupEntryCount());
    }

    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    public Set entrySet() {
        return map.entrySet();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public Set keySet() {
        return map.keySet();
    }

    public void putAll(Map entries) {
        map.putAll(entries);
    }

    public Collection values() {
        return map.values();
    }

    public long getCacheHits() {
    	return map.getLocalMapStats().getHits();
    }

    public long getCacheMisses() {
    	long hits = map.getLocalMapStats().getHits();
    	return numberOfGets > hits ? numberOfGets - hits : 0;
    }

    public int getCacheSize() {
    	LocalMapStats stats = map.getLocalMapStats();
        return (int) (stats.getOwnedEntryMemoryCost() + stats.getBackupEntryMemoryCost());
    }

    public long getMaxCacheSize() {
        return CacheFactory.getMaxCacheSize(getName());
    }

    public void setMaxCacheSize(int maxSize) {
    	CacheFactory.setMaxSizeProperty(getName(), maxSize);
    }

    public long getMaxLifetime() {
        return CacheFactory.getMaxCacheLifetime(getName());
    }

    public void setMaxLifetime(long maxLifetime) {
    	CacheFactory.setMaxSizeProperty(getName(), maxLifetime);
    }

    public void destroy() {
        map.destroy();
    }

    public boolean lock(Object key, long timeout) {
    	boolean result = true;
    	if (timeout < 0) {
    		map.lock(key);
    	} else if (timeout == 0) {
    		result = map.tryLock(key);
    	} else {
    		result = map.tryLock(key, timeout, TimeUnit.MILLISECONDS);
    	}
        return result;
    }

    public boolean unlock(Object key) {
    	boolean result = true;
         try { map.unlock(key); }
         catch (IllegalMonitorStateException e) {
        	 logger.error("Falied to release cluster lock", e);
        	 result = false;
         }
         return result;
    }

}
