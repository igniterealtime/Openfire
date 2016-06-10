/*
 * Copyright 2016 IgniteRealtime.org
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

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Various helper methods to retrieve plugin metadat from plugin.xml files.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginMetadataHelper
{
    private static final Logger Log = LoggerFactory.getLogger( PluginMetadataHelper.class );

    /**
     * Returns the name of the directory of the parent for this plugin. The value is retrieved from the plugin.xml file
     * of the plugin (which is casted down to lower-case). If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the parent plugin's directory name
     */
    public static String getParentPlugin( Path pluginDir )
    {
        final String name = getElementValue( pluginDir, "/plugin/parentPlugin" );
        if ( name != null && !name.isEmpty() )
        {
            return name.toLowerCase();
        }
        return null;
    }

    /**
     * Returns the canonical name for the plugin, derived from the plugin directory name.
     *
     * Note that this value can be different from the 'human readable' plugin name, as returned by {@link #getName(Path)}.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's canonical name.
     */
    public static String getCanonicalName( Plugin plugin )
    {
        return getCanonicalName( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the canonical name for the plugin, derived from the plugin directory name.
     *
     * Note that this value can be different from the 'human readable' plugin name, as returned by {@link #getName(Path)}.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's canonical name.
     */
    public static String getCanonicalName( Path pluginDir )
    {
        return pluginDir.getFileName().toString().toLowerCase();
    }

    /**
     * Returns the name of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value could not
     * be found, <tt>null</tt> will be returned. Note that this value is a 'human readable' name, which can be distinct
     * from the name of the plugin directory as returned by {@link #getCanonicalName(Path)}.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's human-readable name.
     */
    public static String getName( Plugin plugin )
    {
        return getName( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the name of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value could not
     * be found, <tt>null</tt> will be returned. Note that this value is a 'human readable' name, which can be distinct
     * from the name of the plugin directory as returned by {@link #getCanonicalName(Path)}.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's human-readable name.
     */
    public static String getName( Path pluginDir )
    {
        final String name = getElementValue( pluginDir, "/plugin/name" );
        final String pluginName = getCanonicalName( pluginDir );
        if ( name != null )
        {
            return AdminConsole.getAdminText( name, pluginName );
        }
        else
        {
            return pluginName;
        }
    }

    /**
     * Returns the description of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value
     * could not be found, <tt>null</tt> will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's description.
     */
    public static String getDescription( Plugin plugin )
    {
        return getDescription( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the description of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value
     * could not be found, <tt>null</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's description.
     */
    public static String getDescription( Path pluginDir )
    {
        final String name = getCanonicalName( pluginDir );
        final String description = getElementValue( pluginDir, "/plugin/description" );
        return AdminConsole.getAdminText( description, name );
    }

    /**
     * Returns the author of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value could
     * not be found, <tt>null</tt> will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's author.
     */
    public static String getAuthor( Plugin plugin )
    {
        return getAuthor( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the author of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value could
     * not be found, <tt>null</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's author.
     */
    public static String getAuthor( Path pluginDir )
    {
        return getElementValue( pluginDir, "/plugin/author" );
    }

    /**
     * Returns the version of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value
     * could not be found, <tt>null</tt> will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's version.
     */
    public static String getVersion( Plugin plugin )
    {
        return getVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the version of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value
     * could not be found, <tt>null</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's version.
     */
    public static String getVersion( Path pluginDir )
    {
        return getElementValue( pluginDir, "/plugin/version" );
    }

    /**
     * Returns the minimum server version this plugin can run within. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's minimum server version.
     */
    public static String getMinServerVersion( Plugin plugin )
    {
        return getMinServerVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the minimum server version this plugin can run within. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's minimum server version.
     */
    public static String getMinServerVersion( Path pluginDir )
    {
        return getElementValue( pluginDir, "/plugin/minServerVersion" );
    }

    /**
     * Returns the database schema key of a plugin, if it exists. The value is retrieved from the plugin.xml file of the
     * plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's database schema key or <tt>null</tt> if it doesn't exist.
     */
    public static String getDatabaseKey( Plugin plugin )
    {
        return getDatabaseKey( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the database schema key of a plugin, if it exists. The value is retrieved from the plugin.xml file of the
     * plugin. If the value could not be found, <tt>null</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's database schema key or <tt>null</tt> if it doesn't exist.
     */
    public static String getDatabaseKey( Path pluginDir )
    {
        return getElementValue( pluginDir, "/plugin/databaseKey" );
    }

    /**
     * Returns the database schema version of a plugin, if it exists. The value is retrieved from the plugin.xml file of
     * the plugin. If the value could not be found, <tt>-1</tt> will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's database schema version or <tt>-1</tt> if it doesn't exist.
     */
    public static int getDatabaseVersion( Plugin plugin )
    {
        return getDatabaseVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the database schema version of a plugin, if it exists. The value is retrieved from the plugin.xml file of
     * the plugin. If the value could not be found, <tt>-1</tt> will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's database schema version or <tt>-1</tt> if it doesn't exist.
     */
    public static int getDatabaseVersion( Path pluginDir )
    {
        String versionString = getElementValue( pluginDir, "/plugin/databaseVersion" );
        if ( versionString != null )
        {
            try
            {
                return Integer.parseInt( versionString.trim() );
            }
            catch ( NumberFormatException nfe )
            {
                Log.error( "Unable to parse the database version for plugin '{}'.", getCanonicalName( pluginDir ), nfe );
            }
        }
        return -1;
    }

    /**
     * Returns the license agreement type that the plugin is governed by. The value is retrieved from the plugin.xml
     * file of the plugin. If the value could not be found, {@link License#other} is returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's license agreement.
     */
    public static License getLicense( Plugin plugin )
    {
        return getLicense( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the license agreement type that the plugin is governed by. The value is retrieved from the plugin.xml
     * file of the plugin. If the value could not be found, {@link License#other} is returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's license agreement.
     */
    public static License getLicense( Path pluginDir )
    {
        String licenseString = getElementValue( pluginDir, "/plugin/licenseType" );
        if ( licenseString != null )
        {
            try
            {
                // Attempt to load the get the license type. We lower-case and trim the license type to give plugin
                // author's a break. If the license type is not recognized, we'll log the error and default to "other".
                return License.valueOf( licenseString.toLowerCase().trim() );
            }
            catch ( IllegalArgumentException iae )
            {
                Log.error( "Unrecognized license type '{}' for plugin '{}'.", licenseString.toLowerCase().trim(), getCanonicalName( pluginDir ), iae );
            }
        }
        return License.other;
    }

    /**
     * Returns the value of an element selected via an xpath expression from
     * a Plugin's plugin.xml file.
     *
     * @param pluginDir the path of the plugin directory.
     * @param xpath     the xpath expression.
     * @return the value of the element selected by the xpath expression.
     */
    static String getElementValue( Path pluginDir, String xpath )
    {
        if ( pluginDir == null )
        {
            return null;
        }
        try
        {
            final Path pluginConfig = pluginDir.resolve( "plugin.xml" );
            if ( Files.exists( pluginConfig ) )
            {
                final SAXReader saxReader = new SAXReader();
                saxReader.setEncoding( "UTF-8" );
                final Document pluginXML = saxReader.read( pluginConfig.toFile() );
                final Element element = (Element) pluginXML.selectSingleNode( xpath );
                if ( element != null )
                {
                    return element.getTextTrim();
                }
            }
        }
        catch ( Exception e )
        {
            Log.error( "Unable to get element value '{}' from plugin.xml of plugin in '{}':", xpath, pluginDir, e );
        }
        return null;
    }
}
