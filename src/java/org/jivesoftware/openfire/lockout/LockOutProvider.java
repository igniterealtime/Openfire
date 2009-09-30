/**
 * $Revision$
 * $Date$
 *
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
     * period that the specified account is going to be disabled or null if user can log in
     * just fine.
     *
     * @param username Username of account to request status of.
     * @return The LockOutFlag instance describing the accounts disabled status or null if user
     *         account specified is not currently locked out (disabled).
     */
    public LockOutFlag getDisabledStatus(String username);

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

    /**
     * Returns true if the LockOutProvider allows for a delayed start to the lockout.
     * e.g. you can set the lockout to start in one hour.  This is really only used for UI
     * in the admin interface.  It's up to the provider implementation to ignore the start
     * time.
     *
     * @return true if the lock out provider provides this feature.
     */
    public boolean isDelayedStartSupported();

    /**
     * Returns true if the LockOutProvider allows for a timeout after which the lock out will expire.
     * e.g. you can set the lockout to only last for one day.  This is really only used for UI
     * in the admin interface.  It's up to the provider implementation to ignore the end
     * time.
     *
     * @return true if the lcok out provider provides this feature.
     */
    public boolean isTimeoutSupported();

    /**
     * Returns true if the lock out flags should not be cached, meaning every status lookup will
     * go straight to the source.  This is typically set if the status can change on the provider
     * target without Openfire knowing about it.
     *
     * @return true if disabled status should not be cached.
     */
    public boolean shouldNotBeCached();

}
