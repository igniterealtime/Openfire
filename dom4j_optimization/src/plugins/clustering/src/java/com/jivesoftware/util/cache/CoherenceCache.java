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

import com.tangosol.net.cache.CacheLoader;
import com.tangosol.net.cache.LocalCache;
import com.tangosol.net.cache.OldCache;
import com.tangosol.util.SafeHashMap;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;

/**
 * Implementation of the Cache interface that uses the Coherence LocalCache class.
 *
 */
public class CoherenceCache extends LocalCache implements Cache {

    private static final String FLUSH_DELAY_PROP = "cache.local.flushDelay";

    private String name = "";

    /**
     * Default constructor for coherence to create instances.
     */
    public CoherenceCache(){
    }

    public CoherenceCache(int maxSize) {
        super(maxSize);
    }

    public CoherenceCache(int maxSize, int maxLifetime) {
        super(maxSize, maxLifetime);
    }

    public CoherenceCache(int maxSize, int maxLifetime, CacheLoader loader) {
        super(maxSize, maxLifetime, loader);
    }

    /**
     * Constructor used only by jive - never called by coherence. If you are creating a subclass, or directly
     * instantiating this one, you should only use this constructor, as it does several things not normally required.
     * For instance, the low units (low water mark) will be set to 90 percent of the value of <tt>maxSize</tt>. Also,
     * if the local jive property <tt>'cache.local.flushDelay'</tt> is set, that value will be used as the number of
     * milliseconds passed to {@link #setFlushDelay}. If it is not set, the default value (one minute) will be used.
     *
     * @param name the name of the cache
     * @param maxSize the maximum number of units (bytes, in our case) the cache will hold before evicting older entries.
     * @param maxLifetime the time to live (in milliseconds) for entries in the cache.
     */
    public CoherenceCache(String name, int maxSize, long maxLifetime)  {
        super(maxSize<=0?Integer.MAX_VALUE:maxSize, maxLifetime<0?0:(int)maxLifetime);
        init(maxSize, name);
    }

    /**
     * Constructor used only by jive - never called by coherence. If you are creating a subclass, or directly
     * instantiating this one, you should only use this constructor, as it does several things not normally required.
     * For instance, the low units (low water mark) will be set to 90 percent of the value of <tt>maxSize</tt>. Also,
     * if the local jive property <tt>'cache.local.flushDelay'</tt> is set, that value will be used as the number of
     * milliseconds passed to {@link #setFlushDelay}. If it is not set, the default value (one minute) will be used.
     *
     * @param name the name of the cache
     * @param maxSize the maximum number of units (bytes, in our case) the cache will hold before evicting older entries.
     * @param maxLifetime the time to live (in milliseconds) for entries in the cache.
     * @param cacheLoader the <tt>CacheLoader</tt> or <tt>CacheStore</tt> to use.
     */
    public CoherenceCache(String name, int maxSize, long maxLifetime, CacheLoader cacheLoader) {
        super(maxSize<=0?Integer.MAX_VALUE:maxSize, maxLifetime<0?0:(int)maxLifetime, cacheLoader);
        init(maxSize, name);
    }

    private void init(int maxSize, String name) {
        if (maxSize > 0) {
            setLowUnits((int)(maxSize*.9));
        }

        String delayProp = JiveGlobals.getProperty(FLUSH_DELAY_PROP);
        if (delayProp != null) {
            try {
                long delay = Long.parseLong(delayProp);
                if (delay >=0) {
                    setFlushDelay((int)delay);
                }
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse " + FLUSH_DELAY_PROP + " using default value of " + delayProp);
            }
        }

        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getMaxCacheSize() {
        return getHighUnits();
    }

    public void setMaxCacheSize(int maxSize) {
        setHighUnits(maxSize<=0?Integer.MAX_VALUE:maxSize);
        if (maxSize > 0) {
            setLowUnits((int)(maxSize*.9));
        }
        //TODO write these through to coherence configuration
    }

    public long getMaxLifetime() {
        return getExpiryDelay();
    }

    public void setMaxLifetime(long maxLifetime) {
        setExpiryDelay(maxLifetime<0?0:(int)maxLifetime);
        //TODO write these through to coherence configuration
    }

    public int getCacheSize() {
        return getUnits();
    }

    protected SafeHashMap.Entry instantiateEntry() {
        return new Entry();
    }

    /**
    * A holder for a cached value.
    */
    public class Entry extends OldCache.Entry {

        public int calculateUnits(Object object) {
            // If the object is Cacheable, ask for its size.
            if (object instanceof Cacheable) {
                return ((Cacheable) object).getCachedSize();
            }
            // Coherence puts com.tangosol.util.Binary objects in cache.
            else if (object instanceof com.tangosol.util.Binary) {
                return ((com.tangosol.util.Binary) object).length();
            }
            // Check for other common types of objects put into cache.
            else if (object instanceof Long) {
                return CacheSizes.sizeOfLong();
            }
            else if (object instanceof Integer) {
                return CacheSizes.sizeOfObject() + CacheSizes.sizeOfInt();
            }
            else if (object instanceof Boolean) {
                return CacheSizes.sizeOfObject() + CacheSizes.sizeOfBoolean();
            }
            else if (object instanceof long[]) {
                long[] array = (long[]) object;
                return CacheSizes.sizeOfObject() + array.length * CacheSizes.sizeOfLong();
            }
            else if (object instanceof String) {
                return CacheSizes.sizeOfString((String)object);
            }
            else {
                return 1;
            }
        }

    }
}
