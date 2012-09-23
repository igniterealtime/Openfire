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

import com.tangosol.net.BackingMapManager;
import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.net.MemberListener;
import com.tangosol.net.NamedCache;
import com.tangosol.net.cache.NearCache;
import com.tangosol.net.cache.ReadWriteBackingMap;
import com.tangosol.util.*;
import org.jivesoftware.util.cache.Cache;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

/**
 * Clustered implementation of the Cache interface using Tangosol's Coherence product.
 *
 */
public class ClusteredCache implements Cache, QueryMap, InvocableMap {

    /**
     * The map is used for distributed operations such as get, put, etc.
     */
    protected NamedCache map;

    /**
     * The cache is used as the backing store of the main distributed map and
     * makes decisions about when to cull or expire cache entries.
     */
    private Cache backingCache;

    /**
     * Create a new cache.
     *
     * @param name a name for the cache, which should be unique per vm.
     */
    protected ClusteredCache(String name) {
        NamedCache cache = com.tangosol.net.CacheFactory.getCache(name);
        init(name, cache);
    }

    /**
     * Create a new cache using the supplied named cache as the actual cache implementation
     *
     * @param name a name for the cache, which should be unique per vm.
     * @param cache the cache implementation
     */
    protected ClusteredCache(String name, NamedCache cache) {
        init(name, cache);
    }

    private void init(String name, NamedCache cache) {
        map = cache;
        BackingMapManager backingManager = cache.getCacheService().getBackingMapManager();
        Map mapBacking = null;
        if (backingManager instanceof DefaultConfigurableCacheFactory.Manager) {
            DefaultConfigurableCacheFactory.Manager actualManager =
                    (DefaultConfigurableCacheFactory.Manager) backingManager;
            int count = 0;
            mapBacking = actualManager.getBackingMap(name);

            //this ugly logic is necessary because the backing map instance seems to be made available asynchronously
            //by the coherence api
            while (mapBacking == null && count < 5) {
                // Wait a full second
                try {
                    Thread.sleep(1000);
                }
                catch (Exception e) { /*ignore*/ }
                count++;
                mapBacking = actualManager.getBackingMap(name);
            }

            if (mapBacking instanceof ReadWriteBackingMap) {
                ReadWriteBackingMap readWriteMap = (ReadWriteBackingMap)mapBacking;
                Map realBackingMap = readWriteMap.getInternalCache();
                if (realBackingMap instanceof Cache) {
                    backingCache = (Cache)realBackingMap;
                }
            }
            else if (mapBacking instanceof Cache) {
                backingCache = (Cache)mapBacking;
            }
        }
        if (backingCache == null) {
            throw new IllegalStateException("Unable to access backing cache for " + name + ". BackingMapManager is a " +
                    backingManager.getClass().getName() + " and backing map is " +
                    ((mapBacking != null) ? mapBacking.getClass().getName() : "null")
            );
        }

        backingCache.setName(name);

    }

    public void addMemberListener(MemberListener listener) {
        map.getCacheService().addMemberListener(listener);
    }

    public void removeMemberListener(MemberListener listener) {
        map.getCacheService().removeMemberListener(listener);
    }

    public void addMapListener(MapListener mapListener, Filter filter, boolean fLite) {
        map.addMapListener(mapListener, filter, fLite);
    }

    public void removeMapListener(MapListener mapListener, Filter filter) {
        map.removeMapListener(mapListener, filter);
    }

    // Cache Interface

    public String getName() {
        return backingCache.getName();
    }

    public void setName(String name) {
        backingCache.setName(name);
    }

    public Object put(Object key, Object object) {
        return map.put(key, object);
    }

    public Object get(Object key) {
        return map.get(key);
    }

    public Object remove(Object key) {
        return map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return backingCache.size();
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
        if (map instanceof NearCache) {
             return ((NearCache)map).getCacheStatistics().getCacheHits();
        }
        else if (backingCache != null) {
            return backingCache.getCacheHits();
        }
        else {
            return -1;
        }
    }

    public long getCacheMisses() {
       if (map instanceof NearCache) {
             return ((NearCache)map).getCacheStatistics().getCacheMisses();
        }
        else if (backingCache != null) {
            return backingCache.getCacheMisses();
        }
        else {
            return -1;
        }
    }

    public int getCacheSize() {
        return backingCache.getCacheSize();
    }

    public long getMaxCacheSize() {
        return backingCache.getMaxCacheSize();
    }

    public void setMaxCacheSize(int maxSize) {
        backingCache.setMaxCacheSize(maxSize);
    }

    public long getMaxLifetime() {
        return backingCache.getMaxLifetime();
    }

    public void setMaxLifetime(long maxLifetime) {
        backingCache.setMaxLifetime(maxLifetime);
    }

    public void destroy() {
        map.destroy();
    }

    public boolean lock(Object key, long timeout) {
        return map.lock(key, timeout);
    }

    public boolean unlock(Object key) {
        return map.unlock(key);
    }

    ///////// InvocableMap methods //////////////////////////////////

    public Object invoke(Object object, EntryProcessor entryProcessor) {
        return map.invoke(object, entryProcessor);
    }

    public Map invokeAll(Collection collection, EntryProcessor entryProcessor) {
        return map.invokeAll(collection, entryProcessor);
    }

    public Map invokeAll(Filter filter, EntryProcessor entryProcessor) {
        return map.invokeAll(filter, entryProcessor);
    }

    public Object aggregate(Collection collection, EntryAggregator entryAggregator) {
        return map.aggregate(collection, entryAggregator);
    }

    public Object aggregate(Filter filter, EntryAggregator entryAggregator) {
        return map.aggregate(filter, entryAggregator);
    }

    ////////////// QueryMap methods /////////////////////////

    public Set keySet(Filter filter) {
        return map.keySet(filter);
    }

    public Set entrySet(Filter filter) {
        return map.entrySet(filter);
    }

    public Set entrySet(Filter filter, Comparator comparator) {
        return map.entrySet(filter, comparator);
    }

    public void addIndex(ValueExtractor valueExtractor, boolean sorted, Comparator comparator) {
        map.addIndex(valueExtractor, sorted, comparator);
    }

    public void removeIndex(ValueExtractor valueExtractor) {
        map.removeIndex(valueExtractor);
    }
}
