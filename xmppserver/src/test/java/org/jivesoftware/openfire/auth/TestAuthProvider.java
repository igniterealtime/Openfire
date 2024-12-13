/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved.
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

import java.util.*;

/**
 * A very basic implementation of a AuthProvider, that retains data in-memory.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestAuthProvider implements AuthProvider
{
    final Map<String, String> data = new HashMap<>();

    @Override
    public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException
    {
        final String storedPassword = data.get(username);
        if (storedPassword == null || !storedPassword.equals(password)) {
            throw new UnauthorizedException();
        }
    }

    @Override
    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException
    {
        final String storedPassword = data.get(username);
        if (storedPassword == null) {
            throw new UserNotFoundException();
        }
        return storedPassword;
    }

    @Override
    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException
    {
        final String storedPassword = data.get(username);
        if (storedPassword == null) {
            throw new UserNotFoundException();
        }
        data.put(username, password);
    }

    @Override
    public boolean supportsPasswordRetrieval()
    {
        return true;
    }

    @Override
    public boolean isScramSupported()
    {
        return false;
    }

    @Override
    public String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * A provider that holds no data.
     */
    public static class NoAuthProvider extends TestAuthProvider
    {}

    /**
     * A provider that holds one auth for a user named 'jane'.
     */
    public static class JaneAuthProvider extends TestAuthProvider
    {
        public JaneAuthProvider() {
            data.put("jane", "secret");
        }
    }

    /**
     * A provider that holds one auth for a user named 'john'.
     */
    public static class JohnAuthProvider extends TestAuthProvider
    {
        public JohnAuthProvider() {
            data.put("john", "secret");
        }
    }
}
