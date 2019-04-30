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

package org.jivesoftware.openfire.container;

import java.io.File;

/**
 * Plugin interface. Plugins enhance the functionality of Openfire. They can:<ul>
 *
 *      <li>Act as {@link org.xmpp.component.Component Components} to implement
 *      additional features in the XMPP protocol.
 *      <li>Dynamically modify the admin console.
 *      <li>Use the Openfire API to add new functionality to the server.
 * </ul>
 *
 * Plugins live in the {@code plugins} directory of {@code home}. Plugins
 * that are packaged as JAR files will be automatically expanded into directories. A
 * plugin directory should have the following structure:
 *
 * <pre>[pluginDir]
 *    |-- plugin.xml
 *    |-- classes/
 *    |-- lib/</pre>
 *
 * The {@code classes} and {@code lib} directory are optional. Any files in the
 * {@code classes} directory will be added to the classpath of the plugin, as well
 * as any JAR files in the {@code lib} directory. The {@code plugin.xml} file is
 * required, and specifies the className of the Plugin implementation. The XML file
 * should resemble the following XML:
 *
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;plugin&gt;
 *     &lt;class&gt;org.example.YourPlugin&lt;/class&gt;
 *     &lt;name&gt;Example Plugin&lt;/name&gt;
 *     &lt;description&gt;This is an example plugin.&lt;/description&gt;
 *     &lt;author&gt;Foo Inc.&lt;/author&gt;
 *     &lt;version&gt;1.0&lt;/version&gt;
 *     &lt;minServerVersion&gt;3.0.0&lt;/minServerVersion&gt;
 *     &lt;licenseType&gt;gpl&lt;/licenseType&gt;
 * &lt;/plugin&gt;</pre>
 * <p>
 * Each plugin will be loaded in its own class loader, unless the plugin is configured
 * with a parent plugin.</p>
 *
 * Please see the Plugin Developer Guide (available with the
 * Openfire documentation) for additional details about plugin development.
 *
 * @author Matt Tucker
 */
public interface Plugin {

    /**
     * Initializes the plugin.
     *
     * @param manager the plugin manager.
     * @param pluginDirectory the directory where the plugin is located.
     */
    void initializePlugin( PluginManager manager, File pluginDirectory );

    /**
     * Destroys the plugin.<p>
     *
     * Implementations of this method must release all resources held
     * by the plugin such as file handles, database or network connections,
     * and references to core Openfire classes. In other words, a
     * garbage collection executed after this method is called must be able
     * to clean up all plugin classes.
     */
    void destroyPlugin();

}
