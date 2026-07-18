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
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha512SaslServer;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
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
            .setString(Mockito.eq(2), anyString());

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
     *  All supported mechanisms have a stored credential → the set is complete → returns false.
     */
    @Test
    void hasIncompleteSet_returnsFalse_whenAllMechanismsPresent() throws Exception
    {
        // Setup test fixture.
        final Set<String> presentMechanisms = Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME, ScramSha512SaslServer.MECHANISM_NAME);

        // Execute system under test.
        final boolean result = runHasIncompleteSet("user", presentMechanisms);

        // Verify result.
        assertFalse(result, "When every supported mechanism has a stored credential, the set is complete.");
    }

    /**
     * A legacy user with only SHA-1 and SHA-512 (SHA-256 missing) → incomplete → returns true (triggers regeneration).
     */
    @Test
    void hasIncompleteSet_returnsTrue_whenMechanismsMissing() throws Exception
    {
        // Setup test fixture.
        final Set<String> presentMechanisms = Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha512SaslServer.MECHANISM_NAME); // SHA-256 absent

        // Execute system under test.
        final boolean result = runHasIncompleteSet("user", presentMechanisms);

        // Verify result.
        assertTrue(result, "A user missing any supported mechanism's credential has an incomplete set.");
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
                    Mockito.any(byte[].class), anyString(), Mockito.anyInt(),
                    anyString(), anyString()))
                .thenThrow(new SaslException("forced derivation failure for test"));

            // Execute system under test & verify result: setPassword must refuse.
            assertThrows(UserNotFoundException.class,
                () -> new DefaultAuthProvider().setPassword("user", "pencil"),
                "In scramOnly mode with no derivable SCRAM credential, setPassword must throw rather than persist a credential-less user.");

            // Verify the refusal happened before any transaction was opened (nothing was written).
            db.verify(DbConnectionManager::getTransactionConnection, never());
        }
    }

    /**
     * A mechanism whose key derivation fails during a password change must have its stored credential removed: the
     * stored row holds keys derived from the *previous* password, so leaving it in place would let the old password
     * continue to authenticate through that mechanism after the password was changed.
     *
     * This test lets SHA-1 and SHA-512 derive normally but forces SHA-256 derivation to fail, then asserts that every
     * supported mechanism is acted upon: written when derivation succeeded, deleted when it failed.
     */
    @Test
    void setPassword_removesStoredCredentialForMechanismThatFailedToDerive() throws Exception
    {
        // Setup test fixture: record each prepared statement's SQL together with the parameters bound to it.
        final Map<PreparedStatement, String> sqlByStatement = new HashMap<>();
        final Map<PreparedStatement, Map<Integer, String>> bindingsByStatement = new HashMap<>();
        final Connection con = Mockito.mock(Connection.class);
        when(con.prepareStatement(anyString())).thenAnswer(inv -> {
            final String sql = inv.getArgument(0, String.class);
            final PreparedStatement ps = Mockito.mock(PreparedStatement.class);
            sqlByStatement.put(ps, sql);
            bindingsByStatement.put(ps, new HashMap<>());
            when(ps.executeUpdate()).thenReturn(1);

            // setPassword also SELECTs the stored mechanisms; return an empty result set for it.
            final ResultSet emptyRs = Mockito.mock(ResultSet.class);
            when(emptyRs.next()).thenReturn(false);
            when(ps.executeQuery()).thenReturn(emptyRs);

            Mockito.doAnswer(bind -> {
                bindingsByStatement.get(ps).put(bind.getArgument(0), bind.getArgument(1));
                return null;
            }).when(ps).setString(Mockito.anyInt(), anyString());
            return ps;
        });

        try (final MockedStatic<ScramUtils> scramUtils = Mockito.mockStatic(ScramUtils.class);
             final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class);
             final MockedStatic<AuthFactory> auth = Mockito.mockStatic(AuthFactory.class))
        {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(con);
            auth.when(() -> AuthFactory.encryptPassword(anyString())).thenReturn("encrypted");

            // Let every mechanism derive, except SCRAM-SHA-256.
            scramUtils.when(() -> ScramUtils.deriveScramKeys(
                    Mockito.any(byte[].class), anyString(), Mockito.anyInt(),
                    Mockito.eq(ScramSha256SaslServer.HMAC_ALGORITHM_NAME), anyString()))
                .thenThrow(new SaslException("forced derivation failure for test"));
            scramUtils.when(() -> ScramUtils.deriveScramKeys(
                    Mockito.any(byte[].class), anyString(), Mockito.anyInt(),
                    Mockito.eq(ScramSha1SaslServer.HMAC_ALGORITHM_NAME), anyString()))
                .thenReturn(new ScramUtils.ScramKeys(new byte[20], new byte[20]));
            scramUtils.when(() -> ScramUtils.deriveScramKeys(
                    Mockito.any(byte[].class), anyString(), Mockito.anyInt(),
                    Mockito.eq(ScramSha512SaslServer.HMAC_ALGORITHM_NAME), anyString()))
                .thenReturn(new ScramUtils.ScramKeys(new byte[64], new byte[64]));

            // Execute system under test.
            new DefaultAuthProvider().setPassword("user", "pencil");

            // Verify result: the mechanism that failed to derive had its stored credential deleted.
            assertEquals(Set.of(ScramSha256SaslServer.MECHANISM_NAME),
                mechanismsTouchedBy(sqlByStatement, bindingsByStatement, sql -> sql.contains("delete")),
                "Exactly the mechanism whose derivation failed must be deleted, so the previous password can no longer authenticate through it.");

            // Verify result: the mechanisms that derived successfully were written.
            assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha512SaslServer.MECHANISM_NAME),
                mechanismsTouchedBy(sqlByStatement, bindingsByStatement, sql -> sql.contains("update") || sql.contains("insert")),
                "Mechanisms that derived successfully must have their credential written.");
        }
    }

    /**
     * Mechanism names bound by statements against ofUserScram whose SQL matches the given predicate.
     */
    private static Set<String> mechanismsTouchedBy(
        final Map<PreparedStatement, String> sqlByStatement,
        final Map<PreparedStatement, Map<Integer, String>> bindingsByStatement,
        final Predicate<String> sqlMatcher)
    {
        final Set<String> allMechanisms = Set.of(
            ScramSha1SaslServer.MECHANISM_NAME,
            ScramSha256SaslServer.MECHANISM_NAME,
            ScramSha512SaslServer.MECHANISM_NAME);

        return sqlByStatement.entrySet().stream()
            // Check if the prepared statement touches the expected database table.
            .filter(e -> {
                final String n = e.getValue().toLowerCase(Locale.ROOT);
                return n.contains("ofuserscram") && sqlMatcher.test(n);
            })
            // Check if the prepared statement actually got executed.
            .filter(e -> Mockito.mockingDetails(e.getKey()).getInvocations().stream()
                .map(invocation -> invocation.getMethod().getName())
                .anyMatch(name -> Set.of("execute", "executeUpdate", "executeLargeUpdate", "executeQuery").contains(name)))
            .flatMap(e -> bindingsByStatement.get(e.getKey()).values().stream())
            .filter(allMechanisms::contains)
            .collect(Collectors.toSet());
    }
}
