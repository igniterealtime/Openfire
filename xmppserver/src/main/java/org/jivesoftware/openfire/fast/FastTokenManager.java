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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.Encryptor;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Manages FAST (XEP-0484) authentication tokens.
 *
 * Tokens are stored per user, client and mechanism in current/new slots. The recoverable token
 * string is required to verify the initiator HMAC and produce the responder HMAC.
 */
public class FastTokenManager {

    private static final Logger Log = LoggerFactory.getLogger(FastTokenManager.class);

    /** XEP-0484 namespace */
    public static final String NAMESPACE = "urn:xmpp:fast:0";

    // -------------------------------------------------------------------------
    // HT-* mechanism names (original HT draft: hash + channel-binding variants)
    // -------------------------------------------------------------------------

    /** HT-SHA-256-NONE: original HT draft, SHA-256, no channel binding. */
    public static final String HT_SHA_256_NONE = "HT-SHA-256-NONE";

    /** HT-SHA-256-UNIQ: original HT draft, SHA-256, tls-unique channel binding. */
    public static final String HT_SHA_256_UNIQ = "HT-SHA-256-UNIQ";

    /** HT-SHA-256-ENDP: original HT draft, SHA-256, tls-server-end-point channel binding. */
    public static final String HT_SHA_256_ENDP = "HT-SHA-256-ENDP";

    /** HT-SHA-256-EXPR: original HT draft, SHA-256, tls-exporter channel binding. */
    public static final String HT_SHA_256_EXPR = "HT-SHA-256-EXPR";

    /** HT-SHA-512-NONE: original HT draft, SHA-512, no channel binding. */
    public static final String HT_SHA_512_NONE = "HT-SHA-512-NONE";

    /** HT-SHA-512-UNIQ: original HT draft, SHA-512, tls-unique channel binding. */
    public static final String HT_SHA_512_UNIQ = "HT-SHA-512-UNIQ";

    /** HT-SHA-512-ENDP: original HT draft, SHA-512, tls-server-end-point channel binding. */
    public static final String HT_SHA_512_ENDP = "HT-SHA-512-ENDP";

    /** HT-SHA-512-EXPR: original HT draft, SHA-512, tls-exporter channel binding. */
    public static final String HT_SHA_512_EXPR = "HT-SHA-512-EXPR";
    public static final String HT_SHA3_512_NONE = "HT-SHA3-512-NONE";
    public static final String HT_SHA3_512_UNIQ = "HT-SHA3-512-UNIQ";
    public static final String HT_SHA3_512_ENDP = "HT-SHA3-512-ENDP";
    public static final String HT_SHA3_512_EXPR = "HT-SHA3-512-EXPR";

    // -------------------------------------------------------------------------
    // HT2-* mechanism names (draft-ietf-kitten-sasl-ht: HMAC-based variants)
    // -------------------------------------------------------------------------

    /** HT2-SHA-256-NONE: HT2 draft, SHA-256, no channel binding. */
    public static final String HT2_SHA_256_NONE = "HT2-SHA-256-NONE";

    /** HT2-SHA-256-UNIQ: HT2 draft, SHA-256, tls-unique channel binding. */
    public static final String HT2_SHA_256_UNIQ = "HT2-SHA-256-UNIQ";

    /** HT2-SHA-256-ENDP: HT2 draft, SHA-256, tls-server-end-point channel binding. */
    public static final String HT2_SHA_256_ENDP = "HT2-SHA-256-ENDP";

    /** HT2-SHA-256-EXPR: HT2 draft, SHA-256, tls-exporter channel binding. */
    public static final String HT2_SHA_256_EXPR = "HT2-SHA-256-EXPR";

    /** HT2-SHA-512-NONE: HT2 draft, SHA-512, no channel binding. */
    public static final String HT2_SHA_512_NONE = "HT2-SHA-512-NONE";

    /** HT2-SHA-512-UNIQ: HT2 draft, SHA-512, tls-unique channel binding. */
    public static final String HT2_SHA_512_UNIQ = "HT2-SHA-512-UNIQ";

    /** HT2-SHA-512-ENDP: HT2 draft, SHA-512, tls-server-end-point channel binding. */
    public static final String HT2_SHA_512_ENDP = "HT2-SHA-512-ENDP";

    /** HT2-SHA-512-EXPR: HT2 draft, SHA-512, tls-exporter channel binding. */
    public static final String HT2_SHA_512_EXPR = "HT2-SHA-512-EXPR";
    public static final String HT2_SHA3_512_NONE = "HT2-SHA3-512-NONE";
    public static final String HT2_SHA3_512_UNIQ = "HT2-SHA3-512-UNIQ";
    public static final String HT2_SHA3_512_ENDP = "HT2-SHA3-512-ENDP";
    public static final String HT2_SHA3_512_EXPR = "HT2-SHA3-512-EXPR";

    /** System property to enable or disable FAST support. */
    public static final SystemProperty<Boolean> ENABLE_FAST = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.fast.enabled")
        .setDefaultValue(Boolean.TRUE)
        .setDynamic(Boolean.TRUE)
        .build();

    /** System property controlling the default token expiry duration. */
    public static final SystemProperty<Duration> TOKEN_EXPIRY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.fast.token.expiry")
        .setDefaultValue(Duration.ofDays(7))
        .setChronoUnit(ChronoUnit.DAYS)
        .setDynamic(Boolean.TRUE)
        .build();

    public static final SystemProperty<Duration> TOKEN_ROTATION_THRESHOLD = SystemProperty.Builder.ofType(Duration.class)
        .setKey("xmpp.fast.token.rotation-threshold")
        .setDefaultValue(Duration.ofDays(1))
        .setChronoUnit(ChronoUnit.HOURS)
        .setDynamic(Boolean.TRUE)
        .build();

    private static final String DELETE_NEW_TOKEN =
        "DELETE FROM ofFastToken WHERE username=? AND mechanism=? AND clientID=? AND tokenSlot='N'";
    private static final String INSERT_TOKEN =
        "INSERT INTO ofFastToken (username, mechanism, clientID, tokenSlot, replayCounter, tokenHash, expiry) VALUES (?,?,?,'N',0,?,?)";
    private static final String SELECT_TOKEN =
        "SELECT tokenSlot, replayCounter, tokenHash, expiry FROM ofFastToken WHERE username=? AND mechanism=? AND clientID=?";
    private static final String DELETE_TOKENS_FOR_USER =
        "DELETE FROM ofFastToken WHERE username=?";
    private static final String DELETE_TOKENS_FOR_CLIENT =
        "DELETE FROM ofFastToken WHERE username=? AND mechanism=? AND clientID=?";
    private static final String DELETE_CURRENT_TOKEN = DELETE_TOKENS_FOR_CLIENT + " AND tokenSlot='C'";
    private static final String PROMOTE_NEW_TOKEN =
        "UPDATE ofFastToken SET tokenSlot='C' WHERE username=? AND mechanism=? AND clientID=? AND tokenSlot='N'";
    private static final String UPDATE_REPLAY_COUNTER =
        "UPDATE ofFastToken SET replayCounter=? WHERE username=? AND mechanism=? AND clientID=? AND tokenSlot='C' AND replayCounter<?";
    private static final String DELETE_EXPIRED_TOKENS =
        "DELETE FROM ofFastToken WHERE expiry < ?";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final List<String> MECHANISMS = List.of(
        HT_SHA_256_NONE, HT_SHA_256_UNIQ, HT_SHA_256_ENDP, HT_SHA_256_EXPR,
        HT_SHA_512_NONE, HT_SHA_512_UNIQ, HT_SHA_512_ENDP, HT_SHA_512_EXPR,
        HT_SHA3_512_NONE, HT_SHA3_512_UNIQ, HT_SHA3_512_ENDP, HT_SHA3_512_EXPR,
        HT2_SHA_256_NONE, HT2_SHA_256_UNIQ, HT2_SHA_256_ENDP, HT2_SHA_256_EXPR,
        HT2_SHA_512_NONE, HT2_SHA_512_UNIQ, HT2_SHA_512_ENDP, HT2_SHA_512_EXPR,
        HT2_SHA3_512_NONE, HT2_SHA3_512_UNIQ, HT2_SHA3_512_ENDP, HT2_SHA3_512_EXPR);

    private FastTokenManager() {}

    /**
     * Returns an XML element advertising FAST as an inline feature for use in SASL2 (XEP-0388)
     * inline feature advertisement.
     *
     * @return a {@code <fast/>} element in the {@link #NAMESPACE} namespace
     */
    public static Element featureElement() {
        return featureElement(MECHANISMS);
    }

    public static Element featureElement(@Nonnull final Collection<String> mechanisms) {
        final Element fast = DocumentHelper.createElement(QName.get("fast", NAMESPACE));
        mechanisms.stream().filter(MECHANISMS::contains)
            .forEach(mechanism -> fast.addElement("mechanism").setText(mechanism));
        return fast;
    }

    public static boolean isMechanism(@Nonnull final String mechanism) {
        return MECHANISMS.contains(mechanism.toUpperCase(Locale.ROOT));
    }

    /**
     * Returns {@code true} if the given mechanism name is an HT2 mechanism.
     */
    static boolean isHt2Mechanism(@Nonnull final String mechanism) {
        return mechanism.startsWith("HT2-");
    }

    /**
     * Extracts the JCA hash algorithm name from a mechanism name of the form
     * {@code HT-SHA-256-NONE}, {@code HT2-SHA-512-UNIQ}, etc.
     *
     * <p>The second segment (between the first and second {@code -}) is the hash family
     * (e.g. {@code SHA}) and the third segment is the bit length (e.g. {@code 256}), giving
     * a JCA name of {@code SHA-256} or {@code SHA-512}.</p>
     *
     * @param mechanism the FAST SASL mechanism name (cannot be null)
     * @return the JCA algorithm name, e.g. {@code "SHA-256"} or {@code "SHA-512"}
     * @throws IllegalArgumentException if the mechanism name does not follow the expected pattern
     */
    public static String hashAlgorithmForMechanism(@Nonnull final String mechanism) {
        // Format: (HT|HT2)-HASH-BITS-CBTYPE, e.g. HT-SHA-256-NONE or HT2-SHA-512-UNIQ
        final String[] parts = mechanism.split("-");
        // parts[0] = "HT" or "HT2", parts[-1] = cb type, middle parts = hash name
        // For HT-SHA-256-NONE  → parts = ["HT",  "SHA", "256", "NONE"]
        // For HT2-SHA-512-UNIQ → parts = ["HT2", "SHA", "512", "UNIQ"]
        if (parts.length < 4) {
            throw new IllegalArgumentException("Unrecognised HT mechanism name: " + mechanism);
        }
        // Hash spans parts[1] through parts[parts.length - 2]
        final StringBuilder hash = new StringBuilder();
        for (int i = 1; i < parts.length - 1; i++) {
            if (i > 1) hash.append('-');
            hash.append(parts[i]);
        }
        return hash.toString(); // e.g. "SHA-256" or "SHA-512"
    }

    /**
     * Returns the JCA HMAC algorithm name corresponding to the hash used by the given mechanism.
     * For example, {@code "SHA-256"} maps to {@code "HmacSHA256"}, {@code "SHA-512"} to
     * {@code "HmacSHA512"}.
     *
     * @param mechanism the FAST SASL mechanism name (cannot be null)
     * @return the JCA HMAC algorithm name
     */
    public static String hmacAlgorithmForMechanism(@Nonnull final String mechanism) {
        final String hash = hashAlgorithmForMechanism(mechanism);
        // Map "SHA-256" → "HmacSHA256", "SHA-512" → "HmacSHA512"
        return hash.startsWith("SHA3-") ? "Hmac" + hash : "Hmac" + hash.replace("-", "");
    }

    /**
     * Issues a new FAST token for the given username and mechanism, storing it in the database.
     * Any unacknowledged new token for the same user, mechanism and client is replaced. The
     * current token remains valid until the client proves possession of this new token.
     *
     * The generated token is a Base64 Unicode string. Its UTF-8 representation is persisted so
     * that both HT families can verify and produce their mutual-authentication HMACs.
     *
     * @param username  the local username (cannot be null)
     * @param mechanism the FAST SASL mechanism name (cannot be null)
     * @return the newly issued {@link FastToken} containing the UTF-8 token bytes and expiry
     */
    @Nonnull
    public static FastToken issueToken(@Nonnull final String username, @Nonnull final String mechanism) {
        return issueToken(username, "legacy", mechanism);
    }

    @Nonnull
    public static FastToken issueToken(@Nonnull final String username, @Nonnull final String clientId,
                                       @Nonnull final String mechanism) {
        if (!MECHANISMS.contains(mechanism)) {
            throw new IllegalArgumentException("Unsupported FAST mechanism: " + mechanism);
        }
        final byte[] entropy = new byte[32];
        SECURE_RANDOM.nextBytes(entropy);
        // SASL-HT defines the token as a Unicode string. The XML attribute value, the value
        // persisted by the server and the UTF-8 HMAC key must therefore all be identical.
        final byte[] rawToken = Base64.getEncoder().encode(entropy);
        final Instant expiry = Instant.now().plus(TOKEN_EXPIRY.getValue());
        final String storedValue = protectToken(new String(rawToken, StandardCharsets.US_ASCII));
        final String expiryString = XMPPDateTimeFormat.format(java.util.Date.from(expiry));

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            con.setAutoCommit(false);
            pstmt = con.prepareStatement(DELETE_NEW_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.setString(3, clientId);
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Insert the new token.
            pstmt = con.prepareStatement(INSERT_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.setString(3, clientId);
            pstmt.setString(4, storedValue);
            pstmt.setString(5, expiryString);
            pstmt.executeUpdate();
            con.commit();
        } catch (final SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (final SQLException rollbackError) { e.addSuppressed(rollbackError); }
            }
            Log.error("Failed to store FAST token for user '{}' mechanism '{}'", username, mechanism, e);
            throw new IllegalStateException("Unable to persist FAST token", e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        return new FastToken(username, mechanism, rawToken, expiry);
    }

    /**
     * Validates an HT2 FAST token presented by a client using HMAC verification
     * (draft-ietf-kitten-sasl-ht).
     *
     * The client sends {@code HMAC(token, "Initiator" || cbData || extraValues)} as the
     * {@code initiator-hashed-token}. This method fetches the stored UTF-8 token bytes from
     * the database and recomputes the expected HMAC for comparison.
     *
     * If the new token is valid, it is promoted to current. If a current token is nearing
     * expiry, a replacement is issued into the new slot. The optional replacement is returned
     * together with the responder HMAC. If validation fails, {@code null} is returned.
     *
     * @param username              the local username (cannot be null)
     * @param mechanism             the FAST SASL mechanism name, must start with "HT2-" (cannot be null)
     * @param initiatorHashedToken  the HMAC bytes presented by the client (cannot be null)
     * @param cbData                the channel-binding data; empty byte array for NONE variant (cannot be null)
     * @param extraInitiatorValues  the extra initiator key/value pairs string; empty string if none (cannot be null)
     * @param extraResponderValues  the extra responder key/value pairs string; empty string if none (cannot be null)
     * @return a {@link Ht2ValidationResult} on success, or {@code null} on failure
     */
    public static Ht2ValidationResult validateTokenHt2(@Nonnull final String username,
                                                        @Nonnull final String clientId,
                                                        @Nonnull final String mechanism,
                                                        @Nonnull final byte[] initiatorHashedToken,
                                                        @Nonnull final byte[] cbData,
                                                        @Nonnull final String extraInitiatorValues,
                                                        @Nonnull final String extraResponderValues) {
        return validateTokenHt2(username, clientId, mechanism, initiatorHashedToken, cbData,
            extraInitiatorValues, extraResponderValues, null);
    }

    public static Ht2ValidationResult validateTokenHt2(@Nonnull final String username,
                                                        @Nonnull final String clientId,
                                                        @Nonnull final String mechanism,
                                                        @Nonnull final byte[] initiatorHashedToken,
                                                        @Nonnull final byte[] cbData,
                                                        @Nonnull final String extraInitiatorValues,
                                                        @Nonnull final String extraResponderValues,
                                                        final Long replayCount) {
        byte[] matchedToken = null;
        String matchedClientId = null;
        String matchedSlot = null;
        Instant matchedExpiry = null;
        long matchedReplayCounter = 0;
        boolean matchingExpiredToken = false;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            con.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            con.setAutoCommit(false);
            pstmt = con.prepareStatement(SELECT_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.setString(3, clientId);
            rs = pstmt.executeQuery();
            final String hmacAlg = hmacAlgorithmForMechanism(mechanism);
            final byte[] initiatorMsg = buildHmacMessage("Initiator", cbData, extraInitiatorValues);
            while (rs.next()) {
                final Instant expiry;
                try {
                    expiry = new XMPPDateTimeFormat().parseString(rs.getString("expiry")).toInstant();
                } catch (final Exception e) {
                    Log.warn("Ignoring FAST token with malformed expiry for user '{}'", username, e);
                    continue;
                }
                final byte[] candidate;
                try {
                    candidate = unprotectToken(rs.getString("tokenHash")).getBytes(StandardCharsets.UTF_8);
                } catch (final RuntimeException e) {
                    Log.warn("Ignoring unreadable FAST token for user '{}'", username, e);
                    continue;
                }
                final byte[] expected = hmac(candidate, initiatorMsg, hmacAlg);
                final boolean proofMatches = MessageDigest.isEqual(expected, initiatorHashedToken);
                if (proofMatches && Instant.now().isAfter(expiry)) matchingExpiredToken = true;
                final boolean valid = !Instant.now().isAfter(expiry) && proofMatches;
                if (valid && matchedToken == null) {
                    matchedToken = candidate;
                    matchedClientId = clientId;
                    matchedSlot = rs.getString("tokenSlot");
                    matchedExpiry = expiry;
                    matchedReplayCounter = rs.getLong("replayCounter");
                }
            }
            DbConnectionManager.closeResultSet(rs);
            rs = null;
            DbConnectionManager.fastcloseStmt(pstmt);
            pstmt = null;
            if (matchedToken == null) {
                con.rollback();
                return matchingExpiredToken ? Ht2ValidationResult.expired() : null;
            }
            if (replayCount != null && (replayCount <= 0 || replayCount <= matchedReplayCounter)) {
                con.rollback();
                return null;
            }
            if ("N".equals(matchedSlot)) {
                pstmt = con.prepareStatement(DELETE_CURRENT_TOKEN);
                pstmt.setString(1, username);
                pstmt.setString(2, mechanism);
                pstmt.setString(3, matchedClientId);
                pstmt.executeUpdate();
                DbConnectionManager.fastcloseStmt(pstmt);
                pstmt = con.prepareStatement(PROMOTE_NEW_TOKEN);
                pstmt.setString(1, username);
                pstmt.setString(2, mechanism);
                pstmt.setString(3, matchedClientId);
                pstmt.executeUpdate();
                DbConnectionManager.fastcloseStmt(pstmt);
                pstmt = null;
            }
            if (replayCount != null) {
                pstmt = con.prepareStatement(UPDATE_REPLAY_COUNTER);
                pstmt.setLong(1, replayCount);
                pstmt.setString(2, username);
                pstmt.setString(3, mechanism);
                pstmt.setString(4, matchedClientId);
                pstmt.setLong(5, replayCount);
                if (pstmt.executeUpdate() != 1) {
                    con.rollback();
                    return null;
                }
            }
            con.commit();
        } catch (final SQLException e) {
            if (con != null) try { con.rollback(); } catch (final SQLException rollbackError) { e.addSuppressed(rollbackError); }
            Log.error("Failed to fetch HT2 FAST token for user '{}' mechanism '{}'", username, mechanism, e);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Compute responder-hashed-token = HMAC(token, "Responder" || cbData || extraResponderValues)
        final String hmacAlg = hmacAlgorithmForMechanism(mechanism);
        final byte[] responderMsg = buildHmacMessage("Responder", cbData, extraResponderValues);
        final byte[] responderHashedToken = hmac(matchedToken, responderMsg, hmacAlg);

        FastToken replacement = null;
        if ("C".equals(matchedSlot) && matchedExpiry != null
            && Duration.between(Instant.now(), matchedExpiry).compareTo(TOKEN_ROTATION_THRESHOLD.getValue()) <= 0) {
            try {
                replacement = issueToken(username, matchedClientId, mechanism);
            } catch (final RuntimeException e) {
                Log.warn("Unable to rotate FAST token for user '{}'", username, e);
            }
        }

        Log.debug("HT2 FAST authentication successful for user '{}'", username);
        return new Ht2ValidationResult(replacement, responderHashedToken, matchedClientId);
    }

    /**
     * Builds the HMAC message for HT2 as: UTF-8 bytes of prefix || cbData || UTF-8 bytes of extraValues.
     */
    private static byte[] buildHmacMessage(@Nonnull final String prefix,
                                            @Nonnull final byte[] cbData,
                                            @Nonnull final String extraValues) {
        final byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        final byte[] extraBytes = extraValues.getBytes(StandardCharsets.UTF_8);
        final byte[] result = new byte[prefixBytes.length + cbData.length + extraBytes.length];
        System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
        System.arraycopy(cbData, 0, result, prefixBytes.length, cbData.length);
        System.arraycopy(extraBytes, 0, result, prefixBytes.length + cbData.length, extraBytes.length);
        return result;
    }

    /**
     * Result of a successful HT2 token validation, carrying both the rotated token and the
     * responder HMAC that must be sent to the client for mutual authentication.
     */
    public static final class Ht2ValidationResult {
        private final FastToken rotatedToken;
        private final byte[] responderHashedToken;
        private final String clientId;
        private final boolean expired;

        public Ht2ValidationResult(final FastToken rotatedToken, @Nonnull final byte[] responderHashedToken) {
            this(rotatedToken, responderHashedToken, null);
        }

        public Ht2ValidationResult(final FastToken rotatedToken, @Nonnull final byte[] responderHashedToken,
                                   final String clientId) {
            this.rotatedToken = rotatedToken;
            this.responderHashedToken = responderHashedToken.clone();
            this.clientId = clientId;
            this.expired = false;
        }

        private Ht2ValidationResult() {
            rotatedToken = null;
            responderHashedToken = new byte[0];
            clientId = null;
            expired = true;
        }

        public static Ht2ValidationResult expired() {
            return new Ht2ValidationResult();
        }

        public boolean isExpired() {
            return expired;
        }

        /** Returns the newly rotated FAST token. */
        public FastToken getRotatedToken() {
            return rotatedToken;
        }

        /** Returns a copy of the responder-hashed-token to be included in the server success message. */
        @Nonnull
        public byte[] getResponderHashedToken() {
            return responderHashedToken.clone();
        }

        public String getClientId() {
            return clientId;
        }
    }

    /**
     * Invalidates all FAST tokens for the given username.
     *
     * @param username the local username (cannot be null)
     */
    public static void invalidateTokens(@Nonnull final String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_TOKENS_FOR_USER);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            Log.error("Failed to invalidate FAST tokens for user '{}'", username, e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public static void invalidateToken(@Nonnull final String username, @Nonnull final String mechanism,
                                       @Nonnull final String clientId) {
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(DELETE_TOKENS_FOR_CLIENT)) {
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.setString(3, clientId);
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            throw new IllegalStateException("Unable to invalidate FAST token", e);
        }
    }

    public static boolean advanceReplayCounter(@Nonnull final String username, @Nonnull final String mechanism,
                                               @Nonnull final String clientId, final long count) {
        if (count <= 0) return false;
        try (Connection con = DbConnectionManager.getConnection();
             PreparedStatement pstmt = con.prepareStatement(UPDATE_REPLAY_COUNTER)) {
            pstmt.setLong(1, count);
            pstmt.setString(2, username);
            pstmt.setString(3, mechanism);
            pstmt.setString(4, clientId);
            pstmt.setLong(5, count);
            return pstmt.executeUpdate() == 1;
        } catch (final SQLException e) {
            throw new IllegalStateException("Unable to update FAST replay counter", e);
        }
    }

    /**
     * Purges all expired FAST tokens from the database.
     */
    public static void purgeExpiredTokens() {
        final String nowString = XMPPDateTimeFormat.format(java.util.Date.from(Instant.now()));
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_EXPIRED_TOKENS);
            pstmt.setString(1, nowString);
            final int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                Log.debug("Purged {} expired FAST token(s)", deleted);
            }
        } catch (final SQLException e) {
            Log.error("Failed to purge expired FAST tokens", e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Computes HMAC of the given message using the provided key and JCA algorithm name.
     *
     * @param key       the HMAC key bytes (cannot be null)
     * @param message   the message bytes (cannot be null)
     * @param algorithm the JCA HMAC algorithm name, e.g. {@code "HmacSHA256"} (cannot be null)
     * @return the raw HMAC bytes
     */
    static byte[] hmac(@Nonnull final byte[] key, @Nonnull final byte[] message,
                       @Nonnull final String algorithm) {
        try {
            final Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(message);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        } catch (final InvalidKeyException e) {
            throw new IllegalStateException("Invalid HMAC key for " + algorithm, e);
        }
    }

    private static String protectToken(final String token) {
        final byte[] iv = new byte[16];
        SECURE_RANDOM.nextBytes(iv);
        return protectToken(token, JiveGlobals.getPropertyEncryptor(), iv);
    }

    static String protectToken(final String token, final Encryptor encryptor, final byte[] iv) {
        return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + encryptor.encrypt(token, iv);
    }

    static String unprotectToken(final String storedValue) {
        return unprotectToken(storedValue, JiveGlobals.getPropertyEncryptor());
    }

    static String unprotectToken(final String storedValue, final Encryptor encryptor) {
        if (!storedValue.startsWith("v1:")) {
            throw new IllegalArgumentException("Unsupported encrypted FAST token format");
        }
        final int separator = storedValue.indexOf(':', 3);
        if (separator < 0) {
            throw new IllegalArgumentException("Malformed encrypted FAST token");
        }
        final byte[] iv = Base64.getDecoder().decode(storedValue.substring(3, separator));
        return encryptor.decrypt(storedValue.substring(separator + 1), iv);
    }

    /**
     * Computes a hash of the given bytes using the specified JCA algorithm and returns the
     * result as a lowercase hex string.
     *
     * @param data      the data to hash (cannot be null)
     * @param algorithm the JCA digest algorithm name, e.g. {@code "SHA-256"} or {@code "SHA-512"}
     * @return the hex-encoded hash
     */
    static String hashHex(@Nonnull final byte[] data, @Nonnull final String algorithm) {
        try {
            final MessageDigest digest = MessageDigest.getInstance(algorithm);
            final byte[] hash = digest.digest(data);
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " not available", e);
        }
    }

    /**
     * Computes the SHA-256 hash of the given bytes and returns it as a lowercase hex string.
     *
     * @param data the data to hash (cannot be null)
     * @return the hex-encoded SHA-256 hash
     * @deprecated Use {@link #hashHex(byte[], String)} with algorithm {@code "SHA-256"} instead.
     */
    @Deprecated
    static String sha256Hex(@Nonnull final byte[] data) {
        return hashHex(data, "SHA-256");
    }

    /**
     * Computes HMAC-SHA-256 of the given message using the provided key.
     *
     * @param key     the HMAC key bytes (cannot be null)
     * @param message the message bytes (cannot be null)
     * @return the raw HMAC-SHA-256 bytes
     * @deprecated Use {@link #hmac(byte[], byte[], String)} with algorithm {@code "HmacSHA256"} instead.
     */
    @Deprecated
    static byte[] hmacSha256(@Nonnull final byte[] key, @Nonnull final byte[] message) {
        return hmac(key, message, "HmacSHA256");
    }
}
