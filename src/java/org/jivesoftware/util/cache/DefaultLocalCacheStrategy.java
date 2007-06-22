/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.util.cache;

import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    /**
     * Default properties to use for local caches. Default properties can be overridden
     * by setting the corresponding system properties.
     */
    private static final Map<String, Long> cacheProps = new HashMap<String, Long>();


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
        cacheNames.put("File Transfer Cache", "fileTransfer");
        cacheNames.put("File Transfer", "transferProxy");
        cacheNames.put("POP3 Authentication", "pop3");
        cacheNames.put("LDAP Authentication", "ldap");
        cacheNames.put("Routing Servers Cache", "routeServer");
        cacheNames.put("Routing Components Cache", "routeComponent");
        cacheNames.put("Routing Users Cache", "routeUser");
        cacheNames.put("Routing AnonymousUsers Cache", "routeAnonymousUser");
        cacheNames.put("Routing User Sessions", "routeUserSessions");
        cacheNames.put("Session Manager Counters", "sessManagerCounters");
        cacheNames.put("Components Sessions", "componentsSessions");
        cacheNames.put("Connection Managers Sessions", "connManagerSessions");
        cacheNames.put("Incoming Server Sessions", "incServerSessions");
        cacheNames.put("Sessions by Hostname", "sessionsHostname");
        cacheNames.put("Secret Keys Cache", "secretKeys");
        cacheNames.put("Validated Domains", "validatedDomains");
        cacheNames.put("Directed Presences", "directedPresences");
        cacheNames.put("Disco Server Features", "serverFeatures");
        cacheNames.put("Disco Server Items", "serverItems");

        cacheProps.put("cache.fileTransfer.size", 128 * 1024l);
        cacheProps.put("cache.fileTransfer.expirationTime", 1000 * 60 * 10l);
        cacheProps.put("cache.multicast.size", 128 * 1024l);
        cacheProps.put("cache.multicast.expirationTime", JiveConstants.DAY);
        cacheProps.put("cache.offlinemessage.size", 100 * 1024l);
        cacheProps.put("cache.offlinemessage.expirationTime", JiveConstants.HOUR * 12);
        cacheProps.put("cache.pop3.size", 512 * 1024l);
        cacheProps.put("cache.pop3.expirationTime", JiveConstants.HOUR);
        cacheProps.put("cache.transferProxy.size", -1l);
        cacheProps.put("cache.transferProxy.expirationTime", 1000 * 60 * 10l);
        cacheProps.put("cache.group.size", 1024 * 1024l);
        cacheProps.put("cache.group.expirationTime", JiveConstants.MINUTE * 15);
        cacheProps.put("cache.groupMeta.size", 512 * 1024l);
        cacheProps.put("cache.groupMeta.expirationTime", JiveConstants.MINUTE * 15);
        cacheProps.put("cache.javascript.size", 128 * 1024l);
        cacheProps.put("cache.javascript.expirationTime", 3600 * 24 * 10l);
        cacheProps.put("cache.ldap.size", 512 * 1024l);
        cacheProps.put("cache.ldap.expirationTime", JiveConstants.HOUR * 2);
        cacheProps.put("cache.listsCache.size", 512 * 1024l);
        cacheProps.put("cache.offlinePresence.size", 512 * 1024l);
        cacheProps.put("cache.lastActivity.size", 128 * 1024l);
        cacheProps.put("cache.userCache.size", 512 * 1024l);
        cacheProps.put("cache.userCache.expirationTime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.remoteUsersCache.size", 512 * 1024l);
        cacheProps.put("cache.remoteUsersCache.expirationTime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.vcardCache.size", 512 * 1024l);
        cacheProps.put("cache.faviconHits.size", 128 * 1024l);
        cacheProps.put("cache.faviconMisses.size", 128 * 1024l);
        cacheProps.put("cache.routeServer.size", -1l);
        cacheProps.put("cache.routeServer.expirationTime", -1l);
        cacheProps.put("cache.routeComponent.size", -1l);
        cacheProps.put("cache.routeComponent.expirationTime", -1l);
        cacheProps.put("cache.routeUser.size", -1l);
        cacheProps.put("cache.routeUser.expirationTime", -1l);
        cacheProps.put("cache.routeAnonymousUser.size", -1l);
        cacheProps.put("cache.routeAnonymousUser.expirationTime", -1l);
        cacheProps.put("cache.routeUserSessions.size", -1l);
        cacheProps.put("cache.routeUserSessions.expirationTime", -1l);
        cacheProps.put("cache.sessManagerCounters.size", -1l);
        cacheProps.put("cache.sessManagerCounters.expirationTime", -1l);
        cacheProps.put("cache.componentsSessions.size", -1l);
        cacheProps.put("cache.componentsSessions.expirationTime", -1l);
        cacheProps.put("cache.connManagerSessions.size", -1l);
        cacheProps.put("cache.connManagerSessions.expirationTime", -1l);
        cacheProps.put("cache.incServerSessions.size", -1l);
        cacheProps.put("cache.incServerSessions.expirationTime", -1l);
        cacheProps.put("cache.sessionsHostname.size", -1l);
        cacheProps.put("cache.sessionsHostname.expirationTime", -1l);
        cacheProps.put("cache.secretKeys.size", -1l);
        cacheProps.put("cache.secretKeys.expirationTime", -1l);
        cacheProps.put("cache.validatedDomains.size", -1l);
        cacheProps.put("cache.validatedDomains.expirationTime", -1l);
        cacheProps.put("cache.directedPresences.size", -1l);
        cacheProps.put("cache.directedPresences.expirationTime", -1l);
        cacheProps.put("cache.serverFeatures.size", -1l);
        cacheProps.put("cache.serverFeatures.expirationTime", -1l);
        cacheProps.put("cache.serverItems.size", -1l);
        cacheProps.put("cache.serverItems.expirationTime", -1l);
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
        return new DefaultCache(name, getMaxSizeFromProperty(propname), getMaxLifetimeFromProperty(propname));
    }

    public boolean isSeniorClusterMember() {
        return true;
    }

    public byte[] getClusterMemberID() {
        return new byte[0];
    }

    public void doClusterTask(final ClusterTask task) {
    }

    public boolean doClusterTask(ClusterTask task, byte[] nodeID) {
        throw new IllegalStateException("Cluster service is not available");
    }

    public Collection<Object> doSynchronousClusterTask(ClusterTask task, boolean includeLocalMember) {
        return Collections.emptyList();
    }

    public Object doSynchronousClusterTask(ClusterTask task, byte[] nodeID) {
        throw new IllegalStateException("Cluster service is not available");
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
        JiveGlobals.setProperty("cache." + cacheName + ".size", Integer.toString(size));
    }

    /**
     * Sets a local property which overrides the maximum cache entry lifetime as configured in coherence-cache-config.xml
     * for the supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param lifetime the maximum cache entry lifetime.
     */
    public static void setMaxLifetimeProperty(String cacheName, long lifetime) {
        cacheName = cacheName.replaceAll(" ", "");
        JiveGlobals.setProperty(("cache." + cacheName + ".maxLifetime"), Long.toString(lifetime));
    }

    /**
     * If a local property is found for the supplied name which specifies a value for cache size, it is returned.
     * Otherwise, the defaultSize argument is returned.
     * 
     * @param cacheName the name of the cache to look up a corresponding property for.
     * @return either the property value or the default value.
     */
    private static long getMaxSizeFromProperty(String cacheName) {
        String propName = "cache." + cacheName.replaceAll(" ", "") + ".size";
        String sizeProp = JiveGlobals.getProperty(propName);
        if (sizeProp != null) {
            try {
                return Integer.parseInt(sizeProp);
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse " + propName + " using default value.");
            }
        }
        // Check if there is a default size value for this cache 
        Long defaultSize = cacheProps.get(propName);
        return defaultSize == null ? DEFAULT_MAX_CACHE_SIZE : defaultSize;
    }

     /**
     * If a local property is found for the supplied name which specifies a value for cache entry lifetime, it
      * is returned. Otherwise, the defaultLifetime argument is returned.
      *
     * @param cacheName the name of the cache to look up a corresponding property for.
     * @return either the property value or the default value.
     */
    private static long getMaxLifetimeFromProperty(String cacheName) {
        String propName = "cache." + cacheName.replaceAll(" ", "") + ".maxLifetime";
        String lifetimeProp = JiveGlobals.getProperty(propName);
        if (lifetimeProp != null) {
            try {
                return Long.parseLong(lifetimeProp);
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse " + propName + " using default value.");
            }
        }
         // Check if there is a default lifetime value for this cache
         Long defaultLifetime = cacheProps.get(propName);
         return defaultLifetime == null ? DEFAULT_MAX_CACHE_LIFETIME : defaultLifetime;
    }
}
