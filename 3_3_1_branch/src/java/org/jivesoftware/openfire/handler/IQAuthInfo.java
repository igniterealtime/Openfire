/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.handler;

import org.jivesoftware.openfire.auth.UnauthorizedException;

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
    public boolean isAnonymousAllowed();

    /**
     * Changes the server's support for anonymous authentication.
     *
     * @param isAnonymous True if anonymous logins should be allowed.
     * @throws UnauthorizedException If you don't have permission to adjust this setting
     */
    public void setAllowAnonymous(boolean isAnonymous) throws UnauthorizedException;
}