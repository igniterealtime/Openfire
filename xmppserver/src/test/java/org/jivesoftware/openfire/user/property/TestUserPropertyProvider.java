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
package org.jivesoftware.openfire.user.property;

import org.jivesoftware.openfire.user.UserNotFoundException;

import java.util.*;

/**
 * A very basic implementation of a UserPropertyProvider, that retains data in-memory.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestUserPropertyProvider implements UserPropertyProvider
{
    final Map<String, Map<String, String>> data = new HashMap<>();

    @Override
    public boolean isReadOnly()
    {
        return false;
    }

    @Override
    public Map<String, String> loadProperties(String username) throws UserNotFoundException
    {
        if (!data.containsKey(username)) {
            throw new UserNotFoundException();
        }
        return data.get(username);
    }

    @Override
    public String loadProperty(String username, String propName) throws UserNotFoundException
    {
        if (!data.containsKey(username)) {
            throw new UserNotFoundException();
        }
        return data.get(username).get(propName);
    }

    @Override
    public void insertProperty(String username, String propName, String propValue) throws UserNotFoundException, UnsupportedOperationException
    {
        if (!data.containsKey(username)) {
            throw new UserNotFoundException();
        }
        data.get(username).put(propName, propValue);
    }

    @Override
    public void updateProperty(String username, String propName, String propValue) throws UserNotFoundException, UnsupportedOperationException
    {
        if (!data.containsKey(username)) {
            throw new UserNotFoundException();
        }
        data.get(username).put(propName, propValue);
    }

    @Override
    public void deleteProperty(String username, String propName) throws UserNotFoundException, UnsupportedOperationException
    {
        if (!data.containsKey(username)) {
            throw new UserNotFoundException();
        }
        data.get(username).remove(propName);
    }

    /**
     * A provider that holds no data.
     */
    public static class NoUserProvider extends TestUserPropertyProvider
    {}

    /**
     * A provider that holds data for one user, named 'jane'.
     */
    public static class JaneUserProvider extends TestUserPropertyProvider
    {
        public JaneUserProvider() {
            final Map<String, String> userData = new HashMap<>();
            userData.put("testprop", "test jane value");
            data.put("jane", userData);
        }
    }

    /**
     * A provider that holds data for one user, named 'john'.
     */
    public static class JohnUserProvider extends TestUserPropertyProvider
    {
        public JohnUserProvider() {
            final Map<String, String> userData = new HashMap<>();
            userData.put("testprop", "test john value");
            data.put("john", userData);
        }
    }
}
