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

import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegate UserPropertyProvider operations among up to three configurable provider implementation classes.
 *
 * This implementation will not explicitly verify if a user exists, when operating on its properties. The methods of
 * this implementation will <em>not</em> throw {@link org.jivesoftware.openfire.user.UserNotFoundException}.
 *
 * This class related to, but is distinct from {@link MappedUserPropertyProvider}. The Hybrid variant of the provider
 * iterates over providers, operating on the first applicable instance. The Mapped variant, however, maps each user to
 * exactly one provider.
 *
 * To use this provider, use the following system property definition:
 *
 * <ul>
 * <li>{@code provider.userproperty.className = org.jivesoftware.openfire.user.HybridUserPropertyProvider}</li>
 * </ul>
 *
 * Next, configure up to three providers, by setting these properties:
 * <ol>
 * <li>{@code hybridUserPropertyProvider.primaryProvider.className = fully.qualified.ClassUserPropertyProvider}</li>
 * <li>{@code hybridUserPropertyProvider.secondaryProvider.className = fully.qualified.ClassUserPropertyProvider}</li>
 * <li>{@code hybridUserPropertyProvider.tertiaryProvider.className = fully.qualified.ClassUserPropertyProvider}</li>
 * </ol>
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class HybridUserPropertyProvider extends UserPropertyMultiProvider
{
    private static final Logger Log = LoggerFactory.getLogger( HybridUserPropertyProvider.class );

    private final List<UserPropertyProvider> providers = new ArrayList<>();

    public HybridUserPropertyProvider()
    {
        // Migrate user provider properties
        JiveGlobals.migrateProperty( "hybridUserPropertyProvider.primaryProvider.className" );
        JiveGlobals.migrateProperty( "hybridUserPropertyProvider.secondaryProvider.className" );
        JiveGlobals.migrateProperty( "hybridUserPropertyProvider.tertiaryProvider.className" );

        // Load primary, secondary, and tertiary user providers.
        final UserPropertyProvider primary = MappedUserPropertyProvider.instantiate( "hybridUserPropertyProvider.primaryProvider.className" );
        if ( primary != null )
        {
            providers.add( primary );
        }
        final UserPropertyProvider secondary = MappedUserPropertyProvider.instantiate( "hybridUserPropertyProvider.secondaryProvider.className" );
        if ( secondary != null )
        {
            providers.add( secondary );
        }
        final UserPropertyProvider tertiary = MappedUserPropertyProvider.instantiate( "hybridUserPropertyProvider.tertiaryProvider.className" );
        if ( tertiary != null )
        {
            providers.add( tertiary );
        }

        // Verify that there's at least one provider available.
        if ( providers.isEmpty() )
        {
            Log.error( "At least one UserPropertyProvider must be specified via openfire.xml or the system properties!" );
        }
    }

    @Override
    protected List<UserPropertyProvider> getUserPropertyProviders()
    {
        return providers;
    }

    /**
     * Returns the first provider that contains the user, or the first provider that is not read-only when the user
     * does not exist in any provider.
     *
     * @param username the username (cannot be null or empty).
     * @return The user property provider (never null)
     */
    @Override
    public UserPropertyProvider getUserPropertyProvider(String username)
    {
        UserPropertyProvider nonReadOnly = null;
        for (final UserPropertyProvider provider : getUserPropertyProviders())
        {
            try
            {
                provider.loadProperties(username);
            }
            catch (UserNotFoundException unfe)
            {
                Log.debug("User {} not found by UserPropertyProvider {}", username, provider.getClass().getName());

                if (nonReadOnly == null && !provider.isReadOnly()) {
                    nonReadOnly = provider;
                }
            }
        }

        // User does not exist. Return a provider suitable for creating user properties.
        if (nonReadOnly == null) {
            throw new UnsupportedOperationException();
        }

        return nonReadOnly;
    }
}
