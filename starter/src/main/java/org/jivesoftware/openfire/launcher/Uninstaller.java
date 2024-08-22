/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

import com.install4j.api.actions.AbstractUninstallAction;
import com.install4j.api.context.UninstallerContext;
import com.install4j.api.context.UserCanceledException;

import java.io.File;

/**
 * Used with the Install4J installer to uninstall the remaining files within
 * the Openfire install.
 */
public class Uninstaller extends AbstractUninstallAction
{
    @Override
    public boolean uninstall(UninstallerContext context) throws UserCanceledException
    {
        final File installationDirectory = context.getInstallationDirectory();

        File libDirectory = new File(installationDirectory, "lib");

        // If the directory still exists, remove all JAR files.
        boolean result = true;
        if (libDirectory.exists() && libDirectory.isDirectory()) {
            File[] jars = libDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jars != null) {
                for (File jar : jars) {
                    if (!jar.delete()) {
                        result = false;
                    }
                }
            }
        }

        return result;
    }
}
