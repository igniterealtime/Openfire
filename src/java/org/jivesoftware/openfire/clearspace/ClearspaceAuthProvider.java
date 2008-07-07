/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.clearspace;

import org.jivesoftware.openfire.auth.AuthProvider;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.JID;

/**
 * The ClearspaceAuthProvider uses the PermissionService web service inside of Clearspace
 * to retrieve authenticate users. It current version of Clearspace only supports plain authentication.
 *
 * @author Gabriel Guardincerri
 */
public class ClearspaceAuthProvider implements AuthProvider {

    // Service url prefix
    protected static final String URL_PREFIX = "permissionService/";

    public ClearspaceAuthProvider() {
        // Add SASL mechanism for use with Clearspace's group chat integration
        SASLAuthentication.addSupportedMechanism("CLEARSPACE");
    }

    /**
     * Clearspace currently supports only plain authentication.
     *
     * @return true
     */
    public boolean isPlainSupported() {
        return true;
    }

    /**
     * Clearspace currently doesn't support digest authentication.
     *
     * @return false
     */
    public boolean isDigestSupported() {
        return false;
    }

    /**
     * Authenticates the user using permissionService/authenticate service of Clearspace.
     * Throws an UnauthorizedException if the user or password are incorrect.
     *
     * @param username the username.
     * @param password the password.
     * @throws UnauthorizedException if the username of password are incorrect.
     */
    public void authenticate(String username, String password) throws UnauthorizedException {
        try {
            // Un-escape username.
            username = JID.unescapeNode(username);
            // Encode potentially non-ASCII characters
            username = URLUTF8Encoder.encode(username);
            String path = URL_PREFIX + "authenticate/" + username + "/" + password;
            ClearspaceManager.getInstance().executeRequest(GET, path);
        } catch (UnauthorizedException ue) {
            throw ue;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnauthorizedException("Unexpected error", e);
        }
    }

    /**
     * This method is not supported.
     *
     * @param username the username
     * @param token    the token
     * @param digest   the digest
     * @throws UnauthorizedException         never throws it
     * @throws UnsupportedOperationException always throws it
     */
    public void authenticate(String username, String token, String digest) throws UnauthorizedException {
        throw new UnsupportedOperationException("Digest not supported");
    }

    /**
     * This method is not supported.
     *
     * @throws UnsupportedOperationException always throws it
     */
    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Password retrieval not supported");
    }

    /**
     * This method is not supported.
     *
     * @throws UnsupportedOperationException always throws it
     */
    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException {
        throw new UnsupportedOperationException("Change Password not supported");
    }

    /**
     * This method is not supported.
     *
     * @throws UnsupportedOperationException always throws it
     */
    public boolean supportsPasswordRetrieval() {
        return false;
    }
}
