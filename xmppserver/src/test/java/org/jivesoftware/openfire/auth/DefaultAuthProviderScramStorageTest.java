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
package org.jivesoftware.openfire.auth;

import org.jivesoftware.Fixtures;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.security.sasl.SaslException;
import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAuthProvider#checkPassword(String, String)}.
 *
 * These tests verify authentication behavior for the supported credential types and the precedence rules that apply
 * when multiple credential types are available.
 *
 * <h2>How these tests are wired</h2>
 * {@link DefaultAuthProvider#checkPassword(String, String)} loads a user's password and SCRAM material with a single
 * {@code LEFT JOIN} query against {@code ofUser} and {@code ofUserScram}. Every test therefore needs the same fixture:
 * a mocked {@link ResultSet} describing one user's stored credentials, wrapped in a mocked {@link PreparedStatement}
 * and {@link Connection}, reached through a statically-mocked {@link DbConnectionManager}. That shared wiring lives in
 * {@link #runCheckPassword}, so each test only has to describe the stored credentials and assert the outcome.
 */
public class DefaultAuthProviderScramStorageTest
{
    private static final String PASSWORD = "pencil";
    private static final String SALT_BASE64 = "QSXCR+Q6sek8bf92";
    private static final int ITERATIONS = 4096;

    /** The encrypted-password sentinel that {@link #runCheckPassword} teaches {@code AuthFactory} to decrypt. */
    private static final String ENCRYPTED_VALUE = "encrypted-value";

    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterEach
    void clearProperties() {
        Fixtures.clearExistingProperties();
    }

    /**
     * Computes the base64 stored key for a password using the same SCRAM-SHA-1 derivation as production code, so the
     * test verifies real cryptographic agreement rather than an echoed constant.
     */
    private static String storedKeyFor(final String password) throws Exception
    {
        final byte[] salt = DatatypeConverter.parseBase64Binary(SALT_BASE64);
        final byte[] saltedPassword = ScramUtils.createSaltedPassword(salt, password, ITERATIONS, ScramSha1SaslServer.HMAC_ALGORITHM_NAME);
        final byte[] clientKey = ScramUtils.computeHmac(saltedPassword, "Client Key", ScramSha1SaslServer.HMAC_ALGORITHM_NAME);
        return DatatypeConverter.printBase64Binary(MessageDigest.getInstance(ScramSha1SaslServer.DIGEST_ALGORITHM_NAME).digest(clientKey));
    }

    /**
     * Builds a mock {@link ResultSet} representing the single row that the combined {@code LEFT JOIN} query returns for
     * one user. The column layout mirrors {@code DefaultAuthProvider.LOAD_PASSWORD_AND_SCRAM}:
     *
     * <ol>
     *   <li>{@code plainPassword} - the {@code ofUser.plainPassword} value, or {@code null} when not stored.</li>
     *   <li>{@code encryptedPassword} - the {@code ofUser.encryptedPassword} value, or {@code null} when not stored.</li>
     *   <li>{@code mechanism} - the {@code ofUserScram.mechanism} value. Pass {@code null} to model a {@code LEFT JOIN}
     *       with no matching {@code ofUserScram} row (i.e. the user has no SCRAM credentials); in that case the remaining
     *       SCRAM columns can be left unstubbed (Mockito defaults them to {@code null}/{@code 0}, which production logic skips).</li>
     *   <li>{@code iterations} - the {@code ofUserScram.iterations} value. Only stubbed when {@code mechanism} is
     *       non-null. A {@code null} value models a SQL {@code NULL} (mirrored via {@link ResultSet#wasNull()}).</li>
     *   <li>{@code salt} - the {@code ofUserScram.salt} value. Only stubbed when {@code mechanism} is non-null.</li>
     *   <li>{@code storedKey} - the {@code ofUserScram.storedKey} value. Only stubbed when {@code mechanism} is
     *       non-null.</li>
     * </ol>
     *
     * The {@code serverKey} column (7) is stubbed with a placeholder whenever SCRAM data is present, because
     * {@code checkPassword} reads it but does not use it for password verification.
     *
     * @param plainPassword     stored plaintext password, or {@code null}
     * @param encryptedPassword stored encrypted password, or {@code null}
     * @param mechanism         stored SCRAM mechanism name, or {@code null} for no SCRAM row
     * @param iterations        stored SCRAM iteration count (ignored when {@code mechanism} is {@code null})
     * @param salt              stored SCRAM salt (ignored when {@code mechanism} is {@code null})
     * @param storedKey         stored SCRAM stored-key (ignored when {@code mechanism} is {@code null})
     * @return a mocked single-row {@link ResultSet}
     */
    private static ResultSet storedCredentialsRow(final String plainPassword, final String encryptedPassword, final String mechanism, final Integer iterations, final String salt, final String storedKey) throws Exception
    {
        final ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.next()).thenReturn(true, false); // exactly one row, then exhausted
        when(rs.getString(1)).thenReturn(plainPassword);
        when(rs.getString(2)).thenReturn(encryptedPassword);
        when(rs.getString(3)).thenReturn(mechanism);
        if (mechanism != null) {
            when(rs.getInt(4)).thenReturn(iterations == null ? 0 : iterations);
            when(rs.wasNull()).thenReturn(iterations == null);
            when(rs.getString(5)).thenReturn(salt);
            when(rs.getString(6)).thenReturn(storedKey);
            when(rs.getString(7)).thenReturn("server-key-not-used-for-password-check");
        }
        return rs;
    }

    /**
     * Runs {@link DefaultAuthProvider#checkPassword(String, String)} against a mocked database whose combined
     * {@code LEFT JOIN} query returns {@code storedCredentials}. This method owns all the shared fixture wiring:
     * the {@link PreparedStatement}, the {@link Connection}, and the statically-mocked {@link DbConnectionManager}.
     *
     * <p>When {@code withDecrypt} is {@code true}, {@link AuthFactory} is also statically mocked so that
     * {@link AuthFactory#decryptPassword(String) decryptPassword("encrypted-value")} returns {@link #PASSWORD}. Both
     * static mocks are opened in try-with-resources and closed before this method returns, so no static mock leaks
     * outside the call.
     *
     * @param username          the username to authenticate
     * @param testPassword      the password to check
     * @param storedCredentials the mocked row describing what is stored for the user
     * @param withDecrypt       whether to also mock {@link AuthFactory} to decrypt {@link #ENCRYPTED_VALUE}
     * @return the result of {@code checkPassword}
     */
    @SuppressWarnings("SqlSourceToSinkFlow")
    private static boolean runCheckPassword(final String username, final String testPassword, final ResultSet storedCredentials, final boolean withDecrypt) throws Exception
    {
        final PreparedStatement combinedStmt = Mockito.mock(PreparedStatement.class);
        when(combinedStmt.executeQuery()).thenReturn(storedCredentials);

        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(argThat(sql -> {
            final String s = sql.toLowerCase(Locale.ROOT);
            return s.contains("from ofuser") && s.contains("left join ofuserscram");
        }))).thenReturn(combinedStmt);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            if (withDecrypt) {
                try (final MockedStatic<AuthFactory> authFactory = Mockito.mockStatic(AuthFactory.class)) {
                    authFactory.when(() -> AuthFactory.decryptPassword(ENCRYPTED_VALUE)).thenReturn(PASSWORD);
                    return new DefaultAuthProvider().checkPassword(username, testPassword);
                }
            }
            return new DefaultAuthProvider().checkPassword(username, testPassword);
        }
    }

    /**
     * Convenience overload for the common case where {@link AuthFactory} does not need to be mocked.
     */
    private static boolean runCheckPassword(final String username, final String testPassword, final ResultSet storedCredentials) throws Exception
    {
        return runCheckPassword(username, testPassword, storedCredentials, false);
    }

    /**
     * Runs {@link DefaultAuthProvider#hasIncompleteSetOfScramCredentials(String)} against a mocked database.
     *
     * Each supported mechanism is probed independently via {@code LOAD_SCRAM_CREDENTIAL} (keyed on username + mechanism),
     * so the fixture is expressed as "which mechanisms have a stored row." A mechanism whose name is in
     * {@code presentMechanisms} yields a one-row ResultSet (credential exists); any other yields an empty ResultSet.
     *
     * @param username           the user identifier
     * @param presentMechanisms  the mechanism names for which a credential row exists
     * @return the result of {@code hasIncompleteSetOfScramCredentials}
     */
    @SuppressWarnings("SqlSourceToSinkFlow")
    private static boolean runHasIncompleteSet(final String username, final Set<String> presentMechanisms) throws Exception
    {
        final PreparedStatement scramStmt = Mockito.mock(PreparedStatement.class);

        // Capture the mechanism bound as parameter 2, then answer executeQuery() based on it.
        final String[] boundMechanism = new String[1];
        Mockito
            .doAnswer(inv -> { boundMechanism[0] = inv.getArgument(1); return null; })
            .when(scramStmt)
            .setString(Mockito.eq(2), Mockito.anyString());

        when(
            scramStmt.executeQuery()
        ).thenAnswer(inv -> {
            final ResultSet rs = Mockito.mock(ResultSet.class);
            final boolean present = presentMechanisms.contains(boundMechanism[0]);

            when(
                rs.next()
            ).thenReturn(present, false);

            if (present) {
                when(rs.getInt(1)).thenReturn(ITERATIONS);
                when(rs.getString(2)).thenReturn(SALT_BASE64);
                when(rs.getString(3)).thenReturn("stored");
                when(rs.getString(4)).thenReturn("server");
            }
            return rs;
        });

        final Connection connection = Mockito.mock(Connection.class);
        when(
            connection.prepareStatement(
                argThat(sql -> sql.toLowerCase(Locale.ROOT).contains("from ofuserscram") && !sql.toLowerCase(Locale.ROOT).contains("left join"))
            )
        ).thenReturn(scramStmt);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            return new DefaultAuthProvider().hasIncompleteSetOfScramCredentials(username);
        }
    }

    /**
     * Verifies that a correct password is accepted by verifying against SCRAM material held in {@code ofUserScram}.
     */
    @Test
    void checkPassword_acceptsMatchingScramCredential() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = null;
        final String mechanism = ScramSha1SaslServer.MECHANISM_NAME;
        final String storedKey = storedKeyFor(PASSWORD);
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, ITERATIONS, SALT_BASE64, storedKey);

        // Execute system under test.
        final boolean result = runCheckPassword("user", PASSWORD, storedCredentials);

        // Verify result.
        assertTrue(result, "Correct password should verify against SCRAM-SHA-1.");
    }

    /**
     * Verifies that an incorrect password is rejected when checked against SCRAM material in {@code ofUserScram}.
     */
    @Test
    void checkPassword_rejectsNonMatchingScramCredential() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = null;
        final String mechanism = ScramSha1SaslServer.MECHANISM_NAME;
        final String storedKey = storedKeyFor(PASSWORD);
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, ITERATIONS, SALT_BASE64, storedKey);

        // Execute system under test.
        final boolean result = runCheckPassword("user", "not-the-password", storedCredentials);

        // Verify result.
        assertFalse(result, "Incorrect password should be rejected against SCRAM-SHA-1.");
    }

    /**
     * Verifies that a correct plaintext password is accepted when ONLY plaintext password exists.
     */
    @Test
    void checkPassword_acceptsMatchingPlaintextPassword() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = PASSWORD;
        final String encryptedPassword = null;
        final String mechanism = null; // no SCRAM row
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, null, null, null);

        // Execute system under test.
        final boolean result = runCheckPassword("user", PASSWORD, storedCredentials);

        // Verify result.
        assertTrue(result, "Correct plaintext password should be accepted.");
    }

    /**
     * Verifies that an incorrect plaintext password is rejected.
     */
    @Test
    void checkPassword_rejectsNonMatchingPlaintextPassword() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = PASSWORD;
        final String encryptedPassword = null;
        final String mechanism = null; // no SCRAM row
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, null, null, null);

        // Execute system under test.
        final boolean result = runCheckPassword("user", "wrong-password", storedCredentials);

        // Verify result.
        assertFalse(result, "Incorrect plaintext password should be rejected.");
    }

    /**
     * Verifies that a correct password is accepted when ONLY encrypted password exists.
     */
    @Test
    void checkPassword_acceptsMatchingEncryptedPassword() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = ENCRYPTED_VALUE;
        final String mechanism = null; // no SCRAM row
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, null, null, null);

        // Execute system under test.
        final boolean result = runCheckPassword("user", PASSWORD, storedCredentials, true);

        // Verify result.
        assertTrue(result, "Correct password decrypted from encrypted value should be accepted.");
    }

    /**
     * Verifies that an incorrect password is rejected against encrypted password.
     */
    @Test
    void checkPassword_rejectsNonMatchingEncryptedPassword() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = ENCRYPTED_VALUE;
        final String mechanism = null; // no SCRAM row
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, null, null, null);

        // Execute system under test.
        final boolean result = runCheckPassword("user", "wrong-password", storedCredentials, true);

        // Verify result.
        assertFalse(result, "Incorrect password should be rejected against decrypted password.");
    }

    /**
     * Verifies that missing credentials return false (not an exception).
     */
    @Test
    void checkPassword_returnsFalseWhenNoCredentialsExist() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = null;
        final String mechanism = null; // no SCRAM row
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, null, null, null);

        // Execute system under test.
        final boolean result = runCheckPassword("user", PASSWORD, storedCredentials);

        // Verify result.
        assertFalse(result, "Should return false when no credentials exist (not throw exception).");
    }

    /**
     * Verifies that a SCRAM row missing a required column (here a null salt) is treated as unusable and rejected,
     * rather than being used to derive a stored key. Production guards this with an explicit null/zero check.
     */
    @Test
    void checkPassword_rejectsIncompleteScramCredential() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = null;
        final String mechanism = ScramSha1SaslServer.MECHANISM_NAME;
        final String salt = null; // corrupt/partial SCRAM row: required column missing
        final String storedKey = storedKeyFor(PASSWORD);
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, ITERATIONS, salt, storedKey);

        // Execute system under test.
        final boolean result = runCheckPassword("user", PASSWORD, storedCredentials);

        // Verify result.
        assertFalse(result, "A SCRAM credential missing a required column should be rejected.");
    }

    /**
     * Verifies that a SCRAM row with a zero iteration count is treated as unusable and rejected. Production explicitly
     * rejects {@code iterations == 0} rather than attempting a (meaningless) zero-iteration derivation.
     */
    @Test
    void checkPassword_rejectsScramCredentialWithZeroIterations() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = null;
        final String mechanism = ScramSha1SaslServer.MECHANISM_NAME;
        final int iterations = 0; // unusable iteration count
        final String storedKey = storedKeyFor(PASSWORD);
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, iterations, SALT_BASE64, storedKey);

        // Execute system under test.
        final boolean result = runCheckPassword("user", PASSWORD, storedCredentials);

        // Verify result.
        assertFalse(result, "A SCRAM credential with zero iterations should be rejected.");
    }

    /**
     * Verifies that an empty password does not authenticate against a real (non-empty) SCRAM credential.
     */
    @Test
    void checkPassword_rejectsEmptyPasswordAgainstScramCredential() throws Exception
    {
        // Setup test fixture.
        final String plainPassword = null;
        final String encryptedPassword = null;
        final String mechanism = ScramSha1SaslServer.MECHANISM_NAME;
        final String storedKey = storedKeyFor(PASSWORD);
        final ResultSet storedCredentials = storedCredentialsRow(plainPassword, encryptedPassword, mechanism, ITERATIONS, SALT_BASE64, storedKey);

        // Execute system under test.
        final boolean result = runCheckPassword("user", "", storedCredentials);

        // Verify result.
        assertFalse(result, "An empty password should not verify against a non-empty SCRAM credential.");
    }

    /**
     * No SCRAM credentials at all → incomplete → returns true.
     */
    @Test
    void hasIncompleteSet_returnsTrue_whenNoMechanismsPresent() throws Exception
    {
        // Setup test fixture.
        final Set<String> presentMechanisms = Set.of();

        // Execute system under test.
        final boolean result = runHasIncompleteSet("user", presentMechanisms);

        // Verify result.
        assertTrue(result, "A user with no SCRAM credentials has an incomplete set.");
    }

    /**
     * When {@code user.scramHashedPasswordOnly} is set and *no* SCRAM mechanism can derive a credential, setPassword
     * must refuse rather than persist a user with no plaintext, no encrypted password, and no SCRAM rows, a state that
     * would silently lock the user out of every authentication path.
     *
     * The guard must fire <em>before</em> the database transaction is opened: this test asserts both that
     * {@link UserNotFoundException} is thrown and that {@link DbConnectionManager#getTransactionConnection()} is never
     * called, so a broken guard that threw only after committing would fail here.
     */
    @Test
    void setPassword_scramOnly_refusesWhenNoScramCredentialCanBeDerived()
    {
        // Setup test fixture: SCRAM-only mode, and force every mechanism's key derivation to fail.
        JiveGlobals.setProperty("user.scramHashedPasswordOnly", "true");

        try (final MockedStatic<ScramUtils> scramUtils = Mockito.mockStatic(ScramUtils.class);
             final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class))
        {
            scramUtils.when(() -> ScramUtils.deriveScramKeys(
                    Mockito.any(byte[].class), Mockito.anyString(), Mockito.anyInt(),
                    Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new SaslException("forced derivation failure for test"));

            // Execute system under test & verify result: setPassword must refuse.
            assertThrows(UserNotFoundException.class,
                () -> new DefaultAuthProvider().setPassword("user", "pencil"),
                "In scramOnly mode with no derivable SCRAM credential, setPassword must throw rather than persist a credential-less user.");

            // Verify the refusal happened before any transaction was opened (nothing was written).
            db.verify(DbConnectionManager::getTransactionConnection, never());
        }
    }
}
