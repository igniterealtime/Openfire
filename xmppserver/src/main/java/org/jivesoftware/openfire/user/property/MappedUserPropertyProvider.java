/*
 * Copyright 2017 IgniteRealtime.org
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
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A {@link UserPropertyProvider} that delegates to a user-specific UserPropertyProvider.
 *
 * This implementation will explicitly verify if a user exists, when operating on its properties, but only if the
 * corresponding mapped provider does so. If that is the case, then the methods of this implementation will throw
 * {@link org.jivesoftware.openfire.user.UserNotFoundException}.
 *
 * This class related to, but is distinct from {@link HybridUserPropertyProvider}. The Hybrid variant of the provider
 * iterates over providers, operating on the first applicable instance. This Mapped variant, however, maps each user to
 * exactly one provider.
 *
 * To use this provider, use the following system property definition:
 *
 * <ul>
 * <li>{@code provider.userproperty.className = org.jivesoftware.openfire.user.MappedUserPropertyProvider}</li>
 * </ul>
 *
 * To be usable, a {@link UserPropertyProviderMapper} must be configured using the {@code mappedUserPropertyProvider.mapper.className}
 * system property. It is of importance to note that most UserPropertyProviderMapper implementations will require additional
 * configuration.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class MappedUserPropertyProvider implements UserPropertyProvider
{
    /**
     * Name of the property of which the value is expected to be the classname of the UserPropertyProviderMapper
     * instance to be used by instances of this class.
     */
    public static final String PROPERTY_MAPPER_CLASSNAME = "mappedUserPropertyProvider.mapper.className";
    private static final Logger Log = LoggerFactory.getLogger( MappedUserPropertyProvider.class );
    /**
     * Used to determine what provider is to be used to operate on a particular user.
     */
    protected final UserPropertyProviderMapper mapper;

    public MappedUserPropertyProvider()
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
            mapper = (UserPropertyProviderMapper) c.newInstance();
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "Unable to create new instance of UserPropertyProviderMapper class: " + mapperClass, e );
        }
    }

    /**
     * Instantiates a UserPropertyProvider based on a property value (that is expected to be a class name). When the
     * property is not set, this method returns null. When the property is set, but an exception occurs while
     * instantiating the class, this method logs the error and returns null.
     *
     * UserProvider classes are required to have a public, no-argument constructor.
     *
     * @param propertyName A property name (cannot ben ull).
     * @return A user provider (can be null).
     */
    public static UserPropertyProvider instantiate( String propertyName )
    {
        final String className = JiveGlobals.getProperty( propertyName );
        if ( className == null )
        {
            Log.debug( "Property '{}' is undefined. Skipping.", propertyName );
            return null;
        }
        Log.debug( "About to to instantiate an UserPropertyProvider '{}' based on the value of property '{}'.", className, propertyName );
        try
        {
            final Class c = ClassUtils.forName( className );
            final UserPropertyProvider provider = (UserPropertyProvider) c.newInstance();
            Log.debug( "Instantiated UserPropertyProvider '{}'", className );
            return provider;
        }
        catch ( Exception e )
        {
            Log.error( "Unable to load UserPropertyProvider '{}'. Users in this provider will be disabled.", className, e );
            return null;
        }
    }

    @Override
    public Map<String, String> loadProperties( String username ) throws UserNotFoundException
    {
        return mapper.getUserPropertyProvider( username ).loadProperties( username );
    }

    @Override
    public String loadProperty( String username, String propName ) throws UserNotFoundException
    {
        return mapper.getUserPropertyProvider( username ).loadProperty( username, propName );
    }

    @Override
    public void insertProperty( String username, String propName, String propValue ) throws UserNotFoundException
    {
        mapper.getUserPropertyProvider( username ).insertProperty( username, propName, propValue );
    }

    @Override
    public void updateProperty( String username, String propName, String propValue ) throws UserNotFoundException
    {
        mapper.getUserPropertyProvider( username ).updateProperty( username, propName, propValue );
    }

    @Override
    public void deleteProperty( String username, String propName ) throws UserNotFoundException
    {
        mapper.getUserPropertyProvider( username ).deleteProperty( username, propName );
    }

    /**
     * Returns whether <em>all</em> backing providers are read-only. When read-only, properties can not be created,
     * deleted, or modified. If at least one provider is not read-only, this method returns false.
     *
     * @return true when all backing providers are read-only, otherwise false.
     */
    @Override
    public boolean isReadOnly()
    {
        // TODO Make calls concurrent for improved throughput.
        for ( final UserPropertyProvider provider : mapper.getUserPropertyProviders() )
        {
            // If at least one provider is not readonly, neither is this proxy.
            if ( !provider.isReadOnly() )
            {
                return false;
            }
        }

        return true;
    }
}
