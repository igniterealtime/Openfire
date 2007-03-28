/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
 * Plugins live in the <tt>plugins</tt> directory of <tt>home</tt>. Plugins
 * that are packaged as JAR files will be automatically expanded into directories. A
 * plugin directory should have the following structure:
 *
 * <pre>[pluginDir]
 *    |-- plugin.xml
 *    |-- classes/
 *    |-- lib/</pre>
 *
 * The <tt>classes</tt> and <tt>lib</tt> directory are optional. Any files in the
 * <tt>classes</tt> directory will be added to the classpath of the plugin, as well
 * as any JAR files in the <tt>lib</tt> directory. The <tt>plugin.xml</tt> file is
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
 * <p/>
 * Each plugin will be loaded in its own class loader, unless the plugin is configured
 * with a parent plugin.<p/>
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
    public void initializePlugin(PluginManager manager, File pluginDirectory);

    /**
     * Destroys the plugin.<p>
     *
     * Implementations of this method must release all resources held
     * by the plugin such as file handles, database or network connections,
     * and references to core Openfire classes. In other words, a
     * garbage collection executed after this method is called must be able
     * to clean up all plugin classes.
     */
    public void destroyPlugin();

}