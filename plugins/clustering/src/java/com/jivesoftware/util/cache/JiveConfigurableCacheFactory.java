/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

import com.tangosol.net.DefaultConfigurableCacheFactory;
import com.tangosol.run.xml.XmlElement;
import org.jivesoftware.util.cache.CacheFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A coherence cache factory which adds the ability to register caches at runtime which are not defined
 * in the core coherence-cache-config.xml.
 */
public class JiveConfigurableCacheFactory extends DefaultConfigurableCacheFactory
{

    public JiveConfigurableCacheFactory() {
        super();
    }

    public JiveConfigurableCacheFactory(String s) {
        super(s);
    }

    public JiveConfigurableCacheFactory(String s, ClassLoader classLoader) {
        super(s, classLoader);
    }

    public JiveConfigurableCacheFactory(XmlElement xmlElement) {
        super(xmlElement);
    }

    public CacheInfo findSchemeMapping(String cacheName) {
        CacheInfo mapping = null;
        try {
            mapping = super.findSchemeMapping(cacheName);
            // Check if there are system properties overriding default values
            if (CacheFactory.getCacheTypeProperty(cacheName) != null) {
                String cacheType = CacheFactory.getCacheTypeProperty(cacheName);
                mapping = new CacheInfo(cacheName, cacheType, mapping.getAttributes());
            }
            if (CacheFactory.hasMaxSizeFromProperty(cacheName)) {
                long maxCacheSize = CacheFactory.getMaxCacheSize(cacheName);
                if (maxCacheSize == -1l) {
                    maxCacheSize = 0;
                }
                mapping.getAttributes().put("back-size-high", Long.toString(maxCacheSize));
            }
            if (CacheFactory.hasMaxLifetimeFromProperty(cacheName)) {
                long maxLifetime = CacheFactory.getMaxCacheLifetime(cacheName);
                if (maxLifetime == -1l) {
                    maxLifetime = 0;
                }
                mapping.getAttributes().put("back-expiry", Long.toString(maxLifetime));
            }
        }
        catch (IllegalArgumentException iae) {
            // Do nothing. Mapping will be null so we will find one later
        }

        if (mapping == null) {
            String typeProperty = CacheFactory.getCacheTypeProperty(cacheName);
            long maxCacheSize = CacheFactory.getMaxCacheSize(cacheName);
            long minCacheSize = CacheFactory.getMinCacheSize(cacheName);
            long maxLifetime = CacheFactory.getMaxCacheLifetime(cacheName);
            if (typeProperty != null) {
                Map<String, String> attributes = new HashMap<String, String>();
                if (maxCacheSize == -1l) {
                    maxCacheSize = 0;
                }
                attributes.put("back-size-high", Long.toString(maxCacheSize));

                if (maxLifetime == -1l) {
                    maxLifetime = 0;
                }
                attributes.put("back-expiry", Long.toString(maxLifetime));

                if (minCacheSize == -1l) {
                    minCacheSize = 0;
                }
                attributes.put("back-size-low", Long.toString(minCacheSize));

                mapping = new CacheInfo(cacheName, typeProperty, attributes);
            }
        }

        if (mapping == null) {
            //this is to mirror the superclass behavior
            throw new IllegalArgumentException("No scheme for cache: " + cacheName);
        }

        return mapping;
    }
}