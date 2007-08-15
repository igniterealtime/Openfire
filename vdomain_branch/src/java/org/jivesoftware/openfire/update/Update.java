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

package org.jivesoftware.openfire.update;

/**
 * An Update represents a component that needs to be updated. By component we can refer
 * to the Openfire server itself or to any of the installed plugins.
 *
 * @author Gaston Dombiak
 */
public class Update {

    /**
     * Name of the component that is outdated. The name could be of the server
     * (i.e. "Openfire") or of installed plugins.
     */
    private String componentName;
    /**
     * Latest version of the component that was found.
     */
    private String latestVersion;
    /**
     * URL from where the latest version of the component can be downloaded.
     */
    private String url;
    /**
     * Changelog URL of the latest version of the component.
     */
    private String changelog;

    /**
     * Flag that indicates if the plugin was downloaded. This flag only makes sense for
     * plugins since we currently do not support download new openfire releases.
     */
    private boolean downloaded;

    public Update(String componentName, String latestVersion, String changelog, String url) {
        this.componentName = componentName;
        this.latestVersion = latestVersion;
        this.changelog = changelog;
        this.url = url;
    }

    /**
     * Returns the name of the component that is outdated. When the server is the
     * outdated component then a "Openfire" will be returned. Otherwise, the name of
     * the outdated plugin is returned.
     *
     * @return the name of the component that is outdated.
     */
    public String getComponentName() {
        return componentName;
    }

    /**
     * Returns the latest version of the component that was found.
     *
     * @return the latest version of the component that was found.
     */
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the URL to the change log of the latest version of the component.
     *
     * @return the URL to the change log of the latest version of the component.
     */
    public String getChangelog() {
        return changelog;
    }

    /**
     * Returns the URL from where the latest version of the component can be downloaded.
     *
     * @return the URL from where the latest version of the component can be downloaded.
     */
    public String getURL() {
        return url;
    }

    /**
     * Returns true if the plugin was downloaded. Once a plugin has been downloaded
     * it may take a couple of seconds to be installed. This flag only makes sense for
     * plugins since we currently do not support download new openfire releases.
     *
     * @return true if the plugin was downloaded.
     */
    public boolean isDownloaded() {
        return downloaded;
    }

    /**
     * Sets if the plugin was downloaded. Once a plugin has been downloaded
     * it may take a couple of seconds to be installed. This flag only makes sense for
     * plugins since we currently do not support download new openfire releases.
     *
     * @param downloaded true if the plugin was downloaded.
     */
    public void setDownloaded(boolean downloaded) {
        this.downloaded = downloaded;
    }

    /**
     * Returns the hashCode for this update object.
     * @return hashCode
     */
    public int getHashCode(){
        return hashCode();
    }
}
