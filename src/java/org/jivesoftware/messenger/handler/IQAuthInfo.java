/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
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