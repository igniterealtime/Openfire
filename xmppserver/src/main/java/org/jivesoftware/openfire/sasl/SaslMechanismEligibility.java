/*
 * Copyright (C) 2005-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.keystore.TrustStore;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.CertificateManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Narrows the SASL mechanisms that this server supports down to those that a particular session may use.
 *
 * {@link SaslMechanismCatalog} answers what this deployment can do at all. This class answers what one peer, on one
 * connection, in its current state, may be offered: whether it presented a usable certificate, whether the user it
 * claims to be holds SCRAM credentials, and whether its connection can supply the channel-binding data that a
 * mechanism needs.
 *
 * Offering a mechanism that a session cannot complete is not merely untidy. A peer that selects it has to fall back,
 * and for the SCRAM family the set that was offered is itself covered by the XEP-0474 downgrade-protection hash, so
 * offering the wrong set breaks authentication outright.
 */
public class SaslMechanismEligibility
{
    /**
     * Session Data property name used to cache the SCRAM mechanism names that are usable for the user that is expected
     * to authenticate on a session.
     *
     * Determining these requires a credential lookup, which is driven by a username that an unauthenticated peer
     * supplies. A stream is typically opened more than once before authentication completes (notably after TLS
     * negotiation), and each of those regenerates the stream features. Caching the outcome for the duration of the
     * session avoids repeating that lookup on every one of them.
     *
     * Instead of using this value directly, use {@link #getScramMechanismsForSession(LocalClientSession)}, which
     * encapsulates the business logic related to this constant.
     */
    private static final String SCRAM_MECHANISMS_FOR_SESSION = "SaslScramMechanismsForExpectedUser";

    /**
     * The SCRAM mechanism names that were determined to be usable, together with the expected username that they were
     * derived from. Both are held in one value so that a reader cannot observe a set that belongs to a different user
     * than the one it is paired with.
     */
    private record CachedScramMechanisms(@Nullable String expectedUsername, @Nonnull Set<String> mechanisms) {}

    private SaslMechanismEligibility() {
    }

    /**
     * Returns a Set of SASL mechanism names are applicable to advertise to the given session.
     *
     * When the session is already authenticated, SASL mechanisms are no longer to be advertised. An empty collection is
     * returned for these sessions.
     *
     * @param session the local session for which to determine applicable SASL mechanisms (cannot be null)
     * @return a set of SASL mechanism names; never null, possibly empty
     */
    public static Set<String> getAdvertisableSASLMechanisms(@Nonnull final LocalSession session)
    {
        // If the session is already authenticated, do not list anything.
        return session.isAuthenticated() ? Collections.emptySet() : getAvailableMechanismsForSession(session);
    }

    /**
     * Returns the set of SASL mechanisms available for the given session.
     *
     * @param session the session (cannot be null).
     * @return a set of available mechanism names for the session (never null, possibly empty).
     */
    public static Set<String> getAvailableMechanismsForSession( final LocalSession session )
    {
        if ( session instanceof ClientSession )
        {
            return getAvailableMechanismsForClientSession( (ClientSession) session );
        }
        else if ( session instanceof LocalIncomingServerSession )
        {
            return getAvailableMechanismsForServerSession( (LocalIncomingServerSession) session );
        }
        else
        {
            return Collections.emptySet();
        }
    }

    /**
     * Determines and returns the set of SASL mechanisms that are available for a given client session. This includes
     * mechanisms from the list of supported mechanisms, applying additional checks to ensure mechanism-specific
     * requirements (e.g., encryption and certificate validation) are met.
     *
     * @param session The client session for which available SASL mechanisms need to be determined.
     *                Must not be null.
     * @return A set of available SASL mechanism names for the specified client session.
     *         Will never be null but might be empty if no mechanisms are available.
     */
    private static Set<String> getAvailableMechanismsForClientSession(@Nonnull final ClientSession session )
    {
        final LocalClientSession localClientSession = (LocalClientSession) session;
        final Connection connection = localClientSession.getConnection();
        assert connection != null; // While the client is performing a SASL negotiation, the connection can't be null.
        final Set<String> result = new HashSet<>();

        final Set<String> mechanismsForWhichCredentialsAreAvailable = getScramMechanismsForSession(localClientSession);

        for (String mech : SaslMechanismCatalog.getSupportedMechanisms())
        {
            // Prevent offering EXTERNAL mechanism when no usable peer certificate is available.
            if (mech.equals("EXTERNAL")) {
                boolean trustedCert = false;
                if (session.isEncrypted()) {
                    if ( SASLAuthentication.SKIP_PEER_CERT_REVALIDATION_CLIENT.getValue() ) {
                        // Trust that the peer certificate has been validated when TLS got established.
                        trustedCert = connection.getPeerCertificates() != null && connection.getPeerCertificates().length > 0;
                    } else {
                        // Re-evaluate the validity of the peer certificate.
                        final TrustStore trustStore = connection.getConfiguration().getTrustStore();
                        trustedCert = trustStore.isTrusted( connection.getPeerCertificates() );
                    }
                }
                if ( !trustedCert ) {
                    continue; // Do not offer EXTERNAL.
                }
            }

            // Prevent offering SCRAM mechanism when no mechanism-specific credentials (salt, iterations, keys) are available.
            if (MechanismName.isScram(mech) && !mechanismsForWhichCredentialsAreAvailable.contains(mech)) {
                continue;
            }

            if (MechanismName.requiresChannelBinding(mech)) {
                // Channel binding would be a binding to TLS, thus encryption is required for channel binding.
                if (!session.isEncrypted()) { // This ought to be redundant, as getSupportedChannelBindingTypes() will return an empty set if not encrypted.
                    continue;
                }
                final String requiredCbType = MechanismName.requiredChannelBindingType(mech);
                if (requiredCbType != null) {
                    // Mechanism encodes a specific CB type (e.g. HT-*-UNIQ): only offer it when
                    // that exact type is available on this connection.
                    if (!connection.getSupportedChannelBindingTypes().contains(requiredCbType)) {
                        continue; // Do not offer this channel-binding variant.
                    }
                } else {
                    // Mechanism uses runtime-negotiated CB (e.g. SCRAM-SHA-1-PLUS): require at
                    // least one CB type to be available on this connection.
                    if (connection.getSupportedChannelBindingTypes().isEmpty()) {
                        continue; // Do not offer channel-binding variants.
                    }
                }
            }

            // All fine: this mechanism can be offered.
            result.add(mech);
        }
        return result;
    }

    /**
     * Returns the SCRAM mechanism names that are usable for the user that is expected to authenticate on the provided
     * session, including the channel binding variant of each.
     *
     * The result is cached on the session, keyed by the expected username. When that username changes (as it does when
     * a peer restates, omits or changes its claimed identity on a new stream, or once it has authenticated), the cached
     * value is not used and the mechanisms are determined again.
     *
     * Note that a credential that is added or removed while a session is negotiating is not reflected until the next
     * time the expected username changes. That window is brief, and the alternative is to repeat the lookup on data
     * that an unauthenticated peer controls.
     *
     * @param session the session for which to determine usable SCRAM mechanisms (cannot be null).
     * @return SCRAM mechanism names (never null, possibly empty).
     */
    @Nonnull
    public static Set<String> getScramMechanismsForSession(@Nonnull final LocalClientSession session)
    {
        // When tailoring is disabled, the session is treated as if no user could be identified.
        final String expectedUsername = SASLAuthentication.SCRAM_MECHANISMS_PER_USER.getValue()
            ? session.getExpectedUsername().orElse(null)
            : null;

        final Object cached = session.getSessionData(SCRAM_MECHANISMS_FOR_SESSION);
        if (cached instanceof CachedScramMechanisms entry && Objects.equals(entry.expectedUsername(), expectedUsername)) {
            return entry.mechanisms();
        }

        final Set<String> available = expectedUsername == null
            ? AuthFactory.getFallbackScramMechanisms()
            : AuthFactory.getScramMechanisms(expectedUsername);

        // Expand to include the channel binding equivalent of each mechanism, which shares its credentials.
        final Set<String> result = available.stream()
            .flatMap(mechanism -> Stream.of(mechanism, mechanism + "-PLUS"))
            .collect(Collectors.toUnmodifiableSet());

        session.setSessionData(SCRAM_MECHANISMS_FOR_SESSION, new CachedScramMechanisms(expectedUsername, result));
        return result;
    }

    /**
     * Determines the set of available SASL mechanisms for the given server session. This method checks the session's
     * encryption status and examines the trust relationship to determine if specific mechanisms (such as SASL EXTERNAL)
     * can be offered.
     *
     * @param session the server session for which the available mechanisms are to be determined.
     *                Must not be null.
     * @return a set of SASL mechanism names that can be offered for the specified session.
     *         If no mechanisms are available, an empty set is returned.
     */
    private static Set<String> getAvailableMechanismsForServerSession(@Nonnull final LocalIncomingServerSession session)
    {
        final Set<String> result = new HashSet<>();

        // Check if EXTERNAL is enabled in the supported mechanisms configuration
        if (!SaslMechanismCatalog.getSupportedMechanisms().contains("EXTERNAL")) {
            return result;
        }

        if (session.isEncrypted()) {
            final Connection connection   = session.getConnection();
            final TrustStore trustStore   = connection.getConfiguration().getTrustStore();
            final X509Certificate trusted = trustStore.getEndEntityCertificate( session.getConnection().getPeerCertificates() );

            boolean haveTrustedCertificate = trusted != null;
            if (trusted != null && session.getDefaultIdentity() != null) {
                haveTrustedCertificate = CertificateManager.verifyCertificate(trusted, session.getDefaultIdentity());
            }
            if (haveTrustedCertificate) {
                // Offer SASL EXTERNAL only if TLS has already been negotiated and the peer has a trusted cert.
                result.add("EXTERNAL");
            }
        }
        return result;
    }

    /**
     * Returns the channel-binding types to advertise to the given session.
     *
     * Channel-binding types are announced only when at least one channel-binding-capable mechanism is being
     * offered. The types themselves are those the session's connection can supply in its current state, which is
     * not necessarily every type for which a provider is registered: a connection that is not encrypted, or whose
     * transport cannot supply channel-binding data, supports none.
     *
     * @param session the session the types would be advertised to (cannot be null).
     * @param advertisableSASLMechanisms the SASL mechanism names being offered (cannot be null).
     * @return the channel-binding type names to advertise; never null, possibly empty.
     * @see <a href="https://xmpp.org/extensions/xep-0440.html">XEP-0440: SASL Channel-Binding Type Capability</a>
     */
    @Nonnull
    public static Set<String> getAdvertisableChannelBindingTypes(@Nonnull final LocalSession session, @Nonnull final Set<String> advertisableSASLMechanisms)
    {
        if (advertisableSASLMechanisms.stream().noneMatch(MechanismName::requiresChannelBinding)) {
            return Set.of();
        }
        final Connection connection = session.getConnection();
        if (connection == null) {
            return Set.of();
        }
        return Set.copyOf(connection.getSupportedChannelBindingTypes());
    }
}
