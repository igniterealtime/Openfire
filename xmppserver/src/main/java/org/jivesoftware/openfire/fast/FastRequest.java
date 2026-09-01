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

import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.sasl.Failure;
import org.jivesoftware.openfire.sasl.MechanismName;
import org.jivesoftware.openfire.sasl.SaslFailureException;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The FAST (XEP-0484) content of a SASL2 {@code <authenticate/>} element: a request for a new token, an
 * authentication with an existing one, or both.
 *
 * Parsing is separated from application, so that the whole element is validated before any of it is recorded on the
 * session. {@link #from(Element, String, String, LocalClientSession)} reads and validates;
 * {@link #applyTo(LocalSession)} writes the result through {@link FastSessionState}.
 *
 * This mirrors {@code Bind2Request}, which occupies the same position in the handling of {@code <authenticate/>}.
 */
public final class FastRequest
{
    private static final Logger Log = LoggerFactory.getLogger(FastRequest.class);

    @Nullable private final String expectedUsername;
    @Nullable private final String clientId;
    @Nullable private final String requestedMechanism;
    @Nullable private final Long replayCount;
    private final boolean invalidate;

    /**
     * Constructs a parsed request. Instances are created only by {@link #from(Element, String, String, LocalClientSession)},
     * which has already validated every value passed here.
     *
     * @param expectedUsername   the local username claimed in the stream's 'from' attribute, or null if this request
     *                           carried no identity prerequisites
     * @param clientId           the SASL2 user-agent id, or null if this request carried no identity prerequisites
     * @param requestedMechanism the FAST mechanism a new token was requested for, or null if none was requested
     * @param replayCount        the replay counter supplied by the client, or null if none was supplied
     * @param invalidate         whether the client asked for the token it authenticated with to be invalidated
     */
    private FastRequest(@Nullable final String expectedUsername, @Nullable final String clientId,
                        @Nullable final String requestedMechanism, @Nullable final Long replayCount,
                        final boolean invalidate)
    {
        this.expectedUsername = expectedUsername;
        this.clientId = clientId;
        this.requestedMechanism = requestedMechanism;
        this.replayCount = replayCount;
        this.invalidate = invalidate;
    }

    /**
     * Parses and validates the FAST content of a SASL2 {@code <authenticate/>} element.
     *
     * Returns {@code null} when the element carries no FAST content and the selected mechanism is not a FAST
     * mechanism, in which case there is nothing for the caller to apply.
     *
     * The identity prerequisites of XEP-0484 § 4.1 (a local authenticating JID in the stream's 'from' attribute, and a
     * SASL2 user-agent id) are enforced here, for both token authentication and token requests.
     *
     * @param doc           the {@code <authenticate/>} element (cannot be null).
     * @param mechanismName the upper-cased name of the selected SASL mechanism (cannot be null).
     * @param userAgentId   the 'id' of the SASL2 {@code <user-agent/>} element, or null if the client supplied none.
     * @param session       the session that is authenticating (cannot be null).
     * @return the parsed request, or {@code null} if the element carries no FAST content.
     * @throws SaslFailureException if the FAST content is malformed, or names a mechanism that was not offered.
     */
    @Nullable
    public static FastRequest from(@Nonnull final Element doc,
                                   @Nonnull final String mechanismName,
                                   @Nullable final String userAgentId,
                                   @Nonnull final LocalClientSession session) throws SaslFailureException
    {
        final boolean isFastAuth = MechanismName.isFast(mechanismName);
        final Element requestTokenEl = doc.element(new QName("request-token", new Namespace("", FastTokenManager.NAMESPACE)));
        final Element fastEl = doc.element(new QName("fast", new Namespace("", FastTokenManager.NAMESPACE)));

        if (!isFastAuth && requestTokenEl == null && fastEl == null) {
            return null;
        }

        // XEP-0484 § 4.1: clients using FAST provide their JID in the stream's 'from' attribute and a user-agent id.
        String expectedUsername = null;
        String clientId = null;
        if (isFastAuth || requestTokenEl != null) {
            final Optional<String> expected = session.getExpectedUsername();
            if (userAgentId == null || expected.isEmpty()) {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST,
                    "FAST requires a local authenticating JID in the stream 'from' attribute and a valid user-agent id");
            }
            expectedUsername = expected.get();
            clientId = userAgentId;
        }

        // XEP-0484: <request-token xmlns='urn:xmpp:fast:0' mechanism='...'/>
        String requestedMechanism = null;
        if (requestTokenEl != null) {
            final String rawMechanism = requestTokenEl.attributeValue("mechanism");
            if (rawMechanism == null || !MechanismName.isFast(rawMechanism)) {
                throw new SaslFailureException(Failure.MALFORMED_REQUEST,
                    "FAST token requests must specify a known mechanism");
            }
            final String normalizedMechanism = rawMechanism.toUpperCase(Locale.ROOT);
            final Set<String> offered = FastSessionState.getAdvertisedMechanisms(session).orElse(Collections.emptySet());
            if (!FastTokenManager.ENABLE_FAST.getValue() || !offered.contains(normalizedMechanism)) {
                throw new SaslFailureException(Failure.INVALID_MECHANISM,
                    "The requested FAST mechanism was not offered for this session");
            }
            requestedMechanism = normalizedMechanism;
        }

        // XEP-0484: <fast xmlns='urn:xmpp:fast:0' [count='..'] [invalidate='true']/>
        if (isFastAuth && fastEl == null) {
            throw new SaslFailureException(Failure.MALFORMED_REQUEST,
                "FAST token authentication requires a <fast/> element");
        }

        Long replayCount = null;
        boolean invalidate = false;
        if (fastEl != null) {
            final String countAttr = fastEl.attributeValue("count");
            if (countAttr != null) {
                try {
                    final long count = Long.parseLong(countAttr);
                    if (count <= 0) throw new NumberFormatException();
                    replayCount = count;
                } catch (final NumberFormatException e) {
                    throw new SaslFailureException(Failure.MALFORMED_REQUEST, "FAST count must be a positive integer");
                }
            }
            final String invalidateAttr = fastEl.attributeValue("invalidate");
            invalidate = "true".equalsIgnoreCase(invalidateAttr) || "1".equals(invalidateAttr);
        }

        return new FastRequest(expectedUsername, clientId, requestedMechanism, replayCount, invalidate);
    }

    /**
     * Records this request on the session, for the SASL mechanism implementation and for the handling of a successful
     * authentication to act on.
     *
     * @param session the session that is authenticating (cannot be null).
     */
    public void applyTo(@Nonnull final LocalSession session)
    {
        if (expectedUsername != null) {
            FastSessionState.setExpectedUsername(session, expectedUsername);
        }
        if (clientId != null) {
            FastSessionState.setClientId(session, clientId);
        }
        if (requestedMechanism != null) {
            FastSessionState.setRequestedMechanism(session, requestedMechanism);
            Log.debug("FAST token requested for mechanism '{}' by {}", requestedMechanism, session);
        }
        if (replayCount != null) {
            FastSessionState.setReplayCount(session, replayCount);
        }
        if (invalidate) {
            FastSessionState.setInvalidate(session);
            Log.debug("FAST token invalidation requested by {}", session);
        }
    }

    /**
     * Returns the FAST mechanism for which a new token was requested, or {@code null} if none was requested.
     *
     * @return a FAST mechanism name, or {@code null}.
     */
    @Nullable
    public String getRequestedMechanism()
    {
        return requestedMechanism;
    }

    /**
     * Returns the user-agent id that identifies the client installation, or {@code null} if this request carried no
     * identity prerequisites, which is the case for a {@code <fast/>} element on a non-FAST authentication.
     *
     * @return a client identifier, or {@code null}.
     */
    @Nullable
    public String getClientId()
    {
        return clientId;
    }

    /**
     * Returns the replay counter supplied by the client, or {@code null} if none was supplied.
     *
     * @return a positive counter value, or {@code null}.
     */
    @Nullable
    public Long getReplayCount()
    {
        return replayCount;
    }

    /**
     * Returns whether the client asked for the token it authenticated with to be invalidated.
     *
     * @return {@code true} if invalidation was requested.
     */
    public boolean isInvalidate()
    {
        return invalidate;
    }

    @Override
    public String toString()
    {
        return "FastRequest{requestedMechanism='" + requestedMechanism + "', clientId='" + clientId
            + "', replayCount=" + replayCount + ", invalidate=" + invalidate + '}';
    }
}
