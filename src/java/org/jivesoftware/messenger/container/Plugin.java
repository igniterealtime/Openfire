/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.container;

import java.io.File;

/**
 * Plugin interface. Plugins enhance the functionality of Jive Messenger. They can:<ul>
 *
 *      <li>Act as {@link org.xmpp.component.Component Components} to implement
 *      additional features in the XMPP protocol.
 *      <li>Dynamically modify the admin console.
 *      <li>Use the Jive Messenger API to add new functionality to server.
 * </ul>
 *
 * Plugins live in the <tt>plugins</tt> directory of <tt>messengerHome</tt>. Plugins
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
 * &lt;/plugin&gt;</pre>
 *
 * Each plugin will be loaded in its own class loader.
 *
 * @author Matt Tucker
 */
public interface Plugin {

    /**
     * Returns the name of this plugin.
     *
     * @return the plugin's name.
     */
    public String getName();

    /**
     * Returns the description of the plugin or <tt>null</tt> if there is no description.
     *
     * @return this plugin's description.
     */
    public String getDescription();

    /**
     * Returns the author of this plugin.
     *
     * @return the plugin's author.
     */
    public String getAuthor();

    /**
     * The plugin's version.
     *
     * @return the version of the plugin.
     */
    public String getVersion();

    /**
     * Initializes the plugin.
     *
     * @param manager the plugin manager.
     * @param pluginDirectory the directory where the plugin is located.
     */
    public void initialize(PluginManager manager, File pluginDirectory);

    /**
     * Destroys the plugin.
     */
    public void destroy();

}