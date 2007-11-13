/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.container;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A simple registry of cache configuration data for plugins.
 */
public class PluginCacheRegistry {

    private static Map<String, CacheInfo> extraCacheMappings = new HashMap<String, CacheInfo>();
    private static Map<String, List<CacheInfo>> pluginCaches = new HashMap<String, List<CacheInfo>>();

    /**
     * Registers cache configuration data for a give cache and plugin.
     *
     * @param pluginName the name of the plugin which will use the cache.
     * @param info the cache configuration data.
     */
    public static void registerCache(String pluginName, CacheInfo info) {
        extraCacheMappings.put(info.getCacheName(), info);
        List<CacheInfo> caches = pluginCaches.get(pluginName);

        if (caches == null) {
            caches = new ArrayList<CacheInfo>();
            pluginCaches.put(pluginName, caches);
        }

        caches.add(info);
    }

    /**
     * Unregisters all caches for the given plugin.
     *
     * @param pluginName the name of the plugin whose caches will be unregistered.
     */
    public static void unregisterCaches(String pluginName) {
        List<CacheInfo> caches = pluginCaches.remove(pluginName);
        if (caches != null) {
            for (CacheInfo info : caches) {
                extraCacheMappings.remove(info.getCacheName());
                try {
                    CacheFactory.destroyCache(info.getCacheName());
                }
                catch (Exception e) {
                    Log.warn(e);
                }
            }
        }
    }

    public static CacheInfo getCacheInfo(String name) {
        return extraCacheMappings.get(name);
    }
}
