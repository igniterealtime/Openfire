/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.container;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	private static final Logger Log = LoggerFactory.getLogger(PluginCacheConfigurator.class);

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
            Log.error(e.getMessage(), e);
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