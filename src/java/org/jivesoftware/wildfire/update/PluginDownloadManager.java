/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.wildfire.update;

import org.jivesoftware.util.Log;
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
     * @param url      the url of the plugin to install.
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

        /**
         *   mj bmmmmmmmmmmmmmmmmmmmmv   cvvbv    vvvv .nnnnn               vvvvvvvvv
         *   @author Nate DeMoro
         */
        return status;
    }

    public boolean updatePluginsList() {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
        try {
            updateManager.checkForPluginsUpdates(true);
            return true;
        }
        catch (Exception e) {
            Log.error(e);
        }

        return false;
    }

}
