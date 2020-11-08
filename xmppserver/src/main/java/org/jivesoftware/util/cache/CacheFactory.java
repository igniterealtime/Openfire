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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerListener;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.util.InitializationException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

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

    private static final String              PROPERTY_PREFIX_CACHE         = "cache.";

    private static final String              PROPERTY_SUFFIX_MAX_LIFE_TIME = ".maxLifetime";

    private static final String              PROPERTY_SUFFIX_SIZE          = ".size";

    private static final String              PROPERTY_SUFFIX_TYPE          = ".type";

    private static final String              PROPERTY_SUFFIX_MIN           = ".min";

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
        cacheNames.put("RosterItems", "username2rosterItems");
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
        cacheNames.put("Routing Result Listeners", "routeResultListeners");
        cacheNames.put("Components", "components");
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
        cacheNames.put("JID Node-parts", "jidNodeprep");
        cacheNames.put("JID Domain-parts", "jidDomainprep");
        cacheNames.put("JID Resource-parts", "jidResourceprep");

        cacheProps.put(PROPERTY_PREFIX_CACHE + "fileTransfer" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "fileTransfer" + PROPERTY_SUFFIX_MAX_LIFE_TIME, 1000 * 60 * 10L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "multicast" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "multicast" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.DAY);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "offlinemessage" + PROPERTY_SUFFIX_SIZE, 100 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "offlinemessage" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.HOUR * 12);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "pop3" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "pop3" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.HOUR);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "transferProxy" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "transferProxy" + PROPERTY_SUFFIX_MAX_LIFE_TIME, 1000 * 60 * 10L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "group" + PROPERTY_SUFFIX_SIZE, 1024 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "group" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 15);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "lockOutCache" + PROPERTY_SUFFIX_SIZE, 1024 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "lockOutCache" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 15);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "groupMeta" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "groupMeta" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 15);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "username2roster" + PROPERTY_SUFFIX_SIZE, 1024 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "username2roster" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 30);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "username2rosterItems" + PROPERTY_SUFFIX_SIZE, 1024 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "username2rosterItems" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 10);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "javascript" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "javascript" + PROPERTY_SUFFIX_MAX_LIFE_TIME, 3600 * 24 * 10L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "ldap" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "ldap" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.HOUR * 2);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "listsCache" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "offlinePresence" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "lastActivity" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "userCache" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "userCache" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 30);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "remoteUsersCache" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "remoteUsersCache" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 30);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "vcardCache" + PROPERTY_SUFFIX_SIZE, 512 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "faviconHits" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "faviconMisses" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeServer" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeServer" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeComponent" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeComponent" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeUser" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeUser" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeAnonymousUser" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeAnonymousUser" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeUserSessions" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeUserSessions" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeResultListeners" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "routeResultListeners" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "components" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "components" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "componentsSessions" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "componentsSessions" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "connManagerSessions" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "connManagerSessions" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "incServerSessions" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "incServerSessions" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "sessionsHostname" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "sessionsHostname" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "secretKeys" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "secretKeys" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "validatedDomains" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "validatedDomains" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "directedPresences" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "directedPresences" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "serverFeatures" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "serverFeatures" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "serverItems" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "serverItems" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "serversConfigurations" + PROPERTY_SUFFIX_SIZE, 128 * 1024L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "serversConfigurations" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 30);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "entityCapabilities" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "entityCapabilities" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.DAY * 2);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "entityCapabilitiesUsers" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "entityCapabilitiesUsers" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.DAY * 2);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "pluginCacheInfo" + PROPERTY_SUFFIX_SIZE, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "pluginCacheInfo" + PROPERTY_SUFFIX_MAX_LIFE_TIME, -1L);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "pepServiceManager" + PROPERTY_SUFFIX_SIZE, 1024L * 1024 * 10);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "pepServiceManager" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 30);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "publishedItems" + PROPERTY_SUFFIX_SIZE, 1024L * 1024 * 10);
        cacheProps.put(PROPERTY_PREFIX_CACHE + "publishedItems" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JiveConstants.MINUTE * 15);

        // The JID-based classes (wrappers for Caffeine caches) take their default values from whatever is hardcoded in the JID implementation.
        cacheProps.put(PROPERTY_PREFIX_CACHE + "jidNodeprep" + PROPERTY_SUFFIX_SIZE, JID.NODEPREP_CACHE.policy().eviction().get().getMaximum() );
        cacheProps.put(PROPERTY_PREFIX_CACHE + "jidNodeprep" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JID.NODEPREP_CACHE.policy().expireAfterWrite().get().getExpiresAfter( TimeUnit.MILLISECONDS ) );
        cacheProps.put(PROPERTY_PREFIX_CACHE + "jidDomainprep" + PROPERTY_SUFFIX_SIZE, JID.DOMAINPREP_CACHE.policy().eviction().get().getMaximum() );
        cacheProps.put(PROPERTY_PREFIX_CACHE + "jidDomainprep" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JID.DOMAINPREP_CACHE.policy().expireAfterWrite().get().getExpiresAfter( TimeUnit.MILLISECONDS ) );
        cacheProps.put(PROPERTY_PREFIX_CACHE + "jidResourceprep" + PROPERTY_SUFFIX_SIZE, JID.RESOURCEPREP_CACHE.policy().eviction().get().getMaximum() );
        cacheProps.put(PROPERTY_PREFIX_CACHE + "jidResourceprep" + PROPERTY_SUFFIX_MAX_LIFE_TIME, JID.RESOURCEPREP_CACHE.policy().expireAfterWrite().get().getExpiresAfter( TimeUnit.MILLISECONDS ) );

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

                if (property.endsWith(PROPERTY_SUFFIX_SIZE))
                {
                    final long size = getMaxCacheSize( cache.getName() );
                    cache.setMaxCacheSize( size );
                }

                if (property.endsWith(PROPERTY_SUFFIX_MAX_LIFE_TIME))
                {
                    final long lifetime = getMaxCacheLifetime( cache.getName() );
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
        return getCacheProperty(cacheName, PROPERTY_SUFFIX_SIZE, DEFAULT_MAX_CACHE_SIZE);
    }

    /**
     * Sets a local property which overrides the maximum cache size for the
     * supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param size the maximum cache size.
     */
    public static void setMaxSizeProperty(String cacheName, long size) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !Long.toString(size).equals(JiveGlobals.getProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_SIZE)))
        {
            JiveGlobals.setProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_SIZE, Long.toString(size));
        }
    }

    public static boolean hasMaxSizeFromProperty(String cacheName) {
        return hasCacheProperty(cacheName, PROPERTY_SUFFIX_SIZE);
    }

    /**
    * If a local property is found for the supplied name which specifies a value for cache entry lifetime, it
     * is returned. Otherwise, the defaultLifetime argument is returned.
     *
    * @param cacheName the name of the cache to look up a corresponding property for.
    * @return either the property value or the default value.
    */
    public static long getMaxCacheLifetime(String cacheName) {
        return getCacheProperty(cacheName, PROPERTY_SUFFIX_MAX_LIFE_TIME, DEFAULT_MAX_CACHE_LIFETIME);
    }

    /**
     * Sets a local property which overrides the maximum cache entry lifetime
     * for the supplied cache name.
     * @param cacheName the name of the cache to store a value for.
     * @param lifetime the maximum cache entry lifetime.
     */
    public static void setMaxLifetimeProperty(String cacheName, long lifetime) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !Long.toString(lifetime).equals(JiveGlobals.getProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_MAX_LIFE_TIME)))
        {
            JiveGlobals.setProperty((PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_MAX_LIFE_TIME), Long.toString(lifetime));
        }
    }

    public static boolean hasMaxLifetimeFromProperty(String cacheName) {
        return hasCacheProperty(cacheName, PROPERTY_SUFFIX_MAX_LIFE_TIME);
    }

    public static void setCacheTypeProperty(String cacheName, String type) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !type.equals(JiveGlobals.getProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_TYPE)))
        {
            JiveGlobals.setProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_TYPE, type);
        }
    }

    public static String getCacheTypeProperty(String cacheName) {
        cacheName = cacheName.replaceAll(" ", "");
        return JiveGlobals.getProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_TYPE);
    }

    public static void setMinCacheSize(String cacheName, long size) {
        cacheName = cacheName.replaceAll(" ", "");
        if ( !Long.toString(size).equals(JiveGlobals.getProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_MIN)))
        {
            JiveGlobals.setProperty(PROPERTY_PREFIX_CACHE + cacheName + PROPERTY_SUFFIX_MIN, Long.toString(size));
        }
    }

    public static long getMinCacheSize(String cacheName) {
        return getCacheProperty(cacheName, PROPERTY_SUFFIX_MIN, 0);
    }

    private static Cache getCacheByProperty( String property )
    {
        if ( !property.startsWith(PROPERTY_PREFIX_CACHE))
        {
            return null;
        }

        // Extract the cache name identifier from the property name.
        final String name = property.substring(PROPERTY_PREFIX_CACHE.length(), property.lastIndexOf("."));

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
        String propName = PROPERTY_PREFIX_CACHE + cacheName.replaceAll(" ", "") + suffix;
        String sizeProp = JiveGlobals.getProperty(propName);
        if (sizeProp == null && cacheNames.containsKey(cacheName)) {
            // No system property was found for the cache name so try now with short name
            propName = PROPERTY_PREFIX_CACHE + cacheNames.get(cacheName) + suffix;
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
        String propName = PROPERTY_PREFIX_CACHE + cacheName.replaceAll(" ", "") + suffix;
        String sizeProp = JiveGlobals.getProperty(propName);
        if (sizeProp == null && cacheNames.containsKey(cacheName)) {
            // No system property was found for the cache name so try now with short name
            propName = PROPERTY_PREFIX_CACHE + cacheNames.get(cacheName) + suffix;
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
     * @param <T> the type cache being created
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
     * @param <T> the type cache being created
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
     * @deprecated in favour of {@link Cache#getLock}. Will be removed in Openfire 5.0.0.
     *
     * <p>Returns an existing {@link java.util.concurrent.locks.Lock} on the specified key or creates a new one
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
    @Deprecated
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
     * Returns a byte[] that uniquely identifies this member within the cluster or {@code null}
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

    public synchronized static void clearCaches( String... cacheName )
    {
        caches.values().parallelStream()
            .filter(cache -> Arrays.asList(cacheName).contains(cache.getName()))
            .forEach(Map::clear);
    }

    /**
     * Returns a byte[] that uniquely identifies this senior cluster member or {@code null}
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
     * @param <T> the return type of the cluster task
     * @return collection with the result of the execution.
     */
    public static <T> Collection<T> doSynchronousClusterTask(ClusterTask<T> task, boolean includeLocalMember) {
        return cacheFactoryStrategy.doSynchronousClusterTask(task, includeLocalMember);
    }

    /**
     * Invokes a task on a given cluster member synchronously and returns the result of
     * the remote operation. If clustering is not enabled, this method will return null.
     *
     * @param task        the ClusterTask object to be invoked on a given cluster member.
     * @param nodeID      the byte array that identifies the target cluster member.
     * @param <T> the return type of the cluster task
     * @return result of remote operation or null if operation failed or operation returned null.
     * @throws IllegalStateException if requested node was not found or not running in a cluster.
     */
    public static <T> T doSynchronousClusterTask(ClusterTask<T> task, byte[] nodeID) {
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

            // Update the JID-internal caches, if they're configured differently than their default.
            JID.NODEPREP_CACHE.policy().eviction().get().setMaximum( getMaxCacheSize( "jidNodeprep" ) );
            JID.NODEPREP_CACHE.policy().expireAfterWrite().get().setExpiresAfter( getMaxCacheLifetime( "jidNodeprep" ), TimeUnit.MILLISECONDS );
            JID.DOMAINPREP_CACHE.policy().eviction().get().setMaximum( getMaxCacheSize( "jidDomainprep" ) );
            JID.DOMAINPREP_CACHE.policy().expireAfterWrite().get().setExpiresAfter( getMaxCacheLifetime( "jidDomainprep" ), TimeUnit.MILLISECONDS );
            JID.RESOURCEPREP_CACHE.policy().eviction().get().setMaximum( getMaxCacheSize( "jidResourceprep" ) );
            JID.RESOURCEPREP_CACHE.policy().expireAfterWrite().get().setExpiresAfter( getMaxCacheLifetime( "jidResourceprep" ), TimeUnit.MILLISECONDS );

            // Mock cache creation for the JID-internal classes, by wrapping them in a compatibility layer.
            caches.put("JID Node-parts", CaffeineCache.of( JID.NODEPREP_CACHE, "JID Node-parts" ));
            caches.put("JID Domain-parts", CaffeineCache.of( JID.DOMAINPREP_CACHE, "JID Domain-parts" ));
            caches.put("JID Resource-parts", CaffeineCache.of( JID.RESOURCEPREP_CACHE, "JID Resource-parts" ));

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
            clusteringStarting = true;
            clusteringStarted = clusteredCacheFactoryStrategy.startCluster();
            clusteringStarting = false;
        }
        if (clusteringStarted) {
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
        // Loop through local caches and switch them to clustered cache (purge content)
        Arrays.stream(getAllCaches())
            .filter(CacheFactory::isClusterableCache)
            .forEach(cache -> {
                final CacheWrapper cacheWrapper = ((CacheWrapper) cache);
                final Cache clusteredCache = cacheFactoryStrategy.createCache(cacheWrapper.getName());
                cacheWrapper.setWrappedCache(clusteredCache);
            });
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

        // Loop through clustered caches and change them to local caches (purge content)
        Arrays.stream(getAllCaches())
            .filter(CacheFactory::isClusterableCache)
            .forEach(cache -> {
                final CacheWrapper cacheWrapper = ((CacheWrapper) cache);
                final Cache standaloneCache = cacheFactoryStrategy.createCache(cacheWrapper.getName());
                cacheWrapper.setWrappedCache(standaloneCache);
            });
        log.info("Clustering stopped; cache migration complete");
    }

    /**
     * Indicates if the supplied Cache is "clusterable". This is used to determine if a cache should be migrated
     * between a {@link DefaultCache} and a clustered cache when the node joins/leaves the cluster.
     * <p>
     * A cache is considered 'clusterable' if;
     * <ul>
     *     <li>the cache is not a 'local' cache - which apply to the local node only so do not need to be clustered, and</li>
     *     <li>the cache is actually a {@link CacheWrapper} which wraps the underlying default or clustered cache</li>
     * </ul>
     *
     * @param cache the cache to check
     * @return {@code true} if the cache can be converted to/from a clustered cache, otherwise {@code false}
     */
    private static boolean isClusterableCache(final Cache cache) {
        return cache instanceof CacheWrapper && !localOnly.contains(cache.getName());
    }

}
