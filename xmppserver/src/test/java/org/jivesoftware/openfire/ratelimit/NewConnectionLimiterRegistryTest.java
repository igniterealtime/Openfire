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
package org.jivesoftware.openfire.ratelimit;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.util.TokenBucketRateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests that verify the implementation of {@link NewConnectionLimiterRegistry}.
 *
 * Tests verify correct limiter retrieval for C2S and S2S connection types, correct behavior for unsupported types,
 * and dynamic updates of the limiter when system properties change.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@ExtendWith(MockitoExtension.class)
public class NewConnectionLimiterRegistryTest {

    @Mock
    private XMPPServer xmppServer;
    @Mock
    private PluginManager pluginManager;

    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @SuppressWarnings({"deprecation"})
    @BeforeEach
    public void setUp() {
        XMPPServer.setInstance(xmppServer);

        // Setup test fixture.
        NewConnectionLimiterRegistry.C2S_PERMITS_PER_SECOND.setValue(20);
        NewConnectionLimiterRegistry.C2S_MAX_BURST.setValue(50);
        NewConnectionLimiterRegistry.S2S_PERMITS_PER_SECOND.setValue(20);
        NewConnectionLimiterRegistry.S2S_MAX_BURST.setValue(50);
        // Force limiters to reload
        NewConnectionLimiterRegistry.getLimiter(ConnectionType.SOCKET_C2S);
        NewConnectionLimiterRegistry.getLimiter(ConnectionType.SOCKET_S2S);
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that client-to-server connection types share the same limiter.
     */
    @Test
    void testGetLimiter_ClientToServer()
    {
        // Execute system under test.
        final TokenBucketRateLimiter limiter1 = NewConnectionLimiterRegistry.getLimiter(ConnectionType.SOCKET_C2S);
        final TokenBucketRateLimiter limiter2 = NewConnectionLimiterRegistry.getLimiter(ConnectionType.BOSH_C2S);

        // Verify result.
        assertNotNull(limiter1);
        assertNotNull(limiter2);
        assertSame(limiter1, limiter2, "All client-to-server types should share the same limiter");
    }

    /**
     * Verifies that the server-to-server connection type returns a limiter.
     */
    @Test
    void testGetLimiter_ServerToServer()
    {
        // Setup test fixture.
        final ConnectionType type = ConnectionType.SOCKET_S2S;

        // Execute system under test.
        final TokenBucketRateLimiter limiter = NewConnectionLimiterRegistry.getLimiter(type);

        // Verify result.
        assertNotNull(limiter);
    }

    /**
     * Verifies that unsupported connection types return separate unlimited limiters.
     */
    @Test
    void testGetLimiter_UnsupportedConnectionTypes()
    {
        // Setup test fixture.
        final Set<ConnectionType> unsupportedTypes = Stream.of(ConnectionType.WEBADMIN, ConnectionType.COMPONENT, ConnectionType.CONNECTION_MANAGER)
            .collect(Collectors.toSet());

        // Execute system under test.
        final TokenBucketRateLimiter limiter1 = NewConnectionLimiterRegistry.getLimiter(ConnectionType.WEBADMIN);
        final TokenBucketRateLimiter limiter2 = NewConnectionLimiterRegistry.getLimiter(ConnectionType.COMPONENT);
        final TokenBucketRateLimiter limiter3 = NewConnectionLimiterRegistry.getLimiter(ConnectionType.CONNECTION_MANAGER);
        final TokenBucketRateLimiter limiter1Again = NewConnectionLimiterRegistry.getLimiter(ConnectionType.WEBADMIN);

        // Verify result.
        assertNotNull(limiter1);
        assertNotNull(limiter2);
        assertNotNull(limiter3);
        // Same type should return same instance
        assertSame(limiter1, limiter1Again, "Repeated calls for same unsupported type should return the same limiter instance");
        // Different types should return different instances
        assertNotSame(limiter1, limiter2);
        assertNotSame(limiter2, limiter3);
        assertNotSame(limiter1, limiter3);
    }

    /**
     * Verifies that dynamically updating client-to-server properties replaces the limiter.
     */
    @Test
    void testDynamicUpdate_C2S()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter originalLimiter = NewConnectionLimiterRegistry.getLimiter(ConnectionType.SOCKET_C2S);
        final int newPermitsPerSecond = 99;
        final int newMaxBurst = 123;

        // Execute system under test.
        NewConnectionLimiterRegistry.C2S_PERMITS_PER_SECOND.setValue(newPermitsPerSecond);
        NewConnectionLimiterRegistry.C2S_MAX_BURST.setValue(newMaxBurst);
        final TokenBucketRateLimiter updatedLimiter = NewConnectionLimiterRegistry.getLimiter(ConnectionType.BOSH_C2S);

        // Verify result.
        assertNotSame(originalLimiter, updatedLimiter, "Limiter should be replaced after property update");
        assertEquals(newPermitsPerSecond, updatedLimiter.getPermitsPerSecond(), "Updated permits per second should be applied");
        assertEquals(newMaxBurst, updatedLimiter.getMaxBurst(), "Updated max burst should be applied");
    }

    /**
     * Verifies that dynamically updating server-to-server properties replaces the limiter.
     */
    @Test
    void testDynamicUpdate_S2S()
    {
        // Setup test fixture.
        final TokenBucketRateLimiter originalLimiter = NewConnectionLimiterRegistry.getLimiter(ConnectionType.SOCKET_S2S);
        final int newPermitsPerSecond = 77;
        final int newMaxBurst = 88;

        // Execute system under test.
        NewConnectionLimiterRegistry.S2S_PERMITS_PER_SECOND.setValue(newPermitsPerSecond);
        NewConnectionLimiterRegistry.S2S_MAX_BURST.setValue(newMaxBurst);
        final TokenBucketRateLimiter updatedLimiter = NewConnectionLimiterRegistry.getLimiter(ConnectionType.SOCKET_S2S);

        // Verify result.
        assertNotSame(originalLimiter, updatedLimiter, "Limiter should be replaced after property update");
        assertEquals(newPermitsPerSecond, updatedLimiter.getPermitsPerSecond(), "Updated permits per second should be applied");
        assertEquals(newMaxBurst, updatedLimiter.getMaxBurst(), "Updated max burst should be applied");
    }
}
