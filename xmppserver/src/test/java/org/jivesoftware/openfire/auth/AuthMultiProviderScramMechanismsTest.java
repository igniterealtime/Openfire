/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha512SaslServer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Verifies how {@link AuthMultiProvider} reports SCRAM mechanism names on behalf of its backing providers.
 *
 * This provider maps each user to exactly one backing provider, so an identified user is answered by that provider.
 * A user that maps to no provider must not be answered with an empty set: that would reveal that no provider serves
 * the user, which is the distinction that the contract of these methods forbids.
 */
public class AuthMultiProviderScramMechanismsTest
{
    private static final String USERNAME = "juliet";

    /**
     * Verifies that the mechanisms reported for a user are those of the provider that the user maps to.
     */
    @Test
    void getScramMechanisms_delegatesToMappedProvider()
    {
        // Setup test fixture.
        final AuthProvider mapped = providerReporting(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), Set.of(ScramSha1SaslServer.MECHANISM_NAME));
        final AuthProvider other = providerReporting(Set.of(ScramSha512SaslServer.MECHANISM_NAME), Set.of(ScramSha512SaslServer.MECHANISM_NAME));
        final AuthMultiProvider provider = new TestAuthMultiProvider(List.of(mapped, other), Map.of(USERNAME, mapped));

        // Execute system under test.
        final Set<String> result = provider.getScramMechanisms(USERNAME);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), result, "The mechanisms reported for a user must be those of the provider that the user maps to.");
    }

    /**
     * Verifies that a user that maps to no provider is answered with the fallback, rather than with an empty set. An
     * empty set would let a peer determine that no provider serves the claimed user.
     */
    @Test
    void getScramMechanisms_fallsBackForUnmappedUser()
    {
        // Setup test fixture.
        final AuthProvider backing = providerReporting(Set.of(ScramSha256SaslServer.MECHANISM_NAME), Set.of(ScramSha1SaslServer.MECHANISM_NAME));
        final AuthMultiProvider provider = new TestAuthMultiProvider(List.of(backing), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getScramMechanisms(USERNAME);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user that maps to no provider must be answered with the fallback, so that the response does not reveal that no provider serves the user.");
    }

    /**
     * Verifies that the fallback is the intersection of what the backing providers assume, as any of them may end up
     * serving the user that is going to authenticate (every candidate must be able to service it).
     */
    @Test
    void getFallbackScramMechanisms_intersectsBackingProviders()
    {
        // Setup test fixture.
        final AuthProvider first = providerReporting(Set.of(), Set.of(ScramSha1SaslServer.MECHANISM_NAME));
        final AuthProvider second = providerReporting(Set.of(), Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha512SaslServer.MECHANISM_NAME));
        final AuthMultiProvider provider = new TestAuthMultiProvider(List.of(first, second), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getFallbackScramMechanisms();

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "The fallback must be the intersection of what each backing provider assumes, as any of them may serve the user.");
    }

    /**
     * Verifies that a backing provider that does not support SCRAM does not reduce what the other providers can
     * guarantee. Such a provider is never going to serve a user through a SCRAM mechanism, so it must not veto one.
     */
    @Test
    void getFallbackScramMechanisms_ignoresProviderWithoutScramSupport()
    {
        // Setup test fixture.
        final AuthProvider scramCapable = providerReporting(Set.of(), Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME));
        final AuthProvider withoutScram = providerWithoutScramSupport();
        final AuthMultiProvider provider = new TestAuthMultiProvider(List.of(scramCapable, withoutScram), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getFallbackScramMechanisms();

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), result, "A backing provider that does not support SCRAM must not reduce the mechanisms that the other providers guarantee.");
    }

    /**
     * Verifies that no mechanisms are reported when none of the backing providers supports SCRAM. This is the
     * configuration that a deployment without any SCRAM-capable provider yields, so it must not be answered with the
     * mechanisms that a provider would assume on its own.
     */
    @Test
    void getFallbackScramMechanisms_isEmptyWhenNoProviderSupportsScram()
    {
        // Setup test fixture.
        final AuthMultiProvider provider = new TestAuthMultiProvider(List.of(providerWithoutScramSupport(), providerWithoutScramSupport()), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getFallbackScramMechanisms();

        // Verify result.
        assertTrue(result.isEmpty(), "When no backing provider supports SCRAM, no mechanism can be assumed to be usable.");
    }

    /**
     * Verifies that a provider without any backing providers reports no fallback mechanisms.
     */
    @Test
    void getFallbackScramMechanisms_isEmptyWithoutBackingProviders()
    {
        // Setup test fixture.
        final AuthMultiProvider provider = new TestAuthMultiProvider(List.of(), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getFallbackScramMechanisms();

        // Verify result.
        assertTrue(result.isEmpty(), "Without any backing provider, no mechanism can be assumed to be usable.");
    }

    /**
     * Returns a backing provider that supports SCRAM and reports the provided mechanism names.
     *
     * @param mechanisms the mechanisms to report for any user.
     * @param fallback the mechanisms to report when a user cannot be identified.
     * @return a backing provider.
     */
    private static AuthProvider providerReporting(final Set<String> mechanisms, final Set<String> fallback)
    {
        final AuthProvider provider = Mockito.mock(AuthProvider.class);
        when(provider.isScramSupported()).thenReturn(true);
        when(provider.getScramMechanisms(Mockito.anyString())).thenReturn(mechanisms);
        when(provider.getFallbackScramMechanisms()).thenReturn(fallback);
        return provider;
    }

    /**
     * Returns a backing provider that does not support SCRAM.
     *
     * Its fallback mechanisms are deliberately left unstubbed: a provider that does not support SCRAM must be filtered
     * out before its answer is considered at all, so a test that depends on that answer would be testing the wrong
     * thing.
     *
     * @return a backing provider.
     */
    private static AuthProvider providerWithoutScramSupport()
    {
        final AuthProvider provider = Mockito.mock(AuthProvider.class);
        when(provider.isScramSupported()).thenReturn(false);
        return provider;
    }

    /**
     * An {@link AuthMultiProvider} whose backing providers and user-to-provider mapping are supplied directly, so that
     * the delegation logic can be exercised without the configuration that a concrete implementation requires.
     */
    private static class TestAuthMultiProvider extends AuthMultiProvider
    {
        private final Collection<AuthProvider> providers;

        private final Map<String, AuthProvider> mapping;

        TestAuthMultiProvider(final Collection<AuthProvider> providers, final Map<String, AuthProvider> mapping)
        {
            this.providers = providers;
            this.mapping = mapping;
        }

        @Override
        Collection<AuthProvider> getAuthProviders()
        {
            return providers;
        }

        @Override
        AuthProvider getAuthProvider(final String username)
        {
            return mapping.get(username);
        }
    }
}
