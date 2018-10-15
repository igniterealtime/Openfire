/*
 * Copyright 2016 IgniteRealtime.org
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A {@link UserProvider} that delegates to one or more 'backing' UserProvider.
 *
 * @author GUus der Kinderen, guus@goodbytes.nl
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
     * @param propertyName A property name (cannot ben ull).
     * @return A user provider (can be null).
     */
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
        int total = 0;
        // TODO Make calls concurrent for improved throughput.
        for ( final UserProvider provider : getUserProviders() )
        {
            total += provider.getUserCount();
        }

        return total;
    }

    @Override
    public Collection<User> getUsers()
    {
        final Collection<User> result = new ArrayList<>();
        for ( final UserProvider provider : getUserProviders() )
        {
            // TODO Make calls concurrent for improved throughput.
            result.addAll( provider.getUsers() );
        }

        return result;
    }

    @Override
    public Collection<String> getUsernames()
    {
        final Collection<String> result = new ArrayList<>();
        for ( final UserProvider provider : getUserProviders() )
        {
            // TODO Make calls concurrent for improved throughput.
            result.addAll( provider.getUsernames() );
        }

        return result;
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
        final List<User> userList = new ArrayList<>();
        int supportSearch = getUserProviders().size();

        // TODO Make calls concurrent for improved throughput.
        for ( final UserProvider provider : getUserProviders() )
        {
            try
            {
                // Use only those fields that are supported by the provider.
                final Set<String> supportedFields = new HashSet<>( fields );
                supportedFields.retainAll( provider.getSearchFields() );

                userList.addAll( provider.findUsers( supportedFields, query ) );
            }
            catch ( UnsupportedOperationException uoe )
            {
                Log.warn( "UserProvider.findUsers is not supported by this UserProvider: {}. Its users are not returned as part of search queries.", provider.getClass().getName() );
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
                final Collection<User> providerResults = provider.findUsers( fields, query );

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
                userList.addAll( providerList.subList( providerStartIndex, providerResultMax ) );

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
        int supportSearch = getUserProviders().size();
        final Set<String> result = new HashSet<>();

        // TODO Make calls concurrent for improved throughput.
        for ( final UserProvider provider : getUserProviders() )
        {
            try
            {
                result.addAll( provider.getSearchFields() );
            }
            catch ( UnsupportedOperationException uoe )
            {
                Log.warn( "getSearchFields is not supported by this UserProvider: " + provider.getClass().getName() );
                supportSearch--;
            }
        }

        if ( supportSearch == 0 )
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
        // TODO Make calls concurrent for improved throughput.
        for ( final UserProvider provider : getUserProviders() )
        {
            // If at least one provider is not readonly, neither is this proxy.
            if ( !provider.isReadOnly() )
            {
                return false;
            }
        }

        return true;
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
        // TODO Make calls concurrent for improved throughput.
        for ( final UserProvider provider : getUserProviders() )
        {
            // If at least one provider does not require a name, neither is this proxy.
            if ( !provider.isNameRequired() )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns whether <em>all</em> backing providers require an email address to be set on User objects. If at least
     * one proivder does not, this method returns false.
     *
     * @return true when all backing providers require an email address to be set on User objects, otherwise false.
     */
    @Override
    public boolean isEmailRequired()
    {
        // TODO Make calls concurrent for improved throughput.
        for ( final UserProvider provider : getUserProviders() )
        {
            // If at least one provider does not require an email, neither is this proxy.
            if ( !provider.isEmailRequired() )
            {
                return false;
            }
        }

        return true;
    }
}
