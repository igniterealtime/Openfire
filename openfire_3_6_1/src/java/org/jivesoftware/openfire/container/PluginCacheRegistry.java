/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.container;

import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheFactory;

import java.util.*;

/**
 * A simple registry of cache configuration data for plugins.
 */
public class PluginCacheRegistry {
    private static final PluginCacheRegistry instance = new PluginCacheRegistry();

    private Map<String, CacheInfo> extraCacheMappings = new HashMap<String, CacheInfo>();
    private Map<String, List<CacheInfo>> pluginCaches = new HashMap<String, List<CacheInfo>>();

    public static PluginCacheRegistry getInstance() {
        return instance;
    }

    private PluginCacheRegistry() {
    }

    /**
     * Registers cache configuration data for a give cache and plugin.
     *
     * @param pluginName the name of the plugin which will use the cache.
     * @param info the cache configuration data.
     */
    public void registerCache(String pluginName, CacheInfo info) {
        extraCacheMappings.put(info.getCacheName(), info);
        List<CacheInfo> caches = pluginCaches.get(pluginName);

        if (caches == null) {
            caches = new ArrayList<CacheInfo>();
            pluginCaches.put(pluginName, caches);
        }

        caches.add(info);

        // Set system properties for this cache
        CacheFactory.setCacheTypeProperty(info.getCacheName(), info.getType().getName());
        CacheFactory.setMaxSizeProperty(info.getCacheName(), getMaxSizeFromProperty(info));
        CacheFactory.setMaxLifetimeProperty(info.getCacheName(), getMaxLifetimeFromProperty(info));
        CacheFactory.setMinCacheSize(info.getCacheName(), getMinSizeFromProperty(info));
    }

    /**
     * Unregisters all caches for the given plugin.
     *
     * @param pluginName the name of the plugin whose caches will be unregistered.
     */
    public void unregisterCaches(String pluginName) {
        List<CacheInfo> caches = pluginCaches.remove(pluginName);
        if (caches != null) {
            for (CacheInfo info : caches) {
                extraCacheMappings.remove(info.getCacheName());
                // Check if other cluster nodes have this plugin installed
                Collection<Object> answers =
                        CacheFactory.doSynchronousClusterTask(new IsPluginInstalledTask(pluginName), false);
                for (Object installed : answers) {
                    if ((Boolean) installed) {
                        return;
                    }
                }
                // Destroy cache if we are the last node hosting this plugin
                try {
                    CacheFactory.destroyCache(info.getCacheName());
                }
                catch (Exception e) {
                    Log.warn(e);
                }
            }
        }
    }

    public CacheInfo getCacheInfo(String name) {
        return extraCacheMappings.get(name);
    }

    private long getMaxSizeFromProperty(CacheInfo cacheInfo) {
        String sizeProp = cacheInfo.getParams().get("back-size-high");
        if (sizeProp != null) {
            if ("0".equals(sizeProp)) {
                return -1l;
            }
            try {
                return Integer.parseInt(sizeProp);
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse back-size-high for cache: " + cacheInfo.getCacheName());
            }
        }
        // Return default
        return CacheFactory.DEFAULT_MAX_CACHE_SIZE;
    }

    private static long getMaxLifetimeFromProperty(CacheInfo cacheInfo) {
        String lifetimeProp = cacheInfo.getParams().get("back-expiry");
        if (lifetimeProp != null) {
            if ("0".equals(lifetimeProp)) {
                return -1l;
            }
            long factor = 1;
            if (lifetimeProp.endsWith("m")) {
                factor = JiveConstants.MINUTE;
            }
            else if (lifetimeProp.endsWith("h")) {
                factor = JiveConstants.HOUR;
            }
            else if (lifetimeProp.endsWith("d")) {
                factor = JiveConstants.DAY;
            }
            try {
                return Long.parseLong(lifetimeProp.substring(0, lifetimeProp.length()-1)) * factor;
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse back-expiry for cache: " + cacheInfo.getCacheName());
            }
        }
        // Return default
        return CacheFactory.DEFAULT_MAX_CACHE_LIFETIME;
    }

    private long getMinSizeFromProperty(CacheInfo cacheInfo) {
        String sizeProp = cacheInfo.getParams().get("back-size-low");
        if (sizeProp != null) {
            if ("0".equals(sizeProp)) {
                return -1l;
            }
            try {
                return Integer.parseInt(sizeProp);
            }
            catch (NumberFormatException nfe) {
                Log.warn("Unable to parse back-size-low for cache: " + cacheInfo.getCacheName());
            }
        }
        // Return default
        return CacheFactory.DEFAULT_MAX_CACHE_SIZE;
    }
}
