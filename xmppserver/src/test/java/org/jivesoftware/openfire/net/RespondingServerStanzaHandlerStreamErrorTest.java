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
package org.jivesoftware.openfire.net;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies how {@link RespondingServerStanzaHandler} describes a stream error sent by a remote server, for the S2S
 * connection test. The remote chooses the content, so the description must hold up against anything it sends.
 *
 * @author Matthew Vivian
 */
public class RespondingServerStanzaHandlerStreamErrorTest
{
    @Test
    public void testConditionIsDescribed() throws Exception
    {
        final Element error = parse("<stream:error xmlns:stream='http://etherx.jabber.org/streams'><host-unknown xmlns='urn:ietf:params:xml:ns:xmpp-streams'/></stream:error>");

        assertEquals("host-unknown", RespondingServerStanzaHandler.describeStreamError(error), "a stream error must be described by its condition");
    }

    @Test
    public void testTextFollowsTheCondition() throws Exception
    {
        final Element error = parse("<stream:error xmlns:stream='http://etherx.jabber.org/streams'><policy-violation xmlns='urn:ietf:params:xml:ns:xmpp-streams'/><text xmlns='urn:ietf:params:xml:ns:xmpp-streams'>TLS required</text></stream:error>");

        assertEquals("policy-violation: TLS required", RespondingServerStanzaHandler.describeStreamError(error), "the text the remote sent must follow the condition");
    }

    @Test
    public void testErrorWithoutAConditionIsDescribedAsUndefined() throws Exception
    {
        final Element error = parse("<stream:error xmlns:stream='http://etherx.jabber.org/streams'><something-else xmlns='urn:example'/></stream:error>");

        assertEquals("undefined-condition", RespondingServerStanzaHandler.describeStreamError(error), "an error with no condition in the streams namespace must still be described");
    }

    @Test
    public void testLongTextIsCut() throws Exception
    {
        // Setup test fixture.
        final Element error = parse("<stream:error xmlns:stream='http://etherx.jabber.org/streams'><conflict xmlns='urn:ietf:params:xml:ns:xmpp-streams'/><text xmlns='urn:ietf:params:xml:ns:xmpp-streams'>" + "x".repeat(5000) + "</text></stream:error>");

        // Execute system under test.
        final String description = RespondingServerStanzaHandler.describeStreamError(error);

        // Verify results.
        assertTrue(description.startsWith("conflict: xxx"), "a cut description must keep its start");
        assertEquals(203, description.length(), "the text the remote sent must be cut to 200 characters, marked as cut with three dots");
        assertTrue(description.endsWith("..."), "a cut description must show that it was cut");
    }

    @Test
    public void testShortTextIsKeptAsSent()
    {
        assertEquals("remote.example.org", AttemptMilestones.remoteText("remote.example.org"), "text within the limit must be recorded as the remote sent it");
    }

    @Test
    public void testControlCharactersAreReplaced()
    {
        // A remote can put line breaks in its stream 'from' with character references, which would start what looks
        // like a new line of the diagnostic log.
        assertEquals("remote.example.org INFO: Connection step TLS: NEGOTIATED", AttemptMilestones.remoteText("remote.example.org\nINFO: Connection step TLS: NEGOTIATED"),
            "control characters in text the remote chose must be replaced, so it cannot forge lines of the diagnostic log");
    }

    private static Element parse(final String xml) throws Exception
    {
        return DocumentHelper.parseText(xml).getRootElement();
    }
}
