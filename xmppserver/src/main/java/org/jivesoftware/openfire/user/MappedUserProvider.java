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

import java.util.Collection;
import java.util.Date;

/**
 * A {@link UserProvider} that delegates to a user-specific UserProvider.
 *
 * This class related to, but is distinct from {@link HybridUserProvider}. The Hybrid variant of the provider iterates
 * over providers, operating on the first applicable instance. This Mapped variant, however, maps each user to exactly
 * one provider.
 *
 * To use this provider, use the following system property definition:
 *
 * <ul>
 * <li>{@code provider.user.className = org.jivesoftware.openfire.user.MappedUserProvider}</li>
 * </ul>
 *
 * To be usable, a {@link UserProviderMapper} must be configured using the {@code mappedUserProvider.mapper.className}
 * system property. It is of importance to note that most UserProviderMapper implementations will require additional
 * configuration.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 * @see org.jivesoftware.openfire.auth.MappedAuthProvider
 */
public class MappedUserProvider extends UserMultiProvider
{
    /**
     * Name of the property of which the value is expected to be the classname of the UserProviderMapper instance to be
     * used by instances of this class.
     */
    public static final String PROPERTY_MAPPER_CLASSNAME = "mappedUserProvider.mapper.className";

    /**
     * Used to determine what provider is to be used to operate on a particular user.
     */
    protected final UserProviderMapper mapper;

    public MappedUserProvider()
    {

        // Migrate properties.
        JiveGlobals.migrateProperty( PROPERTY_MAPPER_CLASSNAME );

        // Instantiate mapper.
        final String mapperClass = JiveGlobals.getProperty( PROPERTY_MAPPER_CLASSNAME );
        if ( mapperClass == null )
        {
            throw new IllegalStateException( "A mapper must be specified via openfire.xml or the system properties." );
        }

        try
        {
            final Class c = ClassUtils.forName( mapperClass );
            mapper = (UserProviderMapper) c.newInstance();
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Unable to create new instance of UserProviderMapper class: " + mapperClass, e );
        }
    }

    @Override
    public Collection<UserProvider> getUserProviders()
    {
        return mapper.getUserProviders();
    }

    @Override
    public UserProvider getUserProvider( String username )
    {
        return mapper.getUserProvider( username );
    }

    @Override
    public User loadUser( String username ) throws UserNotFoundException
    {
        final UserProvider userProvider;
        try{
            userProvider = getUserProvider( username );
        } catch (RuntimeException e){
            throw new UserNotFoundException("Unable to identify user provider for username "+username, e);
        }
        return userProvider.loadUser( username );
    }

    @Override
    public User createUser( String username, String password, String name, String email ) throws UserAlreadyExistsException
    {
        return getUserProvider( username ).createUser( username, password, name, email );
    }

    @Override
    public void deleteUser( String username )
    {
        getUserProvider( username ).deleteUser( username );
    }

    @Override
    public void setName( String username, String name ) throws UserNotFoundException
    {
        getUserProvider( username ).setName( username, name );
    }

    @Override
    public void setEmail( String username, String email ) throws UserNotFoundException
    {
        getUserProvider( username ).setEmail( username, email );
    }

    @Override
    public void setCreationDate( String username, Date creationDate ) throws UserNotFoundException
    {
        getUserProvider( username ).setCreationDate( username, creationDate );
    }

    @Override
    public void setModificationDate( String username, Date modificationDate ) throws UserNotFoundException
    {
        getUserProvider( username ).setModificationDate( username, modificationDate );
    }
}
