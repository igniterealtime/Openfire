/**
 * $RCSfile$
 * $Revision: 1116 $
 * $Date: 2005-03-10 20:18:08 -0300 (Thu, 10 Mar 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.auth;

import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.user.DefaultUserProvider;

/**
 * Default AuthProvider implementation. It authenticates against the <tt>jiveUser</tt>
 * database table and supports plain text and digest authentication.
 *
 * Because each call to authenticate() makes a database connection, the
 * results of authentication should be cached whenever possible.
 *
 * @author Matt Tucker
 */
public class DefaultAuthProvider implements AuthProvider {

    private DefaultUserProvider userProvider;

    /**
     * Constructs a new DefaultAuthProvider.
     */
    public DefaultAuthProvider() {
        // Create a new default user provider since we need it to get the
        // user's password. We always create our own user provider because
        // we don't know what user provider is configured for the system and
        // the contract of this class is to authenticate against the jiveUser
        // database table.
        userProvider = new DefaultUserProvider();
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        try {
            if (!password.equals(userProvider.getPassword(username))) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        if (username == null || token == null || digest == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        try {
            String password = userProvider.getPassword(username);
            String anticipatedDigest = AuthFactory.createDigest(token, password);
            if (!digest.equalsIgnoreCase(anticipatedDigest)) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return true;
    }
}