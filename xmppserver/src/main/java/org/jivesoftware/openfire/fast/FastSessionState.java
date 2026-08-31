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
package org.jivesoftware.openfire.fast;

import org.jivesoftware.openfire.session.LocalSession;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Typed access to FAST state that exists only for one SASL2 authentication attempt. */
public final class FastSessionState {

    private static final String REQUESTED_MECHANISM = "fast-request-token-mechanism";
    private static final String INVALIDATE = "fast-invalidate";
    private static final String REPLAY_COUNT = "fast-count";
    private static final String EXPECTED_USERNAME = "fast-expected-username";
    private static final String CLIENT_ID = "fast-client-id";
    private static final String AUTHENTICATED_CLIENT_ID = "fast-authenticated-client-id";
    private static final String ROTATED_TOKEN = "fast-rotated-token";

    private FastSessionState() {
    }

    public static void setRequestedMechanism(@Nonnull final LocalSession session, @Nonnull final String mechanism) {
        session.setSessionData(REQUESTED_MECHANISM, mechanism);
    }

    @Nullable
    public static String getRequestedMechanism(@Nonnull final LocalSession session) {
        return value(session, REQUESTED_MECHANISM, String.class);
    }

    public static void setInvalidate(@Nonnull final LocalSession session) {
        session.setSessionData(INVALIDATE, Boolean.TRUE);
    }

    public static boolean isInvalidateRequested(@Nonnull final LocalSession session) {
        return Boolean.TRUE.equals(value(session, INVALIDATE, Boolean.class));
    }

    public static void setReplayCount(@Nonnull final LocalSession session, final long count) {
        session.setSessionData(REPLAY_COUNT, count);
    }

    @Nullable
    public static Long getReplayCount(@Nonnull final LocalSession session) {
        return value(session, REPLAY_COUNT, Long.class);
    }

    public static void setExpectedUsername(@Nonnull final LocalSession session, @Nonnull final String expectedUsername) {
        session.setSessionData(EXPECTED_USERNAME, expectedUsername);
    }

    @Nullable
    public static String getExpectedUsername(@Nonnull final LocalSession session) {
        return value(session, EXPECTED_USERNAME, String.class);
    }

    public static void setClientId(@Nonnull final LocalSession session, @Nonnull final String clientId) {
        session.setSessionData(CLIENT_ID, clientId);
    }

    @Nullable
    public static String getClientId(@Nonnull final LocalSession session) {
        return value(session, CLIENT_ID, String.class);
    }

    public static void setAuthenticatedClientId(@Nonnull final LocalSession session, @Nonnull final String clientId) {
        session.setSessionData(AUTHENTICATED_CLIENT_ID, clientId);
    }

    @Nullable
    public static String getAuthenticatedClientId(@Nonnull final LocalSession session) {
        return value(session, AUTHENTICATED_CLIENT_ID, String.class);
    }

    public static void setRotatedToken(@Nonnull final LocalSession session, @Nonnull final FastToken token) {
        session.setSessionData(ROTATED_TOKEN, token);
    }

    @Nullable
    public static FastToken getRotatedToken(@Nonnull final LocalSession session) {
        return value(session, ROTATED_TOKEN, FastToken.class);
    }

    /** Clears input state before parsing a new authentication request. */
    public static void clearRequest(@Nonnull final LocalSession session) {
        session.removeSessionData(REQUESTED_MECHANISM);
        session.removeSessionData(INVALIDATE);
        session.removeSessionData(REPLAY_COUNT);
        session.removeSessionData(EXPECTED_USERNAME);
        session.removeSessionData(CLIENT_ID);
    }

    /** Clears all state after an authentication attempt has completed. */
    public static void clearAuthenticationAttempt(@Nonnull final LocalSession session) {
        clearRequest(session);
        session.removeSessionData(AUTHENTICATED_CLIENT_ID);
        session.removeSessionData(ROTATED_TOKEN);
    }

    @Nullable
    private static <T> T value(final LocalSession session, final String key, final Class<T> type) {
        final Object value = session.getSessionData(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
