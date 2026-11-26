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
package org.jivesoftware.openfire.server.s2stest;

import org.junit.jupiter.api.Test;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies how {@link PingOutcome#fromReply(IQ, boolean)} classifies the reply to the test's ping: a reply only
 * counts as coming from the remote server when the ping was actually sent to it.
 *
 * @author Matthew Vivian
 */
public class PingOutcomeTest
{
    private static IQ reply(final IQ.Type type)
    {
        final IQ iq = new IQ(type, "ping-1");
        iq.setFrom("remote.example.org");
        iq.setTo("example.com");
        return iq;
    }

    private static IQ error(final PacketError.Condition condition)
    {
        final IQ iq = reply(IQ.Type.error);
        iq.setError(condition);
        return iq;
    }

    @Test
    public void testNoReplyIsATimeout()
    {
        assertEquals(PingOutcome.timedOut(), PingOutcome.fromReply(null, true), "no reply within the wait must be a timeout");
    }

    @Test
    public void testResultIsFromTheRemote()
    {
        assertEquals(PingOutcome.result(), PingOutcome.fromReply(reply(IQ.Type.result), true), "a result to a ping that was sent must be the remote's reply");
    }

    @Test
    public void testErrorAfterThePingWasSentIsFromTheRemote()
    {
        // Execute system under test.
        final PingOutcome outcome = PingOutcome.fromReply(error(PacketError.Condition.service_unavailable), true);

        // Verify results.
        assertEquals(PingOutcome.remoteError("service-unavailable"), outcome, "an error to a ping that was sent must be the remote's reply, with its condition");
    }

    @Test
    public void testErrorWhenThePingWasNeverSentIsOurOwn()
    {
        // Execute system under test.
        final PingOutcome outcome = PingOutcome.fromReply(error(PacketError.Condition.remote_server_not_found), false);

        // Verify results.
        assertEquals(PingOutcome.localError("remote-server-not-found"), outcome, "an error to a ping that never left this server must be one this server generated, even though it is addressed as if from the remote");
    }

    @Test
    public void testErrorWithoutAConditionIsStillClassified()
    {
        // Setup test fixture: an error stanza with no error child.
        final IQ bare = reply(IQ.Type.error);

        // Execute system under test.
        final PingOutcome outcome = PingOutcome.fromReply(bare, true);

        // Verify results.
        assertEquals(PingOutcome.Kind.REMOTE_ERROR, outcome.kind(), "an error without a condition must still count as an error reply from the remote");
        assertEquals("undefined-condition", outcome.errorCondition(), "a missing condition must be reported as undefined-condition");
    }

    @Test
    public void testErrorElementWithoutAConditionIsStillClassified()
    {
        // Setup test fixture: an error child with no condition element in it, as a faulty remote can send.
        final IQ empty = reply(IQ.Type.error);
        empty.getElement().addElement("error").addAttribute("type", "cancel");

        // Execute system under test.
        final PingOutcome outcome = PingOutcome.fromReply(empty, true);

        // Verify results.
        assertEquals(PingOutcome.remoteError("undefined-condition"), outcome, "an error element without a condition must be reported as undefined-condition, not end the test");
    }

    @Test
    public void testErrorWithAnUnknownConditionIsStillClassified()
    {
        // Setup test fixture: a condition element in the stanzas namespace that is not a defined condition.
        final IQ unknown = reply(IQ.Type.error);
        unknown.getElement().addElement("error").addAttribute("type", "cancel")
            .addElement("not-a-condition", "urn:ietf:params:xml:ns:xmpp-stanzas");

        // Execute system under test.
        final PingOutcome outcome = PingOutcome.fromReply(unknown, true);

        // Verify results.
        assertEquals(PingOutcome.remoteError("undefined-condition"), outcome, "an unknown condition must be reported as undefined-condition, not end the test");
    }
}
