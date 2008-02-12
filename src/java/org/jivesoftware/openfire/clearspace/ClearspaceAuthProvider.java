/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.user.UserNotFoundException;

/**
 * @author Daniel Henninger
 */
public class ClearspaceAuthProvider implements AuthProvider {
    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return true;
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        // Nothing
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        // Nothing
    }

    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
        return (username.equals("admin") ? "test" : "asdasdasdasd");
    }

    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
        // Nothing
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}
