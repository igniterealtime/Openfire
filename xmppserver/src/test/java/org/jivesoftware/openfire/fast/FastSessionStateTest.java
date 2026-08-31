/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed Under The Apache License, Version 2.0 (the "License");
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
package org.jivesoftware.openfire.fast;

import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FastSessionStateTest {

    @Test
    void exposesTypedValuesAndClearsTheAuthenticationAttempt() {
        final LocalSession session = mock(LocalSession.class);
        final Map<String, Object> values = new HashMap<>();
        doAnswer(invocation -> values.put(invocation.getArgument(0), invocation.getArgument(1)))
            .when(session).setSessionData(anyString(), any());
        when(session.getSessionData(anyString())).thenAnswer(invocation -> values.get(invocation.getArgument(0)));
        when(session.removeSessionData(anyString())).thenAnswer(invocation -> values.remove(invocation.getArgument(0)));
        final FastToken token = new FastToken("user", FastTokenManager.HT_SHA_256_NONE,
            "token".getBytes(java.nio.charset.StandardCharsets.UTF_8), java.time.Instant.now().plusSeconds(60));

        FastSessionState.setRequestedMechanism(session, FastTokenManager.HT_SHA_256_NONE);
        FastSessionState.setExpectedUsername(session, "user");
        FastSessionState.setClientId(session, "client-a");
        FastSessionState.setInvalidate(session);
        FastSessionState.setReplayCount(session, 7L);
        FastSessionState.setAuthenticatedClientId(session, "client");
        FastSessionState.setRotatedToken(session, token);

        assertEquals(FastTokenManager.HT_SHA_256_NONE, FastSessionState.getRequestedMechanism(session));
        assertEquals("user", FastSessionState.getExpectedUsername(session));
        assertEquals("client-a", FastSessionState.getClientId(session));
        assertTrue(FastSessionState.isInvalidateRequested(session));
        assertEquals(7L, FastSessionState.getReplayCount(session));
        assertEquals("client", FastSessionState.getAuthenticatedClientId(session));
        assertSame(token, FastSessionState.getRotatedToken(session));

        FastSessionState.clearAuthenticationAttempt(session);
        assertNull(FastSessionState.getRequestedMechanism(session));
        assertNull(FastSessionState.getExpectedUsername(session));
        assertNull(FastSessionState.getClientId(session));
        assertFalse(FastSessionState.isInvalidateRequested(session));
        assertNull(FastSessionState.getReplayCount(session));
        assertNull(FastSessionState.getAuthenticatedClientId(session));
        assertNull(FastSessionState.getRotatedToken(session));
    }
}
