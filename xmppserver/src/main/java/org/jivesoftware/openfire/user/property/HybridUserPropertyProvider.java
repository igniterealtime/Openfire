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
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
public class HybridUserPropertyProvider implements UserPropertyProvider
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

    /**
     * Returns the properties from the first provider that returns a non-empty collection.
     *
     * When none of the providers provide properties an empty collection is returned.
     *
     * @param username The identifier of the user (cannot be null or empty).
     * @return A collection, possibly empty, never null.
     */
    @Override
    public Map<String, String> loadProperties( String username )
    {
        for ( final UserPropertyProvider provider : providers )
        {
            try
            {
                final Map<String, String> properties = provider.loadProperties( username );
                if ( !properties.isEmpty() )
                {
                    return properties;
                }
            }
            catch ( UserNotFoundException e )
            {
                // User not in this provider. Try other providers;
            }
        }
        return Collections.emptyMap();
    }

    /**
     * Returns a property from the first provider that returns a non-null value.
     *
     * This method will return null when the desired property was not defined in any provider.
     *
     * @param username The identifier of the user (cannot be null or empty).
     * @param propName The property name (cannot be null or empty).
     * @return The property value (possibly null).
     */
    @Override
    public String loadProperty( String username, String propName )
    {
        for ( final UserPropertyProvider provider : providers )
        {
            try
            {
                final String property = provider.loadProperty( username, propName );
                if ( property != null )
                {
                    return property;
                }
            }
            catch ( UserNotFoundException e )
            {
                // User not in this provider. Try other providers;
            }
        }
        return null;
    }

    /**
     * Adds a new property, updating a previous property value if one already exists.
     *
     * Note that the implementation of this method is equal to that of {@link #updateProperty(String, String, String)}.
     *
     * First, tries to find a provider that has the property for the provided user. If that provider is read-only, an
     * UnsupportedOperationException is thrown. If the provider is not read-only, the existing property value will be
     * updated.
     *
     * When the property is not defined in any provider, it will be added in the first non-read-only provider.
     *
     * When all providers are read-only, an UnsupportedOperationException is thrown.
     *
     * @param username  The identifier of the user (cannot be null or empty).
     * @param propName  The property name (cannot be null or empty).
     * @param propValue The property value (cannot be null).
     */
    @Override
    public void insertProperty( String username, String propName, String propValue ) throws UnsupportedOperationException
    {
        updateProperty( username, propName, propValue );
    }

    /**
     * Updates a property (or adds a new property when the property does not exist).
     *
     * Note that the implementation of this method is equal to that of {@link #insertProperty(String, String, String)}.
     *
     * First, tries to find a provider that has the property for the provided user. If that provider is read-only, an
     * UnsupportedOperationException is thrown. If the provider is not read-only, the existing property value will be
     * updated.
     *
     * When the property is not defined in any provider, it will be added in the first non-read-only provider.
     *
     * When all providers are read-only, an UnsupportedOperationException is thrown.
     *
     * @param username  The identifier of the user (cannot be null or empty).
     * @param propName  The property name (cannot be null or empty).
     * @param propValue The property value (cannot be null).
     */
    @Override
    public void updateProperty( String username, String propName, String propValue ) throws UnsupportedOperationException
    {
        for ( final UserPropertyProvider provider : providers )
        {
            try
            {
                if ( provider.loadProperty( username, propName ) != null )
                {
                    provider.updateProperty( username, propName, propValue );
                    return;
                }
            }
            catch ( UserNotFoundException e )
            {
                // User not in this provider. Try other providers;
            }
        }

        for ( final UserPropertyProvider provider : providers )
        {
            try
            {
                if ( !provider.isReadOnly() )
                {
                    provider.insertProperty( username, propName, propValue );
                    return;
                }
            }
            catch ( UserNotFoundException e )
            {
                // User not in this provider. Try other providers;
            }
        }
        throw new UnsupportedOperationException();
    }

    /**
     * Removes a property from all non-read-only providers.
     *
     * @param username The identifier of the user (cannot be null or empty).
     * @param propName The property name (cannot be null or empty).
     */
    @Override
    public void deleteProperty( String username, String propName ) throws UnsupportedOperationException
    {
        // all providers are read-only
        if ( isReadOnly() )
        {
            throw new UnsupportedOperationException();
        }

        for ( final UserPropertyProvider provider : providers )
        {
            if ( provider.isReadOnly() )
            {
                continue;
            }

            try
            {
                provider.deleteProperty( username, propName );
            }
            catch ( UserNotFoundException e )
            {
                // User not in this provider. Try other providers;
            }
        }
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
        for ( final UserPropertyProvider provider : providers )
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
