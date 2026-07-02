/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.user.UserNotFoundException;

/**
 * Provider interface for authentication. Users that wish to integrate with
 * their own authentication system must implement this class and then register
 * the implementation with Openfire in the {@code openfire.xml}
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
     * Returns if the username and password are valid; otherwise this
     * method throws an UnauthorizedException.<p>
     *
     * @param username the username or full JID.
     * @param password the password
     * @throws UnauthorizedException if the username and password do
     *      not match any existing user.
     * @throws ConnectionException it there is a problem connecting to user and group system
     * @throws InternalUnauthenticatedException if there is a problem authentication Openfire itself into the user and group system
     */
    void authenticate(String username, String password) throws UnauthorizedException,
            ConnectionException, InternalUnauthenticatedException;

    /**
     * Returns the user's password. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @return the user's password.
     * @throws UserNotFoundException if the given user's password could not be loaded.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    String getPassword( String username ) throws UserNotFoundException,
            UnsupportedOperationException;

    /**
     * Sets the user's password. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @param password the new plaintext password for the user.
     * @throws UserNotFoundException if the given user could not be loaded.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    void setPassword( String username, String password )
            throws UserNotFoundException, UnsupportedOperationException;

    /**
     * Returns true if this UserProvider is able to retrieve user passwords from
     * the backend user store. If this operation is not supported then {@link #getPassword(String)}
     * will throw an {@link UnsupportedOperationException} if invoked.
     *
     * @return true if this UserProvider is able to retrieve user passwords from the
     *         backend user store.
     */
    boolean supportsPasswordRetrieval();

    boolean isScramSupported();

    /**
     * Returns SCRAM credentials for a user and mechanism.
     *
     * This default implementation is backed by the deprecated single-argument accessors, which can only supply
     * {@code SCRAM-SHA-1} material. It therefore supports only the {@code SCRAM-SHA-1} mechanism (with or without the
     * {@code -PLUS} suffix). Providers that add support for other SCRAM mechanisms must override this method.
     *
     * @param username the username of the user.
     * @param mechanism the SCRAM mechanism name.
     * @return the SCRAM credentials for the user under the (normalized) mechanism.
     * @throws UnsupportedOperationException if the mechanism is not {@code SCRAM-SHA-1}.
     * @throws UserNotFoundException if the user's credentials could not be loaded.
     */
    default ScramCredentialData getScramCredential(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        final String normalizedMechanism = ScramCredentialData.normalizeMechanismName(mechanism);
        if (!ScramSha1SaslServer.MECHANISM_NAME.equals(normalizedMechanism)) {
            throw new UnsupportedOperationException("SCRAM mechanism is not supported: " + mechanism);
        }

        return new ScramCredentialData(
            normalizedMechanism,
            getSalt(username),
            getIterations(username),
            getStoredKey(username),
            getServerKey(username)
        );
    }

    /**
     * Returns a SCRAM salt for a user and mechanism.
     */
    default String getSalt(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        return getScramCredential(username, mechanism).salt;
    }

    /**
     * Returns a SCRAM iteration count for a user and mechanism.
     */
    default int getIterations(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        return getScramCredential(username, mechanism).iterations;
    }

    /**
     * Returns a SCRAM server key for a user and mechanism.
     */
    default String getServerKey(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        return getScramCredential(username, mechanism).serverKey;
    }

    /**
     * Returns a SCRAM stored key for a user and mechanism.
     */
    default String getStoredKey(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        return getScramCredential(username, mechanism).storedKey;
    }

    /**
     * Returns a SCRAM-SHA-1 salt for a user.
     *
     * @deprecated Use getSalt(String, String) with the mechanism name.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException;

    /**
     * Returns a SCRAM-SHA-1 iteration count for a user.
     *
     * @deprecated Use getIterations(String, String) with the mechanism name.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException;

    /**
     * Returns a SCRAM-SHA-1 server key for a user.
     *
     * @deprecated Use getServerKey(String, String) with the mechanism name.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException;

    /**
     * Returns a SCRAM-SHA-1 stored key for a user.
     *
     * @deprecated Use getStoredKey(String, String) with the mechanism name.
     */
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException;
}
