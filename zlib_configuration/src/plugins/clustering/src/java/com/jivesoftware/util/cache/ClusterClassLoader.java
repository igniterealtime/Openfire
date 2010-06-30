/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginClassLoader;
import org.jivesoftware.openfire.container.PluginManager;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

/**
 * Class loader to be used by coherence to load classes that live in the enterprise plugin,
 * the Openfire core and also classes defined in other plugins. With this new class loader
 * plugins can now make use of coherence.<p>
 *
 * However, there is a catch with this class loader. Plugins that define the same class name
 * (i.e. package and class name) will have a problem if they try to send that class through
 * the cluster. Coherence will deserialize the class and will use the first class definition
 * found in the list of plugins.<p>
 *
 * The sequence of search for this class loader is first check the enterprise plugin that
 * includes checking the Openfire core. If not found then try with the other plugins.
 *
 * @author Gaston Dombiak
 */
public class ClusterClassLoader extends ClassLoader {
    private PluginClassLoader enterpriseClassloader;

    public ClusterClassLoader() {
        super();
        Plugin plugin = XMPPServer.getInstance().getPluginManager().getPlugin("clustering");
        enterpriseClassloader = XMPPServer.getInstance().getPluginManager().getPluginClassloader(plugin);
    }

    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return enterpriseClassloader.loadClass(name);
        }
        catch (ClassNotFoundException e) {
            PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
            for (Plugin plugin : pluginManager.getPlugins()) {
                String pluginName = pluginManager.getPluginDirectory(plugin).getName();
                if ("clustering".equals(pluginName) || "admin".equals(pluginName)) {
                    continue;
                }
                PluginClassLoader pluginClassloader = pluginManager.getPluginClassloader(plugin);
                try {
                    return pluginClassloader.loadClass(name);
                }
                catch (ClassNotFoundException e1) {
                    // Do nothing. Continue to the next plugin
                }
            }
        }
        throw new ClassNotFoundException(name);
    }

    public URL getResource(String name) {
        URL resource = enterpriseClassloader.getResource(name);
        if (resource == null) {
            PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
            for (Plugin plugin : pluginManager.getPlugins()) {
                String pluginName = pluginManager.getPluginDirectory(plugin).getName();
                if ("clustering".equals(pluginName) || "admin".equals(pluginName)) {
                    continue;
                }
                PluginClassLoader pluginClassloader = pluginManager.getPluginClassloader(plugin);
                resource = pluginClassloader.getResource(name);
                if (resource != null) {
                    return resource;
                }
            }
        }
        return resource;
    }

    public Enumeration<URL> getResources(String name) throws IOException {
        Enumeration<URL> answer = null;
        try {
            answer = enterpriseClassloader.getResources(name);
        }
        catch (IOException e) {
            // Ignore
        }
        if (answer == null || !answer.hasMoreElements()) {
            PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
            for (Plugin plugin : pluginManager.getPlugins()) {
                String pluginName = pluginManager.getPluginDirectory(plugin).getName();
                if ("clustering".equals(pluginName) || "admin".equals(pluginName)) {
                    continue;
                }
                PluginClassLoader pluginClassloader = pluginManager.getPluginClassloader(plugin);
                try {
                    answer = pluginClassloader.getResources(name);
                }
                catch (IOException e) {
                    // Ignore
                }
                if (answer != null && answer.hasMoreElements()) {
                    return answer;
                }
            }
        }
        return answer;
    }
}
