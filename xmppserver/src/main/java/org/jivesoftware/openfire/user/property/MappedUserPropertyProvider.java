/*
 * Copyright (C) 2017-2024 Ignite Realtime Foundation. All rights reserved
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

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;

import java.util.Collection;

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
public class MappedUserPropertyProvider extends UserPropertyMultiProvider
{
    /**
     * Name of the property of which the value is expected to be the classname of the UserPropertyProviderMapper
     * instance to be used by instances of this class.
     */
    public static final String PROPERTY_MAPPER_CLASSNAME = "mappedUserPropertyProvider.mapper.className";

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

    @Override
    Collection<UserPropertyProvider> getUserPropertyProviders()
    {
        return mapper.getUserPropertyProviders();
    }

    @Override
    UserPropertyProvider getUserPropertyProvider(final String username)
    {
        return mapper.getUserPropertyProvider(username);
    }
}
