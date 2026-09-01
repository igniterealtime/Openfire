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
import java.util.Optional;
import java.util.Set;

/**
 * Typed access to the FAST (XEP-0484) state that is held on a session.
 *
 * Most of this state belongs to a single SASL2 authentication attempt and is cleared between attempts, by
 * {@link #clearRequest(LocalSession)} before a new request is parsed and by
 * {@link #clearAuthenticationAttempt(LocalSession)} once one has completed. The set of advertised mechanisms is the
 * exception: it belongs to the session and survives for as long as it does.
 */
public final class FastSessionState {

    private static final String REQUESTED_MECHANISM = "fast-request-token-mechanism";
    private static final String INVALIDATE = "fast-invalidate";
    private static final String REPLAY_COUNT = "fast-count";
    private static final String EXPECTED_USERNAME = "fast-expected-username";
    private static final String CLIENT_ID = "fast-client-id";
    private static final String AUTHENTICATED_CLIENT_ID = "fast-authenticated-client-id";
    private static final String ROTATED_TOKEN = "fast-rotated-token";

    /**
     * The FAST mechanisms that were advertised to this session in the XEP-0484 inline feature.
     *
     * Unlike the other state in this class, this belongs to the session rather than to a single authentication
     * attempt, so it is not cleared between attempts.
     */
    private static final String ADVERTISED_MECHANISMS = "FastMechanismsOfferedByServer";

    private FastSessionState() {
    }

    /**
     * Records the FAST mechanism for which the client has asked to be issued a token.
     *
     * @param session   the session that is authenticating (cannot be null)
     * @param mechanism the requested FAST mechanism name (cannot be null)
     */
    public static void setRequestedMechanism(@Nonnull final LocalSession session, @Nonnull final String mechanism) {
        session.setSessionData(REQUESTED_MECHANISM, mechanism);
    }

    /**
     * Returns the FAST mechanism for which the client has asked to be issued a token.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return the requested FAST mechanism name, or null if no token was requested
     */
    @Nullable
    public static String getRequestedMechanism(@Nonnull final LocalSession session) {
        return value(session, REQUESTED_MECHANISM, String.class);
    }

    /**
     * Records that the client has asked for the token it is authenticating with to be invalidated.
     *
     * @param session the session that is authenticating (cannot be null)
     */
    public static void setInvalidate(@Nonnull final LocalSession session) {
        session.setSessionData(INVALIDATE, Boolean.TRUE);
    }

    /**
     * Returns whether the client has asked for the token it is authenticating with to be invalidated.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return true if invalidation was requested
     */
    public static boolean isInvalidateRequested(@Nonnull final LocalSession session) {
        return Boolean.TRUE.equals(value(session, INVALIDATE, Boolean.class));
    }

    /**
     * Records the replay counter that the client supplied with its token.
     *
     * @param session the session that is authenticating (cannot be null)
     * @param count   the counter value, which is always positive
     */
    public static void setReplayCount(@Nonnull final LocalSession session, final long count) {
        session.setSessionData(REPLAY_COUNT, count);
    }

    /**
     * Returns the replay counter that the client supplied with its token.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return the counter value, or null if the client supplied none
     */
    @Nullable
    public static Long getReplayCount(@Nonnull final LocalSession session) {
        return value(session, REPLAY_COUNT, Long.class);
    }

    /**
     * Records the username that the client claimed in the stream's 'from' attribute.
     *
     * The authcid in a FAST initiator message is checked against this value, so that a token cannot be presented for
     * an account other than the one the stream claims.
     *
     * @param session          the session that is authenticating (cannot be null)
     * @param expectedUsername the local username derived from the stream's 'from' attribute (cannot be null)
     */
    public static void setExpectedUsername(@Nonnull final LocalSession session, @Nonnull final String expectedUsername) {
        session.setSessionData(EXPECTED_USERNAME, expectedUsername);
    }

    /**
     * Returns the username that the client claimed in the stream's 'from' attribute.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return the expected local username, or null if none was recorded
     */
    @Nullable
    public static String getExpectedUsername(@Nonnull final LocalSession session) {
        return value(session, EXPECTED_USERNAME, String.class);
    }

    /**
     * Records the client identifier that this authentication attempt applies to, taken from the 'id' attribute of the
     * SASL2 user-agent element. FAST tokens are issued and looked up per client.
     *
     * @param session  the session that is authenticating (cannot be null)
     * @param clientId the user-agent identifier (cannot be null)
     */
    public static void setClientId(@Nonnull final LocalSession session, @Nonnull final String clientId) {
        session.setSessionData(CLIENT_ID, clientId);
    }

    /**
     * Returns the client identifier that this authentication attempt applies to.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return the user-agent identifier, or null if none was recorded
     */
    @Nullable
    public static String getClientId(@Nonnull final LocalSession session) {
        return value(session, CLIENT_ID, String.class);
    }

    /**
     * Records the client identifier whose token was successfully validated, as established by the SASL mechanism
     * rather than claimed by the client.
     *
     * @param session  the session that is authenticating (cannot be null)
     * @param clientId the identifier of the client whose token was used (cannot be null)
     */
    public static void setAuthenticatedClientId(@Nonnull final LocalSession session, @Nonnull final String clientId) {
        session.setSessionData(AUTHENTICATED_CLIENT_ID, clientId);
    }

    /**
     * Returns the client identifier whose token was successfully validated.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return the identifier of the client whose token was used, or null if no token was validated
     */
    @Nullable
    public static String getAuthenticatedClientId(@Nonnull final LocalSession session) {
        return value(session, AUTHENTICATED_CLIENT_ID, String.class);
    }

    /**
     * Records a replacement token that was issued while validating the token the client presented, for inclusion in
     * the SASL2 success response.
     *
     * @param session the session that is authenticating (cannot be null)
     * @param token   the newly issued token (cannot be null)
     */
    public static void setRotatedToken(@Nonnull final LocalSession session, @Nonnull final FastToken token) {
        session.setSessionData(ROTATED_TOKEN, token);
    }

    /**
     * Returns the replacement token that was issued while validating the token the client presented.
     *
     * @param session the session that is authenticating (cannot be null)
     * @return the newly issued token, or null if no token was rotated
     */
    @Nullable
    public static FastToken getRotatedToken(@Nonnull final LocalSession session) {
        return value(session, ROTATED_TOKEN, FastToken.class);
    }

    /**
     * Records the FAST mechanisms that were advertised to this session in the XEP-0484 inline feature.
     *
     * A mechanism that a session was not offered cannot be selected or requested by it, so this is what an inbound
     * selection is validated against.
     *
     * @param session    the session the mechanisms were advertised to (cannot be null)
     * @param mechanisms the advertised FAST mechanism names, possibly empty (cannot be null)
     */
    public static void setAdvertisedMechanisms(@Nonnull final LocalSession session, @Nonnull final Set<String> mechanisms) {
        session.setSessionData(ADVERTISED_MECHANISMS, mechanisms);
    }

    /**
     * Returns the FAST mechanisms that were advertised to this session in the XEP-0484 inline feature.
     *
     * An empty Optional means that no advertisement has happened yet, which is distinct from an advertisement that
     * offered no FAST mechanisms at all.
     *
     * @param session the session the mechanisms were advertised to (cannot be null)
     * @return the advertised FAST mechanism names, or an empty Optional if nothing has been advertised yet
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static Optional<Set<String>> getAdvertisedMechanisms(@Nonnull final LocalSession session) {
        final Object value = session.getSessionData(ADVERTISED_MECHANISMS);
        return value instanceof Set ? Optional.of((Set<String>) value) : Optional.empty();
    }

    /**
     * Clears input state before parsing a new authentication request.
     *
     * @param session the session that is authenticating (cannot be null)
     */
    public static void clearRequest(@Nonnull final LocalSession session) {
        session.removeSessionData(REQUESTED_MECHANISM);
        session.removeSessionData(INVALIDATE);
        session.removeSessionData(REPLAY_COUNT);
        session.removeSessionData(EXPECTED_USERNAME);
        session.removeSessionData(CLIENT_ID);
    }

    /**
     * Clears all state after an authentication attempt has completed.
     *
     * @param session the session that was authenticating (cannot be null)
     */
    public static void clearAuthenticationAttempt(@Nonnull final LocalSession session) {
        clearRequest(session);
        session.removeSessionData(AUTHENTICATED_CLIENT_ID);
        session.removeSessionData(ROTATED_TOKEN);
    }

    /**
     * Returns a session data value, or null when it is absent or not of the expected type.
     *
     * @param session the session to read from (cannot be null)
     * @param key     the session data key (cannot be null)
     * @param type    the expected type of the value (cannot be null)
     * @param <T>     the expected type of the value
     * @return the value, or null
     */
    @Nullable
    private static <T> T value(final LocalSession session, final String key, final Class<T> type) {
        final Object value = session.getSessionData(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }
}
