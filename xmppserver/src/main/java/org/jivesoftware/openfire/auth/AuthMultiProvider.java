/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved
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

import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * An {@link AuthProvider} that delegates to one or more 'backing' AuthProviders.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class AuthMultiProvider implements AuthProvider
{
    private final static Logger Log = LoggerFactory.getLogger(AuthMultiProvider.class);

    /**
     * Returns all AuthProvider instances that serve as 'backing' providers.
     *
     * @return A collection of providers (never null).
     */
    abstract Collection<AuthProvider> getAuthProviders();

    /**
     * Returns the 'backing' provider that serves the provided user.
     *
     * Finds a suitable AuthProvider for the user.
     *
     * Unlike other MultiProvider interfaces, this interface does not require this method to return a non-null value.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return A AuthProvider for the user (possibly null).
     */
    abstract AuthProvider getAuthProvider(String username);

    @Override
    public boolean supportsPasswordRetrieval()
    {
        // TODO Make calls concurrent for improved throughput.
        for (final AuthProvider provider : getAuthProviders())
        {
            // If at least one provider supports password retrieval, so does this proxy.
            if (provider.supportsPasswordRetrieval()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isScramSupported()
    {
        // TODO Make calls concurrent for improved throughput.
        for (final AuthProvider provider : getAuthProviders())
        {
            // If at least one provider supports SCRAM, so does this proxy.
            if ( provider.isScramSupported() )
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UnauthorizedException();
        }
        provider.authenticate(username, password);
    }

    @Override
    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException
    {
        if (!supportsPasswordRetrieval()) {
            throw new UnsupportedOperationException();
        }

        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        return provider.getPassword( username );
    }

    @Override
    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        provider.setPassword( username, password );
    }

    @Override
    public String getSalt(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        return provider.getSalt( username );
    }

    @Override
    public int getIterations(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        return provider.getIterations(username);
    }

    @Override
    public String getServerKey(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null) {
            throw new UserNotFoundException();
        }
        return provider.getServerKey(username);
    }

    @Override
    public String getStoredKey(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null) {
            throw new UserNotFoundException();
        }
        return provider.getStoredKey(username);
    }
}
