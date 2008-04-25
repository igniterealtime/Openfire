/**
 * $RCSfile$
 * $Revision: 19036 $
 * $Date: 2005-06-13 16:53:54 -0700 (Mon, 13 Jun 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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

