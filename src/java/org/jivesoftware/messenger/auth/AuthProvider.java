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

package org.jivesoftware.messenger.auth;

import org.jivesoftware.messenger.user.UserNotFoundException;

/**
 * The essential interface to implement when creating an
 * authentication service plug-in.<p>
 *
 * Implementations of auth provider strictly handles the persistent
 * storage access for authentication in Messenger. The auth provider
 * is the only Messenger system dealing with users that does not necessarily
 * have to associate a username with a user ID. This keeps the auth
 * provider completely separate from the user system.
 *
 * @author Iain Shigeoka
 */
public interface AuthProvider {

    /**
     * <p>Determines if the authentication system supports the use of
     * plain-text passwords.</p>
     * <p/>
     * <p>Some servers may wish to disable plain text password
     * support because the passwords are easy to steal in
     * transit unless SSL is used. Plain-text passwords will be verified via the
     * getAuthToken(String username, String password) method.</p>
     * <p/>
     * <p>Notice that this flag is used to indicate to clients
     * whether the server will accept plain text passwords. It
     * does not have to imply that passwords are stored as plain text.
     * For example, passwords may be stored as MD5 hashes of the
     * password in the database. Plain text passwords can be supported
     * by simply taking the plain-text password sent from the client,
     * MD5 hashing it, and seeing if it matches the hash stored in the
     * database.</p>
     *
     * @return True if plain text passwords are supported on the server
     */
    boolean isPlainSupported();

    /**
     * <p>Determines if the authentication system supports the use
     * of digest authentication.</p>
     * <p/>
     * <p>Some servers may wish to only enable plain text password
     * support because the passwords are easy to steal using
     * plain-text in transit unless SSL is used.</p>
     * <p/>
     * <p>Perhaps ironically, digest authentication requires plain-text
     * passwords to be stored for each user. The digest protocol
     * protects the password sent over the network by SHA-1 digesting
     * it with a unique token. To check the digest, the plain-text copy
     * of the password must be available on the server (thus increasing
     * the need for keeping the backend store secure). If your user
     * system cannot store passwords in plain text, then you should return
     * false so digest authentication is not used. If you must use plain-text
     * on an insecure network, it is recommend that users connect with
     * SSL to protect the passwords in transit.</p>
     *
     * @return true if digest authentication is supported on the server.
     */
    boolean isDigestSupported();

    /**
     * <p>Returns if the username and password are valid otherwise the method throws an
     * UnauthorizedException.<p>
     *
     * @param username the username to create an AuthToken with.
     * @param password the password to create an AuthToken with.
     * @throws UnauthorizedException if the username and password
     *                               do not match any existing user.
     */
    void authenticate(String username, String password) throws UnauthorizedException;

    /**
     * <p>Returns the AuthToken token associated with the specified
     * username, unique session token, and digest generated from the
     * password and token according to the Jabber digest auth protocol.</p>
     * <p/>
     * <p>If the username and digest do not match the record of
     * any user in the system, the method throws an UnauthorizedException.<p>
     *
     * @param username the username to create an AuthToken with.
     * @param token    the token that was used with plain-text
     *                 password to generate the digest
     * @param digest   The digest generated from plain-text password and unique token
     * @throws UnauthorizedException if the username and password
     *                               do not match any existing user.
     */
    void authenticate(String username, String token, String digest)
            throws UnauthorizedException;

    /**
     * <p>Update the password for the given user.</p>
     * <p/>
     * <p>Sets the users's password. The password should be passed
     * in as plain text. The way the password is stored is
     * implementation dependent. However, it is recommended
     * to at least hash passwords with an algorithm such as
     * MD5 if you don't need to support digest authentication.</p>
     * <p/>
     * <p>If you don't want people to change their password through
     * Messenger just throw UnauthorizedException.</p>
     *
     * @param username The username of the user who's password is changing
     * @param password The new password for the user
     * @throws UnauthorizedException         If the password is invalid or
     *                                       the caller does not have permission to make the change
     * @throws UserNotFoundException         If the given user could not be located
     * @throws UnsupportedOperationException If the provider does not
     *                                       support the operation (this is an optional operation)
     */
    public void updatePassword(String username, String password)
            throws UserNotFoundException,
            UnauthorizedException,
            UnsupportedOperationException;
}
