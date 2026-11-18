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
package org.jivesoftware.openfire.server;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.server.diagnostics.AttemptRecorder;
import org.jivesoftware.openfire.server.diagnostics.LatestAttempts;
import org.jivesoftware.openfire.session.DomainAuthResult;
import org.jivesoftware.openfire.session.DomainPair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Verifies the link between an outgoing connection attempt and the S2S connection test: the attempt that
 * {@link OutgoingSessionPromise} authenticates with is the one the test reads back as the latest attempt.
 *
 * @author Matthew Vivian
 */
public class OutgoingSessionPromiseTest
{
    private static final DomainPair PAIR = new DomainPair("example.com", "remote.example.org");

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        // Loading OutgoingSessionPromise creates its instance, which needs caches and a server.
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
    }

    @Test
    public void testAttemptIsReadableWhileAuthenticationRuns()
    {
        // Setup test fixture.
        final LatestAttempts latestAttempts = new LatestAttempts(10);
        final AtomicReference<AttemptRecorder> usedRecorder = new AtomicReference<>();
        final AtomicReference<Optional<AttemptRecorder>> storedDuringAuthentication = new AtomicReference<>();

        // Execute system under test.
        OutgoingSessionPromise.authenticateRecordingAttempt(PAIR, latestAttempts, (pair, recorder) -> {
            usedRecorder.set(recorder);
            storedDuringAuthentication.set(latestAttempts.get(pair.getLocal(), pair.getRemote()));
            return DomainAuthResult.failure(List.of());
        });

        // Verify results.
        assertNotNull(usedRecorder.get(), "authentication must be given a recorder");
        assertSame(usedRecorder.get(), storedDuringAuthentication.get().orElse(null), "the attempt must be stored before authentication starts, so that the test can read it while it is in progress");
        assertSame(usedRecorder.get(), latestAttempts.get(PAIR.getLocal(), PAIR.getRemote()).orElse(null), "the attempt must still be stored after authentication, so that the test can read its outcome");
    }

    @Test
    public void testAuthenticationResultIsReturned()
    {
        // Setup test fixture.
        final DomainAuthResult expected = DomainAuthResult.success(List.of("DEBUG: Authentication successful."));

        // Execute system under test.
        final DomainAuthResult result = OutgoingSessionPromise.authenticateRecordingAttempt(PAIR, new LatestAttempts(10), (pair, recorder) -> expected);

        // Verify results.
        assertSame(expected, result, "the result of authentication must be passed back unchanged");
    }

    @Test
    public void testFailureToStoreTheAttemptDoesNotPreventAuthentication()
    {
        // Setup test fixture.
        final LatestAttempts failingStore = mock(LatestAttempts.class);
        doThrow(new IllegalStateException("store unavailable")).when(failingStore).put(any());
        final AtomicReference<AttemptRecorder> usedRecorder = new AtomicReference<>();

        // Execute system under test.
        final DomainAuthResult result = OutgoingSessionPromise.authenticateRecordingAttempt(PAIR, failingStore, (pair, recorder) -> {
            usedRecorder.set(recorder);
            return DomainAuthResult.success(List.of());
        });

        // Verify results.
        assertTrue(result.isSuccess(), "recording only describes the attempt, so a failure to store it must not change the connection's outcome");
        assertNotNull(usedRecorder.get(), "authentication must still run, with a usable recorder");
        assertEquals(PAIR.getRemote(), usedRecorder.get().getRemoteDomain(), "the recorder must describe the attempt being made");
    }
}
