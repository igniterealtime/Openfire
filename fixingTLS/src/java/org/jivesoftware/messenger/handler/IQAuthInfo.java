/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.handler;

import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Information for controlling the authentication options for the server.
 *
 * @author Iain Shigeoka
 */
public interface IQAuthInfo {

    /**
     * Returns true if anonymous authentication is allowed.
     *
     * @return true if anonymous logins are allowed
     */
    public boolean isAllowAnonymous();

    /**
     * Changes the server's support for anonymous authentication.
     *
     * @param isAnonymous True if anonymous logins should be allowed.
     * @throws UnauthorizedException If you don't have permission to adjust this setting
     */
    public void setAllowAnonymous(boolean isAnonymous) throws UnauthorizedException;
}