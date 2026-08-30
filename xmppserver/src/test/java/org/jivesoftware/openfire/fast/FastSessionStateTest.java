/* Copyright (C) 2026 Ignite Realtime Foundation. Licensed under the Apache License, Version 2.0. */
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
        FastSessionState.setClientId(session, "client-a");
        FastSessionState.setInvalidate(session);
        FastSessionState.setReplayCount(session, 7L);
        FastSessionState.setAuthenticatedClientId(session, "client");
        FastSessionState.setRotatedToken(session, token);

        assertEquals(FastTokenManager.HT_SHA_256_NONE, FastSessionState.getRequestedMechanism(session));
        assertEquals("client-a", FastSessionState.getClientId(session));
        assertTrue(FastSessionState.isInvalidateRequested(session));
        assertEquals(7L, FastSessionState.getReplayCount(session));
        assertEquals("client", FastSessionState.getAuthenticatedClientId(session));
        assertSame(token, FastSessionState.getRotatedToken(session));

        FastSessionState.clearAuthenticationAttempt(session);
        assertNull(FastSessionState.getRequestedMechanism(session));
        assertNull(FastSessionState.getClientId(session));
        assertFalse(FastSessionState.isInvalidateRequested(session));
        assertNull(FastSessionState.getReplayCount(session));
        assertNull(FastSessionState.getAuthenticatedClientId(session));
        assertNull(FastSessionState.getRotatedToken(session));
    }
}
