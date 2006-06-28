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
import org.jivesoftware.wildfire.update.Update;
import org.jivesoftware.wildfire.update.UpdateManager;

/**
 *
 */
public class PluginDownloadManager {

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

        try {
            updateManager.checkForPluginsUpdates(true);
        }
        catch (Exception e) {
            Log.error(e);
        }

        return returnUpdate;
    }


    public int installPlugin(String url, int hashCode) {
        UpdateManager updateManager = XMPPServer.getInstance().getUpdateManager();

        updateManager.downloadPlugin(url);

        return hashCode;
    }

}
