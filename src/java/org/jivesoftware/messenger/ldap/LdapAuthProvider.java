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

package org.jivesoftware.messenger.ldap;

import org.jivesoftware.messenger.auth.AuthProvider;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Implementation of auth provider interface for LDAP
 * authentication service plug-in.
 *
 * @author Matt Tucker
 */
public class LdapAuthProvider implements AuthProvider {
    private LdapManager manager;

    public LdapAuthProvider() {
        manager = LdapManager.getInstance();
    }

    public boolean isPlainSupported() {
        return true;
    }

    public boolean isDigestSupported() {
        return false;
    }

    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        String userDN = null;
        try {
            // The username by itself won't help us much with LDAP since we
            // need a fully qualified dn. We could make the assumption that
            // the baseDN would always be the location of user profiles. For
            // example if the baseDN was set to "ou=People, o=jivesoftare, o=com"
            // then we would be able to directly load users from that node
            // of the LDAP tree. However, it's a poor assumption that only a
            // flat structure will be used. Therefore, we search all subtrees
            // of the baseDN for the username. So, if the baseDN is set to
            // "o=jivesoftware, o=com" then a search will include the "People"
            // node as well all the others under the base.
            userDN = manager.findUserDN(username);

            // See if the user authenticates.
            if (!manager.checkAuthentication(userDN, password)) {
                throw new UnauthorizedException("Username and password don't match");
            }
        }
        catch (Exception e) {
            throw new UnauthorizedException(e);
        }
    }

    public void authenticate(String username, String token, String digest) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Digest authentication not currently supported.");
    }

    public void updatePassword(String username, String password) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot update password in LDAP");
    }
}