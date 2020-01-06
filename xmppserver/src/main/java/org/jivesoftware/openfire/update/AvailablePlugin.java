/*
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

package org.jivesoftware.openfire.update;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.dom4j.Element;
import org.jivesoftware.openfire.container.PluginMetadata;
import org.jivesoftware.util.JavaSpecVersion;
import org.jivesoftware.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plugin available at igniterealtime.org. The plugin may or may not be locally installed.
 *
 * @author Gaston Dombiak
 */
public class AvailablePlugin extends PluginMetadata
{
    private static final Logger Log = LoggerFactory.getLogger( AvailablePlugin.class );
    private static final DateFormat RELEASE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final DateFormat RELEASE_DATE_DISPLAY_FORMAT = DateFormat.getDateInstance(DateFormat.MEDIUM);

    /**
     * URL from where the latest version of the plugin can be downloaded.
     */
    private final URL downloadURL;

    /**
     * Size in bytes of the plugin jar file.
     */
    private final long fileSize;
    private final String releaseDate;

    public static AvailablePlugin getInstance( Element plugin )
    {
        String pluginName = plugin.attributeValue("name");
        Version latestVersion = null;
        String latestVersionValue = plugin.attributeValue("latest");
        if ( latestVersionValue != null && !latestVersionValue.isEmpty() )
        {
            latestVersion = new Version( latestVersionValue );
        }

        URL icon = null;
        String iconValue = plugin.attributeValue("icon");
        if ( iconValue != null && !iconValue.isEmpty() )
        {
            try
            {
                icon = new URL( iconValue );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "Unable to create icon URL from value '{}' for plugin {}.", iconValue, pluginName, e );
            }
        }

        URL readme = null;
        String readmeValue = plugin.attributeValue("readme");
        if ( readmeValue != null && !readmeValue.isEmpty() )
        {
            try
            {
                readme = new URL( readmeValue );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "Unable to create readme URL from value '{}' for plugin {}.", readmeValue, pluginName, e );
            }
        }

        URL changelog = null;
        String changelogValue = plugin.attributeValue("changelog");
        if ( changelogValue != null && !changelogValue.isEmpty() )
        {
            try
            {
                changelog = new URL( changelogValue );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "Unable to create changelog URL from value '{}' for plugin {}.", changelogValue, pluginName, e );
            }
        }
        URL downloadUrl = null;
        String downloadUrlValue = plugin.attributeValue("url");
        if ( downloadUrlValue != null && !downloadUrlValue.isEmpty() )
        {
            try
            {
                downloadUrl = new URL( downloadUrlValue );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "Unable to create download URL from value '{}' for plugin {}.", downloadUrlValue, pluginName, e );
            }
        }

        String license = plugin.attributeValue("licenseType");
        String description = plugin.attributeValue("description");
        String author = plugin.attributeValue("author");

        Version minServerVersion = null;
        String minServerVersionValue = plugin.attributeValue("minServerVersion");
        if ( minServerVersionValue != null && !minServerVersionValue.isEmpty() )
        {
            minServerVersion = new Version( minServerVersionValue );
        }

        Version priorToServerVersion = null;
        String priorToServerVersionValue = plugin.attributeValue("priorToServerVersion");
        if ( priorToServerVersionValue != null && !priorToServerVersionValue.isEmpty() )
        {
            priorToServerVersion = new Version( priorToServerVersionValue );
        }

        JavaSpecVersion minJavaVersion = null;
        String minJavaVersionValue = plugin.attributeValue( "minJavaVersion" );
        if ( minJavaVersionValue != null && !minJavaVersionValue.isEmpty() )
        {
            minJavaVersion = new JavaSpecVersion( minJavaVersionValue );
        }

        String releaseDate = null;
        final String releaseDateString = plugin.attributeValue("releaseDate");
        if( releaseDateString!= null) {
            try {
                releaseDate = RELEASE_DATE_DISPLAY_FORMAT.format(RELEASE_DATE_FORMAT.parse(releaseDateString));
            } catch (final ParseException e) {
                Log.warn("Unexpected exception parsing release date: " + releaseDateString, e);
            }
        }

        long fileSize = -1;
        String fileSizeValue = plugin.attributeValue("fileSize");
        if ( fileSizeValue != null && !fileSizeValue.isEmpty() )
        {
            fileSize = Long.parseLong( fileSizeValue );
        }

        String canonical = downloadUrlValue != null ? downloadUrlValue.substring( downloadUrlValue.lastIndexOf( '/' ) + 1, downloadUrlValue.lastIndexOf( '.' ) ) : null;

        return new AvailablePlugin(
                pluginName,
                canonical,
                description,
                latestVersion,
                author,
                icon,
                changelog,
                readme,
                license,
                minServerVersion,
                priorToServerVersion,
                minJavaVersion,
                downloadUrl,
                fileSize,
                releaseDate
        );

    }
    public AvailablePlugin(String name, String canonicalName, String description, Version latestVersion, String author,
                           URL icon, URL changelog, URL readme, String license,
                           Version minServerVersion, Version priorToServerVersion, JavaSpecVersion minJavaVersion,
                           URL downloadUrl, long fileSize, final String releaseDate) {
        super(
                name,
                canonicalName,
                description,
                latestVersion,
                author,
                icon,
                changelog,
                readme,
                license,
                minServerVersion,
                priorToServerVersion,
                minJavaVersion,
                false
        );
        this.downloadURL = downloadUrl;
        this.fileSize = fileSize;
        this.releaseDate = releaseDate;
    }

    /**
     * URL from where the latest version of the plugin can be downloaded.
     *
     * @return download URL.
     */
    public URL getDownloadURL() {
        return downloadURL;
    }
    /**
     * Returns the size in bytes of the plugin jar file.
     *
     * @return the size in bytes of the plugin jar file.
     */
    public long getFileSize() {
        return fileSize;
    }

    /**
     * @return the date the plugin was released
     */
    public String getReleaseDate() {
        return releaseDate;
    }
}
