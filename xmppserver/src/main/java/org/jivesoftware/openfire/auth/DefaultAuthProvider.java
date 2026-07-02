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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
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
                    boolean scramOnly = JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly");
                    if (scramOnly || !hasScramCredential(username, ScramSha1SaslServer.MECHANISM_NAME)) {
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
     * Only {@code SCRAM-SHA-1} is supported. When SHA-1 credentials are missing but a plaintext (or decryptable)
     * password is available, the credentials are regenerated (preserving the historical behavior of this provider).
     */
    @Override
    public ScramCredentialData getScramCredential(final String username, final String mechanism) throws UnsupportedOperationException, UserNotFoundException
    {
        final String normalizedMechanism = ScramCredentialData.normalizeMechanismName(mechanism);
        if (!ScramSha1SaslServer.MECHANISM_NAME.equals(normalizedMechanism)) {
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
                if (mechanism == null) {
                    // No SCRAM credentials for this user
                    Log.debug("No SCRAM credentials available for checkPassword for user {}", username);
                    return false;
                }
                Integer iterations = rs.getInt(4);
                if (rs.wasNull()) { // correct for 'getInt' returning '0' on null values.
                    iterations = null;
                }
                final String salt = rs.getString(5);
                final String storedKey = rs.getString(6);
                final String serverKey = rs.getString(7);
                if (salt == null || iterations == null || iterations == 0 || storedKey == null || serverKey == null) {
                    Log.debug("No available SCRAM credentials for checkPassword for user {}", username);
                    return false;
                }

                credentialsByMechanismName.put(mechanism, new ScramCredentialData(mechanism, salt, iterations, storedKey, serverKey));
            } while (rs.next());

            // TODO: When supporting more than just SCRAM-SHA-1, drop this. This got introduced in a commit that refactored the existing storage solution, prior to implementing support for additional mechanisms (OF-3322).
            final ScramCredentialData credential = credentialsByMechanismName.get(ScramSha1SaslServer.MECHANISM_NAME);
            if (credential == null) {
                Log.debug("No available SCRAM-SHA-1 credentials for checkPassword for user {}", username);
                return false;
            }
            final byte[] testStoredKey;
            try {
                final byte[] saltShaker = DatatypeConverter.parseBase64Binary(credential.salt);
                final byte[] saltedPassword = ScramUtils.createSaltedPassword(saltShaker, testPassword, credential.iterations);
                final byte[] clientKey = ScramUtils.computeHmac(saltedPassword, "Client Key");
                testStoredKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
            } catch(SaslException | NoSuchAlgorithmException | IllegalArgumentException e) {
                Log.warn("Unable to check SCRAM values for PLAIN authentication for user '{}'", username, e);
                return false;
            }
            return DatatypeConverter.printBase64Binary(testStoredKey).equals(credential.storedKey);
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

        // Compute the SCRAM (SHA-1) credential from the plaintext password *before* the plaintext is nulled out below.
        byte[] saltShaker = new byte[SALT_LENGTH];
        random.nextBytes(saltShaker);
        final String salt = DatatypeConverter.printBase64Binary(saltShaker);
        final int iterations = ScramSha1SaslServer.ITERATION_COUNT.getValue();
        byte[] saltedPassword = null, clientKey = null, storedKey = null, serverKey = null;
        try {
            saltedPassword = ScramUtils.createSaltedPassword(saltShaker, password, iterations);
            clientKey = ScramUtils.computeHmac(saltedPassword, "Client Key");
            storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
            serverKey = ScramUtils.computeHmac(saltedPassword, "Server Key");
        } catch (SaslException | NoSuchAlgorithmException e) {
            Log.warn("Unable to persist values for SCRAM authentication.");
        }

        String plaintextToStore = password;
        if (!scramOnly && !usePlainPassword) {
            try {
                encryptedPassword = AuthFactory.encryptPassword(password);
                // Set plaintext to null so that it's inserted that way.
                plaintextToStore = null;
            }
            catch (UnsupportedOperationException uoe) {
                // Encryption may fail. In that case, ignore the error and
                // the plain password will be stored.
            }
        }
        if (scramOnly) {
            encryptedPassword = null;
            plaintextToStore = null;
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
            pstmt.executeUpdate();
            DbConnectionManager.fastcloseStmt(pstmt);
            pstmt = null;

            upsertScramCredential(
                con,
                username,
                new ScramCredentialData(
                    ScramSha1SaslServer.MECHANISM_NAME,
                    salt,
                    iterations,
                    storedKey == null ? null : DatatypeConverter.printBase64Binary(storedKey),
                    serverKey == null ? null : DatatypeConverter.printBase64Binary(serverKey)
                )
            );
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw new UserNotFoundException(sqle);
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
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
