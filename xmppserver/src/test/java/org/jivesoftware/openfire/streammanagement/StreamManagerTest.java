/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.streammanagement;

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link StreamManager}.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerTest
{
    @Test
    public void testValidateClientAcknowledgement() throws Exception
    {
        // Setup test fixture.
        final long h = 0;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksSentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = 1L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksSentStanzaWithMoreInflight() throws Exception
    {
        // Setup test fixture.
        final long h = 10;
        final long oldH = 4;
        final Long lastUnackedX = 12L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksUnsentStanza() throws Exception
    {
        // Setup test fixture.
        final long h = 1;
        final long oldH = 0;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_clientAcksUnsentStanzaA() throws Exception
    {
        // Setup test fixture.
        final long h = 3;
        final long oldH = 1;
        final Long lastUnackedX = 2L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX - 1;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }


    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase_sent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX - 1;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase1() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase2() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = MAX;
        final long oldH = MAX-2;
        final Long lastUnackedX = MAX;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }
    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX-2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase3_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX - 2;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 0L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = 3L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }
    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase4_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 0;
        final long oldH = MAX;
        final Long lastUnackedX = null;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 3;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5a() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 4;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertTrue(result);
    }

    @Test
    public void testValidateClientAcknowledgement_rollover_edgecase5_unsent() throws Exception
    {
        // Setup test fixture.
        final long MAX = new BigInteger( "2" ).pow( 32 ).longValue() - 1;
        final long h = 5;
        final long oldH = MAX - 2;
        final Long lastUnackedX = 4L;

        // Execute system under test.
        final boolean result = StreamManager.validateClientAcknowledgement(h, oldH, lastUnackedX);

        // Verify results.
        assertFalse(result);
    }

    @Test
    public void testFeatureElementHasCorrectName() {
        // Execute system under test.
        final Element feature = StreamManager.featureElement();

        // Verify results.
        assertNotNull(feature);
        assertEquals("sm", feature.getName());
    }

    @Test
    public void testFeatureElementHasCorrectNamespace() {
        // Execute system under test.
        final Element feature = StreamManager.featureElement();

        // Verify results.
        assertNotNull(feature);
        assertEquals(StreamManager.NAMESPACE_V3, feature.getNamespaceURI());
    }

    @Test
    public void testFeatureElementIsDistinctOnEachCall() {
        // Execute system under test.
        final Element feature1 = StreamManager.featureElement();
        final Element feature2 = StreamManager.featureElement();

        // Verify results: each call returns a new element instance.
        assertNotSame(feature1, feature2);
    }

    /**
     * Verifies that a freshly constructed StreamManager does not have a pending SASL2 redelivery.
     */
    @Test
    public void testPendingSasl2RedeliveryIsFalseByDefault() {
        // Setup test fixture.
        final LocalClientSession mockSession = mock(LocalClientSession.class);
        final Connection mockConnection = mock(Connection.class);
        when(mockSession.getConnection()).thenReturn(mockConnection);
        final StreamManager streamManager = new StreamManager(mockSession);
        // Clear interactions caused by the constructor (e.g. getHostAddress()).
        clearInvocations(mockConnection);

        // Execute system under test: redeliverIfPendingSasl2 with no flag set should be a no-op.
        final JID serverAddress = new JID(null, "example.org", null, true);
        streamManager.redeliverIfPendingSasl2(serverAddress);

        // Verify result: no interaction with the connection (no stanzas delivered).
        verifyNoInteractions(mockConnection);
    }

    /**
     * Verifies that setting the pending SASL2 redelivery flag and then calling
     * redeliverIfPendingSasl2 clears the flag (i.e. a second call is a no-op).
     */
    @Test
    public void testRedeliverIfPendingSasl2ClearsFlagAfterFirstCall() {
        // Setup test fixture.
        final LocalClientSession mockSession = mock(LocalClientSession.class);
        final Connection mockConnection = mock(Connection.class);
        when(mockSession.getConnection()).thenReturn(mockConnection);
        final StreamManager streamManager = new StreamManager(mockSession);
        streamManager.setPendingSasl2Redelivery(true);

        final JID serverAddress = new JID(null, "example.org", null, true);

        // First call: flag is set, so redelivery runs (no unacked stanzas, but the flag is consumed).
        streamManager.redeliverIfPendingSasl2(serverAddress);

        // Second call: flag has been cleared, so this must be a no-op.
        // We verify by resetting the mock and confirming no further deliveries occur.
        clearInvocations(mockConnection);
        streamManager.redeliverIfPendingSasl2(serverAddress);
        verifyNoInteractions(mockConnection);
    }

    /**
     * Verifies that setPendingSasl2Redelivery(false) prevents redeliverIfPendingSasl2 from acting.
     */
    @Test
    public void testSetPendingSasl2RedeliveryFalsePreventsTrigger() {
        // Setup test fixture.
        final LocalClientSession mockSession = mock(LocalClientSession.class);
        final Connection mockConnection = mock(Connection.class);
        when(mockSession.getConnection()).thenReturn(mockConnection);
        final StreamManager streamManager = new StreamManager(mockSession);
        // Clear interactions caused by the constructor (e.g. getHostAddress()).
        clearInvocations(mockConnection);

        // Set then immediately clear the flag.
        streamManager.setPendingSasl2Redelivery(true);
        streamManager.setPendingSasl2Redelivery(false);

        // Execute system under test.
        final JID serverAddress = new JID(null, "example.org", null, true);
        streamManager.redeliverIfPendingSasl2(serverAddress);

        // Verify result: no interaction with the connection.
        verifyNoInteractions(mockConnection);
    }
}
