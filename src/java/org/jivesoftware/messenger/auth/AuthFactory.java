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

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.messenger.auth.spi.AuthTokenImpl;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * An abstract class that defines a framework for providing authentication services in Jive. The
 * static getAuthToken(String, String),
 * getAuthToken(HttpServletRequest, HttpServletResponse), and getAnonymousAuthToken()
 * methods should be called directly from applications using Jive in order to obtain an
 * AuthToken.<p>
 * <p/>
 * Users of Jive that wish to change the AuthProvider implementation used to authenticate users
 * can set the <code>AuthProvider.className</code> Jive property. For example, if
 * you have altered Jive to use LDAP for user information, you'd want to send a custom
 * implementation of AuthFactory to make LDAP authToken queries. After changing the
 * <code>AuthProvider.className</code> Jive property, you must restart your application
 * server.<p>
 * <p/>
 * The getAuthToken method that takes servlet request and response objects as arguments can be
 * used to implement single sign-on. Additionally, two helper methods are provided for securely
 * encrypting and decrypting login information so that it can be stored as a cookie value to
 * implement auto-login.<p>
 *
 * @author Matt Tucker
 * @author Iain Shigeoka
 */
public abstract class AuthFactory {

    /**
     * Name of the key in a user's session that AuthToken tokens are customarily stored at.
     */
    public static final String SESSION_AUTHORIZATION = "jive.authToken";

    private static AuthProvider authProvider = null;

    private static MessageDigest sha;

    /**
     * Initializes encryption and decryption ciphers using the secret key found in the Jive property
     * "cookieKey". If a secret key has not been created yet, it is automatically generated and
     * saved.
     */
    static {
        authProvider = AuthProviderFactory.getAuthProvider();
        try {
            sha = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * <p>Determines if the authentication system supports the use of plain-text passwords.</p>
     * <p>Some servers may wish to disable plain text password support because the passwords are
     * easy to steal in transit unless SSL is used. Plain-text passwords will be verified via the
     * getAuthToken(String username, String password) method.</p>
     * <p>Notice that this flag is used to indicate to clients whether the server will accept
     * plain text passwords. It does not have to imply that passwords are stored as plain text.
     * For example, passwords may be stored as MD5 hashes of the password in the database. Plain
     * text passwords can be supported by simply taking the plain-text password sent from the client,
     * MD5 hashing it, and seeing if it matches the hash stored in the database.</p>
     *
     * @return True if plain text passwords are supported on the server
     */
    public static boolean isPlainSupported() {
        return authProvider.isPlainSupported();
    }

    /**
     * <p>Determines if the authentication system supports the use of digest authentication.</p>
     * <p>Some servers may wish to only enable plain text password support because the passwords are
     * easy to steal using plain-text in transit unless SSL is used.</p>
     * <p/>
     * <p>Perhaps ironically, digest authentication requires plain-text passwords to be stored
     * for each user. The digest protocol protects the password sent over the network by SHA-1
     * digesting it with a unique token. To check the digest, the plain-text copy of the password
     * must be available on the server (thus increasing the need for keeping the backend store
     * secure). If your user system cannot store passwords in plain text, then you should return
     * false so digest authentication is not used. If you must use plain-text on an insecure network,
     * it is recommend that users connect with SSL to protect the passwords in transit.</p>
     *
     * @return True if digest authentication is supported on the server
     */
    public static boolean isDigestSupported() {
        return authProvider.isDigestSupported();
    }

    /**
     * Returns the AuthToken token associated with the specified username and password. If the
     * username and password do not match the record of any user in the system, the method throws an
     * UnauthorizedException.<p>
     *
     * @param username the username to create an AuthToken with.
     * @param password the password to create an AuthToken with.
     * @return an AuthToken token if the username and password are correct.
     * @throws UnauthorizedException if the username and password do not match any existing user.
     */
    public static AuthToken getAuthToken(String username, String password)
            throws UnauthorizedException
    {
        authProvider.authenticate(username, password);
        return new AuthTokenImpl(username);
    }

    /**
     * <p>Returns the AuthToken token associated with the specified username, unique session token, and
     * digest generated from the password and token according to the Jabber digest auth protocol.</p>
     * <p>If the username and digest do not match the record of any user in the system, the method throws an
     * UnauthorizedException.<p>
     *
     * @param username the username to create an AuthToken with.
     * @param token    the token that was used with plain-text password to generate the digest
     * @param digest   The digest generated from plain-text password and unique token
     * @return an AuthToken token if the username and digest are correct for the user's password and given token.
     * @throws UnauthorizedException if the username and password do not match any existing user.
     */
    public static AuthToken getAuthToken(String username, String token, String digest)
            throws UnauthorizedException {
        authProvider.authenticate(username, token, digest);
        return new AuthTokenImpl(username);
    }

    /**
     * The same token can be used for all anonymous users, so cache it.
     */
    private static final AuthToken anonymousAuth = new AuthTokenImpl(null);

    /**
     * Returns an anonymous user AuthToken.
     *
     * @return an anonymous AuthToken token.
     */
    public static AuthToken getAnonymousAuthToken() {
        return anonymousAuth;
    }

    /**
     * Utility method that digests the given password and token according to the Jabber
     * digest auth protocol. The result of createPasswordTokenDigest() can be
     * String.equalsIgnoreCase() against the authentication digest passed into
     * implementations of {@link #getAuthToken(String, String, String) }.
     * The algorithm simply SHA-1 digests the token and password.
     *
     * @param token the token used in the digest.
     * @param password the plain-text password to be digested.
     * @return the digested result as a hex string following Jabber digest auth protocol guidelines.
     */
    public static String createTokenPasswordDigest(String token, String password) {
        sha.update(token.getBytes());
        return StringUtils.encodeHex(sha.digest(password.getBytes()));
    }
}