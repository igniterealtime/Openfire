/*
 * Copyright 2017 Ignite Realtime Foundation
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

import java.util.*;

/**
 * A {@link UserProviderMapper} that can be used to draw some users from another source than the other users.
 *
 * This implementation uses properties to define sets of usernames and a corresponding provider. When a user is not in
 * any set, a fallback provider is used.
 *
 * Each set of usernames is defined by two properties. Use the following property to define the classname of an
 * {@link UserProvider} to be used for this set: {@code propertyBasedUserMapper.set.SET_NAME.provider.className}
 *
 * Use the following property to identify a set of usernames: {@code propertyBasedUserMapper.set.SET_NAME.members}. The
 * value for this property must be the name of another property, which is a listing of usernames (such a property is
 * likely re-used in a corresponding {@link org.jivesoftware.openfire.auth.PropertyBasedAuthProviderMapper}
 * configuration).
 *
 * There is no upper bound on the amount of sets that can be configured.
 *
 * Users that are not in any set will use the fallback provider. This provider is defined by its class name in the
 * property {@code propertyBasedUserMapper.fallbackProvider.className}.
 *
 * The following example defines two sets. Set "A" serves users john, jane and jack, and uses a DefaultUserProvider.
 * Set "B" serves users dave and doris, and uses A JDBCUserProvider. All other users are served by the fallback provider
 * that is a NativeUserProvider.
 *
 * <ul>
 * <li>{@code members.set.A = List( "john", "jane", "jack" )}</li>
 * <li>{@code members.set.B = List( "dave", "doris" )}</li>
 * <li>{@code propertyBasedUserMapper.set.A.provider.className = org.jivesoftware.openfire.user.DefaultUserProvider}</li>
 * <li>{@code propertyBasedUserMapper.set.A.members.propertyName = members.set.A}</li>
 * <li>{@code propertyBasedUserMapper.set.B.provider.className = org.jivesoftware.openfire.user.JDBCUserProvider}</li>
 * <li>{@code propertyBasedUserMapper.set.B.members.propertyName = members.set.B}</li>
 * <li>{@code propertyBasedUserMapper.fallbackProvider.className = org.jivesoftware.openfire.auth.NativeUserProvider}</li>
 * </ul>
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class PropertyBasedUserProviderMapper implements UserProviderMapper
{
    protected final Map<String, UserProvider> providersByPrefix = new HashMap<>();

    protected UserProvider fallbackProvider;

    public PropertyBasedUserProviderMapper()
    {
        // Migrate properties.
        JiveGlobals.migratePropertyTree( "propertyBasedUserMapper" );

        // Instantiate the fallback provider
        fallbackProvider = UserMultiProvider.instantiate( "propertyBasedUserMapper.fallbackProvider.className" );
        if ( fallbackProvider == null )
        {
            throw new IllegalStateException( "Expected a UserProvider class name in property 'propertyBasedUserMapper.fallbackProvider.className'" );
        }
        // Instantiate all sets
        final List<String> setProperties = JiveGlobals.getPropertyNames( "propertyBasedUserMapper.set" );
        for ( final String setProperty : setProperties )
        {
            final UserProvider provider = UserMultiProvider.instantiate( setProperty + ".provider.className" );
            if ( provider == null )
            {
                throw new IllegalStateException( "Expected a UserProvider class name in property '" + setProperty + ".provider.className'" );
            }

            providersByPrefix.put( setProperty, provider );
        }
    }

    @Override
    public UserProvider getUserProvider( String username )
    {
        for ( final Map.Entry<String, UserProvider> entry : providersByPrefix.entrySet() )
        {
            final String usersProperty = JiveGlobals.getProperty( entry.getKey() + ".members.propertyName" );
            if ( usersProperty != null )
            {
                final List<String> usersInSet = JiveGlobals.getListProperty( usersProperty, Collections.<String>emptyList() );
                if ( usersInSet.contains( username ) )
                {
                    return entry.getValue();
                }
            }
        }

        return fallbackProvider;
    }

    @Override
    public Set<UserProvider> getUserProviders()
    {
        final Set<UserProvider> result = new LinkedHashSet<>();
        result.addAll( providersByPrefix.values() );
        result.add( fallbackProvider );

        return result;
    }
}
