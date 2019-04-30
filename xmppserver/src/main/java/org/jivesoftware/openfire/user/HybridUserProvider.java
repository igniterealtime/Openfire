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

import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Delegate UserProvider operations among up to three configurable provider implementation classes.
 *
 * This class related to, but is distinct from {@link MappedUserProvider}. The Hybrid variant of the provider iterates
 * over providers, operating on the first applicable instance. The Mapped variant, however, maps each user to exactly
 * one provider.
 *
 * @author Marc Seeger
 * @author Chris Neasbitt
 * @author Tom Evans
 * @author Guus der Kinderen
 */
public class HybridUserProvider extends UserMultiProvider
{
    private static final Logger Log = LoggerFactory.getLogger( HybridUserProvider.class );

    private final List<UserProvider> userProviders = new ArrayList<>();

    public HybridUserProvider()
    {
        // Migrate user provider properties
        JiveGlobals.migrateProperty( "hybridUserProvider.primaryProvider.className" );
        JiveGlobals.migrateProperty( "hybridUserProvider.secondaryProvider.className" );
        JiveGlobals.migrateProperty( "hybridUserProvider.tertiaryProvider.className" );

        // Load primary, secondary, and tertiary user providers.
        final UserProvider primary = instantiate( "hybridUserProvider.primaryProvider.className" );
        if ( primary != null )
        {
            userProviders.add( primary );
        }
        final UserProvider secondary = instantiate( "hybridUserProvider.secondaryProvider.className" );
        if ( secondary != null )
        {
            userProviders.add( secondary );
        }
        final UserProvider tertiary = instantiate( "hybridUserProvider.tertiaryProvider.className" );
        if ( tertiary != null )
        {
            userProviders.add( tertiary );
        }

        // Verify that there's at least one provider available.
        if ( userProviders.isEmpty() )
        {
            Log.error( "At least one UserProvider must be specified via openfire.xml or the system properties!" );
        }
    }

    @Override
    protected List<UserProvider> getUserProviders()
    {
        return userProviders;
    }

    /**
     * Creates a new user in the first non-read-only provider.
     *
     * @param username the username.
     * @param password the plain-text password.
     * @param name     the user's name, which can be {@code null}, unless isNameRequired is set to true.
     * @param email    the user's email address, which can be {@code null}, unless isEmailRequired is set to true.
     * @return The user that was created.
     * @throws UserAlreadyExistsException if the user already exists
     */
    @Override
    public User createUser( String username, String password, String name, String email ) throws UserAlreadyExistsException
    {
        // create the user (first writable provider wins)
        for ( final UserProvider provider : getUserProviders() )
        {
            if ( provider.isReadOnly() )
            {
                continue;
            }
            return provider.createUser( username, password, name, email );
        }

        // all providers are read-only
        throw new UnsupportedOperationException();
    }

    /**
     * Removes a user from all non-read-only providers.
     *
     * @param username the username to delete.
     */
    @Override
    public void deleteUser( String username )
    {
        // all providers are read-only
        if ( isReadOnly() )
        {
            throw new UnsupportedOperationException();
        }

        for ( final UserProvider provider : getUserProviders() )
        {
            if ( provider.isReadOnly() )
            {
                continue;
            }
            provider.deleteUser( username );
        }
    }

    /**
     * Returns the first provider that contains the user, or the first provider that is not read-only when the user
     * does not exist in any provider.
     *
     * @param username the username (cannot be null or empty).
     * @return The user provider (never null)
     */
    public UserProvider getUserProvider( String username )
    {
        UserProvider nonReadOnly = null;
        for ( final UserProvider provider : getUserProviders() )
        {
            try
            {
                provider.loadUser( username );
                return provider;
            }
            catch ( UserNotFoundException unfe )
            {
                if ( Log.isDebugEnabled() )
                {
                    Log.debug( "User {} not found by UserProvider {}", username, provider.getClass().getName() );
                }

                if ( nonReadOnly == null && !provider.isReadOnly() )
                {
                    nonReadOnly = provider;
                }
            }
        }

        // User does not exist. Return a provider suitable for creating users.
        if ( nonReadOnly == null )
        {
            throw new UnsupportedOperationException();
        }

        return nonReadOnly;
    }

    /**
     * Loads a user from the first provider that contains the user.
     *
     * @param username the username (cannot be null or empty).
     * @return The user (never null).
     * @throws UserNotFoundException When none of the providers contains the user.
     */
    @Override
    public User loadUser( String username ) throws UserNotFoundException
    {
        for ( UserProvider provider : userProviders )
        {
            try
            {
                return provider.loadUser( username );
            }
            catch ( UserNotFoundException unfe )
            {
                if ( Log.isDebugEnabled() )
                {
                    Log.debug( "User {} not found by UserProvider {}", username, provider.getClass().getName() );
                }
            }
        }
        //if we get this far, no provider was able to load the user
        throw new UserNotFoundException();
    }

    /**
     * Changes the creation date of a user in the first provider that contains the user.
     *
     * @param username     the username.
     * @param creationDate the date the user was created.
     * @throws UserNotFoundException         when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setCreationDate( String username, Date creationDate ) throws UserNotFoundException
    {
        getUserProvider( username ).setCreationDate( username, creationDate );
    }

    /**
     * Changes the modification date of a user in the first provider that contains the user.
     *
     * @param username         the username.
     * @param modificationDate the date the user was (last) modified.
     * @throws UserNotFoundException         when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setModificationDate( String username, Date modificationDate ) throws UserNotFoundException
    {
        getUserProvider( username ).setCreationDate( username, modificationDate );
    }

    /**
     * Changes the full name of a user in the first provider that contains the user.
     *
     * @param username the username.
     * @param name     the new full name a user.
     * @throws UserNotFoundException         when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setName( String username, String name ) throws UserNotFoundException
    {
        getUserProvider( username ).setEmail( username, name );
    }

    /**
     * Changes the email address of a user in the first provider that contains the user.
     *
     * @param username the username.
     * @param email    the new email address of a user.
     * @throws UserNotFoundException         when the user was not found in any provider.
     * @throws UnsupportedOperationException when the provider is read-only.
     */
    @Override
    public void setEmail( String username, String email ) throws UserNotFoundException
    {
        getUserProvider( username ).setEmail( username, email );
    }
}

