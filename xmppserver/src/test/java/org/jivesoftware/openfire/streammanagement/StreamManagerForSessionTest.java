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
package org.jivesoftware.openfire.streammanagement;

import org.jivesoftware.openfire.http.HttpSession;
import org.jivesoftware.openfire.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * Verifies which stream manager {@link StreamManager#forSession(LocalSession)} creates for each type of session.
 *
 * Session classes that are tied to one end of a stream return the stream manager of that end from their
 * {@code getStreamManager()}, by casting the one that this method creates. These tests make sure that such a cast
 * cannot fail.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class StreamManagerForSessionTest
{
    /**
     * Verifies that every type of session other than a connection that Openfire opened to another server gets a stream
     * manager that answers requests of the peer.
     */
    @ParameterizedTest
    @ValueSource(classes = {LocalClientSession.class, HttpSession.class, LocalIncomingServerSession.class, LocalComponentSession.class, LocalConnectionMultiplexerSession.class})
    public void sessionGetsAnsweringStreamManager(final Class<? extends LocalSession> sessionType)
    {
        // Setup test fixture.
        final LocalSession session = mock(sessionType);

        // Execute system under test.
        final StreamManager result = StreamManager.forSession(session);

        // Verify result.
        assertInstanceOf(AnsweringStreamManager.class, result, "A session of type " + sessionType.getSimpleName() + " must get a stream manager that answers requests of the peer.");
    }

    /**
     * Verifies that a connection that Openfire opened to another server gets a stream manager that asks the peer for
     * Stream Management.
     */
    @Test
    public void outgoingServerSessionGetsRequestingStreamManager()
    {
        // Setup test fixture.
        final LocalSession session = mock(LocalOutgoingServerSession.class);

        // Execute system under test.
        final StreamManager result = StreamManager.forSession(session);

        // Verify result.
        assertInstanceOf(RequestingStreamManager.class, result, "A connection that Openfire opened to another server must get a stream manager that asks the peer for Stream Management.");
    }
}
