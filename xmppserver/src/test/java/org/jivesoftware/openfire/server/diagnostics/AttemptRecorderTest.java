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
package org.jivesoftware.openfire.server.diagnostics;

import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Authentication;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Dns.Target;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tcp.AddressFailure;
import org.jivesoftware.openfire.server.diagnostics.AttemptMilestones.Tls;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link AttemptRecorder}, which collects the milestones of one outbound server-to-server connection attempt
 * while the attempt runs on other threads.
 *
 * @author Matthew Vivian
 */
public class AttemptRecorderTest
{
    @Test
    public void testNewRecorderIsInProgressWithNothingRecorded()
    {
        // Setup test fixture.
        final Instant before = Instant.now();

        // Execute system under test.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Verify results.
        assertEquals(AttemptRecorder.Outcome.IN_PROGRESS, recorder.getOutcome(), "a new attempt must be in progress");
        assertFalse(recorder.getStarted().isBefore(before), "the start time must be when the attempt began");
        assertEquals("example.com", recorder.getLocalDomain(), "the recorder must know the local domain");
        assertEquals("remote.example.org", recorder.getRemoteDomain(), "the recorder must know the remote domain");
        assertTrue(recorder.snapshot().getDns().isEmpty() && recorder.snapshot().getAuthentication().isEmpty(), "nothing must be recorded before any step happens");
    }

    @Test
    public void testRecordedMilestonesAppearInTheSnapshot()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        recorder.dns(Dns.srvRecords(List.of(Target.plain("xmpp.remote.example.org:5269"))));
        recorder.tcp(Tcp.connected("192.0.2.10:5269"));
        recorder.authentication(Authentication.saslExternal());
        final AttemptMilestones snapshot = recorder.snapshot();

        // Verify results.
        assertEquals(Dns.srvRecords(List.of(Target.plain("xmpp.remote.example.org:5269"))), snapshot.getDns().orElseThrow(), "the DNS milestone must be in the snapshot");
        assertEquals(Tcp.connected("192.0.2.10:5269"), snapshot.getTcp().orElseThrow(), "the TCP milestone must be in the snapshot");
        assertEquals(Authentication.saslExternal(), snapshot.getAuthentication().orElseThrow(), "the authentication milestone must be in the snapshot");
        assertTrue(snapshot.getTls().isEmpty(), "a step that was not recorded must stay absent");
    }

    @Test
    public void testSnapshotIsNotChangedByLaterMilestones()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final AttemptMilestones snapshot = recorder.snapshot();

        // Execute system under test.
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));

        // Verify results.
        assertTrue(snapshot.getTls().isEmpty(), "a snapshot must not change after it was taken");
        assertTrue(recorder.snapshot().getTls().isPresent(), "a new snapshot must include the later milestone");
    }

    @Test
    public void testLaterOutcomeReplacesAnEarlierOne()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        recorder.finish(AttemptRecorder.Outcome.NEW_SESSION);
        recorder.finish(AttemptRecorder.Outcome.FAILED);

        // Verify results.
        assertEquals(AttemptRecorder.Outcome.FAILED, recorder.getOutcome(), "a later outcome must replace an earlier one, so a failure after a session was created is not reported as a success");
    }

    @Test
    public void testFinishingAsInProgressIsRejected()
    {
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        assertThrows(IllegalArgumentException.class, () -> recorder.finish(AttemptRecorder.Outcome.IN_PROGRESS), "an attempt cannot finish as in progress");
    }

    @Test
    public void testConcurrentWritersLoseNoCounts() throws Exception
    {
        // Setup test fixture: the connection code's threads count warnings and record milestones at the same time, while
        // the test's thread reads. Unsynchronised, the increments would race and the total would come out short.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final int threads = 4;
        final int warningsPerThread = 10_000;
        final ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Future<?>> tasks = new ArrayList<>();

        // Execute system under test.
        try {
            for (int t = 0; t < threads; t++) {
                tasks.add(pool.submit(() -> { start.await(); for (int i = 0; i < warningsPerThread; i++) { recorder.loggedWarning(); } return null; }));
            }
            tasks.add(pool.submit(() -> { start.await(); for (int i = 0; i < 1000; i++) { recorder.tcp(Tcp.connected("192.0.2.10:5269")); recorder.snapshot(); } return null; }));
            start.countDown();
            for (final Future<?> task : tasks) {
                task.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        recorder.diagnosticLog(List.of());

        // Verify results.
        assertEquals(threads * warningsPerThread, recorder.getLoggedProblems().orElseThrow().warnings(), "every warning counted from another thread must be kept");
        assertTrue(recorder.snapshot().getTcp().isPresent(), "a milestone written from another thread must be recorded");
    }

    @Test
    public void testDescribeRendersEachRecordedMilestone()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269"))));

        // Execute system under test.
        final List<String> lines = recorder.describe();

        // Verify results.
        assertEquals(1, lines.size(), "only recorded milestones must be described");
        assertTrue(lines.get(0).startsWith("INFO: "), "each line must carry a level prefix, like the other diagnostic lines");
        assertEquals("INFO: Connection step TCP: REFUSED 192.0.2.10:5269", lines.get(0), "the line must say what happened and where");
    }

    @Test
    public void testDescribeListsWhatHappenedAtEachAddress()
    {
        // Setup test fixture: DNS listed more targets than are kept, one of them for direct TLS, and the connection was
        // made after other addresses failed in different ways.
        final List<Target> targets = new ArrayList<>();
        targets.add(Target.directTls("xmpps.remote.example.org:5270"));
        for (int i = 1; i < AttemptMilestones.MAX_LISTED_VALUES + 2; i++) {
            targets.add(Target.plain("xmpp" + i + ".remote.example.org:5269"));
        }
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.dns(Dns.srvRecords(targets));
        recorder.tcp(Tcp.connectedAfterFailures("192.0.2.12:5269", List.of(
            AddressFailure.refused("192.0.2.10:5269"),
            AddressFailure.timedOut("192.0.2.11:5269"),
            AddressFailure.failed("[2001:db8::10]:5269", "Network is unreachable"))));

        // Execute system under test.
        final List<String> lines = recorder.describe();

        // Verify results.
        assertEquals("INFO: Connection step DNS: SRV_RECORDS xmpps.remote.example.org:5270 (direct TLS), xmpp1.remote.example.org:5269, xmpp2.remote.example.org:5269, "
            + "xmpp3.remote.example.org:5269, xmpp4.remote.example.org:5269, xmpp5.remote.example.org:5269, xmpp6.remote.example.org:5269, "
            + "xmpp7.remote.example.org:5269, xmpp8.remote.example.org:5269, xmpp9.remote.example.org:5269, and 2 more", lines.get(0),
            "the DNS line must list the targets kept, mark direct TLS, and say how many were left out");
        assertEquals("INFO: Connection step TCP: CONNECTED_AFTER_FAILURES 192.0.2.12:5269 192.0.2.10:5269 refused; 192.0.2.11:5269 timed out; [2001:db8::10]:5269 Network is unreachable", lines.get(1),
            "the TCP line must say what happened at each address that failed before the connection");
    }

    @Test
    public void testDescribeListsTheAddressesOfAFailedConnection()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.allFailed(List.of(AddressFailure.refused("192.0.2.10:5269"), AddressFailure.failed("192.0.2.11:5269", "No route to host"))));

        // Execute system under test.
        final List<String> lines = recorder.describe();

        // Verify results.
        assertEquals("INFO: Connection step TCP: FAILED 192.0.2.10:5269, 192.0.2.11:5269 192.0.2.10:5269 refused; 192.0.2.11:5269 No route to host", lines.get(0),
            "the TCP line must list the addresses tried and what happened at each");
    }

    @Test
    public void testDescribeCutsALongLine()
    {
        // Setup test fixture: a milestone whose detail is longer than a line may be.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.allFailed(List.of(AddressFailure.failed("192.0.2.10:5269", "x".repeat(AttemptRecorder.MAX_DIAGNOSTIC_LOG_LINE_LENGTH)))));

        // Execute system under test.
        final List<String> lines = recorder.describe();

        // Verify results.
        assertEquals(AttemptRecorder.MAX_DIAGNOSTIC_LOG_LINE_LENGTH + 3, lines.get(0).length(), "a described line must be cut to the limit, with three dots marking the cut");
        assertTrue(lines.get(0).endsWith("..."), "a cut line must be marked as cut: " + lines.get(0));
    }

    @Test
    public void testDiagnosticLogLinesAreCutToTheirLimit()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final String longLine = "WARN: " + "x".repeat(AttemptRecorder.MAX_DIAGNOSTIC_LOG_LINE_LENGTH);

        // Execute system under test.
        recorder.diagnosticLog(List.of(longLine, "DEBUG: short"));

        // Verify results.
        final List<String> kept = recorder.getDiagnosticLog().orElseThrow();
        assertEquals(AttemptRecorder.MAX_DIAGNOSTIC_LOG_LINE_LENGTH + 3, kept.get(0).length(), "a kept line must be cut to the limit, with three dots marking the cut, as recorders are kept for many domain pairs");
        assertEquals("DEBUG: short", kept.get(1), "a line within the limit must be kept as it is");
        assertEquals(1, recorder.getLoggedProblems().orElseThrow().warnings(), "a cut line must still be counted by its level");
    }

    @Test
    public void testStreamErrorBeforeTlsFailsTheStream()
    {
        // Setup test fixture: the remote opened a stream, then refused it (for example because it does not serve the domain).
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));

        // Execute system under test.
        recorder.remoteStreamError("host-unknown");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.streamError("host-unknown"), recorder.snapshot().getStream().orElseThrow(), "a stream error before any later step must be shown as the stream failing");
    }

    @Test
    public void testStreamErrorOverDirectTlsBeforeAuthenticationFailsTheStream()
    {
        // Setup test fixture: over direct TLS the handshake comes first; then the remote opened a stream and refused it
        // (for example because it does not serve the domain), before this server started to authenticate.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.directTls(true);
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.valid());
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));

        // Execute system under test.
        recorder.remoteStreamError("host-unknown");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.streamError("host-unknown"), recorder.snapshot().getStream().orElseThrow(), "over direct TLS, a stream error before authentication started must be shown as the stream failing, as it is over STARTTLS");
        assertTrue(recorder.snapshot().getAuthentication().isEmpty(), "authentication had not started, so the error must not be blamed on it");
    }

    @Test
    public void testStreamErrorOverDirectTlsAfterAuthenticationStartedFailsAuthentication()
    {
        // Setup test fixture: over direct TLS, the stream opened and this server started to authenticate.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.directTls(true);
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.valid());
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.authenticationStarted();

        // Execute system under test.
        recorder.remoteStreamError("not-authorized");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.opened("remote.example.org"), recorder.snapshot().getStream().orElseThrow(), "the stream opened, and must stay shown as opened");
        assertEquals(Authentication.streamError("not-authorized"), recorder.snapshot().getAuthentication().orElseThrow(), "a stream error once authentication started must be shown as authentication failing");
    }

    @Test
    public void testHandshakeFailureReasonIsCutAndMadePrintable()
    {
        // Setup test fixture: a reason as long as Netty's hex dump of what a remote sent, with a line break in it.
        final String reason = "line one\nline two " + "3c3f786d6c".repeat(400);

        // Execute system under test.
        final Tls tls = Tls.handshakeFailed(reason);

        // Verify results.
        assertEquals(AttemptMilestones.MAX_REMOTE_TEXT_LENGTH + AttemptRecorder.CUT_MARKER.length(), tls.reason().length(), "the reason can hold text the remote chose, so it must be cut like other remote text");
        assertTrue(tls.reason().endsWith(AttemptRecorder.CUT_MARKER), "the cut must be marked");
        assertFalse(tls.reason().contains("\n"), "control characters must be replaced, so that the reason cannot start a new line");
    }

    @Test
    public void testStreamErrorAfterTlsDoesNotFailTheStream()
    {
        // Setup test fixture: the stream opened and TLS was set up, so a later stream error belongs to a later step.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));

        // Execute system under test.
        recorder.remoteStreamError("not-authorized");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.opened("remote.example.org"), recorder.snapshot().getStream().orElseThrow(), "a stream that opened and got as far as TLS must still show as opened");
        assertEquals(Authentication.streamError("not-authorized"), recorder.snapshot().getAuthentication().orElseThrow(), "a stream error after TLS and before authentication finished must fail authentication");
    }

    @Test
    public void testStreamErrorAfterAuthenticationChangesNothing()
    {
        // Setup test fixture: authentication succeeded, so a later stream error is not part of setting up the connection.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.authentication(Authentication.saslExternal());

        // Execute system under test.
        recorder.remoteStreamError("system-shutdown");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.opened("remote.example.org"), recorder.snapshot().getStream().orElseThrow(), "a stream error after authentication must not fail the stream");
        assertEquals(Authentication.saslExternal(), recorder.snapshot().getAuthentication().orElseThrow(), "a stream error after authentication must not replace how authentication went");
    }

    @Test
    public void testCloseBeforeAnyStreamIsRecordedAsClosedByTheRemote()
    {
        // Setup test fixture: connected, and nothing received yet.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.connected("192.0.2.10:5269"));

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertEquals(AttemptMilestones.Stream.closedByRemote(), recorder.snapshot().getStream().orElseThrow(), "a connection closed before any stream must be recorded as closed by the remote");
    }

    @Test
    public void testCloseAfterALaterStepChangesNothing()
    {
        // Setup test fixture: the handshake failed, after which this server closes the connection itself.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.connected("192.0.2.10:5270"));
        recorder.tls(Tls.handshakeFailed("Received fatal alert: handshake_failure"));

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertTrue(recorder.snapshot().getStream().isEmpty(), "a close after a later step was recorded is explained by that step, so no stream failure may be added");
    }

    @Test
    public void testCloseAfterTheStreamOpenedFailsTls()
    {
        // Setup test fixture: the stream opened, and TLS was still being set up.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertEquals(AttemptMilestones.Stream.opened("remote.example.org"), recorder.snapshot().getStream().orElseThrow(), "a close after the stream opened must not replace the opened stream");
        assertEquals(Tls.closedBeforeSetUp(), recorder.snapshot().getTls().orElse(null), "the step under way when the remote closed the connection must fail, so that it does not read as not observed");
    }

    @Test
    public void testStreamErrorReplacesAFailureInferredFromAnEarlierClose()
    {
        // Setup test fixture: the remote opened a stream and then refused it with a stream error, and the close reached
        // the recorder before the error did.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.connectionClosed();

        // Execute system under test.
        recorder.remoteStreamError("host-unknown");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.streamError("host-unknown"), recorder.snapshot().getStream().orElse(null), "the remote's own error must be recorded against the stream, whatever the order the close and the error arrived in");
        assertTrue(recorder.snapshot().getTls().isEmpty(), "a TLS failure inferred from the close must give way to the error that explains it");
    }

    @Test
    public void testStreamErrorReplacesAnAuthenticationFailureInferredFromAnEarlierClose()
    {
        // Setup test fixture: encrypted, then the remote ended the stream with an error during authentication, and the
        // close reached the recorder first.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.valid());
        recorder.connectionClosed();

        // Execute system under test.
        recorder.remoteStreamError("not-authorized");

        // Verify results.
        assertEquals(Authentication.streamError("not-authorized"), recorder.snapshot().getAuthentication().orElse(null), "the remote's own error must replace the failure inferred from the close");
    }

    @Test
    public void testCloseDuringAuthenticationFailsAuthentication()
    {
        // Setup test fixture: encrypted with a valid certificate, and authentication under way.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.valid());

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertEquals(Authentication.closed(), recorder.snapshot().getAuthentication().orElse(null), "a close during authentication must fail authentication");
    }

    @Test
    public void testCloseAfterARecordedFailureAddsNothing()
    {
        // Setup test fixture: this server rejected the certificate, and closes the connection itself.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.untrusted());

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertTrue(recorder.snapshot().getAuthentication().isEmpty(), "a close that follows a recorded failure is explained by it, so authentication must not be blamed");
    }

    @Test
    public void testCloseAfterEncryptionRequiredButDisabledAddsNothing()
    {
        // Setup test fixture: this server's policy disables encryption, while the remote requires it, so this server
        // closes the connection itself.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.requiredButDisabled());

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertTrue(recorder.snapshot().getAuthentication().isEmpty(), "this server's own close, after its policy refused the connection, must not be blamed on authentication");
    }

    @Test
    public void testCloseByTheRemoteDuringAuthenticationOnAnUnencryptedConnectionFailsAuthentication()
    {
        // Setup test fixture: the connection went ahead unencrypted, as this server's policy allows, and the remote
        // closed it while authentication was under way.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.unencrypted());

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertEquals(Authentication.closed(), recorder.snapshot().getAuthentication().orElse(null), "an unencrypted connection that this server allowed did not fail, so a close that follows must be recorded against authentication, the step under way");
    }

    @Test
    public void testCloseAfterThisServerGaveUpChangesNothing()
    {
        // Setup test fixture: this server gave up waiting, and then closes the connection itself.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.connected("192.0.2.10:5269"));
        recorder.gaveUpWaiting(Duration.ofSeconds(5));

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertEquals(AttemptMilestones.Stream.timedOut(Duration.ofSeconds(5)), recorder.snapshot().getStream().orElseThrow(), "the close that follows giving up must not be blamed on the remote");
    }

    @Test
    public void testGivingUpOverDirectTlsBeforeTheHandshakeFailsTls()
    {
        // Setup test fixture: with direct TLS the handshake comes before any stream.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.directTls(true);
        recorder.tcp(Tcp.connected("192.0.2.10:5270"));

        // Execute system under test.
        recorder.gaveUpWaiting(Duration.ofSeconds(5));

        // Verify results.
        assertEquals(Tls.timedOut(Duration.ofSeconds(5)), recorder.snapshot().getTls().orElseThrow(), "over direct TLS, a remote that never answers stalls the handshake, so TLS must be recorded as timed out");
        assertTrue(recorder.snapshot().getStream().isEmpty(), "no stream can be expected before the handshake, so the stream must not be blamed");
        assertTrue(recorder.snapshot().isDirectTls(), "the attempt must record that it used direct TLS");
    }

    @Test
    public void testGivingUpOverDirectTlsAfterTheHandshakeFailedFailsAuthentication()
    {
        // Setup test fixture: the direct TLS handshake failed, so no stream can open on this connection; only the
        // Server Dialback fallback can still be under way when this server stops waiting.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.directTls(true);
        recorder.tcp(Tcp.connected("192.0.2.10:5270"));
        recorder.tls(Tls.handshakeFailed("Received fatal alert: handshake_failure"));

        // Execute system under test.
        recorder.gaveUpWaiting(Duration.ofSeconds(10));

        // Verify results.
        assertTrue(recorder.snapshot().getStream().isEmpty(), "a stream that could never open after a failed direct TLS handshake must not be blamed for the wait");
        assertEquals(Tls.handshakeFailed("Received fatal alert: handshake_failure"), recorder.snapshot().getTls().orElseThrow(), "the failed handshake must stay recorded");
        assertEquals(Authentication.timedOut(Duration.ofSeconds(10)), recorder.snapshot().getAuthentication().orElse(null), "the wait must be recorded against the Server Dialback fallback, the only step that can still be under way");
    }

    @Test
    public void testCloseOverDirectTlsBeforeTheHandshakeDoesNotBlameTheStream()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.directTls(true);
        recorder.tcp(Tcp.connected("192.0.2.10:5270"));

        // Execute system under test.
        recorder.connectionClosed();

        // Verify results.
        assertTrue(recorder.snapshot().getStream().isEmpty(), "over direct TLS no stream is expected before the handshake, which records its own failure");
    }

    @Test
    public void testGivingUpAfterTheRemoteClosedChangesNothing()
    {
        // Setup test fixture: the remote closed the connection, and this server only notices when its wait ends.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.tcp(Tcp.connected("192.0.2.10:5269"));
        recorder.connectionClosed();

        // Execute system under test.
        recorder.gaveUpWaiting(Duration.ofSeconds(5));

        // Verify results.
        assertEquals(AttemptMilestones.Stream.closedByRemote(), recorder.snapshot().getStream().orElseThrow(), "a remote that closed the connection must not be reported as silent");
    }

    @Test
    public void testGivingUpAfterTheStreamOpenedFailsTls()
    {
        // Setup test fixture: the stream opened, and TLS was still being set up when this server stopped waiting.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));

        // Execute system under test.
        recorder.gaveUpWaiting(Duration.ofSeconds(5));

        // Verify results.
        assertEquals(AttemptMilestones.Stream.opened("remote.example.org"), recorder.snapshot().getStream().orElseThrow(), "a stream that opened must stay opened when a later step times out");
        assertEquals(Tls.notSetUpInTime(Duration.ofSeconds(5)), recorder.snapshot().getTls().orElse(null), "the step under way when this server stopped waiting must fail, with the wait");
    }

    @Test
    public void testGivingUpDuringAuthenticationFailsAuthentication()
    {
        // Setup test fixture: the connection went ahead unencrypted, and Server Dialback never completed (as when the
        // remote cannot connect back to verify this server).
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.unencrypted());

        // Execute system under test.
        recorder.gaveUpWaiting(Duration.ofSeconds(10));

        // Verify results.
        assertEquals(Authentication.timedOut(Duration.ofSeconds(10)), recorder.snapshot().getAuthentication().orElse(null), "authentication that did not complete in time must fail, with the wait, so that the session check is not blamed");
    }

    @Test
    public void testTlsNegotiatedAfterThisServerGaveUpChangesNothing()
    {
        // Setup test fixture: this server gave up while TLS was being set up, and the handshake completes afterwards.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.gaveUpWaiting(Duration.ofSeconds(5));

        // Execute system under test.
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.valid());

        // Verify results.
        assertEquals(Tls.notSetUpInTime(Duration.ofSeconds(5)), recorder.snapshot().getTls().orElse(null), "a handshake that completed after this server stopped waiting did not set up this attempt, so the timeout must stand");
        assertTrue(recorder.snapshot().getCertificate().isEmpty(), "a certificate decided after this server stopped waiting is not part of the attempt");
    }

    @Test
    public void testAuthenticationAfterThisServerGaveUpChangesNothing()
    {
        // Setup test fixture: this server gave up while authentication was under way, and it completes afterwards.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.certificate(AttemptMilestones.Certificate.valid());
        recorder.gaveUpWaiting(Duration.ofSeconds(10));

        // Execute system under test.
        recorder.authentication(Authentication.saslExternal());

        // Verify results.
        assertEquals(Authentication.timedOut(Duration.ofSeconds(10)), recorder.snapshot().getAuthentication().orElse(null), "authentication that completed after this server stopped waiting did not produce a session, so the timeout must stand");
    }

    @Test
    public void testStreamErrorAfterThisServerGaveUpChangesNothing()
    {
        // Setup test fixture: this server gave up while TLS was being set up, and the remote sends an error afterwards.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.gaveUpWaiting(Duration.ofSeconds(5));

        // Execute system under test.
        recorder.remoteStreamError("connection-timeout");

        // Verify results.
        assertEquals(AttemptMilestones.Stream.opened("remote.example.org"), recorder.snapshot().getStream().orElse(null), "an error that arrived after this server stopped waiting did not end the attempt, so the stream must stay opened");
        assertEquals(Tls.notSetUpInTime(Duration.ofSeconds(5)), recorder.snapshot().getTls().orElse(null), "an error that arrived after this server stopped waiting must not replace the timeout");
    }

    @Test
    public void testGivingUpAfterAuthenticationAddsNothing()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.stream(AttemptMilestones.Stream.opened("remote.example.org"));
        recorder.tls(Tls.negotiated("TLSv1.3", "TLS_AES_256_GCM_SHA384"));
        recorder.authentication(Authentication.saslExternal());

        // Execute system under test.
        recorder.gaveUpWaiting(Duration.ofSeconds(10));

        // Verify results.
        assertEquals(Authentication.saslExternal(), recorder.snapshot().getAuthentication().orElse(null), "a completed authentication must not be replaced");
    }

    @Test
    public void testDiagnosticLogWarningsAndErrorsAreCounted()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        recorder.diagnosticLog(List.of("DEBUG: Creating new session...", "INFO: Connection step TCP: CONNECTED", "WARN: Cannot connect", "ERROR: An exception occurred", "WARN: Unable to authenticate"));

        // Verify results.
        assertEquals(new AttemptRecorder.LoggedProblems(2, 1), recorder.getLoggedProblems().orElseThrow(), "the warnings and errors in the attempt's diagnostic log must be counted by level");
    }

    @Test
    public void testProblemsLoggedOutsideTheDiagnosticLogAreAddedToItsCounts()
    {
        // Setup test fixture: the connection code logged a warning and an error that the diagnostic log does not hold.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.loggedWarning();
        recorder.loggedError();

        // Execute system under test.
        recorder.diagnosticLog(List.of("WARN: Unable to authenticate: Fail to create new session."));

        // Verify results.
        assertEquals(new AttemptRecorder.LoggedProblems(2, 1), recorder.getLoggedProblems().orElseThrow(), "warnings and errors logged for the attempt outside its diagnostic log must count with those in it");
    }

    @Test
    public void testProblemsLoggedOutsideTheDiagnosticLogWaitForTheAttemptToEnd()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        recorder.loggedWarning();

        // Verify results.
        assertTrue(recorder.getLoggedProblems().isEmpty(), "the counts must not be judged before the attempt ends and hands over its diagnostic log");
    }

    @Test
    public void testDiagnosticLogLinesAreKept()
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final List<String> lines = List.of("DEBUG: Start domain authentication ...", "WARN: Unable to authenticate.");

        // Execute system under test.
        recorder.diagnosticLog(lines);

        // Verify results.
        assertEquals(lines, recorder.getDiagnosticLog().orElseThrow(), "the attempt's own log lines must be kept, so that the test shows this attempt's log");
    }

    @Test
    public void testLongDiagnosticLogKeepsItsStartAndItsEnd()
    {
        // Setup test fixture: a log longer than is kept, ending with the step lines and the failure, as an attempt's
        // log does.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final List<String> lines = new ArrayList<>();
        lines.add("DEBUG: Start domain authentication ...");
        while (characters(lines) <= AttemptRecorder.MAX_DIAGNOSTIC_LOG_LENGTH * 3) {
            lines.add("WARN: line " + lines.size() + ' ' + "x".repeat(100));
        }
        lines.add("INFO: Connection step TCP: REFUSED 192.0.2.10:5269");
        lines.add("WARN: Unable to authenticate: Fail to create new session.");

        // Execute system under test.
        recorder.diagnosticLog(lines);

        // Verify results.
        final List<String> kept = recorder.getDiagnosticLog().orElseThrow();
        final List<String> markers = kept.stream().filter(line -> line.contains("lines left out")).toList();
        assertEquals(1, markers.size(), "one line must mark where lines were left out: " + markers);
        final int marker = kept.indexOf(markers.get(0));
        final int omitted = lines.size() - (kept.size() - 1);
        assertEquals("INFO: (" + omitted + " lines left out)", markers.get(0), "the marker must say how many lines were left out");
        assertEquals(lines.subList(0, marker), kept.subList(0, marker), "the lines before the marker must be the start of the log, in order");
        assertEquals(lines.subList(lines.size() - (kept.size() - marker - 1), lines.size()), kept.subList(marker + 1, kept.size()), "the lines after the marker must be the end of the log, in order");
        assertEquals("WARN: Unable to authenticate: Fail to create new session.", kept.get(kept.size() - 1), "the failure, which ends the log, must be kept");
        assertTrue(characters(kept.subList(0, marker)) > 0 && characters(kept.subList(marker + 1, kept.size())) > characters(kept.subList(0, marker)), "more of the end must be kept than of the start, as the step lines and the failure are at the end");
        assertTrue(characters(kept) - characters(markers) <= AttemptRecorder.MAX_DIAGNOSTIC_LOG_LENGTH, "the lines kept must fit the limit: " + characters(kept));
        assertEquals(lines.size() - 2, recorder.getLoggedProblems().orElseThrow().warnings(), "the counts must still cover every line, kept or not");
    }

    @Test
    public void testDiagnosticLogWithinTheLimitIsKeptWhole()
    {
        // Setup test fixture: a log just within the limit.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final List<String> lines = new ArrayList<>();
        while (characters(lines) + 102 <= AttemptRecorder.MAX_DIAGNOSTIC_LOG_LENGTH) {
            lines.add("DEBUG: " + "x".repeat(94));
        }

        // Execute system under test.
        recorder.diagnosticLog(lines);

        // Verify results.
        assertEquals(lines, recorder.getDiagnosticLog().orElseThrow(), "a log within the limit must be kept whole");
    }

    /**
     * @return the number of characters in the lines, each followed by a line break, as the log is shown.
     */
    private static int characters(final List<String> lines)
    {
        return lines.stream().mapToInt(line -> line.length() + 1).sum();
    }

    @Test
    public void testNoDiagnosticLogBeforeItIsRecorded()
    {
        assertTrue(new AttemptRecorder("example.com", "remote.example.org").getDiagnosticLog().isEmpty(), "an attempt that has not handed over its log has none to show");
    }

    @Test
    public void testSafelyKeepsAFailedRecordingFromTheConnectionCode()
    {
        // Setup test fixture: a recording that breaks a milestone's rules, as a later change could.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        assertDoesNotThrow(() -> AttemptRecorder.safely(() -> recorder.stream(AttemptMilestones.Stream.streamError(null))), "a recording that fails must never change how the connection proceeds");

        // Verify results.
        assertTrue(recorder.snapshot().getStream().isEmpty(), "a recording that failed records nothing");
    }

    @Test
    public void testAwaitFinishedReturnsWhenTheDiagnosticLogIsRecorded() throws Exception
    {
        // Setup test fixture: an attempt that another thread finishes a little later, recording its diagnostic log last,
        // as LocalOutgoingServerSession.authenticateDomain does.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> {
                Thread.sleep(200);
                recorder.finish(AttemptRecorder.Outcome.FAILED);
                recorder.diagnosticLog(List.of("WARN: Unable to authenticate."));
                return null;
            });

            // Execute system under test.
            final boolean finished = recorder.awaitFinished(Duration.ofSeconds(10));

            // Verify results.
            assertTrue(finished, "the wait must end as soon as the attempt finishes");
            assertTrue(recorder.getLoggedProblems().isPresent(), "once the wait ends, the attempt's log must be available, or the log check would read as not observed");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testAwaitFinishedDoesNotEndAtTheOutcomeAlone() throws Exception
    {
        // Setup test fixture: the connection code has set the outcome, but the attempt has not handed over its log yet.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");
        recorder.finish(AttemptRecorder.Outcome.NEW_SESSION);

        // Execute system under test.
        final boolean finished = recorder.awaitFinished(Duration.ofMillis(100));

        // Verify results.
        assertFalse(finished, "the attempt is not finished until its diagnostic log is recorded, which authenticateDomain does last");
    }

    @Test
    public void testAwaitFinishedGivesUpAfterTheTimeout() throws Exception
    {
        // Setup test fixture.
        final AttemptRecorder recorder = new AttemptRecorder("example.com", "remote.example.org");

        // Execute system under test.
        final boolean finished = recorder.awaitFinished(Duration.ofMillis(100));

        // Verify results.
        assertFalse(finished, "an attempt that does not finish in time must be reported as still running");
    }

    @Test
    public void testDescribeOfAnEmptyAttemptIsEmpty()
    {
        assertTrue(new AttemptRecorder("example.com", "remote.example.org").describe().isEmpty(), "an attempt with no milestones adds no lines to the diagnostic log");
    }
}
