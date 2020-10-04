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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JavaSpecVersion;
import org.jivesoftware.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
     * of the plugin (which is casted down to lower-case). If the value could not be found, {@code null} will be returned.
     *
     * @param plugin The plugin (cannot be null)
     * @return the parent plugin's directory name
     */
    public static String getParentPlugin( Plugin plugin )
    {
        return getParentPlugin( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the name of the directory of the parent for this plugin. The value is retrieved from the plugin.xml file
     * of the plugin (which is casted down to lower-case). If the value could not be found, {@code null} will be returned.
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
     * Returns the canonical name for the plugin, derived from the plugin archive file name.
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
        return XMPPServer.getInstance().getPluginManager().getCanonicalName( plugin );
    }

    /**
     * Returns the canonical name for the plugin, derived from the plugin directory or archive file name.
     *
     * The provided path can refer to either the plugin archive file, or the directory in which the archive was
     * extracted.
     *
     * Note that this value can be different from the 'human readable' plugin name, as returned by {@link #getName(Path)}.
     *
     * @param pluginPath the path of the plugin directory, or plugin archive file.
     * @return the plugin's canonical name.
     */
    public static String getCanonicalName( Path pluginPath )
    {
        final String pathFileName = pluginPath.getFileName().toString().toLowerCase();
        if ( pluginPath.toFile().isDirectory() )
        {
            return pathFileName;
        }
        else
        {
            // Strip file extension
            return pathFileName.substring( 0, pathFileName.lastIndexOf( '.' ) );
        }
    }

    /**
     * Returns the name of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value could not
     * be found, {@code null} will be returned. Note that this value is a 'human readable' name, which can be distinct
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
     * be found, {@code null} will be returned. Note that this value is a 'human readable' name, which can be distinct
     * from the name of the plugin directory as returned by {@link #getCanonicalName(Path)}.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's human-readable name.
     */
    public static String getName( Path pluginDir )
    {
        final String name = getElementValue( pluginDir, "/plugin/name" );
        final String pluginName = getCanonicalName( pluginDir );
        if ( name != null ) {
            try {
                return AdminConsole.getAdminText(name, pluginName);
            } catch (final Exception e) {
                Log.warn("Unexpected exception attempting to retrieve admin text", e);
                // Default to non-internationalised name
                return name;
            }
        }
        return pluginName;
    }

    /**
     * Returns the description of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value
     * could not be found, {@code null} will be returned.
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
     * could not be found, {@code null} will be returned.
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
     * not be found, {@code null} will be returned.
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
     * not be found, {@code null} will be returned.
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
     * could not be found, {@code null} will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's version.
     */
    public static Version getVersion( Plugin plugin )
    {
        return getVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the version of a plugin. The value is retrieved from the plugin.xml file of the plugin. If the value
     * could not be found, {@code null} will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's version.
     */
    public static Version getVersion( Path pluginDir )
    {
        final String value = getElementValue( pluginDir, "/plugin/version" );

        if ( value == null || value.trim().isEmpty() )
        {
            return null;
        }

        return new Version( value );
    }

    /**
     * Returns the minimum server version this plugin can run within. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, {@code null} will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's minimum server version (possibly null).
     */
    public static Version getMinServerVersion( Plugin plugin )
    {
        return getMinServerVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the minimum server version this plugin can run within. The value is retrieved from the plugin.xml file
     * of the plugin. If the value could not be found, {@code null} will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's minimum server version (possibly null).
     */
    public static Version getMinServerVersion( Path pluginDir )
    {
        final String value = getElementValue( pluginDir, "/plugin/minServerVersion" );

        if ( value == null || value.trim().isEmpty() )
        {
            return null;
        }

        return new Version( value );
    }

    /**
     * Returns the server version up, but not including, in which this plugin can run within. The value is retrieved from
     * the plugin.xml file of the plugin. If the value could not be found, {@code null} will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's maximum server version (possibly null).
     */
    public static Version getPriorToServerVersion( Plugin plugin )
    {
        return getPriorToServerVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the server version up, but not including, in which this plugin can run within. The value is retrieved from
     * the plugin.xml file of the plugin. If the value could not be found, {@code null} will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's maximum server version (possibly null).
     */
    public static Version getPriorToServerVersion( Path pluginDir )
    {
        final String value = getElementValue( pluginDir, "/plugin/priorToServerVersion" );
        if ( value == null || value.trim().isEmpty() )
        {
            return null;
        }

        return new Version( value );
    }

    /**
     * Returns the minimum Java specification version this plugin needs to run. The value is retrieved from the
     * plugin.xml file of the plugin. If the value could not be found, {@code null} will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's minimum Java version (possibly null).
     */
    public static JavaSpecVersion getMinJavaVersion( Plugin plugin )
    {
        return getMinJavaVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the minimum Java specification version this plugin needs to run. The value is retrieved from the
     * plugin.xml file of the plugin. If the value could not be found, {@code null} will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's minimum Java version (possibly null).
     */
    public static JavaSpecVersion getMinJavaVersion( Path pluginDir )
    {
        final String value = getElementValue( pluginDir, "/plugin/minJavaVersion" );

        if ( value == null || value.trim().isEmpty() )
        {
            return null;
        }

        return new JavaSpecVersion( value );
    }

    public static boolean isCsrfProtectionEnabled(final Plugin plugin) {
        return isCsrfProtectionEnabled(XMPPServer.getInstance().getPluginManager().getPluginPath(plugin));
    }

    public static boolean isCsrfProtectionEnabled(final Path pluginDir) {
        return Boolean.parseBoolean(getElementValue(pluginDir, "/plugin/csrfProtectionEnabled"));
    }

    /**
     * Returns the database schema key of a plugin, if it exists. The value is retrieved from the plugin.xml file of the
     * plugin. If the value could not be found, {@code null} will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's database schema key or {@code null} if it doesn't exist.
     */
    public static String getDatabaseKey( Plugin plugin )
    {
        return getDatabaseKey( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the database schema key of a plugin, if it exists. The value is retrieved from the plugin.xml file of the
     * plugin. If the value could not be found, {@code null} will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's database schema key or {@code null} if it doesn't exist.
     */
    public static String getDatabaseKey( Path pluginDir )
    {
        return getElementValue( pluginDir, "/plugin/databaseKey" );
    }

    /**
     * Returns the database schema version of a plugin, if it exists. The value is retrieved from the plugin.xml file of
     * the plugin. If the value could not be found, {@code -1} will be returned.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's database schema version or {@code -1} if it doesn't exist.
     */
    public static int getDatabaseVersion( Plugin plugin )
    {
        return getDatabaseVersion( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the database schema version of a plugin, if it exists. The value is retrieved from the plugin.xml file of
     * the plugin. If the value could not be found, {@code -1} will be returned.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's database schema version or {@code -1} if it doesn't exist.
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
     * file of the plugin.
     *
     * Note that this method will return data only for plugins that have successfully been installed. To obtain data
     * from plugin (directories) that have not (yet) been  installed, refer to the overloaded method that takes a Path
     * argument.
     *
     * @param plugin The plugin (cannot be null)
     * @return the plugin's license agreement.
     */
    public static String getLicense( Plugin plugin )
    {
        return getLicense( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    /**
     * Returns the license agreement type that the plugin is governed by. The value is retrieved from the plugin.xml
     * file of the plugin.
     *
     * @param pluginDir the path of the plugin directory.
     * @return the plugin's license agreement.
     */
    public static String getLicense( Path pluginDir )
    {
        return getElementValue( pluginDir, "/plugin/licenseType" );
    }

    public static URL getIcon( Plugin plugin )
    {
        return getIcon( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    public static URL getIcon( Path pluginDir )
    {
        Path icon = pluginDir.resolve( "logo_small.png" );
        if ( !icon.toFile().exists() )
        {
            icon = pluginDir.resolve( "logo_small.gif" );
        }
        if ( !icon.toFile().exists() )
        {
            return null;
        }

        try
        {
            return icon.toUri().toURL();
        }
        catch ( MalformedURLException e )
        {
            Log.warn( "Unable to parse URL for icon of plugin '{}'.", getCanonicalName( pluginDir ), e );
            return null;
        }
    }

    public static URL getReadme( Plugin plugin )
    {
        return getReadme( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    public static URL getReadme( Path pluginDir )
    {
        final Path file = pluginDir.resolve( "readme.html" );
        if ( !file.toFile().exists() )
        {
            return null;
        }

        try
        {
            return file.toUri().toURL();
        }
        catch ( MalformedURLException e )
        {
            Log.warn( "Unable to parse URL for readme of plugin '{}'.", getCanonicalName( pluginDir ), e );
            return null;
        }
    }

    public static URL getChangelog( Plugin plugin )
    {
        return getChangelog( XMPPServer.getInstance().getPluginManager().getPluginPath( plugin ) );
    }

    public static URL getChangelog( Path pluginDir )
    {
        final Path file = pluginDir.resolve( "changelog.html" );
        if ( !file.toFile().exists() )
        {
            return null;
        }

        try
        {
            return file.toUri().toURL();
        }
        catch ( MalformedURLException e )
        {
            Log.warn( "Unable to parse URL for changelog of plugin '{}'.", getCanonicalName( pluginDir ), e );
            return null;
        }
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
                final SAXReader saxReader = setupSAXReader();
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

    private static SAXReader setupSAXReader() throws SAXException {
        final SAXReader saxReader = new SAXReader();
        saxReader.setEntityResolver((publicId, systemId) -> {
            throw new IOException("External entity denied: " + publicId + " // " + systemId);
        });
        saxReader.setEncoding( "UTF-8" );
        saxReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        saxReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        saxReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return saxReader;
    }
}
