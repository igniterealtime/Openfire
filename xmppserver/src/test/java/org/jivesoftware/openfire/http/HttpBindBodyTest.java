/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpBindBody}.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0124.html">XEP-0124: Bidirectional-streams Over Synchronous HTTP (BOSH)</a>
 */
public class HttpBindBodyTest
{
    /**
     * Verifies that the 'ack' attribute is returned when present in a request body.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#ack">XEP-0124 §9 Acknowledgements</a>
     */
    @Test
    public void testGetAckWhenPresent() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1249243566' sid='SomeSID' ack='1249243564' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertNotNull(body.getAck());
        assertEquals(1249243564L, body.getAck());
    }

    /**
     * Verifies that null is returned when no 'ack' attribute is present in a request body.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#ack">XEP-0124 §9 Acknowledgements</a>
     */
    @Test
    public void testGetAckWhenAbsent() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1249243566' sid='SomeSID' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertNull(body.getAck());
    }

    /**
     * Verifies that null is returned when the 'ack' attribute has an invalid (non-numeric) value.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#ack">XEP-0124 §9 Acknowledgements</a>
     */
    @Test
    public void testGetAckWhenInvalid() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1249243566' sid='SomeSID' ack='notanumber' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertNull(body.getAck());
    }

    /**
     * Verifies that the 'ack' attribute value of "1" in a session creation request is correctly parsed.
     * This is the case when a client indicates it will use acknowledgements throughout the session.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#ack">XEP-0124 §9.2 Response Acknowledgements</a>
     */
    @Test
    public void testGetAckInitialValue() throws Exception
    {
        // Setup fixture - initial session request with ack='1' to indicate acknowledgements will be used
        final String content = "<body rid='1573741820' to='example.com' ver='1.6' wait='60' hold='1' ack='1' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertNotNull(body.getAck());
        assertEquals(1L, body.getAck());
    }

    /**
     * Verifies that the RID attribute is parsed correctly.
     */
    @Test
    public void testGetRid() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1573741820' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertNotNull(body.getRid());
        assertEquals(1573741820L, body.getRid());
    }

    /**
     * Verifies that a body without a 'pause' or 'type=terminate' and with empty content is considered a poll request.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#poll">XEP-0124 §12 Polling Sessions</a>
     */
    @Test
    public void testIsPollWhenEmpty() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1249243567' sid='SomeSID' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertTrue(body.isPoll());
    }

    /**
     * Verifies that a body with 'type=terminate' is NOT considered a poll request.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#terminate">XEP-0124 §13 Terminating the BOSH Session</a>
     */
    @Test
    public void testIsPollWhenTerminate() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1249243567' sid='SomeSID' type='terminate' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertFalse(body.isPoll());
    }

    /**
     * Verifies that a body with a 'pause' attribute is NOT considered a poll request.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0124.html#inactive">XEP-0124 §10 Inactivity</a>
     */
    @Test
    public void testIsPollWhenPause() throws Exception
    {
        // Setup fixture
        final String content = "<body rid='1249243567' sid='SomeSID' pause='60' xmlns='http://jabber.org/protocol/httpbind'/>";

        // Execute system under test
        final HttpBindBody body = HttpBindBody.from(content);

        // Verify results
        assertFalse(body.isPoll());
    }
}
