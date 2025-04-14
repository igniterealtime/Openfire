/*
 * Copyright (C) 2016 IgniteRealtime.org, 2018-2025 Ignite Realtime Foundation. All rights reserved
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

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * A {@link UserProvider} that delegates to one or more 'backing' UserProvider.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class UserMultiProvider implements UserProvider
{
    private final static Logger Log = LoggerFactory.getLogger( UserMultiProvider.class );

    /**
     * Instantiates a UserProvider based on a property value (that is expected to be a class name). When the property
     * is not set, this method returns null. When the property is set, but an exception occurs while instantiating
     * the class, this method logs the error and returns null.
     *
     * UserProvider classes are required to have a public, no-argument constructor.
     *
     * @param propertyName A property name (cannot be null).
     * @return A user provider (can be null).
     * @deprecated Use {@link #instantiate(SystemProperty)} or {@link #instantiate(SystemProperty, SystemProperty)} instead.
     */
    @Deprecated(forRemoval = true, since = "5.0.0") // TODO Remove in or after Openfire 5.1.0
    public static UserProvider instantiate( String propertyName )
    {
        final String className = JiveGlobals.getProperty( propertyName );
        if ( className == null )
        {
            Log.debug( "Property '{}' is undefined. Skipping.", propertyName );
            return null;
        }
        Log.debug( "About to to instantiate an UserProvider '{}' based on the value of property '{}'.", className, propertyName );
        try
        {
            final Class c = ClassUtils.forName( className );
            final UserProvider provider = (UserProvider) c.newInstance();
            Log.debug( "Instantiated UserProvider '{}'", className );
            return provider;
        }
        catch ( Exception e )
        {
            Log.error( "Unable to load UserProvider '{}'. Users in this provider will be disabled.", className, e );
            return null;
        }
    }

    /**
     * Instantiates a UserProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * UserProvider classes are required to have a public, no-argument constructor.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @return A user provider (can be null).
     */
    public static UserProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty)
    {
        return instantiate(implementationProperty, null);
    }

    /**
     * Instantiates a UserProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * UserProvider classes are required to have a public, no-argument constructor, but can have an optional additional
     * constructor that takes a single String argument. If such constructor is defined, then it is invoked with the
     * value of the second argument of this method. This is typically used to (but needs not) identify a property
     * (by name) that holds additional configuration for the to be instantiated UserProvider. This
     * implementation will pass on any non-empty value to the constructor. When a configuration argument is provided,
     * but no constructor exists in the implementation that accepts a single String value, this method will log a
     * warning and attempt to return an instance based on the no-arg constructor of the class.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @param configProperty an opaque string value passed to the constructor.
     * @return A user provider (can be null).
     */
    public static UserProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty, @Nullable final SystemProperty<String> configProperty)
    {
        final Class<? extends UserProvider> implementationClass = implementationProperty.getValue();
        if (implementationClass == null) {
            Log.debug( "Property '{}' is undefined or has no value. Skipping.", implementationProperty.getKey() );
            return null;
        }
        Log.debug("About to to instantiate an UserProvider '{}' based on the value of property '{}'.", implementationClass, implementationProperty.getKey());

        try {
            if (configProperty != null && configProperty.getValue() != null && !configProperty.getValue().isEmpty()) {
                try {
                    final Constructor<? extends UserProvider> constructor = implementationClass.getConstructor(String.class);
                    final UserProvider result = constructor.newInstance(configProperty.getValue());
                    Log.debug("Instantiated UserProvider '{}' with configuration: '{}'", implementationClass.getName(), configProperty.getValue());
                    return result;
                } catch (NoSuchMethodException e) {
                    Log.warn("Custom configuration is defined for the a provider but the configured class ('{}') does not provide a constructor that takes a String argument. Custom configuration will be ignored. Ignored configuration: '{}'", implementationProperty.getValue().getName(), configProperty);
                }
            }

            final UserProvider result = implementationClass.getDeclaredConstructor().newInstance();
            Log.debug("Instantiated UserProvider '{}'", implementationClass.getName());
            return result;
        } catch (Exception e) {
            Log.error("Unable to load UserProvider '{}'. Data from this provider will not be available.", implementationClass.getName(), e);
            return null;
        }
    }

    /**
     * Returns all UserProvider instances that serve as 'backing' providers.
     *
     * @return A collection of providers (never null).
     */
    abstract Collection<UserProvider> getUserProviders();

    /**
     * Returns the 'backing' provider that serves the provided user. Note that the user need not exist.
     *
     * Finds a suitable UserProvider for the user.
     *
     * Note that the provided username need not reflect a pre-existing user (the instance might be used to determine in
     * which provider a new user is to be created).
     *
     * Implementations are expected to be able to find a UserProvider for any username. If an implementation fails to do
     * so, such a failure is assumed to be the result of a problem in implementation or configuration.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return A UserProvider for the user (never null).
     */
    abstract UserProvider getUserProvider( String username );

    @Override
    public int getUserCount()
    {
        return getUserProviders().parallelStream()
            .map(UserProvider::getUserCount)
            .reduce(0, Integer::sum);
    }

    @Override
    public Collection<User> getUsers()
    {
        return getUserProviders().parallelStream()
            .map(UserProvider::getUsers)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    @Override
    public Collection<String> getUsernames()
    {
        return getUserProviders().parallelStream()
            .map(UserProvider::getUsernames)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    @Override
    public Collection<User> getUsers( int startIndex, int numResults )
    {
        final List<User> userList = new ArrayList<>();
        int totalUserCount = 0;

        for ( final UserProvider provider : getUserProviders() )
        {
            final int providerStartIndex = Math.max( ( startIndex - totalUserCount ), 0 );
            totalUserCount += provider.getUserCount();
            if ( startIndex >= totalUserCount )
            {
                continue;
            }
            final int providerResultMax = numResults - userList.size();
            userList.addAll( provider.getUsers( providerStartIndex, providerResultMax ) );
            if ( userList.size() >= numResults )
            {
                break;
            }
        }
        return userList;
    }

    /**
     * Searches for users based on a set of fields and a query string. The fields must  be taken from the values
     * returned by {@link #getSearchFields()}. The query can  include wildcards. For example, a search on the field
     * "Name" with a query of "Ma*"  might return user's with the name "Matt", "Martha" and "Madeline".
     *
     * This method throws an UnsupportedOperationException when none of the backing providers support search.
     *
     * When fields are provided that are not supported by a particular provider, those fields are ignored by that
     * provider (but can still be used by other providers).
     *
     * @param fields the fields to search on.
     * @param query  the query string.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException When none of the providers support search.
     */
    @Override
    public Collection<User> findUsers( Set<String> fields, String query ) throws UnsupportedOperationException
    {
        final AtomicLong supportSearch = new AtomicLong(getUserProviders().size());
        final Set<User> result = getUserProviders().parallelStream()
            .map(provider -> {
                try {
                    // Use only those fields that are supported by the provider.
                    final Set<String> supportedFields = new HashSet<>(fields);
                    supportedFields.retainAll(provider.getSearchFields());
                    return provider.findUsers(supportedFields, query);
                } catch (UnsupportedOperationException uoe) {
                    Log.warn("UserProvider.findUsers is not supported by this UserProvider: {}. Its users are not returned as part of search queries.", provider.getClass().getName());
                    supportSearch.decrementAndGet();
                    return new HashSet<User>();
                }
            })
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        if (supportSearch.longValue() == 0) {
            throw new UnsupportedOperationException("None of the backing providers support this operation.");
        }
        return result;
    }

    /**
     * Searches for users based on a set of fields and a query string. The fields must  be taken from the values
     * returned by {@link #getSearchFields()}. The query can  include wildcards. For example, a search on the field
     * "Name" with a query of "Ma*"  might return user's with the name "Matt", "Martha" and "Madeline".
     *
     * This method throws an UnsupportedOperationException when none of the backing providers support search.
     *
     * When fields are provided that are not supported by a particular provider, those fields are ignored by that
     * provider (but can still be used by other providers).
     *
     * The startIndex and numResults parameters are used to page through search results. For example, if the startIndex
     * is 0 and numResults is 10, the first  10 search results will be returned. Note that numResults is a request for
     * the number of results to return and that the actual number of results returned may be fewer.
     *
     * @param fields     the fields to search on.
     * @param query      the query string.
     * @param startIndex the starting index in the search result to return.
     * @param numResults the number of users to return in the search result.
     * @return a Collection of users that match the search.
     * @throws UnsupportedOperationException When none of the providers support search.
     */
    @Override
    public Collection<User> findUsers( Set<String> fields, String query, int startIndex, int numResults ) throws UnsupportedOperationException
    {
        final List<User> userList = new ArrayList<>();
        int supportSearch = getUserProviders().size();
        int totalMatchedUserCount = 0;

        for ( final UserProvider provider : getUserProviders() )
        {
            try
            {
                // Use only those fields that are supported by the provider.
                final Set<String> supportedFields = new HashSet<>( fields );
                supportedFields.retainAll( provider.getSearchFields() );

                // Query the provider for sub-results.
                final Collection<User> providerResults = provider.findUsers( supportedFields, query );

                // Keep track of how many hits we have had so far.
                totalMatchedUserCount += providerResults.size();

                // Check if this sub-result contains the start of the page of data that is searched for.
                if ( startIndex >= totalMatchedUserCount )
                {
                    continue;
                }

                // From the sub-results, take those that are of interest.
                final int providerStartIndex = Math.max( 0, startIndex - totalMatchedUserCount );
                final int providerResultMax = numResults - userList.size();
                final List<User> providerList = providerResults instanceof List<?> ?
                        (List<User>) providerResults : new ArrayList<>( providerResults );
                userList.addAll( providerList.subList( providerStartIndex, Math.min( providerList.size(), providerResultMax ) ));

                // Check if we have enough results.
                if ( userList.size() >= numResults )
                {
                    break;
                }
            }
            catch ( UnsupportedOperationException uoe )
            {
                Log.warn( "UserProvider.findUsers is not supported by this UserProvider: " + provider.getClass().getName() );
                supportSearch--;
            }
        }

        if ( supportSearch == 0 )
        {
            throw new UnsupportedOperationException( "None of the backing providers support this operation." );
        }
        return userList;
    }

    /**
     * Returns the combination of search fields supported by the backing providers. Note that the returned fields might
     * not be supported by every backing provider.
     *
     * @throws UnsupportedOperationException If no search fields are returned, or when at least one of the providers throws UnsupportedOperationException when its #getSearchField() is invoked.
     */
    @Override
    public Set<String> getSearchFields() throws UnsupportedOperationException
    {
        final AtomicLong supportSearch = new AtomicLong(getUserProviders().size());
        final Set<String> result = getUserProviders().parallelStream()
            .map(userProvider -> {
                try {
                    return userProvider.getSearchFields();
                } catch ( UnsupportedOperationException uoe ) {
                    supportSearch.decrementAndGet();
                    return new HashSet<String>();
                }
            })
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());

        if (supportSearch.longValue() == 0)
        {
            throw new UnsupportedOperationException( "None of the backing providers support this operation." );
        }
        return result;
    }

    /**
     * Returns whether <em>all</em> backing providers are read-only. When read-only, users can not be created, deleted,
     * or modified. If at least one provider is not read-only, this method returns false.
     *
     * @return true when all backing providers are read-only, otherwise false.
     */
    @Override
    public boolean isReadOnly()
    {
        // If at least one provider is not readonly, neither is this proxy.
        return getUserProviders().parallelStream()
            .allMatch(UserProvider::isReadOnly);
    }

    /**
     * Returns whether <em>all</em> backing providers require a name to be set on User objects. If at least one proivder
     * does not, this method returns false.
     *
     * @return true when all backing providers require a name to be set on User objects, otherwise false.
     */
    @Override
    public boolean isNameRequired()
    {
        // If at least one provider does not require a name, neither is this proxy.
        return getUserProviders().parallelStream()
            .anyMatch(UserProvider::isNameRequired);
    }

    /**
     * Returns whether <em>all</em> backing providers require an email address to be set on User objects. If at least
     * one provider does not, this method returns false.
     *
     * @return true when all backing providers require an email address to be set on User objects, otherwise false.
     */
    @Override
    public boolean isEmailRequired()
    {
        // If at least one provider does not require an email, neither is this proxy.
        return getUserProviders().parallelStream()
            .anyMatch(UserProvider::isEmailRequired);
    }

    @Override
    public User loadUser(String username) throws UserNotFoundException
    {
        final UserProvider userProvider;
        try {
            userProvider = getUserProvider( username );
        } catch (RuntimeException e){
            throw new UserNotFoundException("Unable to identify user provider for username " + username, e);
        }
        return userProvider.loadUser( username );
    }

    @Override
    public User createUser(String username, String password, String name, String email) throws UserAlreadyExistsException
    {
        return getUserProvider(username).createUser(username, password, name, email);
    }

    /**
     * Removes a user from all non-read-only providers.
     *
     * @param username the username to delete.
     */
    @Override
    public void deleteUser(String username)
    {
        // all providers are read-only
        if (isReadOnly()) {
            throw new UnsupportedOperationException();
        }

        for (final UserProvider provider : getUserProviders())
        {
            if (provider.isReadOnly()) {
                continue;
            }
            provider.deleteUser(username);
        }
    }

    /**
     * Changes the creation date of a user in the first provider that contains the user.
     *
     * @param username the identifier of the user.
     * @param creationDate the date the user was created.
     * @throws UserNotFoundException when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setCreationDate(String username, Date creationDate) throws UserNotFoundException
    {
        getUserProvider(username).setCreationDate(username, creationDate);
    }

    /**
     * Changes the modification date of a user in the first provider that contains the user.
     *
     * @param username the identifier of the user.
     * @param modificationDate the date the user was (last) modified.
     * @throws UserNotFoundException when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setModificationDate(String username, Date modificationDate) throws UserNotFoundException
    {
        getUserProvider(username).setModificationDate(username, modificationDate);
    }

    /**
     * Changes the full name of a user in the first provider that contains the user.
     *
     * @param username the identifier of the user.
     * @param name the new full name a user.
     * @throws UserNotFoundException when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setName(String username, String name) throws UserNotFoundException
    {
        getUserProvider(username).setEmail(username, name);
    }

    /**
     * Changes the email address of a user in the first provider that contains the user.
     *
     * @param username the identifier of the user.
     * @param email the new email address of a user.
     * @throws UserNotFoundException when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setEmail(String username, String email) throws UserNotFoundException
    {
        getUserProvider(username).setEmail(username, email);
    }
}
