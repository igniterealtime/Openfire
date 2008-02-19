/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.NotLockedOutException;

/**
 * The ClearspaceLockOutProvider uses the UserService web service inside of Clearspace
 * to retrieve user properties from Clearspace.  One of these properties refers to whether
 * the user is disabled or not.  In the future we may implement this in a different manner
 * that will require less overall communication with Clearspace.
 *
 * @author Daniel Henninger
 */
public class ClearspaceLockOutProvider implements LockOutProvider {

    /**
     * Generate a ClearspaceLockOutProvider instance.
     */
    public ClearspaceLockOutProvider() {

    }

    /**
     * The ClearspaceLockOutProvider will retrieve lockout information from Clearspace's user properties.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#getDisabledStatus(String)
     */
    public LockOutFlag getDisabledStatus(String username) throws NotLockedOutException {
        // TODO: Will need to retrieve disabled status and return it.
        return null;
    }

    /**
     * The ClearspaceLockOutProvider will set lockouts in Clearspace itself.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#setDisabledStatus(org.jivesoftware.openfire.lockout.LockOutFlag)
     */
    public void setDisabledStatus(LockOutFlag flag) {
        // TODO: Will need to set disabled status.
    }

    /**
     * The ClearspaceLockOutProvider will set lockouts in Clearspace itself.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#unsetDisabledStatus(String)
     */
    public void unsetDisabledStatus(String username) {
        // TODO: Will need to unset disabled status.
    }

    /**
     * The ClearspaceLockOutProvider will set lockouts in Clearspace itself.
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isReadOnly()
     */
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Clearspace only supports a strict "are you disabled or not".
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isDelayedStartSupported()
     */
    public boolean isDelayedStartSupported() {
        return false;
    }

    /**
     * Clearspace only supports a strict "are you disabled or not".
     * @see org.jivesoftware.openfire.lockout.LockOutProvider#isTimeoutSupported()
     */
    public boolean isTimeoutSupported() {
        return false;
    }

}
