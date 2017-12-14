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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerListener;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates Cache objects. The returned caches will either be local or clustered
 * depending on the clustering enabled setting and a user's license.
 *
 * <p>When clustered caching is turned on, cache usage statistics for all caches
 * that have been created are periodically published to the clustered cache
 * named "opt-$cacheStats".</p>
 *
 */
@SuppressWarnings("rawtypes")
public class CacheFactory {

    private static final Logger log = LoggerFactory.getLogger(CacheFactory.class);

    public static String LOCAL_CACHE_PROPERTY_NAME = "cache.clustering.local.class";
    public static String CLUSTERED_CACHE_PROPERTY_NAME = "cache.clustering.clustered.class";

    private static boolean clusteringStarted = false;
    private static boolean clusteringStarting = false;

    /**
     * Storage for all caches that get created.
     */
    private static Map<String, Cache> caches = new ConcurrentHashMap<>();
    private static List<String> localOnly = Collections.synchronizedList(new ArrayList<String>());
    
    private static String localCacheFactoryClass;
    private static String clusteredCacheFactoryClass;
    private static CacheFactoryStrategy cacheFactoryStrategy = new DefaultLocalCacheStrategy();
    private static CacheFactoryStrategy localCacheFactoryStrategy;
    private static CacheFactoryStrategy clusteredCacheFactoryStrategy;
    private static Thread statsThread;

    public static final int DEFAULT_MAX_CACHE_SIZE = 1024 * 256;
    public static final long DEFAULT_MAX_CACHE_LIFETIME = 6 * JiveConstants.HOUR;

    /**
     * This map contains property names which were used to store cache configuration data
     * in local xml properties in previous versions.
     */
    private static final Map<String, String> cacheNames = new HashMap<>();
    /**
     * Default properties to use for local caches. Default properties can be overridden
     * by setting the corresponding system properties.
     */
    private static final Map<String, Long> cacheProps = new HashMap<>();

    static {
        localCacheFactoryClass = JiveGlobals.getProperty(LOCAL_CACHE_PROPERTY_NAME,
                "org.jivesoftware.util.cache.DefaultLocalCacheStrategy");
        clusteredCacheFactoryClass = JiveGlobals.getProperty(CLUSTERED_CACHE_PROPERTY_NAME,
                "org.jivesoftware.openfire.plugin.util.cache.ClusteredCacheFactory");

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
        cacheNames.put("Locked Out Accounts", "lockOutCache");
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
        cacheNames.put("Components Sessions", "componentsSessions");
        cacheNames.put("Connection Managers Sessions", "connManagerSessions");
        cacheNames.put("Incoming Server Sessions", "incServerSessions");
        cacheNames.put("Sessions by Hostname", "sessionsHostname");
        cacheNames.put("Secret Keys Cache", "secretKeys");
        cacheNames.put("Validated Domains", "validatedDomains");
        cacheNames.put("Directed Presences", "directedPresences");
        cacheNames.put("Disco Server Features", "serverFeatures");
        cacheNames.put("Disco Server Items", "serverItems");
        cacheNames.put("Remote Server Configurations", "serversConfigurations");
        cacheNames.put("Entity Capabilities", "entityCapabilities");
        cacheNames.put("Entity Capabilities Users", "entityCapabilitiesUsers");
        cacheNames.put("PEPServiceManager", "pepServiceManager");
        cacheNames.put("Published Items", "publishedItems");

        cacheProps.put("cache.fileTransfer.size", 128 * 1024l);
        cacheProps.put("cache.fileTransfer.maxLifetime", 1000 * 60 * 10l);
        cacheProps.put("cache.multicast.size", 128 * 1024l);
        cacheProps.put("cache.multicast.maxLifetime", JiveConstants.DAY);
        cacheProps.put("cache.offlinemessage.size", 100 * 1024l);
        cacheProps.put("cache.offlinemessage.maxLifetime", JiveConstants.HOUR * 12);
        cacheProps.put("cache.pop3.size", 512 * 1024l);
        cacheProps.put("cache.pop3.maxLifetime", JiveConstants.HOUR);
        cacheProps.put("cache.transferProxy.size", -1l);
        cacheProps.put("cache.transferProxy.maxLifetime", 1000 * 60 * 10l);
        cacheProps.put("cache.group.size", 1024 * 1024l);
        cacheProps.put("cache.group.maxLifetime", JiveConstants.MINUTE * 15);
        cacheProps.put("cache.lockOutCache.size", 1024 * 1024l);
        cacheProps.put("cache.lockOutCache.maxLifetime", JiveConstants.MINUTE * 15);
        cacheProps.put("cache.groupMeta.size", 512 * 1024l);
        cacheProps.put("cache.groupMeta.maxLifetime", JiveConstants.MINUTE * 15);
        cacheProps.put("cache.username2roster.size", 1024 * 1024l);
        cacheProps.put("cache.username2roster.maxLifetime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.javascript.size", 128 * 1024l);
        cacheProps.put("cache.javascript.maxLifetime", 3600 * 24 * 10l);
        cacheProps.put("cache.ldap.size", 512 * 1024l);
        cacheProps.put("cache.ldap.maxLifetime", JiveConstants.HOUR * 2);
        cacheProps.put("cache.listsCache.size", 512 * 1024l);
        cacheProps.put("cache.offlinePresence.size", 512 * 1024l);
        cacheProps.put("cache.lastActivity.size", 128 * 1024l);
        cacheProps.put("cache.userCache.size", 512 * 1024l);
        cacheProps.put("cache.userCache.maxLifetime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.remoteUsersCache.size", 512 * 1024l);
        cacheProps.put("cache.remoteUsersCache.maxLifetime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.vcardCache.size", 512 * 1024l);
        cacheProps.put("cache.faviconHits.size", 128 * 1024l);
        cacheProps.put("cache.faviconMisses.size", 128 * 1024l);
        cacheProps.put("cache.routeServer.size", -1l);
        cacheProps.put("cache.routeServer.maxLifetime", -1l);
        cacheProps.put("cache.routeComponent.size", -1l);
        cacheProps.put("cache.routeComponent.maxLifetime", -1l);
        cacheProps.put("cache.routeUser.size", -1l);
        cacheProps.put("cache.routeUser.maxLifetime", -1l);
        cacheProps.put("cache.routeAnonymousUser.size", -1l);
        cacheProps.put("cache.routeAnonymousUser.maxLifetime", -1l);
        cacheProps.put("cache.routeUserSessions.size", -1l);
        cacheProps.put("cache.routeUserSessions.maxLifetime", -1l);
        cacheProps.put("cache.componentsSessions.size", -1l);
        cacheProps.put("cache.componentsSessions.maxLifetime", -1l);
        cacheProps.put("cache.connManagerSessions.size", -1l);
        cacheProps.put("cache.connManagerSessions.maxLifetime", -1l);
        cacheProps.put("cache.incServerSessions.size", -1l);
        cacheProps.put("cache.incServerSessions.maxLifetime", -1l);
        cacheProps.put("cache.sessionsHostname.size", -1l);
        cacheProps.put("cache.sessionsHostname.maxLifetime", -1l);
        cacheProps.put("cache.secretKeys.size", -1l);
        cacheProps.put("cache.secretKeys.maxLifetime", -1l);
        cacheProps.put("cache.validatedDomains.size", -1l);
        cacheProps.put("cache.validatedDomains.maxLifetime", -1l);
        cacheProps.put("cache.directedPresences.size", -1l);
        cacheProps.put("cache.directedPresences.maxLifetime", -1l);
        cacheProps.put("cache.serverFeatures.size", -1l);
        cacheProps.put("cache.serverFeatures.maxLifetime", -1l);
        cacheProps.put("cache.serverItems.size", -1l);
        cacheProps.put("cache.serverItems.maxLifetime", -1l);
        cacheProps.put("cache.serversConfigurations.size", 128 * 1024l);
        cacheProps.put("cache.serversConfigurations.maxLifetime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.entityCapabilities.size", -1l);
        cacheProps.put("cache.entityCapabilities.maxLifetime", JiveConstants.DAY * 2);
        cacheProps.put("cache.entityCapabilitiesUsers.size", -1l);
        cacheProps.put("cache.entityCapabilitiesUsers.maxLifetime", JiveConstants.DAY * 2);
        cacheProps.put("cache.pluginCacheInfo.size", -1l);
        cacheProps.put("cache.pluginCacheInfo.maxLifetime", -1l);
        cacheProps.put("cache.pepServiceManager.size", 1024l * 1024 * 10);
        cacheProps.put("cache.pepServiceManager.maxLifetime", JiveConstants.MINUTE * 30);
        cacheProps.put("cache.publishedItems.size", 1024l * 1024 * 10);
        cacheProps.put("cache.publishedItems.maxLifetime", JiveConstants.MINUTE * 15);

        PropertyEventDispatcher.addListener( new PropertyEventListener()
        {

            @Override
            public void propertySet( String property, Map<String, Object> params )
            {
                final Cache cache = getCacheByProperty( property );
                if ( cache == null )
                {
                    return;
                }

                if ( property.endsWith( ".size" ) )
                {
                    final Long size = getMaxCacheSize( cache.getName() );
                    cache.setMaxCacheSize( size < Integer.MAX_VALUE ? size.intValue() : Integer.MAX_VALUE );
                }

                if ( property.endsWith( ".maxLifeTime" ) )
                {
                    final Long lifetime = getMaxCacheLifetime( cache.getName() );
                    cache.setMaxLifetime( lifetime );
                }

                // Note that changes to 'min' and 'type' cannot be applied runtime - a restart is required for those.
            }

            @Override
            public void propertyDeleted( String property, Map<String, Object> params )
            {
                propertySet( property, params );
            }

            @Override
            public void xmlPropertySet( String property, Map<String, Object> params )
            {
                propertySet( property, params );
            }

            @Override
            public void xmlPropertyDeleted( String property, Map<String, Object> params )
            {
                propertySet( property, params );
            }
        } );
    }

    private CacheFactory() {
    }

    /**
     * If a local property is found for the supplied name which specifies a value for cache size, it is returned.
     * Otherwise, the defaultSize argument is returned.
     *
     * @param cacheName the name of the cache to look up a corresponding property for.
     * @return either the property value or the default value.
     */
    public static long getMaxCacheSize(String cacheName) {
        return getCacheProperty(cacheName, ".size", DEFAULT_MAX_CACHE_SIZE);
    }

    /**
     * Sets a local property which overrides the maximum cache size for the
     * supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param size the maximum cache size.
     */
    public static void setMaxSizeProperty(String cacheName, long size) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !Long.toString(size).equals( JiveGlobals.getProperty( "cache." + cacheName + ".size" ) ) )
        {
            JiveGlobals.setProperty( "cache." + cacheName + ".size", Long.toString( size ) );
        }
    }

    public static boolean hasMaxSizeFromProperty(String cacheName) {
        return hasCacheProperty(cacheName, ".size");
    }

    /**
    * If a local property is found for the supplied name which specifies a value for cache entry lifetime, it
     * is returned. Otherwise, the defaultLifetime argument is returned.
     *
    * @param cacheName the name of the cache to look up a corresponding property for.
    * @return either the property value or the default value.
    */
    public static long getMaxCacheLifetime(String cacheName) {
        return getCacheProperty(cacheName, ".maxLifetime", DEFAULT_MAX_CACHE_LIFETIME);
    }

    /**
     * Sets a local property which overrides the maximum cache entry lifetime
     * for the supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param lifetime the maximum cache entry lifetime.
     */
    public static void setMaxLifetimeProperty(String cacheName, long lifetime) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !Long.toString( lifetime ).equals( JiveGlobals.getProperty( "cache." + cacheName + ".maxLifetime" ) ))
        {
            JiveGlobals.setProperty( ( "cache." + cacheName + ".maxLifetime" ), Long.toString( lifetime ) );
        }
    }

    public static boolean hasMaxLifetimeFromProperty(String cacheName) {
        return hasCacheProperty(cacheName, ".maxLifetime");
    }

    public static void setCacheTypeProperty(String cacheName, String type) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !type.equals( JiveGlobals.getProperty( "cache." + cacheName + ".type" ) ))
        {
            JiveGlobals.setProperty( "cache." + cacheName + ".type", type );
        }
    }

    public static String getCacheTypeProperty(String cacheName) {
        cacheName = cacheName.replaceAll(" ", "");
        return JiveGlobals.getProperty("cache." + cacheName + ".type");
    }

    public static void setMinCacheSize(String cacheName, long size) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !Long.toString( size ).equals( JiveGlobals.getProperty( "cache." + cacheName + ".min" ) ))
        {
            JiveGlobals.setProperty( "cache." + cacheName + ".min", Long.toString( size ) );
        }
    }

    public static long getMinCacheSize(String cacheName) {
        return getCacheProperty(cacheName, ".min", 0);
    }

    private static Cache getCacheByProperty( String property )
    {
        if ( !property.startsWith( "cache." ) )
        {
            return null;
        }

        // Extract the cache name identifier from the property name.
        final String name = property.substring( "cache.".length(), property.lastIndexOf( "." ) );

        // See if property is using the short name variant.
        for ( final Map.Entry<String, String> entry : cacheNames.entrySet() )
        {
            if ( name.equals( entry.getValue() ) )
            {
                return caches.get( entry.getKey() );
            }
        }

        // If not a short name, then try for a normalized name.
        for ( final Map.Entry<String, Cache> entry : caches.entrySet() )
        {
            if ( entry.getKey().replaceAll(" ", "").equals( name ) )
            {
                return entry.getValue();
            }
        }

        return null;
    }

    private static long getCacheProperty(String cacheName, String suffix, long defaultValue) {
        // First check if user is overwriting default value using a system property for the cache name
        String propName = "cache." + cacheName.replaceAll(" ", "") + suffix;
        String sizeProp = JiveGlobals.getProperty(propName);
        if (sizeProp == null && cacheNames.containsKey(cacheName)) {
            // No system property was found for the cache name so try now with short name
            propName = "cache." + cacheNames.get(cacheName) + suffix;
            sizeProp = JiveGlobals.getProperty(propName);
        }
        if (sizeProp != null) {
            try {
                return Long.parseLong(sizeProp);
            }
            catch (NumberFormatException nfe) {
                log.warn("Unable to parse " + propName + " using default value.");
            }
        }
        // Check if there is a default size value for this cache
        Long defaultSize = cacheProps.get(propName);
        return defaultSize == null ? defaultValue : defaultSize;
    }

    private static boolean hasCacheProperty(String cacheName, String suffix) {
        // First check if user is overwriting default value using a system property for the cache name
        String propName = "cache." + cacheName.replaceAll(" ", "") + suffix;
        String sizeProp = JiveGlobals.getProperty(propName);
        if (sizeProp == null && cacheNames.containsKey(cacheName)) {
            // No system property was found for the cache name so try now with short name
            propName = "cache." + cacheNames.get(cacheName) + suffix;
            sizeProp = JiveGlobals.getProperty(propName);
        }
        if (sizeProp != null) {
            try {
                Long.parseLong(sizeProp);
                return true;
            }
            catch (NumberFormatException nfe) {
                log.warn("Unable to parse " + propName + " using default value.");
            }
        }
        return false;
    }

    /**
     * Returns an array of all caches in the system.
     * @return an array of all caches in the system.
     */
    public static Cache[] getAllCaches() {
        List<Cache> values = new ArrayList<>();
        for (Cache cache : caches.values()) {
            values.add(cache);
        }
        return values.toArray(new Cache[values.size()]);
    }

    /**
     * Returns the named cache, creating it as necessary.
     *
     * @param name         the name of the cache to create.
     * @return the named cache, creating it as necessary.
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T extends Cache> T createCache(String name) {
        T cache = (T) caches.get(name);
        if (cache != null) {
            return cache;
        }
        cache = (T) cacheFactoryStrategy.createCache(name);
        
        log.info("Created cache [" + cacheFactoryStrategy.getClass().getName() + "] for " + name);

        return wrapCache(cache, name);
    }

    /**
     * Returns the named local cache, creating it as necessary.
     *
     * @param name         the name of the cache to create.
     * @return the named cache, creating it as necessary.
     */
    @SuppressWarnings("unchecked")
    public static synchronized <T extends Cache> T createLocalCache(String name) {
        T cache = (T) caches.get(name);
        if (cache != null) {
            return cache;
        }
        cache = (T) localCacheFactoryStrategy.createCache(name);
        localOnly.add(name);

        log.info("Created local-only cache [" + localCacheFactoryClass + "] for " + name);
        
        return wrapCache(cache, name);
    }

    /**
     * Destroys the cache for the cache name specified.
     *
     * @param name the name of the cache to destroy.
     */
    public static synchronized void destroyCache(String name) {
        Cache cache = caches.remove(name);
        if (cache != null) {
            if (localOnly.contains(name)) {
                localOnly.remove(name);
                localCacheFactoryStrategy.destroyCache(cache);
            } else {
                cacheFactoryStrategy.destroyCache(cache);
            }
        }
    }

    /**
     * Returns an existing {@link java.util.concurrent.locks.Lock} on the specified key or creates a new one
     * if none was found. This operation is thread safe. Successive calls with the same key may or may not
     * return the same {@link java.util.concurrent.locks.Lock}. However, different threads asking for the
     * same Lock at the same time will get the same Lock object.<p>
     *
     * The supplied cache may or may not be used depending whether the server is running on cluster mode
     * or not. When not running as part of a cluster then the lock will be unrelated to the cache and will
     * only be visible in this JVM.
     *
     * @param key the object that defines the visibility or scope of the lock.
     * @param cache the cache used for holding the lock.
     * @return an existing lock on the specified key or creates a new one if none was found.
     */
    public static synchronized Lock getLock(Object key, Cache cache) {
        if (localOnly.contains(cache.getName())) {
            return localCacheFactoryStrategy.getLock(key, cache);
        } else {
            return cacheFactoryStrategy.getLock(key, cache);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Cache> T wrapCache(T cache, String name) {
        if ("Routing Components Cache".equals(name)) {
            cache = (T) new ComponentCacheWrapper(cache);
        } else {
            cache = (T) new CacheWrapper(cache);
        }
        cache.setName(name);

        caches.put(name, cache);
        return cache;
    }

    /**
     * Returns true if clustering is installed and can be used by this JVM
     * to join a cluster. A false value could mean that either clustering
     * support is not available or the license does not allow to have more
     * than 1 cluster node.
     *
     * @return true if clustering is installed and can be used by
     * this JVM to join a cluster.
     */
    public static boolean isClusteringAvailable() {
        if (clusteredCacheFactoryStrategy == null) {
            try {
                clusteredCacheFactoryStrategy = (CacheFactoryStrategy) Class.forName(
                        clusteredCacheFactoryClass, true,
                        getClusteredCacheStrategyClassLoader()).newInstance();
            } catch (NoClassDefFoundError | Exception e) {
                log.warn("Clustered cache factory strategy " + clusteredCacheFactoryClass + " not found");
            }
        }
        return (clusteredCacheFactoryStrategy != null);
    }

    /**
     * Returns true is clustering is currently being started. Once the cluster
     * is started or failed to be started this value will be false.
     *
     * @return true is clustering is currently being started.
     */
    public static boolean isClusteringStarting() {
        return clusteringStarting;
    }

    /**
     * Returns true if this node is currently a member of a cluster. The last step of application
     * initialization is to join a cluster, so this method returns false during most of application startup.
     *
     * @return true if this node is currently a member of a cluster.
     */
    public static boolean isClusteringStarted() {
        return clusteringStarted;
    }

    /**
     * Returns a byte[] that uniquely identifies this member within the cluster or <tt>null</tt>
     * when not in a cluster.
     *
     * @return a byte[] that uniquely identifies this member within the cluster or null when not in a cluster.
     */
    public static byte[] getClusterMemberID() {
        return cacheFactoryStrategy.getClusterMemberID();
    }

    public synchronized static void clearCaches() {
        for (String cacheName : caches.keySet()) {
            Cache cache = caches.get(cacheName);
            cache.clear();
        }
    }

    /**
     * Returns a byte[] that uniquely identifies this senior cluster member or <tt>null</tt>
     * when not in a cluster.
     *
     * @return a byte[] that uniquely identifies this senior cluster member or null when not in a cluster.
     */
    public static byte[] getSeniorClusterMemberID() {
        return cacheFactoryStrategy.getSeniorClusterMemberID();
    }

    /**
     * Returns true if this member is the senior member in the cluster. If clustering
     * is not enabled, this method will also return true. This test is useful for
     * tasks that should only be run on a single member in a cluster.
     *
     * @return true if this cluster member is the senior or if clustering is not enabled.
     */
    public static boolean isSeniorClusterMember() {
        return cacheFactoryStrategy.isSeniorClusterMember();
    }

    /**
     * Returns basic information about the current members of the cluster or an empty
     * collection if not running in a cluster.
     *
     * @return information about the current members of the cluster or an empty
     *         collection if not running in a cluster.
     */
    public static Collection<ClusterNodeInfo> getClusterNodesInfo() {
        return cacheFactoryStrategy.getClusterNodesInfo();
    }

    /**
     * Returns the maximum number of cluster members allowed. A value of 0 will
     * be returned when clustering is not allowed.
     *
     * @return the maximum number of cluster members allowed or 0 if clustering is not allowed.
     */
    public static int getMaxClusterNodes() {
        return cacheFactoryStrategy.getMaxClusterNodes();
    }
    
    /**
     * Gets the pseudo-synchronized time from the cluster. While the cluster members may
     * have varying system times, this method is expected to return a timestamp that is
     * synchronized (or nearly so; best effort) across the cluster.
     * 
     * @return Synchronized time for all cluster members
     */
    public static long getClusterTime() {
        // use try/catch here for backward compatibility with older plugin(s)
        try { return cacheFactoryStrategy.getClusterTime(); }
        catch (AbstractMethodError ame) {
            log.warn("Cluster time not available; check for update to hazelcast/clustering plugin");
            return localCacheFactoryStrategy.getClusterTime();
        }
    }
    
    /**
     * Invokes a task on other cluster members in an asynchronous fashion. The task will not be
     * executed on the local cluster member. If clustering is not enabled, this method
     * will do nothing.
     *
     * @param task the task to be invoked on all other cluster members.
     */
    public static void doClusterTask(final ClusterTask<?> task) {
        cacheFactoryStrategy.doClusterTask(task);
    }

    /**
     * Invokes a task on a given cluster member in an asynchronous fashion. If clustering is not enabled,
     * this method will do nothing.
     *
     * @param task the task to be invoked on the specified cluster member.
     * @param nodeID the byte array that identifies the target cluster member.
     * @throws IllegalStateException if requested node was not found or not running in a cluster. 
     */
    public static void doClusterTask(final ClusterTask<?> task, byte[] nodeID) {
        cacheFactoryStrategy.doClusterTask(task, nodeID);
    }

    /**
     * Invokes a task on other cluster members synchronously and returns the result as a Collection
     * (method will not return until the task has been executed on each cluster member).
     * The task will not be executed on the local cluster member. If clustering is not enabled,
     * this method will return an empty collection.
     *
     * @param task               the ClusterTask object to be invoked on all other cluster members.
     * @param includeLocalMember true to run the task on the local member, false otherwise
     * @return collection with the result of the execution.
     */
    public static Collection<Object> doSynchronousClusterTask(ClusterTask<?> task, boolean includeLocalMember) {
        return cacheFactoryStrategy.doSynchronousClusterTask(task, includeLocalMember);
    }

    /**
     * Invokes a task on a given cluster member synchronously and returns the result of
     * the remote operation. If clustering is not enabled, this method will return null.
     *
     * @param task        the ClusterTask object to be invoked on a given cluster member.
     * @param nodeID      the byte array that identifies the target cluster member.
     * @return result of remote operation or null if operation failed or operation returned null.
     * @throws IllegalStateException if requested node was not found or not running in a cluster.
     */
    public static Object doSynchronousClusterTask(ClusterTask<?> task, byte[] nodeID) {
        return cacheFactoryStrategy.doSynchronousClusterTask(task, nodeID);
    }
    
    /**
     * Returns the node info for the given cluster node
     * @param nodeID The target cluster node 
     * @return The info for the cluster node or null if not found
     */
    public static ClusterNodeInfo getClusterNodeInfo(byte[] nodeID) {
        return cacheFactoryStrategy.getClusterNodeInfo(nodeID);
    }

    public static String getPluginName() {
        return cacheFactoryStrategy.getPluginName();
    }

    public static synchronized void initialize() throws InitializationException {
        try {
            localCacheFactoryStrategy = (CacheFactoryStrategy) Class.forName(localCacheFactoryClass).newInstance();
            cacheFactoryStrategy = localCacheFactoryStrategy;
        } catch (Exception e) {
            log.error("Failed to instantiate local cache factory strategy: " + localCacheFactoryClass, e);
             throw new InitializationException(e);
        }
    }

    private static ClassLoader getClusteredCacheStrategyClassLoader() {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        Plugin plugin = pluginManager.getPlugin("hazelcast");
        if (plugin == null) {
            plugin = pluginManager.getPlugin("clustering");
            if (plugin == null) {
                plugin = pluginManager.getPlugin("enterprise");
            }
        }
        PluginClassLoader pluginLoader = pluginManager.getPluginClassloader(plugin);
        if (pluginLoader != null) {
            if (log.isDebugEnabled()) {
                StringBuffer pluginLoaderDetails = new StringBuffer("Clustering plugin class loader: ");
                pluginLoaderDetails.append(pluginLoader.getClass().getName());
                for (URL url : pluginLoader.getURLs()) {
                    pluginLoaderDetails.append("\n\t").append(url.toExternalForm());
                }
                log.debug(pluginLoaderDetails.toString());
            }
            return pluginLoader;
        }
        else {
            log.warn("CacheFactory - Unable to find a Plugin that provides clustering support.");
            return Thread.currentThread().getContextClassLoader();
        }
    }

    public static void startClustering() {
        if (isClusteringAvailable()) {
            clusteringStarting = clusteredCacheFactoryStrategy.startCluster();
        }
        if (clusteringStarting) {
            if (statsThread == null) {
                // Start a timing thread with 1 second of accuracy.
                statsThread = new Thread("Cache Stats") {
                    private volatile boolean destroyed = false;

                    @Override
                    public void run() {
                        XMPPServer.getInstance().addServerListener(new XMPPServerListener() {
                            @Override
                            public void serverStarted() {}

                            @Override
                            public void serverStopping() {
                                destroyed = true;
                            }
                        });
                        ClusterManager.addListener(new ClusterEventListener() {
                            @Override
                            public void joinedCluster() {}

                            @Override
                            public void joinedCluster(byte[] nodeID) {}

                            @Override
                            public void leftCluster() {
                                destroyed = true;
                                ClusterManager.removeListener(this);
                            }

                            @Override
                            public void leftCluster(byte[] nodeID) {}

                            @Override
                            public void markedAsSeniorClusterMember() {}
                        });

                        // Run the timer indefinitely.
                        while (!destroyed && ClusterManager.isClusteringEnabled()) {
                            // Publish cache stats for this cluster node (assuming clustering is
                            // enabled and there are stats to publish).
                            try {
                                cacheFactoryStrategy.updateCacheStats(caches);
                            }
                            catch (Exception e) {
                                log.error(e.getMessage(), e);
                            }
                            try {
                                // Sleep 10 seconds.
                                sleep(10000);
                            }
                            catch (InterruptedException ie) {
                                // Ignore.
                            }
                        }
                        statsThread = null;
                        log.debug("Cache stats thread terminated.");
                    }
                };
                statsThread.setDaemon(true);
                statsThread.start();
            }
        }
    }

    public static void stopClustering() {
        // Stop the cluster
        clusteredCacheFactoryStrategy.stopCluster();
        clusteredCacheFactoryStrategy = null;
        // Set the strategy to local
        cacheFactoryStrategy = localCacheFactoryStrategy;
    }

    /**
     * Notification message indicating that this JVM has joined a cluster.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void joinedCluster() {
        cacheFactoryStrategy = clusteredCacheFactoryStrategy;
        // Loop through local caches and switch them to clustered cache (copy content)
        for (Cache cache : getAllCaches()) {
            // skip local-only caches
            if (localOnly.contains(cache.getName())) continue;
            CacheWrapper cacheWrapper = ((CacheWrapper) cache);
            Cache clusteredCache = cacheFactoryStrategy.createCache(cacheWrapper.getName());
            clusteredCache.putAll(cache);
            cacheWrapper.setWrappedCache(clusteredCache);
        }
        clusteringStarting = false;
        clusteringStarted = true;
        log.info("Clustering started; cache migration complete");
    }

    /**
     * Notification message indicating that this JVM has left the cluster.
     */
    @SuppressWarnings("unchecked")
    public static synchronized void leftCluster() {
        clusteringStarted = false;
        cacheFactoryStrategy = localCacheFactoryStrategy;

        // Loop through clustered caches and change them to local caches (copy content)
        for (Cache cache : getAllCaches()) {
            // skip local-only caches
            if (localOnly.contains(cache.getName())) continue;
            CacheWrapper cacheWrapper = ((CacheWrapper) cache);
            Cache standaloneCache = cacheFactoryStrategy.createCache(cacheWrapper.getName());
            standaloneCache.putAll(cache);
            cacheWrapper.setWrappedCache(standaloneCache);
        }
        log.info("Clustering stopped; cache migration complete");
    }
}
