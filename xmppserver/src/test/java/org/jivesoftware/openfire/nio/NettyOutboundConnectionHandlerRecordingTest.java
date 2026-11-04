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
package org.jivesoftware.openfire.nio;

import io.netty.handler.ssl.NotSslRecordException;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tls;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies how {@link NettyOutboundConnectionHandler} records a TLS handshake that failed.
 *
 * @author Matthew Vivian
 */
public class NettyOutboundConnectionHandlerRecordingTest
{
    @Test
    public void testAnswerThatIsNotTlsIsRecordedAsSuch()
    {
        // Setup test fixture: Netty reports non-TLS bytes with a hex dump of everything the remote sent, such as the
        // stream header of a server that expects an unencrypted connection on the port.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final NotSslRecordException cause = new NotSslRecordException("not an SSL/TLS record: " + "3c3f786d6c2076657273696f6e3d".repeat(300));

        // Execute system under test.
        NettyOutboundConnectionHandler.recordHandshakeFailure(recorder, cause);

        // Verify results.
        assertEquals(Tls.notTls(), recorder.snapshot().getTls().orElseThrow(), "an answer that is not TLS must be recorded as such, without the hex dump of what the remote sent");
    }

    @Test
    public void testOtherHandshakeFailureKeepsItsReasonCut()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final SSLHandshakeException cause = new SSLHandshakeException("Received fatal alert: handshake_failure " + "x".repeat(1000));

        // Execute system under test.
        NettyOutboundConnectionHandler.recordHandshakeFailure(recorder, cause);

        // Verify results.
        final Tls tls = recorder.snapshot().getTls().orElseThrow();
        assertEquals(Tls.Outcome.HANDSHAKE_FAILED, tls.outcome(), "another handshake failure must be recorded with its reason");
        assertTrue(tls.reason().startsWith("Received fatal alert: handshake_failure"), "the reason must be kept: " + tls.reason());
        assertEquals(AttemptMilestones.MAX_REMOTE_TEXT_LENGTH + AttemptRecorder.CUT_MARKER.length(), tls.reason().length(), "the reason must be cut, as it can hold text the remote chose");
    }
}
