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
package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.net.MXParser;
import org.junit.jupiter.api.Test;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;

import java.io.StringReader;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link Session#detectClaimedIdentity(XmlPullParser)}, which extracts the (unverified) identity that a peer
 * claims in the 'from' attribute of a stream header, as described in RFC 6120 § 4.7.1 and XEP-0388.
 *
 * The attribute is optional and entirely peer-controlled, so the central property under test is that every form of
 * absent, empty or malformed input yields an empty result rather than an exception: a peer must never be able to break
 * stream negotiation by sending garbage in an optional attribute.
 */
public class SessionDetectClaimedIdentityTest
{
    /**
     * Asserts that a stream header that carries no 'from' attribute yields no claimed identity.
     */
    @Test
    public void testHeaderWithoutFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertTrue(result.isEmpty(), "A stream header without a 'from' attribute must not yield a claimed identity.");
    }

    /**
     * Asserts that the value of the 'from' attribute of a stream header is returned as the claimed identity.
     */
    @Test
    public void testHeaderWithFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' from='juliet@example.org' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertEquals(Optional.of(new JID("juliet@example.org")), result, "The value of the 'from' attribute must be returned as the claimed identity.");
    }

    /**
     * Asserts that an empty 'from' attribute is treated as if the peer made no claim.
     */
    @Test
    public void testEmptyFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' from='' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertTrue(result.isEmpty(), "An empty 'from' attribute must be treated as if no claim was made.");
    }

    /**
     * Asserts that a 'from' attribute that holds only whitespace is treated as if the peer made no claim.
     */
    @Test
    public void testWhitespaceOnlyFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' from='   ' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertTrue(result.isEmpty(), "A blank 'from' attribute must be treated as if no claim was made.");
    }

    /**
     * Asserts that a 'from' attribute that does not hold a valid JID yields no claim instead of raising an exception.
     * A peer must not be able to abort stream negotiation by sending an unusable value in an optional attribute.
     */
    @Test
    public void testMalformedFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' from='juliet@' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertTrue(result.isEmpty(), "A 'from' attribute that does not contain a valid JID must yield no claim, without throwing.");
    }

    /**
     * Asserts that a full JID is returned unaltered. Reducing the claim to the granularity that a caller needs is the
     * responsibility of that caller; this method must not silently discard what the peer provided.
     */
    @Test
    public void testFullJidInFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' from='juliet@example.org/balcony' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertEquals(Optional.of(new JID("juliet@example.org/balcony")), result, "A full JID in the 'from' attribute must be returned unaltered.");
    }

    /**
     * Asserts that a domain-only claim, as made by server-to-server peers, is recognised as a valid claim that carries
     * no username. Judging whether a claim identifies a user is left to the caller.
     */
    @Test
    public void testDomainOnlyFromAttribute() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:server' from='example.com' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertTrue(result.isPresent(), "A domain-only 'from' attribute is a valid JID and must yield a claim.");
        assertNull(result.get().getNode(), "A domain-only claim must not have a node part.");
        assertEquals("example.com", result.get().getDomain(), "A domain-only claim must retain its domain.");
    }

    /**
     * Asserts that the 'to' attribute of a stream header is not mistaken for the 'from' attribute.
     */
    @Test
    public void testToAttributeIsNotReadAsFrom() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='jabber:client' to='example.org' version='1.0'>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertTrue(result.isEmpty(), "The 'to' attribute must not be read as if it were the 'from' attribute.");
    }

    /**
     * Asserts that a claim made on an RFC 7395 &lt;open/&gt; element is read the same way as one made on a stream
     * header. The method is shared by the TCP and websocket code paths, so it must be indifferent to the element name
     * and namespace that it is invoked on.
     */
    @Test
    public void testWebsocketOpenElement() throws Exception
    {
        // Setup test fixture.
        final XmlPullParser xpp = parserPositionedOn("<open xmlns='urn:ietf:params:xml:ns:xmpp-framing' from='juliet@example.org' to='example.org' version='1.0'/>");

        // Execute system under test.
        final Optional<JID> result = Session.detectClaimedIdentity(xpp);

        // Verify result.
        assertEquals(Optional.of(new JID("juliet@example.org")), result, "A claim made on an RFC 7395 <open/> element must be read the same way as one made on a stream header.");
    }

    /**
     * Returns a namespace-aware parser that has been advanced to the first START_TAG of the provided XML, mirroring
     * the way the production code positions its parser before reading attributes.
     *
     * @param xml the XML to parse.
     * @return a parser positioned on the first element of the provided XML.
     */
    private static XmlPullParser parserPositionedOn(final String xml) throws Exception
    {
        final XmlPullParserFactory factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
        factory.setNamespaceAware(true);
        final XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(xml));
        for (int eventType = xpp.getEventType(); eventType != XmlPullParser.START_TAG;) {
            eventType = xpp.next();
        }
        return xpp;
    }
}
