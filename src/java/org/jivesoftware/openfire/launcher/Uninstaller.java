/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.launcher;

import com.install4j.api.Context;
import com.install4j.api.ProgressInterface;
import com.install4j.api.UninstallAction;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Used with the Install4J installer to uninstall the remaining files within
 * the Openfire install.
 */
public class Uninstaller extends UninstallAction {

    public int getPercentOfTotalInstallation() {
        return 0;
    }

    public boolean performAction(Context context, ProgressInterface progressInterface) {
        final File installationDirectory = context.getInstallationDirectory();

        File libDirectory = new File(installationDirectory, "lib");

        // If the directory still exists, remove all JAR files.
        if (libDirectory.exists() && libDirectory.isDirectory()) {
            File[] jars = libDirectory.listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar");
                }
            });
            for (File jar : jars) {
                jar.delete();
            }
        }

        return super.performAction(context, progressInterface);
    }

}