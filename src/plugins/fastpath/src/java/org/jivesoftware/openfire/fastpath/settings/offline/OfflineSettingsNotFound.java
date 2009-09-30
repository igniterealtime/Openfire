/**
 * $RCSfile$
 * $Revision: 19036 $
 * $Date: 2005-06-13 16:53:54 -0700 (Mon, 13 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.settings.offline;

/**
 * <p>Flags an exception when an OfflineSetting is requested but not found.</p>
 *
 * @author Derek DeMoro
 */
public class OfflineSettingsNotFound extends Exception {

    public OfflineSettingsNotFound() {
    }

    public OfflineSettingsNotFound(String message) {
        super(message);
    }
}

