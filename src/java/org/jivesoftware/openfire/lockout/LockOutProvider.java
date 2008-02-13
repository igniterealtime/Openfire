/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.lockout;

/**
 * A LockOutProvider handles storage of information about disabled accounts, and requests for
 * whether an account is currently disabled.  If set read-only, only requests for disabled
 * status are expected to work.
 *
 * @author Daniel Henninger
 */
public interface LockOutProvider {

    /**
     * Returns a LockOutFlag for a given username, which contains information about the time
     * period that the specified account is going to be disabled.
     *
     * @param username Username of account to request status of.
     * @return The LockOutFlag instance describing the accounts disabled status.
     * @throws NotLockedOutException if user account specified is not currently locked out (disabled).
     */
    public LockOutFlag getDisabledStatus(String username) throws NotLockedOutException;

    /**
     * Sets the locked out (disabled) status of an account according to a LockOutFlag.
     *
     * @param flag A LockOutFlag instance to describe the disabled status of a user.
     */
    public void setDisabledStatus(LockOutFlag flag);

    /**
     * Unsets the locked out (disabled) status of an account, thereby enabling it/cancelling the disable.
     *
     * @param username User to enable.
     */
    public void unsetDisabledStatus(String username);

    /**
     * Returns true if this LockOutProvider is read-only. When read-only,
     * disabled status of accounts can not be changed via Openfire.
     *
     * @return true if the lock out provider is read-only.
     */
    public boolean isReadOnly();

}
