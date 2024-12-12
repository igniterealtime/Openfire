/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2024 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * The hybrid auth provider allows up to three AuthProvider implementations to
 * be strung together to do chained authentication checking. The algorithm is
 * as follows:
 * <ol>
 *      <li>Attempt authentication using the primary provider. If that fails:
 *      <li>If the secondary provider is defined, attempt authentication (otherwise return).
 *          If that fails:
 *      <li>If the tertiary provider is defined, attempt authentication.
 * </ol>
 *
 * This class related to, but is distinct from {@link MappedAuthProvider}. The Hybrid variant of the provider iterates
 * over providers, operating on the first applicable instance. The Mapped variant, however, maps each user to exactly
 * one provider.
 *
 * To enable this provider, set the {@code provider.auth.className} system property to
 * {@code org.jivesoftware.openfire.auth.HybridAuthProvider}.
 *
 * The primary, secondary, and tertiary providers are configured be setting system properties similar to
 * the following:
 *
 * <ul>
 * <li>{@code hybridAuthProvider.primaryProvider = org.jivesoftware.openfire.auth.DefaultAuthProvider}</li>
 * <li>{@code hybridAuthProvider.secondaryProvider = org.jivesoftware.openfire.auth.NativeAuthProvider}</li>
 * </ul>
 *
 * Each of the chained providers can have a list of override users. If a user is in
 * an override list, authentication will only be attempted with the associated provider
 * (bypassing the chaining logic).<p>
 *
 * The full list of properties:
 * <ul>
 *      <li>{@code hybridAuthProvider.primaryProvider.className} (required) -- the class name
 *          of the auth provider.
 *      <li>{@code hybridAuthProvider.primaryProvider.overrideList} -- a comma-delimitted list
 *          of usernames for which authentication will only be tried with this provider.
 *      <li>{@code hybridAuthProvider.secondaryProvider.className} -- the class name
 *          of the auth provider.
 *      <li>{@code hybridAuthProvider.secondaryProvider.overrideList} -- a comma-delimitted list
 *          of usernames for which authentication will only be tried with this provider.
 *      <li>{@code hybridAuthProvider.tertiaryProvider.className} -- the class name
 *          of the auth provider.
 *      <li>{@code hybridAuthProvider.tertiaryProvider.overrideList} -- a comma-delimitted list
 *          of usernames for which authentication will only be tried with this provider.
 * </ul>
 *
 * The primary provider is required, but all other properties are optional. Each provider
 * should be configured as it is normally, using whatever XML configuration options it specifies.
 *
 * @author Matt Tucker
 */
public class HybridAuthProvider extends AuthMultiProvider {

    private static final Logger Log = LoggerFactory.getLogger(HybridAuthProvider.class);
    private static final SystemProperty<Class> PRIMARY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("hybridAuthProvider.primaryProvider.className")
        .setBaseClass(AuthProvider.class)
        .setDefaultValue(DefaultAuthProvider.class)
        .setDynamic(false)
        .build();
    private static final SystemProperty<Class> SECONDARY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("hybridAuthProvider.secondaryProvider.className")
        .setBaseClass(AuthProvider.class)
        .setDynamic(false)
        .build();
    private static final SystemProperty<Class> TERTIARY_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("hybridAuthProvider.tertiaryProvider.className")
        .setBaseClass(AuthProvider.class)
        .setDynamic(false)
        .build();

    private AuthProvider primaryProvider;
    private AuthProvider secondaryProvider;
    private AuthProvider tertiaryProvider;

    private final Set<String> primaryOverrides = new HashSet<>();
    private final Set<String> secondaryOverrides = new HashSet<>();
    private final Set<String> tertiaryOverrides = new HashSet<>();

    public HybridAuthProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty(PRIMARY_PROVIDER.getKey());
        JiveGlobals.migrateProperty(SECONDARY_PROVIDER.getKey());
        JiveGlobals.migrateProperty(TERTIARY_PROVIDER.getKey());
        JiveGlobals.migrateProperty("hybridAuthProvider.primaryProvider.overrideList");
        JiveGlobals.migrateProperty("hybridAuthProvider.secondaryProvider.overrideList");
        JiveGlobals.migrateProperty("hybridAuthProvider.tertiaryProvider.overrideList");

        // Load primary, secondary, and tertiary auth providers.
        final Class primaryClass = PRIMARY_PROVIDER.getValue();
        if (primaryClass == null) {
            Log.error("A primary AuthProvider must be specified. Authentication will be disabled.");
            return;
        }
        try {
            primaryProvider = (AuthProvider)primaryClass.newInstance();
            Log.debug("Primary auth provider: " + primaryClass.getName());
        }
        catch (Exception e) {
            Log.error("Unable to load primary auth provider: " + primaryClass.getName() +
                    ". Authentication will be disabled.", e);
            return;
        }

        final Class secondaryClass = SECONDARY_PROVIDER.getValue();
        if (secondaryClass != null) {
            try {
                secondaryProvider = (AuthProvider)secondaryClass.newInstance();
                Log.debug("Secondary auth provider: " + secondaryClass.getName());
            }
            catch (Exception e) {
                Log.error("Unable to load secondary auth provider: " + secondaryClass.getName(), e);
            }
        }

        final Class tertiaryClass = TERTIARY_PROVIDER.getValue();
        if (tertiaryClass != null) {
            try {
                tertiaryProvider = (AuthProvider)tertiaryClass.newInstance();
                Log.debug("Tertiary auth provider: " + tertiaryClass.getName());
            }
            catch (Exception e) {
                Log.error("Unable to load tertiary auth provider: " + tertiaryClass.getName(), e);
            }
        }

        // Now, load any overrides.
        String overrideList = JiveGlobals.getProperty(
                "hybridAuthProvider.primaryProvider.overrideList", "");
        for (String user: overrideList.split(",")) {
            primaryOverrides.add(user.trim().toLowerCase());
        }

        if (secondaryProvider != null) {
            overrideList = JiveGlobals.getProperty(
                    "hybridAuthProvider.secondaryProvider.overrideList", "");
            for (String user: overrideList.split(",")) {
                secondaryOverrides.add(user.trim().toLowerCase());
            }
        }

        if (tertiaryProvider != null) {
            overrideList = JiveGlobals.getProperty(
                    "hybridAuthProvider.tertiaryProvider.overrideList", "");
            for (String user: overrideList.split(",")) {
                tertiaryOverrides.add(user.trim().toLowerCase());
            }
        }
    }

    @Override
    Collection<AuthProvider> getAuthProviders() {
        final List<AuthProvider> result = new ArrayList<>();
        if (primaryProvider != null) {
            result.add(primaryProvider);
        }
        if (secondaryProvider != null) {
            result.add(secondaryProvider);
        }
        if (tertiaryProvider != null) {
            result.add(tertiaryProvider);
        }

        return result;
    }

    /**
     * Returns an auth provider for the provided username, but only if the username is in the 'override' list for that
     * provider.
     *
     * When this method returns null, all providers should be attempted. When this method returns a non-null value, then
     * only the returned provider is to be used for the provided user.
     *
     * @param username A user identifier (cannot be null or empty).
     * @return an auth provider that MUST be used for this user.
     */
    @Override
    AuthProvider getAuthProvider(String username)
    {
        if (primaryOverrides.contains(username.toLowerCase())) {
            return primaryProvider;
        }
        if (secondaryOverrides.contains(username.toLowerCase())) {
            return secondaryProvider;
        }
        if (tertiaryOverrides.contains(username.toLowerCase())) {
            return tertiaryProvider;
        }
        return null;
    }

    boolean hasOverride(String username) {
        return getAuthProvider(username) != null;
    }

    @Override
    public void authenticate(String username, String password) throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
        // Check overrides first.
        try {
            super.authenticate(username, password);
            return;
        } catch (UnauthorizedException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                provider.authenticate(username, password);
                return;
            } catch (UnauthorizedException e) {
                Log.trace("Could not authenticate user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnauthorizedException();
    }

    @Override
    public String getPassword(String username)
            throws UserNotFoundException, UnsupportedOperationException
    {
        // Check overrides first.
        try {
            return super.getPassword(username);
        } catch (UserNotFoundException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                if (provider.supportsPasswordRetrieval()) {
                    return provider.getPassword(username);
                }
            } catch (UserNotFoundException | UnsupportedOperationException e) {
                Log.trace("Could find user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPassword(String username, String password)
            throws UserNotFoundException, UnsupportedOperationException
    {
        // Check overrides first.
        try {
            super.setPassword(username, password);
            return;
        } catch (UserNotFoundException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                provider.setPassword(username, password);
            } catch (UserNotFoundException | UnsupportedOperationException e) {
                Log.trace("Could set password for user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        // Check overrides first.
        try {
            return super.getSalt(username);
        } catch (UserNotFoundException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                provider.getSalt(username);
            } catch (UserNotFoundException | UnsupportedOperationException e) {
                Log.trace("Could get salt for user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        // Check overrides first.
        try {
            return super.getIterations(username);
        } catch (UserNotFoundException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                provider.getIterations(username);
            } catch (UserNotFoundException | UnsupportedOperationException e) {
                Log.trace("Could get iterations for user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        // Check overrides first.
        try {
            return super.getServerKey(username);
        } catch (UserNotFoundException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                provider.getServerKey(username);
            } catch (UserNotFoundException | UnsupportedOperationException e) {
                Log.trace("Could get serverkey for user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException
    {
        // Check overrides first.
        try {
            return super.getStoredKey(username);
        } catch (UserNotFoundException e) {
            if (hasOverride(username)) {
                // An override was used. Must not try other providers.
                throw e;
            }
        }

        // When there's no override, try all providers in order.
        for (final AuthProvider provider: getAuthProviders()) {
            try {
                provider.getStoredKey(username);
            } catch (UserNotFoundException | UnsupportedOperationException e) {
                Log.trace("Could get storedkey for user {} with auth provider {}. Will try remaining providers (if any)", username, provider.getClass().getName(), e);
            }
        }
        throw new UnsupportedOperationException();
    }

    boolean isProvider(final Class<? extends AuthProvider> clazz) {
        return (primaryProvider != null && clazz.isAssignableFrom(primaryProvider.getClass()))
            || (secondaryProvider != null && clazz.isAssignableFrom(secondaryProvider.getClass()))
            ||(tertiaryProvider != null && clazz.isAssignableFrom(tertiaryProvider.getClass()));
    }
}
