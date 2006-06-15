/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.update;

/**
 * Plugin available at jivesoftware.org. The plugin may or may not be locally installed.
 *
 * @author Gaston Dombiak
 */
public class AvailablePlugin {

    /**
     * Name of the plugin.
     */
    private String name;
    /**
     * Latest version of the plugin that was found.
     */
    private String latestVersion;
    /**
     * URL from where the latest version of the plugin can be downloaded.
     */
    private String url;
    /**
     * Icon's URL of the latest version of the plugin.
     */
    private String icon;
    /**
     * README URL of the latest version of the plugin.
     */
    private String readme;
    /**
     * Changelog URL of the latest version of the plugin.
     */
    private String changelog;
    /**
     * Flag that indicates whether the plugin is commercial or not.
     */
    private  boolean commercial;
    /**
     * Description of the plugin as specified in plugin.xml.
     */
    private String description;
    /**
     * Author of the plugin as specified in plugin.xml.
     */
    private String author;
    /**
     * Minimum server version required by this plugin as specified in plugin.xml.
     */
    private String minServerVersion;

    public AvailablePlugin(String name, String description, String latestVersion, String author,
            String icon, String changelog, String readme, boolean commercial,
            String minServerVersion, String url) {
        this.author = author;
        this.icon = icon;
        this.changelog = changelog;
        this.readme = readme;
        this.commercial = commercial;
        this.description = description;
        this.latestVersion = latestVersion;
        this.minServerVersion = minServerVersion;
        this.name = name;
        this.url = url;
    }

    /**
     * Returns the name of the plugin that is not installed.
     *
     * @return the name of the plugin that is not installed.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the latest version of the plugin that is not installed.
     *
     * @return the latest version of the plugin that is not installed.
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Return the icon's URL of the latest version of the plugin.
     *
     * @return the icon's URL of the latest version of the plugin.
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Returns the URL to the README file of the latest version of the plugin.
     *
     * @return the URL to the README file of the latest version of the plugin.
     */
    public String getReadme() {
        return readme;
    }

    /**
     * Returns the URL to the change log of the plugin.
     *
     * @return the URL to the change log of the plugin.
     */
    public String getChangelog() {
        return changelog;
    }

    /**
     * Returns the URL from where the plugin.
     *
     * @return the URL from where the plugin.
     */
    public String getURL() {
        return url;
    }

    /**
     * Returns the author of the plugin as specified in plugin.xml.
     *
     * @return author of the plugin as specified in plugin.xml.
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Returns true if the plugin is commercial.
     *
     * @return true if the plugin is commercial.
     */
    public boolean isCommercial() {
        return commercial;
    }

    /**
     * Returns the description of the plugin as specified in plugin.xml.
     *
     * @return description of the plugin as specified in plugin.xml.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the minimum server version required by this plugin as specified in plugin.xml.
     *
     * @return minimum server version required by this plugin as specified in plugin.xml.
     */
    public String getMinServerVersion() {
        return minServerVersion;
    }
}
