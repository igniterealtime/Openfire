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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link SaslMechanismEligibility}, which narrows the mechanisms that this deployment supports down to those
 * that one particular session may be offered.
 *
 * Two narrowings are covered here: which mechanisms a session is eligible for, given its type, its encryption state
 * and the certificate its peer presented; and which XEP-0440 channel-binding types accompany them.
 *
 * The per-user SCRAM lookup that feeds the first of these is covered separately, in
 * {@link SaslMechanismEligibilityScramTest}.
 */
public class SaslMechanismEligibilityTest
{
    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    public void setup()
    {
        Fixtures.clearExistingProperties();
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        XMPPServer.setInstance(Fixtures.mockXMPPServer());
        SaslMechanismCatalog.setEnabledMechanisms(Arrays.asList("PLAIN", "EXTERNAL"));
    }

    // -------------------------------------------------------------------------
    // getAvailableMechanismsForSession
    // -------------------------------------------------------------------------

    /**
     * Verifies that getAvailableMechanismsForSession returns mechanisms for a ClientSession.
     */
    @Test
    public void shouldReturnAvailableMechanismsForClientSession()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalClientSession session = new LocalClientSession(Fixtures.XMPP_DOMAIN, connection, streamID, Locale.ENGLISH);

        // Execute system under test.
        final Set<String> mechanisms = SaslMechanismEligibility.getAvailableMechanismsForSession(session);

        // Verify result.
        assertTrue(mechanisms.contains("PLAIN"), "Expected PLAIN to be available for an unencrypted client session.");
        assertFalse(mechanisms.contains("EXTERNAL"), "Expected EXTERNAL not to be available for an unencrypted client session without a trusted cert.");
    }

    /**
     * Verifies that getAvailableMechanismsForSession returns mechanisms for an incoming server session.
     */
    @Test
    public void shouldReturnAvailableMechanismsForIncomingServerSession()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(false);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> mechanisms = SaslMechanismEligibility.getAvailableMechanismsForSession(session);

        // Verify result.
        assertTrue(mechanisms.isEmpty(), "Expected no mechanisms to be available for an unencrypted server session without a trusted cert.");
    }

    /**
     * Verifies that getAvailableMechanismsForSession does not advertise EXTERNAL for an incoming server session
     * when EXTERNAL is disabled in the global SASL mechanisms configuration.
     *
     * Regression test for: Stream features advertise EXTERNAL even when disabled in sasl.mechs
     */
    @Test
    public void shouldNotAdvertiseExternalForIncomingServerSessionWhenDisabledGlobally()
    {
        // Setup test fixture: Disable EXTERNAL in the global mechanisms configuration
        SaslMechanismCatalog.setEnabledMechanisms(Collections.singletonList("PLAIN")); // Only PLAIN, no EXTERNAL

        final Connection connection = mock(Connection.class);
        when(connection.isEncrypted()).thenReturn(true);

        final StreamID streamID = new BasicStreamIDFactory().createStreamID();
        final LocalIncomingServerSession session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, streamID, "remote.example.org");

        // Execute system under test.
        final Set<String> mechanisms = SaslMechanismEligibility.getAvailableMechanismsForSession(session);

        // Verify result.
        assertFalse(mechanisms.contains("EXTERNAL"), "Expected EXTERNAL not to be advertised when disabled in global mechanisms configuration, even for encrypted sessions.");
    }

    /**
     * Verifies that getAvailableMechanismsForSession handles unknown session types gracefully.
     */
    @Test
    public void shouldReturnEmptySetForUnknownSessionType()
    {
        // Setup test fixture.
        final LocalSession unknownSession = mock(LocalSession.class);

        // Execute system under test.
        final Set<String> mechanisms = SaslMechanismEligibility.getAvailableMechanismsForSession(unknownSession);

        // Verify result.
        assertTrue(mechanisms.isEmpty(), "Expected empty set for an unknown session type.");
    }

    // -------------------------------------------------------------------------
    // getAdvertisableChannelBindingTypes
    // -------------------------------------------------------------------------

    /**
     * Verifies that channel-binding types are not advertised when no channel-binding-capable mechanism is offered,
     * since a peer that cannot use channel binding has no use for the type list.
     *
     * The connection is deliberately stubbed to support types: this asserts that the mechanism list alone is
     * enough to suppress the announcement, rather than the outcome coinciding with a connection that happens to
     * support nothing.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0440.html">XEP-0440: SASL Channel-Binding Type Capability</a>
     */
    @Test
    public void getAdvertisableChannelBindingTypes_noPlusMechanism_returnsEmpty()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-exporter"));
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(connection);

        // Execute system under test.
        final Set<String> result = SaslMechanismEligibility.getAdvertisableChannelBindingTypes(session, Set.of("PLAIN", "EXTERNAL"));

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no channel-binding types when no -PLUS mechanism is offered.");
    }

    /**
     * Verifies that no channel-binding types are advertised when a -PLUS mechanism is offered but the session's
     * connection cannot supply any type in its current state (for example, because it is not encrypted).
     */
    @Test
    public void getAdvertisableChannelBindingTypes_plusMechanismButConnectionSupportsNone_returnsEmpty()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of());
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(connection);

        // Execute system under test.
        final Set<String> result = SaslMechanismEligibility.getAdvertisableChannelBindingTypes(session, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no channel-binding types when the connection supports none.");
    }

    /**
     * Verifies that the channel-binding types that the session's connection supports are advertised when a -PLUS
     * mechanism is offered.
     *
     * The types come from the connection rather than from the globally registered providers, because
     * Connection#getSupportedChannelBindingTypes is defined to reflect the connection's current state. Advertising
     * a type the connection cannot supply would leave a peer that selects it to be rejected later, when the
     * channel-binding data cannot be produced.
     */
    @Test
    public void getAdvertisableChannelBindingTypes_plusMechanismWithConnectionSupport_returnsConnectionTypes()
    {
        // Setup test fixture.
        final Connection connection = mock(Connection.class);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Set.of("tls-server-end-point", "tls-exporter"));
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(connection);

        // Execute system under test.
        final Set<String> result = SaslMechanismEligibility.getAdvertisableChannelBindingTypes(session, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Verify result.
        assertEquals(Set.of("tls-server-end-point", "tls-exporter"), result, "Expected the connection's supported channel-binding types to be advertised alongside a -PLUS mechanism.");
    }

    /**
     * Verifies that no channel-binding types are advertised for a session that has no connection, rather than the
     * lookup failing. A stream-management session that is detached has no connection, and stream features can be
     * regenerated after it resumes.
     */
    @Test
    public void getAdvertisableChannelBindingTypes_noConnection_returnsEmpty()
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalSession.class);
        when(session.getConnection()).thenReturn(null);

        // Execute system under test.
        final Set<String> result = SaslMechanismEligibility.getAdvertisableChannelBindingTypes(session, Set.of("SCRAM-SHA-1", "SCRAM-SHA-1-PLUS"));

        // Verify result.
        assertTrue(result.isEmpty(), "Expected no channel-binding types for a session that has no connection.");
    }
}
