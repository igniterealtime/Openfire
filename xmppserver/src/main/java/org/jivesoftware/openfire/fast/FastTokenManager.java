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

/**
 * Manages FAST (XEP-0484) authentication tokens.
 *
 * Tokens are stored in the {@code ofFastToken} database table, indexed by (username, mechanism).
 * Only one token slot exists per (username, mechanism) pair; issuing a new token replaces the old one.
 *
 * For the original HT-* mechanisms the {@code tokenHash} column holds a hex-encoded SHA-256 hash of
 * the raw token bytes (the raw secret is never stored). For HT2-* mechanisms
 * (draft-ietf-kitten-sasl-ht) the column holds the Base64-encoded raw token bytes, because the
 * server must use the token as an HMAC key during verification.
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

    /** HT-SHA-256-PLUS: original HT draft, SHA-256, tls-unique channel binding. */
    public static final String HT_SHA_256_PLUS = "HT-SHA-256-PLUS";

    /** HT-SHA-256-EXPR: original HT draft, SHA-256, tls-exporter channel binding. */
    public static final String HT_SHA_256_EXPR = "HT-SHA-256-EXPR";

    /** HT-SHA-512-NONE: original HT draft, SHA-512, no channel binding. */
    public static final String HT_SHA_512_NONE = "HT-SHA-512-NONE";

    /** HT-SHA-512-PLUS: original HT draft, SHA-512, tls-unique channel binding. */
    public static final String HT_SHA_512_PLUS = "HT-SHA-512-PLUS";

    /** HT-SHA-512-EXPR: original HT draft, SHA-512, tls-exporter channel binding. */
    public static final String HT_SHA_512_EXPR = "HT-SHA-512-EXPR";

    // -------------------------------------------------------------------------
    // HT2-* mechanism names (draft-ietf-kitten-sasl-ht: HMAC-based variants)
    // -------------------------------------------------------------------------

    /** HT2-SHA-256-NONE: HT2 draft, SHA-256, no channel binding. */
    public static final String HT2_SHA_256_NONE = "HT2-SHA-256-NONE";

    /** HT2-SHA-256-PLUS: HT2 draft, SHA-256, tls-unique channel binding. */
    public static final String HT2_SHA_256_PLUS = "HT2-SHA-256-PLUS";

    /** HT2-SHA-256-EXPR: HT2 draft, SHA-256, tls-exporter channel binding. */
    public static final String HT2_SHA_256_EXPR = "HT2-SHA-256-EXPR";

    /** HT2-SHA-512-NONE: HT2 draft, SHA-512, no channel binding. */
    public static final String HT2_SHA_512_NONE = "HT2-SHA-512-NONE";

    /** HT2-SHA-512-PLUS: HT2 draft, SHA-512, tls-unique channel binding. */
    public static final String HT2_SHA_512_PLUS = "HT2-SHA-512-PLUS";

    /** HT2-SHA-512-EXPR: HT2 draft, SHA-512, tls-exporter channel binding. */
    public static final String HT2_SHA_512_EXPR = "HT2-SHA-512-EXPR";

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

    private static final String INSERT_OR_REPLACE_TOKEN =
        "DELETE FROM ofFastToken WHERE username=? AND mechanism=?";
    private static final String INSERT_TOKEN =
        "INSERT INTO ofFastToken (username, mechanism, tokenHash, expiry) VALUES (?,?,?,?)";
    private static final String SELECT_TOKEN =
        "SELECT tokenHash, expiry FROM ofFastToken WHERE username=? AND mechanism=?";
    private static final String DELETE_TOKENS_FOR_USER =
        "DELETE FROM ofFastToken WHERE username=?";
    private static final String DELETE_EXPIRED_TOKENS =
        "DELETE FROM ofFastToken WHERE expiry < ?";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private FastTokenManager() {}

    /**
     * Returns an XML element advertising FAST as an inline feature for use in SASL2 (XEP-0388)
     * inline feature advertisement.
     *
     * @return a {@code <fast/>} element in the {@link #NAMESPACE} namespace
     */
    public static Element featureElement() {
        final Element fast = DocumentHelper.createElement(QName.get("fast", NAMESPACE));
        // HT-* mechanisms (original HT draft)
        fast.addElement("mechanism").setText(HT_SHA_256_NONE);
        fast.addElement("mechanism").setText(HT_SHA_256_PLUS);
        fast.addElement("mechanism").setText(HT_SHA_256_EXPR);
        fast.addElement("mechanism").setText(HT_SHA_512_NONE);
        fast.addElement("mechanism").setText(HT_SHA_512_PLUS);
        fast.addElement("mechanism").setText(HT_SHA_512_EXPR);
        // HT2-* mechanisms (draft-ietf-kitten-sasl-ht)
        fast.addElement("mechanism").setText(HT2_SHA_256_NONE);
        fast.addElement("mechanism").setText(HT2_SHA_256_PLUS);
        fast.addElement("mechanism").setText(HT2_SHA_256_EXPR);
        fast.addElement("mechanism").setText(HT2_SHA_512_NONE);
        fast.addElement("mechanism").setText(HT2_SHA_512_PLUS);
        fast.addElement("mechanism").setText(HT2_SHA_512_EXPR);
        return fast;
    }

    /**
     * Returns {@code true} if the given mechanism name is an HT2 mechanism
     * (draft-ietf-kitten-sasl-ht), which stores the raw token bytes rather than a hash.
     */
    static boolean isHt2Mechanism(@Nonnull final String mechanism) {
        return mechanism.startsWith("HT2-");
    }

    /**
     * Extracts the JCA hash algorithm name from a mechanism name of the form
     * {@code HT-SHA-256-NONE}, {@code HT2-SHA-512-PLUS}, etc.
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
        // Format: (HT|HT2)-HASH-BITS-CBTYPE, e.g. HT-SHA-256-NONE or HT2-SHA-512-PLUS
        final String[] parts = mechanism.split("-");
        // parts[0] = "HT" or "HT2", parts[-1] = cb type, middle parts = hash name
        // For HT-SHA-256-NONE  → parts = ["HT",  "SHA", "256", "NONE"]
        // For HT2-SHA-512-PLUS → parts = ["HT2", "SHA", "512", "PLUS"]
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
        return "Hmac" + hash.replace("-", "");
    }

    /**
     * Issues a new FAST token for the given username and mechanism, storing it in the database.
     * Any previously stored token for the same (username, mechanism) pair is replaced.
     *
     * For HT2-* mechanisms the raw token bytes are stored (Base64-encoded) so that the server can
     * use the token as an HMAC key during verification. For original HT-* mechanisms only the
     * SHA-256 hash is stored.
     *
     * @param username  the local username (cannot be null)
     * @param mechanism the FAST SASL mechanism name (cannot be null)
     * @return the newly issued {@link FastToken} containing the raw token bytes and expiry
     */
    @Nonnull
    public static FastToken issueToken(@Nonnull final String username, @Nonnull final String mechanism) {
        final byte[] rawToken = new byte[32];
        SECURE_RANDOM.nextBytes(rawToken);
        final Instant expiry = Instant.now().plus(TOKEN_EXPIRY.getValue());
        // HT2 mechanisms require the raw token for HMAC; store as Base64.
        // HT mechanisms store a hash of the raw token using the mechanism's hash algorithm.
        final String storedValue = isHt2Mechanism(mechanism)
            ? Base64.getEncoder().encodeToString(rawToken)
            : hashHex(rawToken, hashAlgorithmForMechanism(mechanism));
        final String expiryString = XMPPDateTimeFormat.format(java.util.Date.from(expiry));

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            // Delete any existing token for this (username, mechanism) pair.
            pstmt = con.prepareStatement(INSERT_OR_REPLACE_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);

            // Insert the new token.
            pstmt = con.prepareStatement(INSERT_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            pstmt.setString(3, storedValue);
            pstmt.setString(4, expiryString);
            pstmt.executeUpdate();
        } catch (final SQLException e) {
            Log.error("Failed to store FAST token for user '{}' mechanism '{}'", username, mechanism, e);
        } finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

        return new FastToken(username, mechanism, rawToken, expiry);
    }

    /**
     * Validates a FAST token presented by a client.
     *
     * If the token is valid and not expired, it is rotated (a new token is issued and the old one
     * is invalidated). The new token is returned. If validation fails, {@code null} is returned.
     *
     * @param username  the local username (cannot be null)
     * @param mechanism the FAST SASL mechanism name (cannot be null)
     * @param token     the raw token bytes presented by the client (cannot be null)
     * @return the newly rotated {@link FastToken} on success, or {@code null} on failure
     */
    public static FastToken validateToken(@Nonnull final String username, @Nonnull final String mechanism,
                                          @Nonnull final byte[] token) {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                Log.debug("No FAST token found for user '{}' mechanism '{}'", username, mechanism);
                return null;
            }
            final String storedHash = rs.getString("tokenHash");
            final String expiryString = rs.getString("expiry");
            DbConnectionManager.closeResultSet(rs);
            rs = null;

            // Check expiry.
            final Instant expiry;
            try {
                expiry = new XMPPDateTimeFormat().parseString(expiryString).toInstant();
            } catch (final Exception e) {
                Log.warn("Failed to parse expiry '{}' for user '{}' mechanism '{}'", expiryString, username, mechanism, e);
                return null;
            }
            if (Instant.now().isAfter(expiry)) {
                Log.debug("FAST token expired for user '{}' mechanism '{}'", username, mechanism);
                return null;
            }

            // Constant-time comparison of hashes.
            final String presentedHash = hashHex(token, hashAlgorithmForMechanism(mechanism));
            if (!MessageDigest.isEqual(storedHash.getBytes(), presentedHash.getBytes())) {
                Log.debug("FAST token mismatch for user '{}' mechanism '{}'", username, mechanism);
                return null;
            }
        } catch (final SQLException e) {
            Log.error("Failed to validate FAST token for user '{}' mechanism '{}'", username, mechanism, e);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Token is valid — rotate it.
        return issueToken(username, mechanism);
    }

    /**
     * Validates an HT2 FAST token presented by a client using HMAC verification
     * (draft-ietf-kitten-sasl-ht).
     *
     * The client sends {@code HMAC(token, "Initiator" || cbData || extraValues)} as the
     * {@code initiator-hashed-token}. This method fetches the stored raw token bytes from
     * the database and recomputes the expected HMAC for comparison.
     *
     * If the token is valid and not expired it is rotated (a new token is issued). The new
     * token is returned together with the responder HMAC so that the caller can send the
     * success message back to the client. If validation fails, {@code null} is returned.
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
                                                        @Nonnull final String mechanism,
                                                        @Nonnull final byte[] initiatorHashedToken,
                                                        @Nonnull final byte[] cbData,
                                                        @Nonnull final String extraInitiatorValues,
                                                        @Nonnull final String extraResponderValues) {
        final byte[] storedRawToken;
        final String expiryString;
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(SELECT_TOKEN);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                Log.debug("No HT2 FAST token found for user '{}' mechanism '{}'", username, mechanism);
                return null;
            }
            final String storedValue = rs.getString("tokenHash");
            expiryString = rs.getString("expiry");
            DbConnectionManager.closeResultSet(rs);
            rs = null;
            try {
                storedRawToken = Base64.getDecoder().decode(storedValue);
            } catch (final IllegalArgumentException e) {
                Log.warn("Stored HT2 token for user '{}' mechanism '{}' is not valid Base64", username, mechanism);
                return null;
            }
        } catch (final SQLException e) {
            Log.error("Failed to fetch HT2 FAST token for user '{}' mechanism '{}'", username, mechanism, e);
            return null;
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }

        // Check expiry.
        final Instant expiry;
        try {
            expiry = new XMPPDateTimeFormat().parseString(expiryString).toInstant();
        } catch (final Exception e) {
            Log.warn("Failed to parse expiry '{}' for user '{}' mechanism '{}'", expiryString, username, mechanism, e);
            return null;
        }
        if (Instant.now().isAfter(expiry)) {
            Log.debug("HT2 FAST token expired for user '{}' mechanism '{}'", username, mechanism);
            return null;
        }

        // Compute the expected initiator-hashed-token = HMAC(token, "Initiator" || cbData || extraInitiatorValues)
        final String hmacAlg = hmacAlgorithmForMechanism(mechanism);
        final byte[] initiatorMsg = buildHmacMessage("Initiator", cbData, extraInitiatorValues);
        final byte[] expectedInitiatorToken = hmac(storedRawToken, initiatorMsg, hmacAlg);
        if (!MessageDigest.isEqual(expectedInitiatorToken, initiatorHashedToken)) {
            Log.debug("HT2 FAST token HMAC mismatch for user '{}' mechanism '{}'", username, mechanism);
            return null;
        }

        // Compute responder-hashed-token = HMAC(token, "Responder" || cbData || extraResponderValues)
        final byte[] responderMsg = buildHmacMessage("Responder", cbData, extraResponderValues);
        final byte[] responderHashedToken = hmac(storedRawToken, responderMsg, hmacAlg);

        // Token is valid — rotate it.
        final FastToken newToken = issueToken(username, mechanism);
        Log.debug("HT2 FAST authentication successful for user '{}'", username);
        return new Ht2ValidationResult(newToken, responderHashedToken);
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

        Ht2ValidationResult(@Nonnull final FastToken rotatedToken, @Nonnull final byte[] responderHashedToken) {
            this.rotatedToken = rotatedToken;
            this.responderHashedToken = responderHashedToken.clone();
        }

        /** Returns the newly rotated FAST token. */
        @Nonnull
        public FastToken getRotatedToken() {
            return rotatedToken;
        }

        /** Returns a copy of the responder-hashed-token to be included in the server success message. */
        @Nonnull
        public byte[] getResponderHashedToken() {
            return responderHashedToken.clone();
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
