/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

    @Override
    public int getPercentOfTotalInstallation() {
        return 0;
    }

    @Override
    public boolean performAction(Context context, ProgressInterface progressInterface) {
        final File installationDirectory = context.getInstallationDirectory();

        File libDirectory = new File(installationDirectory, "lib");

        // If the directory still exists, remove all JAR files.
        if (libDirectory.exists() && libDirectory.isDirectory()) {
            File[] jars = libDirectory.listFiles(new FilenameFilter() {
                @Override
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
