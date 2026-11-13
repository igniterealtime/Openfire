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

import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.util.Objects;

/**
 * What came back after the test sent an XMPP ping to the remote domain, classified by where the reply came from.
 *
 * An error that this server generated (for example remote-server-not-found when no session could be made) is addressed
 * as if it came from the remote domain, so its addresses cannot tell it apart from a reply the remote server sent.
 * Whether the ping was sent to the remote server over an outgoing server session tells them apart.
 *
 * @param kind where the reply came from, and of what type.
 * @param errorCondition the stanza error condition of an error reply, or null for a result or a timeout.
 * @author Matthew Vivian
 */
public record PingOutcome(Kind kind, String errorCondition)
{
    public enum Kind
    {
        /**
         * The remote server replied with a result.
         */
        RESULT,

        /**
         * The remote server replied with an error. The round trip still worked.
         */
        REMOTE_ERROR,

        /**
         * Only this server answered, with an error it generated itself.
         */
        LOCAL_ERROR,

        /**
         * No reply arrived in time.
         */
        TIMED_OUT
    }

    public PingOutcome
    {
        Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.REMOTE_ERROR || kind == Kind.LOCAL_ERROR) != (errorCondition != null)) {
            throw new IllegalArgumentException("An error condition is required for an error reply, and only for one.");
        }
    }

    public static PingOutcome result()
    {
        return new PingOutcome(Kind.RESULT, null);
    }

    public static PingOutcome remoteError(final String errorCondition)
    {
        return new PingOutcome(Kind.REMOTE_ERROR, errorCondition);
    }

    public static PingOutcome localError(final String errorCondition)
    {
        return new PingOutcome(Kind.LOCAL_ERROR, errorCondition);
    }

    public static PingOutcome timedOut()
    {
        return new PingOutcome(Kind.TIMED_OUT, null);
    }

    /**
     * Classifies the reply to the ping.
     *
     * This server routes an error it generates itself (remote-server-not-found when no session could be made) exactly
     * like a reply from the remote, addressed as if from the remote domain, so the reply alone cannot say where it came
     * from. Whether the ping was sent to the remote server can: if it never left this server, any reply is our own.
     *
     * @param reply the reply to the ping, or null when none arrived in time.
     * @param pingSent whether the ping was sent over an outgoing server session to the remote.
     * @return the outcome.
     */
    public static PingOutcome fromReply(final IQ reply, final boolean pingSent)
    {
        if (reply == null) {
            return timedOut();
        }
        if (reply.getType() != IQ.Type.error) {
            return result();
        }
        final String condition = conditionOf(reply);
        return pingSent ? remoteError(condition) : localError(condition);
    }

    /**
     * The stanza error condition of an error reply. A reply can come from a faulty remote, so a missing or unknown
     * condition is reported as undefined-condition rather than allowed to end the test.
     */
    private static String conditionOf(final IQ reply)
    {
        try {
            final PacketError error = reply.getError();
            if (error != null && error.getCondition() != null) {
                return error.getCondition().toXMPP();
            }
        } catch (final IllegalArgumentException e) {
            // An element in the stanzas namespace that is not a defined condition.
        }
        return PacketError.Condition.undefined_condition.toXMPP();
    }
}
