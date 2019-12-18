/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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

import java.net.URL;
import java.nio.file.Path;

import org.jivesoftware.util.JavaSpecVersion;
import org.jivesoftware.util.Version;

/**
 * A bean-like representation of the metadata of a plugin.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PluginMetadata
{
    /**
     * Human readable name of the plugin.
     */
    private final String name;

    /**
     * Canonical name of the plugin.
     */
    private final String canonicalName;

    /**
     * Description of the plugin as specified in plugin.xml.
     */
    private final String description;

    /**
     * The version of the plugin.
     */
    private final Version version;

    /**
     * Author of the plugin as specified in plugin.xml.
     */
    private final String author;

    /**
     * Icon's location of the plugin.
     */
    private final URL icon;

    /**
     * Changelog location of the latest version of the plugin.
     */
    private final URL changelog;

    /**
     * ReadMe location of the latest version of the plugin.
     */
    private final URL readme;

    /**
     * Type of license of the plugin.
     */
    private final String license;

    /**
     * Minimum server version (inclusive) required by this plugin as specified in plugin.xml.
     */
    private final Version minServerVersion;

    /**
     * Maximum server version (exclusive) required by this plugin as specified in plugin.xml.
     */
    private final Version priorToServerVersion;

    /**
     * Minimum Java (specification) version (inclusive) required by this plugin as specified in plugin.xml.
     */
    private final JavaSpecVersion minJavaVersion;

    /**
     * Indicates if the plugin supports standard CSRF protection
     */
    private final boolean csrfProtectionEnabled;

    /**
     * Constructs a metadata object based on a plugin.
     *
     * The plugin must be installed in Openfire.
     *
     * @param pluginDir the path of the plugin directory (cannot be null)
     * @return Metadata for the plugin (never null).
     */
    public static PluginMetadata getInstance( Path pluginDir )
    {
        return new PluginMetadata(
                PluginMetadataHelper.getName( pluginDir ),
                PluginMetadataHelper.getCanonicalName( pluginDir ),
                PluginMetadataHelper.getDescription( pluginDir ),
                PluginMetadataHelper.getVersion( pluginDir ),
                PluginMetadataHelper.getAuthor( pluginDir ),
                PluginMetadataHelper.getIcon( pluginDir ),
                PluginMetadataHelper.getChangelog( pluginDir ),
                PluginMetadataHelper.getReadme( pluginDir ),
                PluginMetadataHelper.getLicense( pluginDir ),
                PluginMetadataHelper.getMinServerVersion( pluginDir ),
                PluginMetadataHelper.getPriorToServerVersion( pluginDir ),
                PluginMetadataHelper.getMinJavaVersion( pluginDir ),
                PluginMetadataHelper.isCsrfProtectionEnabled( pluginDir )
                );
    }

    /**
     * Constructs a metadata object based on a plugin.
     *
     * The plugin must be installed in Openfire.
     *
     * @param plugin The plugin (cannot be null)
     * @return Metadata for the plugin (never null).
     */
    public static PluginMetadata getInstance( Plugin plugin )
    {
        return new PluginMetadata(
                PluginMetadataHelper.getName( plugin ),
                PluginMetadataHelper.getCanonicalName( plugin ),
                PluginMetadataHelper.getDescription( plugin ),
                PluginMetadataHelper.getVersion( plugin ),
                PluginMetadataHelper.getAuthor( plugin ),
                PluginMetadataHelper.getIcon( plugin ),
                PluginMetadataHelper.getChangelog( plugin ),
                PluginMetadataHelper.getReadme( plugin ),
                PluginMetadataHelper.getLicense( plugin ),
                PluginMetadataHelper.getMinServerVersion( plugin ),
                PluginMetadataHelper.getPriorToServerVersion( plugin ),
                PluginMetadataHelper.getMinJavaVersion( plugin ),
                PluginMetadataHelper.isCsrfProtectionEnabled( plugin )
        );
    }

    public PluginMetadata( String name, String canonicalName, String description, Version version, String author,
                           URL icon, URL changelog, URL readme, String license,
                           Version minServerVersion, Version priorToServerVersion, JavaSpecVersion minJavaVersion, boolean csrfProtectionEnabled)
    {
        this.name = name;
        this.canonicalName = canonicalName;
        this.description = description;
        this.version = version;
        this.author = author;
        this.icon = icon;
        this.changelog = changelog;
        this.readme = readme;
        this.license = license;
        this.minServerVersion = minServerVersion;
        this.priorToServerVersion = priorToServerVersion;
        this.minJavaVersion = minJavaVersion;
        this.csrfProtectionEnabled = csrfProtectionEnabled;
    }

    public String getName()
    {
        return name;
    }

    public String getCanonicalName()
    {
        return canonicalName;
    }

    public String getDescription()
    {
        return description;
    }

    public Version getVersion()
    {
        return version;
    }

    public String getAuthor()
    {
        return author;
    }

    public URL getIcon()
    {
        return icon;
    }

    public URL getChangelog()
    {
        return changelog;
    }

    public URL getReadme()
    {
        return readme;
    }

    public String getLicense()
    {
        return license;
    }

    public Version getMinServerVersion()
    {
        return minServerVersion;
    }

    public Version getPriorToServerVersion()
    {
        return priorToServerVersion;
    }

    public JavaSpecVersion getMinJavaVersion()
    {
        return minJavaVersion;
    }

    public String getHashCode() {
        return String.valueOf( hashCode() );
    }

    public boolean isCsrfProtectionEnabled() {
        return csrfProtectionEnabled;
    }
}
