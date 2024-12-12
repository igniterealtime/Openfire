/*
 * Copyright (C) 2024 Ignite Realtime Foundation. All rights reserved
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
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Map;

/**
 * A {@link UserPropertyProvider} that delegates to one or more 'backing' UserProvider.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class UserPropertyMultiProvider implements UserPropertyProvider
{
    private final static Logger Log = LoggerFactory.getLogger(UserPropertyMultiProvider.class);

    /**
     * Instantiates a UserPropertyProvider based on a property value (that is expected to be a class name). When the
     * property is not set, this method returns null. When the property is set, but an exception occurs while
     * instantiating the class, this method logs the error and returns null.
     *
     * UserPropertyProvider classes are required to have a public, no-argument constructor.
     *
     * @param propertyName A property name (cannot be null).
     * @return A user provider (can be null).
     * @deprecated Use {@link #instantiate(SystemProperty)} or {@link #instantiate(SystemProperty, SystemProperty)} instead.
     */
    @Deprecated(forRemoval = true, since = "5.0.0") // TODO Remove in or after Openfire 5.1.0
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

    /**
     * Instantiates a UserPropertyProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * UserPropertyProvider classes are required to have a public, no-argument constructor.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @return A user provider (can be null).
     */
    public static UserPropertyProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty)
    {
        return instantiate(implementationProperty, null);
    }

    /**
     * Instantiates a UserPropertyProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * UserPropertyProvider classes are required to have a public, no-argument constructor, but can have an optional
     * additional constructor that takes a single String argument. If such constructor is defined, then it is invoked
     * with the value of the second argument of this method. This is typically used to (but needs not) identify a
     * property (by name) that holds additional configuration for the to be instantiated UserPropertyProvider. This
     * implementation will pass on any non-empty value to the constructor. When a configuration argument is provided,
     * but no constructor exists in the implementation that accepts a single String value, this method will log a
     * warning and attempt to return an instance based on the no-arg constructor of the class.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @param configProperty A property that holds an opaque configuration string value passed to the constructor.
     * @return A user provider (can be null).
     */
    public static UserPropertyProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty, @Nullable final SystemProperty<String> configProperty)
    {
        final Class<? extends UserPropertyProvider> implementationClass = implementationProperty.getValue();
        if (implementationClass == null) {
            Log.debug( "Property '{}' is undefined or has no value. Skipping.", implementationProperty.getKey() );
            return null;
        }
        Log.debug("About to to instantiate an UserPropertyProvider '{}' based on the value of property '{}'.", implementationClass, implementationProperty.getKey());

        try {
            if (configProperty != null && configProperty.getValue() != null && !configProperty.getValue().isEmpty()) {
                try {
                    final Constructor<? extends UserPropertyProvider> constructor = implementationClass.getConstructor(String.class);
                    final UserPropertyProvider result = constructor.newInstance(configProperty.getValue());
                    Log.debug("Instantiated UserPropertyProvider '{}' with configuration: '{}'", implementationClass.getName(), configProperty.getValue());
                    return result;
                } catch (NoSuchMethodException e) {
                    Log.warn("Custom configuration is defined for the a provider but the configured class ('{}') does not provide a constructor that takes a String argument. Custom configuration will be ignored. Ignored configuration: '{}'", implementationProperty.getValue().getName(), configProperty);
                }
            }

            final UserPropertyProvider result = implementationClass.getDeclaredConstructor().newInstance();
            Log.debug("Instantiated UserPropertyProvider '{}'", implementationClass.getName());
            return result;
        } catch (Exception e) {
            Log.error("Unable to load UserPropertyProvider '{}'. Data from this provider will not be available.", implementationClass.getName(), e);
            return null;
        }
    }

    /**
     * Returns all UserPropertyProvider instances that serve as 'backing' providers.
     *
     * @return A collection of providers (never null).
     */
    abstract Collection<UserPropertyProvider> getUserPropertyProviders();

    /**
     * Returns the 'backing' provider that serves the provided user. Note that the user need not exist.
     *
     * Finds a suitable UserPropertyProvider for the user.
     *
     * Note that the provided username need not reflect a pre-existing user (the instance might be used to determine in
     * which provider a new user is to be created).
     *
     * Implementations are expected to be able to find a UserPropertyProvider for any username. If an implementation
     * fails to do so, such a failure is assumed to be the result of a problem in implementation or configuration.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return A UserPropertyProvider for the user (never null).
     */
    abstract UserPropertyProvider getUserPropertyProvider(final String username);

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
        for ( final UserPropertyProvider provider : getUserPropertyProviders() )
        {
            // If at least one provider is not readonly, neither is this proxy.
            if ( !provider.isReadOnly() )
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public Map<String, String> loadProperties(String username) throws UserNotFoundException
    {
        return getUserPropertyProvider(username).loadProperties( username );
    }

    @Override
    public String loadProperty(String username, String propName) throws UserNotFoundException
    {
        return getUserPropertyProvider(username).loadProperty( username, propName );
    }

    @Override
    public void insertProperty(String username, String propName, String propValue) throws UserNotFoundException
    {
        // all providers are read-only
        if (isReadOnly()) {
            throw new UnsupportedOperationException();
        }
        getUserPropertyProvider(username).insertProperty(username, propName, propValue);
    }

    @Override
    public void updateProperty(String username, String propName, String propValue) throws UserNotFoundException
    {
        if (isReadOnly()) {
            throw new UnsupportedOperationException();
        }
        getUserPropertyProvider(username).updateProperty(username, propName, propValue);
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
        if (isReadOnly()) {
            throw new UnsupportedOperationException();
        }

        for (final UserPropertyProvider provider : getUserPropertyProviders())
        {
            if ( provider.isReadOnly() ) {
                continue;
            }

            try {
                provider.deleteProperty(username, propName);
            }
            catch (UserNotFoundException e) {
                // User not in this provider. Try other providers;
            }
        }
    }
}
