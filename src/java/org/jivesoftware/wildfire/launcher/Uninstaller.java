/**
 * $RCSfile: ,v $
 * $Revision: $
 * $Date:  $
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software.
 * Use is subject to license terms.
 */
package org.jivesoftware.wildfire.launcher;

import com.install4j.api.Context;
import com.install4j.api.ProgressInterface;
import com.install4j.api.UninstallAction;

import java.io.File;

/**
 * Used with the Install4J installer to uninstall the remaining files within
 * the WildFire install.
 */
public class Uninstaller extends UninstallAction {

    public int getPercentOfTotalInstallation() {
        return 0;
    }

    public boolean performAction(Context context, ProgressInterface progressInterface) {
        final File installationDirectory = context.getInstallationDirectory();

        File libDirectory = new File(installationDirectory, "lib");

        // If the directory still exists, remove all libs.
        if (libDirectory.exists() && libDirectory.isDirectory()) {
            File[] archives = libDirectory.listFiles();
            int no = archives != null ? archives.length : 0;
            for (int i = 0; i < no; i++) {
                File file = archives[i];
                file.delete();
            }
        }

        return super.performAction(context, progressInterface);    //To change body of overridden methods use File | Settings | File Templates.
    }


}

