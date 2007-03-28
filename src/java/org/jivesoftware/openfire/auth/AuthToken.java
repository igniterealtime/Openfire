/**
 * $RCSfile$
 * $Revision: 691 $
 * $Date: 2004-12-13 15:06:54 -0300 (Mon, 13 Dec 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.user.UserManager;

/**
 * A token that proves that a user has successfully authenticated.
 *
 * @author Matt Tucker
 * @see AuthFactory
 */
public class AuthToken {

    private static final long serialVersionUID = 01L;
    private String username;

    /**
     * Constucts a new AuthToken with the specified username.
     *
     * @param username the username to create an authToken token with.
     */
    public AuthToken(String username) {
        this.username = username;
    }

    /**
     * Returns the username associated with this AuthToken.
     *
     * @return the username associated with this AuthToken.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Returns true if this AuthToken is the Anonymous auth token.
     *
     * @return true if this token is the anonymous AuthToken.
     */
    public boolean isAnonymous() {
        return username == null || !UserManager.getInstance().isRegisteredUser(username);
    }
}