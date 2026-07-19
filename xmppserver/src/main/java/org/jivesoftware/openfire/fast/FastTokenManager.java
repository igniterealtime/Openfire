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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Manages FAST (XEP-0484) authentication tokens.
 *
 * Tokens are stored in the {@code ofFastToken} database table, indexed by (username, mechanism).
 * Only one token slot exists per (username, mechanism) pair; issuing a new token replaces the old one.
 * Token bytes are stored as a SHA-256 hash to avoid storing raw secrets at rest.
 */
public class FastTokenManager {

    private static final Logger Log = LoggerFactory.getLogger(FastTokenManager.class);

    /** XEP-0484 namespace */
    public static final String NAMESPACE = "urn:xmpp:fast:0";

    /** The HT-SHA-256-NONE mechanism name */
    public static final String HT_SHA_256_NONE = "HT-SHA-256-NONE";

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
        fast.addElement("mechanism").setText(HT_SHA_256_NONE);
        return fast;
    }

    /**
     * Issues a new FAST token for the given username and mechanism, storing it in the database.
     * Any previously stored token for the same (username, mechanism) pair is replaced.
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
        final String tokenHash = sha256Hex(rawToken);
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
            pstmt.setString(3, tokenHash);
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
            final String presentedHash = sha256Hex(token);
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
     * Computes the SHA-256 hash of the given bytes and returns it as a lowercase hex string.
     *
     * @param data the data to hash (cannot be null)
     * @return the hex-encoded SHA-256 hash
     */
    static String sha256Hex(@Nonnull final byte[] data) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(data);
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
