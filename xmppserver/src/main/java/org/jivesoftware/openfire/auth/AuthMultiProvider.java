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
package org.jivesoftware.openfire.auth;

import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.Collection;

/**
 * An {@link AuthProvider} that delegates to one or more 'backing' AuthProviders.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public abstract class AuthMultiProvider implements AuthProvider
{
    private final static Logger Log = LoggerFactory.getLogger(AuthMultiProvider.class);

    /**
     * Instantiates a AuthProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * AuthProvider classes are required to have a public, no-argument constructor.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @return A user provider (can be null).
     */
    public static AuthProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty)
    {
        return instantiate(implementationProperty, null);
    }

    /**
     * Instantiates a AuthProvider based on Class-based system property. When the property is not set, this
     * method returns null. When the property is set, but an exception occurs while instantiating the class, this method
     * logs the error and returns null.
     *
     * AuthProvider classes are required to have a public, no-argument constructor, but can have an optional
     * additional constructor that takes a single String argument. If such constructor is defined, then it is invoked
     * with the value of the second argument of this method. This is typically used to (but needs not) identify a
     * property (by name) that holds additional configuration for the to be instantiated AuthProvider. This
     * implementation will pass on any non-empty value to the constructor. When a configuration argument is provided,
     * but no constructor exists in the implementation that accepts a single String value, this method will log a
     * warning and attempt to return an instance based on the no-arg constructor of the class.
     *
     * @param implementationProperty A property that defines the class of the instance to be returned.
     * @param configProperty A property that holds an opaque configuration string value passed to the constructor.
     * @return A user provider (can be null).
     */
    public static AuthProvider instantiate(@Nonnull final SystemProperty<Class> implementationProperty, @Nullable final SystemProperty<String> configProperty)
    {
        final Class<? extends AuthProvider> implementationClass = implementationProperty.getValue();
        if (implementationClass == null) {
            Log.debug( "Property '{}' is undefined or has no value. Skipping.", implementationProperty.getKey() );
            return null;
        }
        Log.debug("About to to instantiate an AuthProvider '{}' based on the value of property '{}'.", implementationClass, implementationProperty.getKey());

        try {
            if (configProperty != null && configProperty.getValue() != null && !configProperty.getValue().isEmpty()) {
                try {
                    final Constructor<? extends AuthProvider> constructor = implementationClass.getConstructor(String.class);
                    final AuthProvider result = constructor.newInstance(configProperty.getValue());
                    Log.debug("Instantiated AuthProvider '{}' with configuration: '{}'", implementationClass.getName(), configProperty.getValue());
                    return result;
                } catch (NoSuchMethodException e) {
                    Log.warn("Custom configuration is defined for the a provider but the configured class ('{}') does not provide a constructor that takes a String argument. Custom configuration will be ignored. Ignored configuration: '{}'", implementationProperty.getValue().getName(), configProperty);
                }
            }

            final AuthProvider result = implementationClass.getDeclaredConstructor().newInstance();
            Log.debug("Instantiated AuthProvider '{}'", implementationClass.getName());
            return result;
        } catch (Exception e) {
            Log.error("Unable to load AuthProvider '{}'. Data from this provider will not be available.", implementationClass.getName(), e);
            return null;
        }
    }

    /**
     * Returns all AuthProvider instances that serve as 'backing' providers.
     *
     * @return A collection of providers (never null).
     */
    abstract Collection<AuthProvider> getAuthProviders();

    /**
     * Returns the 'backing' provider that serves the provided user.
     *
     * Finds a suitable AuthProvider for the user.
     *
     * Unlike other MultiProvider interfaces, this interface does not require this method to return a non-null value.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return A AuthProvider for the user (possibly null).
     */
    abstract AuthProvider getAuthProvider(String username);

    @Override
    public boolean supportsPasswordRetrieval()
    {
        // TODO Make calls concurrent for improved throughput.
        for (final AuthProvider provider : getAuthProviders())
        {
            // If at least one provider supports password retrieval, so does this proxy.
            if (provider.supportsPasswordRetrieval()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isScramSupported()
    {
        // TODO Make calls concurrent for improved throughput.
        for (final AuthProvider provider : getAuthProviders())
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
    public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UnauthorizedException();
        }
        provider.authenticate(username, password);
    }

    @Override
    public String getPassword(String username) throws UserNotFoundException, UnsupportedOperationException
    {
        if (!supportsPasswordRetrieval()) {
            throw new UnsupportedOperationException();
        }

        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        return provider.getPassword( username );
    }

    @Override
    public void setPassword(String username, String password) throws UserNotFoundException, UnsupportedOperationException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        provider.setPassword( username, password );
    }

    @Override
    public String getSalt(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        return provider.getSalt( username );
    }

    @Override
    public int getIterations(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null)
        {
            throw new UserNotFoundException();
        }
        return provider.getIterations(username);
    }

    @Override
    public String getServerKey(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null) {
            throw new UserNotFoundException();
        }
        return provider.getServerKey(username);
    }

    @Override
    public String getStoredKey(String username) throws UserNotFoundException
    {
        final AuthProvider provider = getAuthProvider(username);
        if (provider == null) {
            throw new UserNotFoundException();
        }
        return provider.getStoredKey(username);
    }
}
