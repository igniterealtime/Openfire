/**
 *
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2003 JiveSoftware. All rights reserved.
 *
 * This software is the proprietary information of Jive Software.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.ldap;

import org.jivesoftware.messenger.auth.AuthProvider;
import org.jivesoftware.messenger.auth.UnauthorizedException;

/**
 * Implementation of auth provider interface for LDAP
 * authentication service plug-in.
 *
 * @author Jim Berrettini
 */
public class LdapAuthProvider implements AuthProvider {
    private LdapManager manager;

    public LdapAuthProvider() {
        manager = LdapManager.getInstance();
    }

    /**
     * <p>Determines if the authentication system supports the use of
     * plain-text passwords.</p>
     *
     * @return true - plain text passwords are supported.
     */
    public boolean isPlainSupported() {
        return true;
    }

    /**
     * <p>Determines if the authentication system supports the use
     * of digest authentication.</p>
     *
     * @return false - digest authentication is not currently supported.
     */
    public boolean isDigestSupported() {
        return false;
    }

    /**
     * <p>Validates username and password against data in LDAP store. Returns if the username and password are valid otherwise the method throws an
     * UnauthorizedException.<p>
     *
     * @param username
     * @param password
     * @throws UnauthorizedException
     */
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

    /**
     * <p>Validates username, unique session token, and digest generated from the
     * password and token according to the Jabber digest auth protocol against data in LDAP store.
     * Since Digest authentication is not currently supported, throws UnsupportedOperationsException.</p>
     *
     * @param username
     * @param token
     * @param digest
     * @throws UnsupportedOperationException
     */
    public void authenticate(String username, String token, String digest) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Digest authentication not currently supported.");
    }

    /**
     * <p>Updates username and password. Throws UnsupportedOperationException, as LDAP store is accessed in read-only
     * mode.</p>
     *
     * @param username
     * @param password
     * @throws UnsupportedOperationException
     */
    public void updatePassword(String username, String password) throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Cannot update password in LDAP");
    }
}
