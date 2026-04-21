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
package org.jivesoftware.util.channelbinding;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLEngine;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChannelBindingProviderManager}.
 */
class ChannelBindingProviderManagerTest
{
    private ChannelBindingProviderManager manager;

    @BeforeEach
    void setUp()
    {
        manager = new ChannelBindingProviderManager();
    }

    /**
     * Should return the binding from the first provider that supplies it.
     */
    @Test
    void returnsBindingFromRegisteredProvider()
    {
        // Setup test fixture
        final ChannelBindingProvider provider = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        final byte[] expected = new byte[] {1, 2, 3};
        when(provider.getType()).thenReturn("tls-exporter");
        when(provider.getChannelBinding(engine)).thenReturn(Optional.of(expected));
        manager.addProvider(provider);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertTrue(result.isPresent(), "Expected binding to be present when provider returns a value");
        assertArrayEquals(expected, result.get(), "Returned binding does not match expected value");
    }

    /**
     * Should return empty when there are no providers.
     */
    @Test
    void returnsEmptyWhenNoProviders()
    {
        // Setup test fixture
        final SSLEngine engine = mock(SSLEngine.class);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertFalse(result.isPresent(), "Expected empty result when no providers are registered");
    }

    /**
     * Should return empty when the provider returns empty.
     */
    @Test
    void returnsEmptyWhenProviderReturnsEmpty()
    {
        // Setup test fixture
        final ChannelBindingProvider provider = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        when(provider.getType()).thenReturn("tls-exporter");
        when(provider.getChannelBinding(engine)).thenReturn(Optional.empty());
        manager.addProvider(provider);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertFalse(result.isPresent(), "Expected empty result when provider returns empty");
    }

    /**
     * Should return empty when the provider throws an exception.
     */
    @Test
    void returnsEmptyWhenProviderThrows()
    {
        // Setup test fixture
        final ChannelBindingProvider provider = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        when(provider.getType()).thenReturn("tls-exporter");
        when(provider.getChannelBinding(engine)).thenThrow(new RuntimeException("fail"));
        manager.addProvider(provider);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertFalse(result.isPresent(), "Expected empty result when provider throws");
    }

    /**
     * Should remove a provider and no longer return its binding.
     */
    @Test
    void removeProviderRemovesIt()
    {
        // Setup test fixture
        final ChannelBindingProvider provider = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        when(provider.getType()).thenReturn("tls-exporter");
        when(provider.getChannelBinding(engine)).thenReturn(Optional.of(new byte[] {1}));
        manager.addProvider(provider);
        assertTrue(manager.removeProvider(provider), "Provider should be removed successfully");

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertFalse(result.isPresent(), "Expected empty result after provider is removed");
    }

    /**
     * Should return false when removing a provider that was never registered.
     */
    @Test
    void removeProviderReturnsFalseIfNotPresent()
    {
        // Setup test fixture
        final ChannelBindingProvider provider = mock(ChannelBindingProvider.class);
        when(provider.getType()).thenReturn("tls-exporter");

        // Execute system under test & verify result
        assertFalse(manager.removeProvider(provider), "Should return false when provider was not registered");
    }

    /**
     * Should use the provider added to the head before those added to the tail.
     */
    @Test
    void addProviderToHeadOverridesOrder()
    {
        // Setup test fixture
        final ChannelBindingProvider tail = mock(ChannelBindingProvider.class);
        final ChannelBindingProvider head = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        when(tail.getType()).thenReturn("tls-exporter");
        when(head.getType()).thenReturn("tls-exporter");
        when(tail.getChannelBinding(engine)).thenReturn(Optional.empty());
        final byte[] expected = new byte[] {9, 9, 9};
        when(head.getChannelBinding(engine)).thenReturn(Optional.of(expected));
        manager.addProvider(tail);
        manager.addProviderToHead(head);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertTrue(result.isPresent(), "Expected binding to be present from head provider");
        assertArrayEquals(expected, result.get(), "Returned binding does not match expected value from head provider");
    }

    /**
     * Should try multiple providers for the same prefix in order and return the first non-empty result.
     */
    @Test
    void multipleProvidersForSamePrefixAreTriedInOrder_emptyFirst()
    {
        // Setup test fixture
        final ChannelBindingProvider first = mock(ChannelBindingProvider.class);
        final ChannelBindingProvider second = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        when(first.getType()).thenReturn("tls-exporter");
        when(second.getType()).thenReturn("tls-exporter");
        when(first.getChannelBinding(engine)).thenReturn(Optional.empty());
        final byte[] expected = new byte[] {7, 7, 7};
        when(second.getChannelBinding(engine)).thenReturn(Optional.of(expected));
        manager.addProvider(first);
        manager.addProvider(second);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertTrue(result.isPresent(), "Expected binding to be present from second provider");
        assertArrayEquals(expected, result.get(), "Returned binding does not match expected value from second provider");
    }

    /**
     * Should try multiple providers for the same prefix in order and return the first non-empty result, skipping exceptions.
     */
    @Test
    void multipleProvidersForSamePrefixAreTriedInOrder_exceptionFirst()
    {
        // Setup test fixture
        final ChannelBindingProvider first = mock(ChannelBindingProvider.class);
        final ChannelBindingProvider second = mock(ChannelBindingProvider.class);
        final SSLEngine engine = mock(SSLEngine.class);
        when(first.getType()).thenReturn("tls-exporter");
        when(second.getType()).thenReturn("tls-exporter");
        when(first.getChannelBinding(engine)).thenThrow(new RuntimeException("fail"));
        final byte[] expected = new byte[] {8, 8, 8};
        when(second.getChannelBinding(engine)).thenReturn(Optional.of(expected));
        manager.addProvider(first);
        manager.addProvider(second);

        // Execute system under test
        final Optional<byte[]> result = manager.getChannelBinding("tls-exporter", engine);

        // Verify result
        assertTrue(result.isPresent(), "Expected binding to be present from second provider after exception in first");
        assertArrayEquals(expected, result.get(), "Returned binding does not match expected value from second provider");
    }

    /**
     * Should reflect registered providers in supportsChannelBinding.
     */
    @Test
    void supportsChannelBindingReflectsRegisteredProviders()
    {
        // Setup test fixture
        final ChannelBindingProvider provider = mock(ChannelBindingProvider.class);
        when(provider.getType()).thenReturn("tls-exporter");
        assertFalse(manager.supportsChannelBinding("tls-exporter"), "Should not support prefix before registration");
        manager.addProvider(provider);
        assertTrue(manager.supportsChannelBinding("tls-exporter"), "Should support prefix after registration");
        manager.removeProvider(provider);
        assertFalse(manager.supportsChannelBinding("tls-exporter"), "Should not support prefix after removal");
    }

    /**
     * Should reflect registered providers in getSupportedChannelBindingTypes.
     */
    @Test
    void getSupportedChannelBindingTypesReflectsRegisteredProviders()
    {
        // Setup test fixture
        final ChannelBindingProvider exporter = mock(ChannelBindingProvider.class);
        final ChannelBindingProvider serverEndPoint = mock(ChannelBindingProvider.class);
        when(exporter.getType()).thenReturn("tls-exporter");
        when(serverEndPoint.getType()).thenReturn("tls-server-end-point");
        assertTrue(manager.getSupportedChannelBindingTypes().isEmpty(), "Should be empty before registration");
        manager.addProvider(exporter);
        assertTrue(manager.getSupportedChannelBindingTypes().contains("tls-exporter"), "Should contain tls-exporter after registration");
        manager.addProvider(serverEndPoint);
        assertTrue(manager.getSupportedChannelBindingTypes().contains("tls-server-end-point"), "Should contain tls-server-end-point after registration");
        manager.removeProvider(exporter);
        assertFalse(manager.getSupportedChannelBindingTypes().contains("tls-exporter"), "Should not contain tls-exporter after removal");
        assertTrue(manager.getSupportedChannelBindingTypes().contains("tls-server-end-point"), "Should still contain tls-server-end-point");
    }
}
