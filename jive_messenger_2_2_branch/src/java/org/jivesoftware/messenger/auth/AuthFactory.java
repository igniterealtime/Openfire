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

import org.jivesoftware.util.*;
import org.jivesoftware.util.JiveGlobals;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Authentication service.
 *
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
 */
public class AuthFactory {

    private static AuthProvider authProvider = null;
    private static MessageDigest digest;

    static {
        // Load an auth provider.
        String className = JiveGlobals.getXMLProperty("provider.auth.className",
                "org.jivesoftware.messenger.auth.DefaultAuthProvider");
        try {
            Class c = ClassUtils.forName(className);
            authProvider = (AuthProvider)c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading auth provider: " + className, e);
            authProvider = new DefaultAuthProvider();
        }
        // Create a message digest instance.
        try {
            digest = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    /**
     * Returns true if the currently installed {@link AuthProvider} supports authentication
     * using plain-text passwords according to JEP-0078. Plain-text authentication is
     * not secure and should generally only be used over a TLS/SSL connection.
     *
     * @return true if plain text password authentication is supported.
     */
    public static boolean isPlainSupported() {
        return authProvider.isPlainSupported();
    }

    /**
     * Returns true if the currently installed {@link AuthProvider} supports
     * digest authentication according to JEP-0078.
     *
     * @return true if digest authentication is supported.
     */
    public static boolean isDigestSupported() {
        return authProvider.isDigestSupported();
    }

    /**
     * Authenticates a user with a username and plain text password and returns and
     * AuthToken. If the username and password do not match the record of
     * any user in the system, this method throws an UnauthorizedException.
     *
     * @param username the username.
     * @param password the password.
     * @return an AuthToken token if the username and password are correct.
     * @throws UnauthorizedException if the username and password do not match any existing user.
     */
    public static AuthToken authenticate(String username, String password)
            throws UnauthorizedException
    {
        authProvider.authenticate(username, password);
        return new AuthToken(username);
    }

    /**
     * Authenticates a user with a username, token, and digest and returns an AuthToken.
     * The digest should be generated using the {@link #createDigest(String, String)} method.
     * If the username and digest do not match the record of any user in the system, the
     * method throws an UnauthorizedException.
     *
     * @param username the username.
     * @param token the token that was used with plain-text password to generate the digest.
     * @param digest the digest generated from plain-text password and unique token.
     * @return an AuthToken token if the username and digest are correct for the user's
     *      password and given token.
     * @throws UnauthorizedException if the username and password do not match any
     *      existing user.
     */
    public static AuthToken authenticate(String username, String token, String digest)
            throws UnauthorizedException
    {
        authProvider.authenticate(username, token, digest);
        return new AuthToken(username);
    }

    /**
     * Returns a digest given a token and password, according to JEP-0078.
     *
     * @param token the token used in the digest.
     * @param password the plain-text password to be digested.
     * @return the digested result as a hex string.
     */
    public static String createDigest(String token, String password) {
        synchronized (digest) {
            digest.update(token.getBytes());
            return StringUtils.encodeHex(digest.digest(password.getBytes()));
        }
    }
}