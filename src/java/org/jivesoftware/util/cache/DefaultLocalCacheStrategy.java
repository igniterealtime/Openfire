/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util.cache;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveConstants;

import java.util.Collection;
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

/**
 * CacheFactoryStrategy for use in Openfire. It creates and manages local caches, and it's cluster
 * related method implementations do nothing.
 *
 * @see Cache
 * @see CacheFactory
 */
public class DefaultLocalCacheStrategy implements CacheFactoryStrategy {

    public static final int DEFAULT_MAX_CACHE_SIZE = 1024 * 256;
    public static final long DEFAULT_MAX_CACHE_LIFETIME = 6 * JiveConstants.HOUR;

    /**
     * This map contains property names which were used to store cache configuration data
     * in local xml properties in previous versions.
     */
    private static final Map<String, String> cacheNames = new HashMap<String, String>();


    public DefaultLocalCacheStrategy() {
        initCacheNameMap();
    }

    private void initCacheNameMap() {
        cacheNames.put("Favicon Hits", "faviconHits");
        cacheNames.put("Favicon Misses", "faviconMisses");
        cacheNames.put("Group", "group");
        cacheNames.put("Group Metadata Cache", "groupMeta");
        cacheNames.put("Javascript Cache", "javascript");
        cacheNames.put("Last Activity Cache", "lastActivity");
        cacheNames.put("Multicast Service", "multicast");
        cacheNames.put("Offline Message Size", "offlinemessage");
        cacheNames.put("Offline Presence Cache", "offlinePresence");
        cacheNames.put("Privacy Lists", "listsCache");
        cacheNames.put("Remote Users Existence", "remoteUsersCache");
        cacheNames.put("Roster", "username2roster");
        cacheNames.put("User", "userCache");
        cacheNames.put("VCard", "vcardCache");
    }


    public boolean startCluster() {
        return false;
    }

    public void stopCluster() {
    }

    public Cache createCache(String name) {
        String propname = cacheNames.get(name);
        if (propname == null) {
            propname = name;
        }
        return new DefaultCache(name, getMaxSizeFromProperty(propname, DEFAULT_MAX_CACHE_SIZE),
                getMaxLifetimeFromProperty(propname, DEFAULT_MAX_CACHE_LIFETIME));
    }

    public boolean isSeniorClusterMember() {
        return true;
    }

    public String getClusterMemberID() {
        return "";
    }

    public void doClusterTask(final ClusterTask task) {
    }

    public Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        return Collections.emptyList();
    }

    public void updateCacheStats(Map<String, Cache> caches) {
    }

    public void lockKey(Object key, long timeout) {
    }

    public void unlockKey(Object key) {
    }

    /**
     * Sets a local property which overrides the maximum cache size as configured in coherence-cache-config.xml for the
     * supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param size the maximum cache size.
     */
    public static void setMaxSizeProperty(String cacheName, int size) {
        cacheName = cacheName.replaceAll(" ", "");
        JiveGlobals.setXMLProperty("cache." + cacheName + ".size", Integer.toString(size));
    }

    /**
     * Sets a local property which overrides the maximum cache entry lifetime as configured in coherence-cache-config.xml
     * for the supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param lifetime the maximum cache entry lifetime.
     */
    public static void setMaxLifetimeProperty(String cacheName, long lifetime) {
        cacheName = cacheName.replaceAll(" ", "");
        JiveGlobals.setXMLProperty(("cache." + cacheName + ".maxLifetime"), Long.toString(lifetime));
    }

    /**
     * If a local property is found for the supplied name which specifies a value for cache size, it is returned. Otherwise,
     * the defaultSize argument is returned.
     * @param cacheName the name of the cache to look up a corresponding property for.
     * @param defaultSize the value to return if no property is set.
     * @return either the property value or the default value.
     */
    public static int getMaxSizeFromProperty(String cacheName, int defaultSize) {
        String propName = "cache." + cacheName.replaceAll(" ", "") + ".size";
        String sizeProp = JiveGlobals.getXMLProperty(propName);
        if (sizeProp != null) {
            try {
                return Integer.parseInt(sizeProp);
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse " + propName + " using default value of " + defaultSize);
                return defaultSize;
            }
        }
        else {
            return defaultSize;
        }
    }

     /**
     * If a local property is found for the supplied name which specifies a value for cache entry lifetime, it is returned.
     * Otherwise, the defaultLifetime argument is returned.
     * @param cacheName the name of the cache to look up a corresponding property for.
     * @param defaultLifetime the value to return if no property is set.
     * @return either the property value or the default value.
     */
    public static long getMaxLifetimeFromProperty(String cacheName, long defaultLifetime) {
        String propName = "cache." + cacheName.replaceAll(" ", "") + ".maxLifetime";
        String lifetimeProp = JiveGlobals.getXMLProperty(propName);
        if (lifetimeProp != null) {
            try {
                return Long.parseLong(lifetimeProp);
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse " + propName + " using default value of " + defaultLifetime);
                return defaultLifetime;
            }
        }
        else {
            return defaultLifetime;
        }
    }
}
