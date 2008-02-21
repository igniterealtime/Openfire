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
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.net.SASLAuthentication;

/**
 * @author Daniel Henninger
 */
public class ClearspaceAuthProvider implements AuthProvider {
    protected static final String URL_PREFIX = "permissionService/";

    private ClearspaceManager manager;

    public ClearspaceAuthProvider() {
        // gets the manager
        manager = ClearspaceManager.getInstance();

        // Add SASL mechanism for use with Clearspace's group chat integration
        SASLAuthentication.addSupportedMechanism("CLEARSPACE");
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return false;
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        try {
            String path = URL_PREFIX + "authenticate/" + username + "/" + password;
            manager.executeRequest(GET, path);
        } catch (UnauthorizedException ue) {
            throw ue;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        throw new UnsupportedOperationException("Digest not supported");
    }

    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Password retrieval not supported");
    }

    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Change Password not supported");
    }

    public boolean supportsPasswordRetrieval() {
        return false;
    }
}
