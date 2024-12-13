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
package org.jivesoftware.openfire.user;

import java.util.*;

/**
 * A very basic implementation of a UserProvider, that retains data in-memory.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class TestUserProvider implements UserProvider
{
    final Map<String, User> data = new HashMap<>();

    @Override
    public User loadUser(String username) throws UserNotFoundException
    {
        final User result = data.get(username);
        if (result == null) {
            throw new UserNotFoundException();
        }
        return result;
    }

    @Override
    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException
    {
        if (data.containsKey(username)) {
            throw new UserAlreadyExistsException();
        }
        final User user = new User(username, name, email, new Date(), new Date());
        data.put(username, user);
        return user;
    }

    @Override
    public void deleteUser(String username)
    {
        data.remove(username);
    }

    @Override
    public int getUserCount()
    {
        return data.size();
    }

    @Override
    public Collection<User> getUsers()
    {
        return data.values();
    }

    @Override
    public Collection<String> getUsernames()
    {
        return data.keySet();
    }

    @Override
    public Collection<User> getUsers(int startIndex, int numResults)
    {
        return new ArrayList<>(data.values()).subList(startIndex, Math.min(numResults, data.size() - startIndex));
    }

    @Override
    public void setName(String username, String name) throws UserNotFoundException
    {
        loadUser(username).setName(name);
    }

    @Override
    public void setEmail(String username, String email) throws UserNotFoundException
    {
        loadUser(username).setEmail(email);
    }

    @Override
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException
    {
        loadUser(username).setCreationDate(creationDate);
    }

    @Override
    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException
    {
        loadUser(username).setModificationDate(modificationDate);
    }

    @Override
    public Set<String> getSearchFields() throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<User> findUsers(Set<String> fields, String query, int startIndex, int numResults) throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReadOnly()
    {
        return false;
    }

    @Override
    public boolean isNameRequired()
    {
        return false;
    }

    @Override
    public boolean isEmailRequired()
    {
        return false;
    }

    /**
     * A provider that holds no data.
     */
    public static class NoUserProvider extends TestUserProvider
    {}

    /**
     * A provider that holds one user named 'jane'.
     */
    public static class JaneUserProvider extends TestUserProvider
    {
        public JaneUserProvider() {
            try {
                createUser("jane", "secret", "Jane Doe", "jane@example.org");
            } catch (UserAlreadyExistsException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * A provider that holds one user named 'john'.
     */
    public static class JohnUserProvider extends TestUserProvider
    {
        public JohnUserProvider() {
            try {
                createUser("john", "secret", "John Doe", "john@example.org");
            } catch (UserAlreadyExistsException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
