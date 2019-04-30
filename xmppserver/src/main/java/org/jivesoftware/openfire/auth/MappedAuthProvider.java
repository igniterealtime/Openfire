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

package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;

/**
 * A {@link AuthProvider} that delegates to a user-specific AuthProvider.
 *
 * This class related to, but is distinct from {@link HybridAuthProvider}. The Hybrid variant of the provider iterates
 * over providers, operating on the first applicable instance. This Mapped variant, however, maps each user to exactly
 * one provider.
 *
 * To use this provider, use the following system property definition:
 *
 * <ul>
 * <li>{@code provider.auth.className = org.jivesoftware.openfire.user.MappedAuthProvider}</li>
 * </ul>
 *
 * To be usable, a {@link AuthProviderMapper} must be configured using the {@code mappedAuthProvider.mapper.className}
 * system property. It is of importance to note that most AuthProviderMapper implementations will require additional
 * configuration.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class MappedAuthProvider implements AuthProvider
{
    /**
     * Name of the property of which the value is expected to be the classname of the AuthProviderMapper instance to be
     * used by instances of this class.
     */
    public static final String PROPERTY_MAPPER_CLASSNAME = "mappedAuthProvider.mapper.className";

    /**
     * Used to determine what provider is to be used to operate on a particular user.
     */
    protected final AuthProviderMapper mapper;

    public MappedAuthProvider()
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
            mapper = (AuthProviderMapper) c.newInstance();
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Unable to create new instance of AuthProviderMapper class: " + mapperClass, e );
        }
    }

    @Override
    public void authenticate( String username, String password ) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UnauthorizedException();
        }
        provider.authenticate( username, password );
    }

    @Override
    public String getPassword( String username ) throws UserNotFoundException, UnsupportedOperationException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UserNotFoundException();
        }
        return provider.getPassword( username );
    }

    @Override
    public void setPassword( String username, String password ) throws UserNotFoundException, UnsupportedOperationException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UserNotFoundException();
        }
        provider.setPassword( username, password );
    }

    @Override
    public boolean supportsPasswordRetrieval()
    {
        // TODO Make calls concurrent for improved throughput.
        for ( final AuthProvider provider : mapper.getAuthProviders() )
        {
            // If at least one provider supports password retrieval, so does this proxy.
            if ( provider.supportsPasswordRetrieval() )
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isScramSupported()
    {
        // TODO Make calls concurrent for improved throughput.
        for ( final AuthProvider provider : mapper.getAuthProviders() )
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
    public String getSalt(String username) throws UserNotFoundException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UserNotFoundException();
        }
        return provider.getSalt( username );
    }

    @Override
    public int getIterations(String username) throws UserNotFoundException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UserNotFoundException();
        }
        return provider.getIterations( username );
    }

    @Override
    public String getServerKey(String username) throws UserNotFoundException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UserNotFoundException();
        }
        return provider.getServerKey( username );
    }

    @Override
    public String getStoredKey(String username) throws UserNotFoundException
    {
        final AuthProvider provider = mapper.getAuthProvider( username );
        if ( provider == null )
        {
            throw new UserNotFoundException();
        }
        return provider.getStoredKey( username );
    }
}
