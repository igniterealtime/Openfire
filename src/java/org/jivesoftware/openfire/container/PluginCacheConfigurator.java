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

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.jivesoftware.util.Log;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A helper class to read cache configuration data for a plugin and register the defined caches with
 * the cache factory. Definitions should look something like this:
 * <code>
 *
 * <cache-config>
 *     <cache-mapping>
 *           <cache-name>My Cache</cache-name>
 *           <scheme-name>optimistic</scheme-name>
 *           <init-params>
 *               <init-param>
 *                   <param-name>back-size-high</param-name>
 *                   <param-value>131072</param-value>
 *               </init-param>
 *               <init-param>
 *                   <param-name>back-expiry</param-name>
 *                   <param-value>6h</param-value>
 *               </init-param>
 *               <init-param>
 *                   <param-name>back-size-low</param-name>
 *                   <param-value>117965</param-value>
 *               </init-param>
 *           </init-params>
 *     </cache-mapping>
 * </cache-config>
 *
 * </code>
 */
public class PluginCacheConfigurator {

    private InputStream configDataStream;

    public void setInputStream(InputStream configDataStream) {
        this.configDataStream = configDataStream;
    }

    public void configure(String pluginName) {
        try {
            SAXReader saxReader = new SAXReader();
            saxReader.setEncoding("UTF-8");
            Document cacheXml = saxReader.read(configDataStream);
            List<Node> mappings = cacheXml.selectNodes("/cache-config/cache-mapping");
            for (Node mapping: mappings) {
                registerCache(pluginName, mapping);
            }
        }
        catch (DocumentException e) {
            Log.error(e);
        }
    }

    private void registerCache(String pluginName, Node configData) {
        String cacheName = configData.selectSingleNode("cache-name").getStringValue();
        String schemeName = configData.selectSingleNode("scheme-name").getStringValue();
        if (cacheName == null || schemeName == null) {
            throw new IllegalArgumentException("Both cache-name and scheme-name elements are required. Found cache-name: " + cacheName +
            " and scheme-name: " + schemeName);
        }

        Map<String, String> initParams = readInitParams(configData);
        CacheInfo info = new CacheInfo(cacheName, CacheInfo.Type.valueof(schemeName), initParams);
        PluginCacheRegistry.getInstance().registerCache(pluginName, info);
    }

    private Map<String, String> readInitParams(Node configData) {
        Map<String, String> paramMap = new HashMap<String, String>();
        List<Node> params = configData.selectNodes("init-params/init-param");
        for (Node param : params) {
            String paramName = param.selectSingleNode("param-name").getStringValue();
            String paramValue = param.selectSingleNode("param-value").getStringValue();
            paramMap.put(paramName, paramValue);
        }

        return paramMap;
    }

}