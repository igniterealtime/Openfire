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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies how {@link HybridAuthProvider} reports SCRAM mechanism names on behalf of its backing providers.
 *
 * Unlike a mapped provider, this provider attempts authentication with each of its backing providers in turn, so a
 * mechanism that any of them can service is usable for an identified user. The set reported for such a user is
 * therefore the union of what the providers report.
 *
 * A user on an override list is the exception: authentication for such a user is only attempted with the provider it
 * is overridden to, so only that provider is consulted here as well.
 *
 * Note that the fallback, which applies when no provider reports anything, is deliberately <em>not</em> a union: it is
 * inherited from {@link AuthMultiProvider}, where it is the intersection of what the providers guarantee. A mechanism
 * that is reported for a user that cannot be identified at all must hold no matter which provider ends up serving it.
 */
public class HybridAuthProviderScramMechanismsTest
{
    private static final String USERNAME = "juliet";

    private static final String OVERRIDDEN_USERNAME = "romeo";

    /**
     * Verifies that the mechanisms reported for a user are the union of what each backing provider reports, as
     * authentication is attempted with each of them in turn.
     */
    @Test
    void getScramMechanisms_unionsBackingProviders()
    {
        // Setup test fixture.
        final AuthProvider first = providerReporting(Set.of(ScramSha1SaslServer.MECHANISM_NAME));
        final AuthProvider second = providerReporting(Set.of(ScramSha256SaslServer.MECHANISM_NAME));
        final HybridAuthProvider provider = new TestHybridAuthProvider(List.of(first, second), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getScramMechanisms(USERNAME);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), result, "A mechanism that any backing provider can service is usable, so the union of their answers must be reported.");
    }

    /**
     * Verifies that only the overridden provider is consulted for a user that is on an override list. Authentication
     * for such a user is not attempted with any other provider, so no other provider may influence what is offered.
     */
    @Test
    void getScramMechanisms_consultsOnlyOverriddenProvider()
    {
        // Setup test fixture.
        final AuthProvider overridden = providerReporting(Set.of(ScramSha512SaslServer.MECHANISM_NAME));
        final AuthProvider other = providerReporting(Set.of(ScramSha1SaslServer.MECHANISM_NAME));
        final HybridAuthProvider provider = new TestHybridAuthProvider(List.of(overridden, other), Map.of(OVERRIDDEN_USERNAME, overridden));

        // Execute system under test.
        final Set<String> result = provider.getScramMechanisms(OVERRIDDEN_USERNAME);

        // Verify result.
        assertEquals(Set.of(ScramSha512SaslServer.MECHANISM_NAME), result, "Only the provider that the user is overridden to may determine the reported mechanisms.");
        verify(other, never()).getScramMechanisms(Mockito.anyString());
    }

    /**
     * Verifies that a user for which no backing provider reports any mechanism is answered with the fallback, rather
     * than with an empty set.
     */
    @Test
    void getScramMechanisms_fallsBackWhenNoProviderReportsAnything()
    {
        // Setup test fixture.
        final AuthProvider backing = providerReportingFallback(Set.of(), Set.of(ScramSha1SaslServer.MECHANISM_NAME));
        final HybridAuthProvider provider = new TestHybridAuthProvider(List.of(backing), Map.of());

        // Execute system under test.
        final Set<String> result = provider.getScramMechanisms(USERNAME);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user for which no backing provider reports a mechanism must be answered with the fallback, not with an empty set.");
    }

    /**
     * Returns a backing provider that supports SCRAM, reports the provided mechanism names for any user, and assumes
     * no mechanism to be usable by a user that cannot be identified.
     *
     * @param mechanisms the mechanisms to report.
     * @return a backing provider.
     */
    private static AuthProvider providerReporting(final Set<String> mechanisms)
    {
        return providerReportingFallback(mechanisms, Set.of());
    }

    /**
     * Returns a backing provider that supports SCRAM and reports the provided mechanism names, both for an identified
     * user and for one that cannot be identified.
     *
     * @param mechanisms the mechanisms to report for any user.
     * @param fallback the mechanisms to report when a user cannot be identified.
     * @return a backing provider.
     */
    private static AuthProvider providerReportingFallback(final Set<String> mechanisms, final Set<String> fallback)
    {
        final AuthProvider provider = Mockito.mock(AuthProvider.class);
        when(provider.isScramSupported()).thenReturn(true);
        when(provider.getScramMechanisms(Mockito.anyString())).thenReturn(mechanisms);
        when(provider.getFallbackScramMechanisms()).thenReturn(fallback);
        return provider;
    }

    /**
     * A {@link HybridAuthProvider} whose backing providers and override mapping are supplied directly, so that the
     * aggregation logic can be exercised without the configuration that the class normally reads from properties.
     */
    private static class TestHybridAuthProvider extends HybridAuthProvider
    {
        private final Collection<AuthProvider> providers;

        private final Map<String, AuthProvider> overrides;

        TestHybridAuthProvider(final Collection<AuthProvider> providers, final Map<String, AuthProvider> overrides)
        {
            this.providers = providers;
            this.overrides = overrides;
        }

        @Override
        Collection<AuthProvider> getAuthProviders()
        {
            return providers;
        }

        @Override
        AuthProvider getAuthProvider(final String username)
        {
            return overrides.get(username);
        }
    }
}
