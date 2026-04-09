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

import org.dom4j.DocumentException;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HttpBindBody}
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
 */
public class HttpBindBodyTest
{
    /**
     * Verifies that {@link HttpBindBody#getAck()} returns null when the 'ack' attribute is absent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testGetAckAbsent() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='1' sid='test'/>" );
        assertNull( body.getAck() );
    }

    /**
     * Verifies that {@link HttpBindBody#getAck()} returns the correct value when the 'ack' attribute is present.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testGetAckPresent() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='2' sid='test' ack='1'/>" );
        assertEquals( Long.valueOf( 1L ), body.getAck() );
    }

    /**
     * Verifies that {@link HttpBindBody#getAck()} returns null when the 'ack' attribute has an invalid (non-numeric) value.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testGetAckInvalid() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='3' sid='test' ack='notanumber'/>" );
        assertNull( body.getAck() );
    }

    /**
     * Verifies that {@link HttpBindBody#getAck()} returns null when 'ack' equals the initial value sentinel (-1).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testGetAckNegativeValue() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='4' sid='test' ack='-1'/>" );
        assertNull( body.getAck() );
    }

    /**
     * Verifies that {@link HttpBindBody#getRid()} returns the correct value.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testGetRid() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='42' sid='test'/>" );
        assertEquals( Long.valueOf( 42L ), body.getRid() );
    }

    /**
     * Verifies that {@link HttpBindBody#isPoll()} returns true for an empty body with no stanzas, type, or restart.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testIsPollTrue() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='1' sid='test'/>" );
        assertTrue( body.isPoll() );
    }

    /**
     * Verifies that {@link HttpBindBody#isPoll()} returns false when the body contains stanzas.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3220">OF-3220</a>
     */
    @Test
    public void testIsPollFalseWithStanzas() throws DocumentException, XmlPullParserException, IOException
    {
        final HttpBindBody body = HttpBindBody.from(
            "<body xmlns='http://jabber.org/protocol/httpbind' rid='1' sid='test'>" +
            "<message xmlns='jabber:client' to='user@example.com'/>" +
            "</body>" );
        assertFalse( body.isPoll() );
    }
}
