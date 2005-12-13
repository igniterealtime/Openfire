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

package org.jivesoftware.wildfire.auth;

/**
 * Provider interface for authentication. Users that wish to integrate with
 * their own authentication system must implement this class and then register
 * the implementation with Wildfire in the <tt>wildfire.xml</tt>
 * file. An entry in that file would look like the following:
 *
 * <pre>
 *   &lt;provider&gt;
 *     &lt;auth&gt;
 *       &lt;className&gt;com.foo.auth.CustomAuthProvider&lt;/className&gt;
 *     &lt;/auth&gt;
 *   &lt;/provider&gt;</pre>
 *
 * @author Matt Tucker
 */
public interface AuthProvider {

    /**
     * Returns true if this AuthProvider supports authentication using plain-text
     * passwords according to JEP--0078. Plain text authentication is not secure
     * and should generally only be used for a TLS/SSL connection.
     *
     * @return true if plain text password authentication is supported by
     *      this AuthProvider.
     */
    boolean isPlainSupported();

    /**
     * Returns true if this AuthProvider supports digest authentication
     * according to JEP-0078.
     *
     * @return true if digest authentication is supported by this
     *      AuthProvider.
     */
    boolean isDigestSupported();

    /**
     * Returns if the username and password are valid; otherwise this
     * method throws an UnauthorizedException.<p>
     *
     * If {@link #isPlainSupported()} returns false, this method should
     * throw an UnsupportedOperationException.
     *
     * @param username the username.
     * @param password the passwordl
     * @throws UnauthorizedException if the username and password do
     *      not match any existing user.
     */
    void authenticate(String username, String password) throws UnauthorizedException;

    /**
     * Returns if the username, token, and digest are valid; otherwise this
     * method throws an UnauthorizedException.<p>
     *
     * If {@link #isDigestSupported()} returns false, this method should
     * throw an UnsupportedOperationException.
     *
     * @param username the username.
     * @param token the token that was used with plain-text password to
     *      generate the digest.
     * @param digest the digest generated from plain-text password and unique token.
     * @throws UnauthorizedException if the username and password
     *      do not match any existing user.
     */
    void authenticate(String username, String token, String digest)
            throws UnauthorizedException;
}