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

import java.time.Instant;

import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service that allow for aysynchrous calling of system managers.
 *
 * @author Derek DeMoro
 */
public class PluginDownloadManager {

    private static final Logger Log = LoggerFactory.getLogger(PluginDownloadManager.class);

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
     * Installs a new plugin into Openfire.
     *
     * @param url the url of the plugin to install.
     * @param version the version of the new plugin
     * @param hashCode the matching hashcode of the <code>AvailablePlugin</code>.
     * @return the hashCode.
     */
    public DownloadStatus installPlugin(String url, String version, int hashCode) {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

        boolean worked = updateManager.downloadPlugin(url);

        final DownloadStatus status = new DownloadStatus();
        status.setHashCode(hashCode);
        status.setVersion(version);
        status.setSuccessfull(worked);
        status.setUrl(url);

        return status;
    }

    /**
     * Updates the PluginList from the server. Please note, this method is used with javascript calls and will not
     * be found with a find usages.
     *
     * @return true if the plugin list was updated.
     */
    public boolean updatePluginsList() {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();
        try {
            // Todo: Unify update checking into one xml file. Have the update check set the last check property.
            updateManager.checkForServerUpdate(true);
            updateManager.checkForPluginsUpdates(true);

            // Keep track of the last time we checked for updates
            UpdateManager.LAST_UPDATE_CHECK.setValue(Instant.now());

            return true;
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }

        return false;
    }
}
