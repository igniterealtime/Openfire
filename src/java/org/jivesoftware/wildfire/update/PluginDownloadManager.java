/**
 * $RCSfile$
 * $Revision: 3191 $
 * $Date: 2005-12-12 13:41:22 -0300 (Mon, 12 Dec 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.update;

import org.jivesoftware.wildfire.XMPPServer;

/**
 * Service that allow for aysynchrous calling of system managers.
 *
 * @author Derek DeMoro
 */
public class PluginDownloadManager {

    /**
     * Starts the download process of a given plugin with it's URL.
     *
     * @param url the url of the plugin to download.
     * @return the Update.
     */
    public Update downloadPlugin(String url) {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
        updateManager.downloadPlugin(url);

        Update returnUpdate = null;
        for (Update update : updateManager.getPluginUpdates()) {
            if (update.getURL().equals(url)) {
                returnUpdate = update;
                break;
            }
        }

        return returnUpdate;
    }

    /**
     * Installs a new plugin into Wildfire.
     *
     * @param url the url of the plugin to install.
     * @param hashCode the matching hashcode of the <code>AvailablePlugin</code>.
     * @return the hashCode.
     */
    public DownloadStatus installPlugin(String url, int hashCode) {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

        boolean worked = updateManager.downloadPlugin(url);

        final DownloadStatus status = new DownloadStatus();
        status.setHashCode(hashCode);
        status.setSuccessfull(worked);
        status.setUrl(url);

        return status;
    }
}