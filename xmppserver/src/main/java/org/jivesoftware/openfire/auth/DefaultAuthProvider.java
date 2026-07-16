/*
 * Copyright (C) 2004-2008 Jive Software, 2016-2026 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;

import com.google.common.annotations.VisibleForTesting;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha512SaslServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default AuthProvider implementation. It authenticates against the {@code ofUser}
 * database table and supports plain text and digest authentication.
 *
 * Because each call to authenticate() makes a database connection, the
 * results of authentication should be cached whenever possible.
 *
 * @author Matt Tucker
 */
public class DefaultAuthProvider implements AuthProvider {

    /**
     * Describes a SCRAM mechanism for credential derivation: its storage name, JCA algorithm names, and iteration-count source.
     */
    private record ScramMechanism(String mechanismName, String hmacAlgorithm, String digestAlgorithm, IntSupplier iterationCount) {}

    /**
     * Mechanisms supported by this AuthProvider implementation (not necessarily supported at runtime, or by other parts of Openfire).
     */
    private static final List<ScramMechanism> SCRAM_MECHANISMS = List.of(
        new ScramMechanism(ScramSha1SaslServer.MECHANISM_NAME,   ScramSha1SaslServer.HMAC_ALGORITHM_NAME,   ScramSha1SaslServer.DIGEST_ALGORITHM_NAME,   ScramSha1SaslServer.ITERATION_COUNT::getValue),
        new ScramMechanism(ScramSha256SaslServer.MECHANISM_NAME, ScramSha256SaslServer.HMAC_ALGORITHM_NAME, ScramSha256SaslServer.DIGEST_ALGORITHM_NAME, ScramSha256SaslServer.ITERATION_COUNT::getValue),
        new ScramMechanism(ScramSha512SaslServer.MECHANISM_NAME, ScramSha512SaslServer.HMAC_ALGORITHM_NAME, ScramSha512SaslServer.DIGEST_ALGORITHM_NAME, ScramSha512SaslServer.ITERATION_COUNT::getValue)
    );

    /**
     * The length of the salt used to generate salted passwords.
     */
    public static final int SALT_LENGTH = 24;

    private static final Logger Log = LoggerFactory.getLogger(DefaultAuthProvider.class);

    private static final String LOAD_PASSWORD =
        "SELECT plainPassword,encryptedPassword FROM ofUser WHERE username=?";
    private static final String LOAD_PASSWORD_AND_SCRAM =
        "SELECT u.plainPassword, u.encryptedPassword, s.mechanism, s.iterations, s.salt, s.storedKey, s.serverKey " +
        "FROM ofUser u LEFT JOIN ofUserScram s ON u.username = s.username " +
        "WHERE u.username = ?";
    private static final String UPDATE_PASSWORD =
        "UPDATE ofUser SET plainPassword=?, encryptedPassword=? WHERE username=?";
    private static final String LOAD_SCRAM_CREDENTIAL =
        "SELECT iterations,salt,storedKey,serverKey FROM ofUserScram WHERE username=? AND mechanism=?";
    private static final String UPDATE_SCRAM_CREDENTIAL =
        "UPDATE ofUserScram SET iterations=?, salt=?, storedKey=?, serverKey=? WHERE username=? AND mechanism=?";
    private static final String INSERT_SCRAM_CREDENTIAL =
        "INSERT INTO ofUserScram (username, mechanism, iterations, salt, storedKey, serverKey) VALUES (?, ?, ?, ?, ?, ?)";

    private static final SecureRandom random = new SecureRandom();

    /**
     * Constructs a new DefaultAuthProvider.
     */
    public DefaultAuthProvider() {

    }

    private class UserInfo {
        String plainText;
        String encrypted;
    }

    private UserInfo getUserInfo(String username) throws UnsupportedOperationException, UserNotFoundException {
        return getUserInfo(username, false);
    }

    private UserInfo getUserInfo(String username, boolean recurse) throws UnsupportedOperationException, UserNotFoundException {
        if (!isScramSupported()) {
            // Reject the operation since the provider does not support SCRAM
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PASSWORD);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }
            UserInfo userInfo = new UserInfo();
            userInfo.plainText = rs.getString(1);
            userInfo.encrypted = rs.getString(2);
            if (userInfo.encrypted != null) {
                try {
                    userInfo.plainText = AuthFactory.decryptPassword(userInfo.encrypted);
                }
                catch (UnsupportedOperationException uoe) {
                    // Ignore and return plain password instead.
                }
            }
            if (!recurse) {
                if (userInfo.plainText != null) {
                    // Regenerate SCRAM credentials from the plaintext when a supported mechanism's row is missing
                    // (e.g. a legacy user predating SCRAM-SHA-256). Note (OF-3322): setPassword() skips any mechanism
                    // whose derivation fails, so a mechanism that can never be derived here would trigger this on every
                    // login. Bounded by 'recurse' (no infinite loop); accepted as a per-login cost since derivation
                    // failure for a JVM-standard mechanism isn't expected.
                    boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
                    if (scramOnly || hasIncompleteSetOfScramCredentials(username)) {
                        // If we have a password here, but we're meant to be scramOnly, we should reset it.
                        setPassword(username, userInfo.plainText);
                        // RECURSE
                        return getUserInfo(username, true);
                    }
                }
            }
            // Good to go.
            return userInfo;
        }
        catch (SQLException sqle) {
            Log.error("User SQL failure:", sqle);
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Determines whether this user is missing a credential for any SCRAM mechanism supported by this implementation.
     *
     * @param username the user identifier
     * @return true if at least one supported mechanism has no stored credential for this user; false if all are present
     */
    @VisibleForTesting
    boolean hasIncompleteSetOfScramCredentials(String username)
    {
        return SCRAM_MECHANISMS.stream().anyMatch(m -> {
            try {
                return !hasScramCredential(username, m.mechanismName());
            } catch (SQLException e) {
                Log.warn("Unable to determine if credentials for SCRAM mechanism '{}' are available for user '{}' due to a database error.", m.mechanismName(), username, e);
                return false; // treat as "not known to be missing" so a DB hiccup doesn't force a regeneration storm
            }
        });
    }

    @Override
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public String getSalt(String username) throws UserNotFoundException {
        return getScramCredential(username, ScramSha1SaslServer.MECHANISM_NAME).salt;
    }

    @Override
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public int getIterations(String username) throws UserNotFoundException {
        return getScramCredential(username, ScramSha1SaslServer.MECHANISM_NAME).iterations;
    }

    @Override
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public String getStoredKey(String username) throws UserNotFoundException {
        return getScramCredential(username, ScramSha1SaslServer.MECHANISM_NAME).storedKey;
    }

    @Override
    @Deprecated(forRemoval = true) // Remove in or after Openfire 5.3.0
    public String getServerKey(String username) throws UserNotFoundException {
        return getScramCredential(username, ScramSha1SaslServer.MECHANISM_NAME).serverKey;
    }

    /**
     * Returns SCRAM credentials for a user and mechanism.
     *
     * When SCRAM credentials are missing but a plaintext (or decryptable) password is available, the credentials are
     * regenerated (preserving the historical behavior of this provider).
     */
    @Override
    public ScramCredentialData getScramCredential(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        final String normalizedMechanism = ScramCredentialData.normalizeMechanismName(mechanism);
        if (SCRAM_MECHANISMS.stream().noneMatch(m -> m.mechanismName().equals(normalizedMechanism))) {
            throw new UnsupportedOperationException("SCRAM mechanism is not supported: " + mechanism);
        }

        // Preserve previous behavior: if SHA-1 credentials are missing while plaintext exists, regenerate.
        getUserInfo(username);

        final ScramCredentialData credential;
        try {
            credential = loadScramCredential(username, normalizedMechanism);
        } catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        if (credential == null) {
            throw new UserNotFoundException(username);
        }
        return credential;
    }

    @Override
    public void authenticate(String username, String password) throws UnauthorizedException {
        if (username == null || password == null) {
            throw new UnauthorizedException();
        }
        username = username.trim().toLowerCase();
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain. Return authentication failed.
                throw new UnauthorizedException();
            }
        }
        try {
            if (!checkPassword(username, password)) {
                throw new UnauthorizedException();
            }
        }
        catch (UserNotFoundException unfe) {
            throw new UnauthorizedException();
        }
        // Got this far, so the user must be authorized.
    }

    @Override
    public String getPassword(String username) throws UserNotFoundException {
        if (!supportsPasswordRetrieval()) {
            // Reject the operation since the provider is read-only
            throw new UnsupportedOperationException();
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PASSWORD);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }
            String plainText = rs.getString(1);
            String encrypted = rs.getString(2);
            if (encrypted != null) {
                try {
                    return AuthFactory.decryptPassword(encrypted);
                }
                catch (UnsupportedOperationException uoe) {
                    // Ignore and return plain password instead.
                }
            }
            if (plainText == null) {
                throw new UnsupportedOperationException();
            }
            return plainText;
        }
        catch (SQLException sqle) {
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    public boolean checkPassword(String username, String testPassword) throws UserNotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        Log.debug("Checking password for user '{}'", username);
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PASSWORD_AND_SCRAM);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new UserNotFoundException(username);
            }

            String plainText = rs.getString(1);
            String encrypted = rs.getString(2);
            if (encrypted != null) {
                try {
                    plainText = AuthFactory.decryptPassword(encrypted);
                }
                catch (UnsupportedOperationException uoe) {
                    // Ignore and return plain password instead.
                }
            }
            if (plainText != null) {
                boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
                if (scramOnly) {
                    // If we have a password here, but we're meant to be scramOnly, we should reset it.
                    setPassword(username, plainText);
                }
                return testPassword.equals(plainText);
            }

            // Don't have either plain or encrypted, so test SCRAM hash.
            // LEFT JOIN result set may have multiple rows (one per SCRAM mechanism).
            final Map<String, ScramCredentialData> credentialsByMechanismName = new HashMap<>();
            do {
                final String mechanism = rs.getString(3);
                Integer iterations = rs.getInt(4);
                if (rs.wasNull()) { // correct for 'getInt' returning '0' on null values.
                    iterations = null;
                }
                final String salt = rs.getString(5);
                final String storedKey = rs.getString(6);
                final String serverKey = rs.getString(7);
                if (mechanism == null || salt == null || iterations == null || iterations == 0 || storedKey == null || serverKey == null) {
                    // Either the LEFT JOIN produced no ofUserScram row (all columns null), or a stored row is incomplete.
                    // Skip it rather than failing authentication outright: another row may hold a usable credential.
                    Log.debug("Skipping unavailable or incomplete SCRAM credential (mechanism '{}') for user {}", mechanism, username);
                    continue;
                }

                final ScramCredentialData scram = new ScramCredentialData(mechanism, salt, iterations, storedKey, serverKey);
                credentialsByMechanismName.put(scram.mechanism, scram);
            } while (rs.next());


            // We don't know what algorithm was used for the provided credentials. We'll have to try all supported ones.
            for (final ScramCredentialData credential : credentialsByMechanismName.values())
            {
                final ScramMechanism mech = SCRAM_MECHANISMS.stream()
                    .filter(m -> m.mechanismName().equals(credential.mechanism))
                    .findFirst().orElse(null);
                if (mech == null) {
                    Log.debug("Skipping unsupported SCRAM mechanism '{}' for user {}", credential.mechanism, username); continue;
                }
                try {
                    final byte[] saltShaker = DatatypeConverter.parseBase64Binary(credential.salt);
                    final ScramUtils.ScramKeys keys = ScramUtils.deriveScramKeys(
                        saltShaker, testPassword, credential.iterations,
                        mech.hmacAlgorithm(), mech.digestAlgorithm());
                    if (DatatypeConverter.printBase64Binary(keys.storedKey).equals(credential.storedKey)) {
                        return true;
                    }
                } catch (SaslException | NoSuchAlgorithmException | IllegalArgumentException e) {
                    Log.warn("Unable to check SCRAM values for PLAIN authentication for user '{}'", username, e);
                }
            }
            Log.debug("No usable SCRAM credential found for user {}", username);
            return false;
        }
        catch (SQLException sqle) {
            Log.warn("A database error occurred while authenticating user {}:", username, sqle);
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    @Override
    public void setPassword(String username, String password) throws UserNotFoundException {
        // Determine if the password should be stored as plain text or encrypted.
        boolean usePlainPassword = JiveGlobals.getBooleanProperty("user.usePlainPassword");
        boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
        String encryptedPassword = null;
        if (username.contains("@")) {
            // Check that the specified domain matches the server's domain
            int index = username.indexOf("@");
            String domain = username.substring(index + 1);
            if (domain.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                username = username.substring(0, index);
            } else {
                // Unknown domain.
                throw new UserNotFoundException();
            }
        }

        // Derive SCRAM credentials from the plaintext *before* it's nulled out below.
        final List<ScramCredentialData> scramCredentials = new ArrayList<>();
        for (final ScramMechanism mech : SCRAM_MECHANISMS) {
            final byte[] saltShaker = new byte[SALT_LENGTH];
            random.nextBytes(saltShaker);
            final int iterations = mech.iterationCount().getAsInt();
            try {
                final ScramUtils.ScramKeys keys = ScramUtils.deriveScramKeys(
                    saltShaker, password, iterations, mech.hmacAlgorithm(), mech.digestAlgorithm());
                scramCredentials.add(new ScramCredentialData(
                    mech.mechanismName(),
                    DatatypeConverter.printBase64Binary(saltShaker),
                    iterations,
                    DatatypeConverter.printBase64Binary(keys.storedKey),
                    DatatypeConverter.printBase64Binary(keys.serverKey)));
            } catch (SaslException | NoSuchAlgorithmException e) {
                Log.warn("Unable to derive SCRAM credential for {} while persisting password for user '{}'", mech.mechanismName(), username, e);
            }
        }

        String plaintextToStore = password;
        if (!scramOnly && !usePlainPassword) {
            try {
                encryptedPassword = AuthFactory.encryptPassword(password);
                // Set plaintext to null so that it's inserted that way.
                plaintextToStore = null;
            }
            catch (UnsupportedOperationException uoe) {
                Log.warn("Unable to encrypt password for user '{}' while updating password. Plain-text authentication will remain available.", username, uoe);
            }
        }
        if (scramOnly) {
            encryptedPassword = null;
            plaintextToStore = null;
        }

        if (scramOnly && scramCredentials.isEmpty()) {
            // In scramOnly mode SCRAM is the only stored credential. If none could be derived, committing would
            // leave the user with no usable credential of any kind (locked out), so refuse rather than persist that.
            Log.error("Unable to derive any SCRAM credential for user '{}' while user.scramHashedPasswordOnly is set; refusing to store a credential-less password.", username);
            throw new UserNotFoundException("No SCRAM credentials could be derived (while configured in SCRAM-only mode) for user " + username);
        }

        // Persist the ofUser password row and the ofUserScram credential row atomically: because these now live in
        // two tables, a single UPDATE is no longer sufficient to keep them consistent.
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();

            pstmt = con.prepareStatement(UPDATE_PASSWORD);
            if (plaintextToStore == null) {
                pstmt.setNull(1, Types.VARCHAR);
            }
            else {
                pstmt.setString(1, plaintextToStore);
            }
            if (encryptedPassword == null) {
                pstmt.setNull(2, Types.VARCHAR);
            }
            else {
                pstmt.setString(2, encryptedPassword);
            }
            pstmt.setString(3, username);
            final int updated = pstmt.executeUpdate();
            if (updated == 0) {
                abortTransaction = true;
                throw new UserNotFoundException(username);
            }
            DbConnectionManager.fastcloseStmt(pstmt);
            pstmt = null;

            for (final ScramCredentialData credential : scramCredentials) {
                upsertScramCredential(con, username, credential);
            }
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    @Override
    public boolean supportsPasswordRetrieval() {
        boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
        return !scramOnly;
    }

    @Override
    public boolean isScramSupported() {
        return true;
    }

    /**
     * Inserts or updates the SCRAM credential for a user and mechanism, using the provided (transaction) connection.
     *
     * @param con        an open (transaction) connection to use
     * @param username   the user whose credential is being stored
     * @param credential the SCRAM credential to persist
     * @throws SQLException if the credential could not be persisted
     */
    private void upsertScramCredential(final Connection con, final String username, final ScramCredentialData credential) throws SQLException
    {
        // UPDATE first: for a password change the row already exists, which is the common case.
        if (executeScramUpdate(con, username, credential) > 0) {
            return;
        }
        // No existing row (e.g. first-time SCRAM generation after migration). Try to INSERT it.
        try {
            executeScramInsert(con, username, credential);
        } catch (final SQLException sqle) {
            // A concurrent setPassword() may have inserted the row between our UPDATE and INSERT, yielding a
            // unique/primary-key violation. The row now exists, so a single UPDATE retry resolves it.
            if (!isIntegrityConstraintViolation(sqle) || executeScramUpdate(con, username, credential) == 0) {
                throw sqle;
            }
        }
    }

    /**
     * Updates the stored SCRAM credential for a user and mechanism on the given connection.
     *
     * @param con        an open connection to use
     * @param username   the user whose credential is being updated
     * @param credential the SCRAM credential to store
     * @return the number of rows affected: zero when no matching row exists
     * @throws SQLException if the update failed
     */
    private int executeScramUpdate(final Connection con, final String username, final ScramCredentialData credential) throws SQLException
    {
        try (final PreparedStatement stmt = con.prepareStatement(UPDATE_SCRAM_CREDENTIAL)){
            stmt.setInt(1, credential.iterations);
            stmt.setString(2, credential.salt);
            stmt.setString(3, credential.storedKey);
            stmt.setString(4, credential.serverKey);
            stmt.setString(5, username);
            stmt.setString(6, credential.mechanism);
            return stmt.executeUpdate();
        }
    }

    /**
     * Inserts a new SCRAM credential for a user and mechanism on the given connection.
     *
     * @param con        an open connection to use
     * @param username   the user whose credential is being inserted
     * @param credential the SCRAM credential to store
     * @throws SQLException if the insert failed, including when a row for this user and mechanism already exists
     *                      (a unique/primary-key violation)
     */
    private void executeScramInsert(final Connection con, final String username, final ScramCredentialData credential) throws SQLException
    {
        try (final PreparedStatement stmt = con.prepareStatement(INSERT_SCRAM_CREDENTIAL)){
            stmt.setString(1, username);
            stmt.setString(2, credential.mechanism);
            stmt.setInt(3, credential.iterations);
            stmt.setString(4, credential.salt);
            stmt.setString(5, credential.storedKey);
            stmt.setString(6, credential.serverKey);
            stmt.executeUpdate();
        }
    }

    /**
     * Determines whether the given exception signals a unique or primary-key constraint violation, in a manner intended
     * to work across the different databases that Openfire supports.
     *
     * @param sqle the exception to inspect
     * @return {@code true} if the exception represents a unique or primary-key constraint violation
     */
    private static boolean isIntegrityConstraintViolation(final SQLException sqle)
    {
        // Walk the standard cause chain.
        for (Throwable t = sqle; t != null; t = t.getCause()) {
            if (isConstraintViolationNode(t)) {
                return true;
            }
        }
        // Walk the JDBC-specific chain (independent of getCause()).
        for (SQLException e = sqle; e != null; e = e.getNextException()) {
            if (isConstraintViolationNode(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests a single exception (one node in an exception chain) for an integrity-constraint violation.
     */
    private static boolean isConstraintViolationNode(final Throwable t)
    {
        // Two signals are combined: the JDBC 4 SQLIntegrityConstraintViolationException subclass (which drivers should throw but not all do)...
        if (t instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        // ... and an SQLState in class 23, which the SQL standard assigns to integrity-constraint violations and into which most databases map PK/unique violations.
        if (t instanceof SQLException) {
            final String sqlState = ((SQLException) t).getSQLState();
            return sqlState != null && sqlState.startsWith("23");
        }
        return false;
    }

    /**
     * Loads the SCRAM credential for a user and mechanism, or {@code null} when none is stored.
     */
    private ScramCredentialData loadScramCredential(final String username, final String mechanism) throws SQLException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_SCRAM_CREDENTIAL);
            pstmt.setString(1, username);
            pstmt.setString(2, mechanism);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return new ScramCredentialData(
                mechanism,
                rs.getString(2),
                rs.getInt(1),
                rs.getString(3),
                rs.getString(4)
            );
        } finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Returns {@code true} when a SCRAM credential exists for a user and mechanism.
     */
    private boolean hasScramCredential(final String username, final String mechanism) throws SQLException
    {
        return loadScramCredential(username, mechanism) != null;
    }
}
