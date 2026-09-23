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
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.util.Blowfish;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;

import static org.jivesoftware.openfire.auth.UserTableFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link EncryptedPasswordMigration}.
 *
 * Runs against an in-memory HSQLDB database, with real ciphers and SCRAM credentials.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3374">OF-3374</a>
 */
public class EncryptedPasswordMigrationTest
{
    private static final String PASSWORD_KEY = "test-password-key";

    private Blowfish sha1;
    private Blowfish pbkdf2;
    private String databaseUrl;
    private Connection con;
    private String originalKdf;

    /**
     * Prepares an Openfire home directory, and prevents properties from being stored in a database.
     */
    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    /**
     * Creates SHA1 and PBKDF2 ciphers for the test password key, and an empty in-memory database that holds the user
     * tables.
     */
    @BeforeEach
    public void setup() throws Exception
    {
        originalKdf = JiveGlobals.getBlowfishKdf();

        sha1 = new Blowfish();
        sha1.setKey(PASSWORD_KEY, JiveGlobals.BLOWFISH_KDF_SHA1);
        pbkdf2 = new Blowfish();
        pbkdf2.setKey(PASSWORD_KEY, JiveGlobals.BLOWFISH_KDF_PBKDF2);

        databaseUrl = "jdbc:hsqldb:mem:" + UUID.randomUUID();
        con = DriverManager.getConnection(databaseUrl, "SA", "");
        createUserTables(con);
    }

    /**
     * Discards the in-memory database, and restores the configured KDF, the cached password cipher and the properties
     * that were changed by a test.
     */
    @AfterEach
    public void tearDown() throws Exception
    {
        try (final Statement stmt = con.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
        con.close();

        AuthFactory.resetCipher();
        JiveGlobals.setBlowfishKdf(originalKdf);
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that a password that is encrypted with the source KDF, and that verifies against the SCRAM credential of
     * the user, is re-encrypted with the target KDF.
     */
    @Test
    public void testReencryptsPasswordEncryptedWithSourceKdf() throws Exception
    {
        // Setup test fixture.
        final String original = sha1.encryptString("secret-alice");
        insertUser(con, "alice", null, original);
        insertScramSha1Credential(con, "alice", "secret-alice");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of(), List.of(), List.of(), List.of()), result, "A password that is encrypted with the source KDF and that matches the SCRAM credential of its user is expected to be reported as re-encrypted, and nothing else.");
        final String stored = storedEncryptedPassword(con, "alice");
        assertNotEquals(original, stored, "The stored password is expected to have been replaced by a re-encrypted value.");
        assertEquals("secret-alice", pbkdf2.decryptString(stored), "The re-encrypted password is expected to decrypt to the original password with the target KDF.");
    }

    /**
     * Verifies that a password that is already encrypted with the target KDF is left unchanged. Decrypting it with the
     * source KDF yields a string that does not match the SCRAM credential of the user.
     */
    @Test
    public void testLeavesPasswordEncryptedWithTargetKdfUnchanged() throws Exception
    {
        // Setup test fixture.
        final String original = pbkdf2.encryptString("secret-bob");
        insertUser(con, "bob", null, original);
        insertScramSha1Credential(con, "bob", "secret-bob");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of("bob"), List.of(), List.of(), List.of()), result, "A password that is already encrypted with the target KDF is expected to be reported as unverified, not re-encrypted.");
        assertEquals(original, storedEncryptedPassword(con, "bob"), "A password that is already encrypted with the target KDF is expected to be left unchanged, as re-encrypting it would destroy it.");
    }

    /**
     * Verifies that the password of a user without a SCRAM credential is left unchanged, and reported.
     */
    @Test
    public void testReportsUserWithoutScramCredential() throws Exception
    {
        // Setup test fixture.
        final String original = sha1.encryptString("secret-carol");
        insertUser(con, "carol", null, original);

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of(), List.of("carol"), List.of(), List.of()), result, "A user without a SCRAM credential is expected to be reported as unverifiable, not re-encrypted.");
        assertEquals(original, storedEncryptedPassword(con, "carol"), "The password of a user without a SCRAM credential cannot be verified, and is expected to be left unchanged.");
    }

    /**
     * Verifies that a SCRAM credential that is incomplete is not used for verification, as is the case during
     * authentication. A user with only such a credential is treated as one without a SCRAM credential.
     */
    @Test
    public void testIgnoresIncompleteScramCredential() throws Exception
    {
        // Setup test fixture.
        final String original = sha1.encryptString("secret-carol");
        insertUser(con, "carol", null, original);
        try (final Statement stmt = con.createStatement()) {
            stmt.execute("INSERT INTO ofUserScram (username, mechanism, iterations, salt, storedKey, serverKey) VALUES ('carol', 'SCRAM-SHA-1', 4096, NULL, NULL, NULL)");
        }

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of(), List.of("carol"), List.of(), List.of()), result, "A user with only an incomplete SCRAM credential is expected to be reported as unverifiable, like a user without one.");
        assertEquals(original, storedEncryptedPassword(con, "carol"), "The password of a user with only an incomplete SCRAM credential cannot be verified, and is expected to be left unchanged.");
    }

    /**
     * Verifies that a password that does not match the SCRAM credential of the user is left unchanged, and the user is
     * reported as unverified.
     */
    @Test
    public void testReportsPasswordThatDoesNotMatchScramCredential() throws Exception
    {
        // Setup test fixture.
        final String original = sha1.encryptString("secret-dave");
        insertUser(con, "dave", null, original);
        insertScramSha1Credential(con, "dave", "a-different-password");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of("dave"), List.of(), List.of(), List.of()), result, "A password that does not match the SCRAM credential of its user is expected to be reported as unverified, not re-encrypted.");
        assertEquals(original, storedEncryptedPassword(con, "dave"), "A password that does not match the SCRAM credential of its user is expected to be left unchanged.");
    }

    /**
     * Verifies that verification uses the algorithms of the mechanism of the SCRAM credential, rather than those of
     * SCRAM-SHA-1 only.
     */
    @Test
    public void testVerifiesAgainstScramSha256Credential() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "erin", null, sha1.encryptString("secret-erin"));
        insertScramCredential(con, "erin", "secret-erin", ScramSha256SaslServer.MECHANISM_NAME, ScramSha256SaslServer.HMAC_ALGORITHM_NAME, ScramSha256SaslServer.DIGEST_ALGORITHM_NAME);

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of(), List.of(), List.of(), List.of()), result, "A password that matches a SCRAM-SHA-256 credential is expected to be verified using the algorithms of that mechanism, and re-encrypted.");
        assertEquals("secret-erin", pbkdf2.decryptString(storedEncryptedPassword(con, "erin")), "The re-encrypted password is expected to decrypt to the original password with the target KDF.");
    }

    /**
     * Verifies that users that do not have an encrypted password (for example, because the password is stored in plain
     * text, or only as SCRAM credentials) are not evaluated.
     */
    @Test
    public void testIgnoresUsersWithoutEncryptedPassword() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "frank", "plain-frank", null);
        insertScramSha1Credential(con, "frank", "plain-frank");
        insertUser(con, "grace", null, null);
        insertScramSha1Credential(con, "grace", "secret-grace");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of(), List.of(), List.of(), List.of()), result, "Users without an encrypted password are expected not to be evaluated, and therefore not to appear in the result.");
        assertNull(storedEncryptedPassword(con, "frank"), "A user whose password is stored in plain text is expected to still have no encrypted password.");
        assertNull(storedEncryptedPassword(con, "grace"), "A user without any stored password is expected to still have no encrypted password.");
    }

    /**
     * Verifies that the passwords of users without SCRAM credentials are re-encrypted when requested, while passwords
     * that do not match the SCRAM credentials of their user are still left unchanged.
     */
    @Test
    public void testReencryptsUsersWithoutScramCredentialWhenIncluded() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "carol", null, sha1.encryptString("secret-carol")); // without SCRAM credential
        final String bob = pbkdf2.encryptString("secret-bob"); // already uses the target KDF
        insertUser(con, "bob", null, bob);
        insertScramSha1Credential(con, "bob", "secret-bob");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, true);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of("bob"), List.of(), List.of(), List.of()), result, "Only the password of the user without SCRAM credential is expected to be re-encrypted, and none to be reported as unverifiable.");
        assertEquals("secret-carol", pbkdf2.decryptString(storedEncryptedPassword(con, "carol")), "The password of the user without SCRAM credential is expected to decrypt to the original with the target KDF.");
        assertEquals(bob, storedEncryptedPassword(con, "bob"), "A password that does not match the SCRAM credential of its user is expected to be left unchanged.");
    }

    /**
     * Verifies the outcome for a table that holds a mix of rows, as is the case on an installation where the
     * migration of encrypted properties ran without re-encrypting user passwords, after which some passwords were set.
     */
    @Test
    public void testReencryptsOnlyLegacyRowsInMixedTable() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "alice", null, sha1.encryptString("secret-alice")); // legacy, verifiable
        insertScramSha1Credential(con, "alice", "secret-alice");
        final String bob = pbkdf2.encryptString("secret-bob"); // set after the property migration
        insertUser(con, "bob", null, bob);
        insertScramSha1Credential(con, "bob", "secret-bob");
        final String carol = sha1.encryptString("secret-carol"); // legacy, without SCRAM credential
        insertUser(con, "carol", null, carol);

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of("bob"), List.of("carol"), List.of(), List.of()), result, "In a mixed table, only the legacy password that verifies is expected to be re-encrypted; the others are expected to be reported.");
        assertEquals("secret-alice", pbkdf2.decryptString(storedEncryptedPassword(con, "alice")), "The legacy password that verifies is expected to decrypt to the original password with the target KDF.");
        assertEquals(bob, storedEncryptedPassword(con, "bob"), "The password that already uses the target KDF is expected to be left unchanged.");
        assertEquals(carol, storedEncryptedPassword(con, "carol"), "The legacy password of a user without a SCRAM credential is expected to be left unchanged.");
    }

    /**
     * Verifies that every user is evaluated when there are more users than fit in one page of stored passwords, which
     * requires the paged read to continue after the last user of the previous page.
     */
    @Test
    public void testEvaluatesMoreUsersThanFitInOnePage() throws Exception
    {
        // Setup test fixture. These users have no SCRAM credential, which keeps the test cheap. The user that does
        // have one sorts after all of them, so it can only be found on a later page.
        for (int i = 0; i < 600; i++) {
            insertUser(con, String.format("user-%04d", i), null, sha1.encryptString("secret-" + i));
        }
        insertUser(con, "zara", null, sha1.encryptString("secret-zara"));
        insertScramSha1Credential(con, "zara", "secret-zara");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(1, result.reencrypted(), "The only verifiable user sorts after the first page, and is expected to be re-encrypted, which requires reading more than one page.");
        assertEquals(600, result.unverifiable().size(), "Every user without a SCRAM credential is expected to be evaluated exactly once, across all pages.");
        assertEquals("secret-zara", pbkdf2.decryptString(storedEncryptedPassword(con, "zara")), "The user on a later page is expected to have a password that decrypts to the original with the target KDF.");
    }

    /**
     * Verifies that running the operation a second time does not change passwords that were re-encrypted the first
     * time.
     */
    @Test
    public void testIsIdempotent() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));
        insertScramSha1Credential(con, "alice", "secret-alice");
        EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);
        final String afterFirstRun = storedEncryptedPassword(con, "alice");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, true, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of("alice"), List.of(), List.of(), List.of()), result, "A second run is expected to re-encrypt nothing, and to report the already re-encrypted password as unverified.");
        assertEquals(afterFirstRun, storedEncryptedPassword(con, "alice"), "A second run is expected to leave the password that was re-encrypted by the first run unchanged.");
    }

    /**
     * Verifies that a stored password is not overwritten when it no longer holds the value that was read, as happens
     * when the password is changed while the operation runs.
     */
    @Test
    public void testDoesNotOverwriteConcurrentlyChangedPassword() throws Exception
    {
        // Setup test fixture.
        final String readEarlier = sha1.encryptString("old-password");
        final String changedSince = pbkdf2.encryptString("new-password");
        insertUser(con, "alice", null, changedSince);

        // Execute system under test.
        final boolean updated = EncryptedPasswordMigration.updateIfUnchanged(con, "alice", readEarlier, pbkdf2.encryptString("old-password"));

        // Verify results.
        assertFalse(updated, "A stored password that no longer holds the value that was read is expected not to be updated.");
        assertEquals(changedSince, storedEncryptedPassword(con, "alice"), "A password that was changed since it was read is expected to keep its changed value, rather than being overwritten.");
    }

    /**
     * Verifies that a stored password is replaced when it still holds the value that was read.
     */
    @Test
    public void testUpdatesUnchangedPassword() throws Exception
    {
        // Setup test fixture.
        final String readEarlier = sha1.encryptString("password");
        insertUser(con, "alice", null, readEarlier);
        final String replacement = pbkdf2.encryptString("password");

        // Execute system under test.
        final boolean updated = EncryptedPasswordMigration.updateIfUnchanged(con, "alice", readEarlier, replacement);

        // Verify results.
        assertTrue(updated, "A stored password that still holds the value that was read is expected to be updated.");
        assertEquals(replacement, storedEncryptedPassword(con, "alice"), "The stored password is expected to have been replaced by the provided value.");
    }

    /**
     * Verifies that the public entry point uses the configured password key, deriving it with the requested KDFs.
     */
    @Test
    public void testReencryptsUsingConfiguredPasswordKey() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("passwordKey", PASSWORD_KEY);
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));
        insertScramSha1Credential(con, "alice", "secret-alice");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencryptVerified(con, JiveGlobals.BLOWFISH_KDF_SHA1, JiveGlobals.BLOWFISH_KDF_PBKDF2, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of(), List.of(), List.of(), List.of()), result, "With the configured password key, a password that verifies is expected to be re-encrypted, and nothing else.");
        assertEquals("secret-alice", pbkdf2.decryptString(storedEncryptedPassword(con, "alice")), "The re-encrypted password is expected to decrypt with a key that is derived from the configured password key with the target KDF.");
    }

    /**
     * Verifies that nothing is changed when no password key is configured.
     */
    @Test
    public void testDoesNothingWithoutPasswordKey() throws Exception
    {
        // Setup test fixture.
        final String original = sha1.encryptString("secret-alice");
        insertUser(con, "alice", null, original);
        insertScramSha1Credential(con, "alice", "secret-alice");

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencryptVerified(con, JiveGlobals.BLOWFISH_KDF_SHA1, JiveGlobals.BLOWFISH_KDF_PBKDF2, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(0, List.of(), List.of(), List.of(), List.of()), result, "Without a password key, nothing can have been encrypted with it, so nothing is expected to be evaluated or re-encrypted.");
        assertEquals(original, storedEncryptedPassword(con, "alice"), "Without a password key, the stored password is expected to be left unchanged.");
    }

    /**
     * Verifies that unverified re-encryption re-encrypts every password, including those of users without a SCRAM
     * credential.
     */
    @Test
    public void testReencryptsAllIncludingUsersWithoutScramCredential() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));
        insertScramSha1Credential(con, "alice", "secret-alice");
        insertUser(con, "carol", null, sha1.encryptString("secret-carol")); // no SCRAM credential
        insertUser(con, "dave", null, sha1.encryptString("secret-dave"));
        insertScramSha1Credential(con, "dave", "a-different-password");     // password and credential disagree

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, false, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(3, List.of(), List.of(), List.of(), List.of()), result, "Unverified re-encryption is expected to re-encrypt every stored password, regardless of SCRAM credentials.");
        assertEquals("secret-alice", pbkdf2.decryptString(storedEncryptedPassword(con, "alice")), "The password of a user whose SCRAM credential matches is expected to decrypt to the original with the target KDF.");
        assertEquals("secret-carol", pbkdf2.decryptString(storedEncryptedPassword(con, "carol")), "The password of a user without a SCRAM credential is expected to decrypt to the original with the target KDF.");
        assertEquals("secret-dave", pbkdf2.decryptString(storedEncryptedPassword(con, "dave")), "The password of a user whose SCRAM credential does not match is expected to decrypt to the original with the target KDF.");
    }

    /**
     * Verifies that unverified re-encryption reports, and leaves unchanged, a stored password that does not decrypt to
     * a usable value.
     */
    @Test
    public void testReportsStoredPasswordThatDoesNotDecrypt() throws Exception
    {
        // Setup test fixture. This value is too short to hold the initialisation vector that decryption needs.
        insertUser(con, "mallory", null, "00");
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(con, sha1, pbkdf2, false, false);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of(), List.of(), List.of("mallory"), List.of()), result, "A stored password that does not decrypt is expected to be reported as undecryptable, while other passwords are still re-encrypted.");
        assertEquals("00", storedEncryptedPassword(con, "mallory"), "A stored password that does not decrypt is expected to be left unchanged.");
        assertEquals("secret-alice", pbkdf2.decryptString(storedEncryptedPassword(con, "alice")), "A valid password in the same table is expected to be re-encrypted despite another one not decrypting.");
    }

    /**
     * Verifies that the public entry point for unverified re-encryption uses the configured password key.
     */
    @Test
    public void testReencryptsAllUsingConfiguredPasswordKey() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("passwordKey", PASSWORD_KEY);
        insertUser(con, "carol", null, sha1.encryptString("secret-carol")); // no SCRAM credential

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencryptAll(con, JiveGlobals.BLOWFISH_KDF_SHA1, JiveGlobals.BLOWFISH_KDF_PBKDF2);

        // Verify results.
        assertEquals(new EncryptedPasswordMigration.Result(1, List.of(), List.of(), List.of(), List.of()), result, "Unverified re-encryption with the configured password key is expected to re-encrypt the password of a user without a SCRAM credential.");
        assertEquals("secret-carol", pbkdf2.decryptString(storedEncryptedPassword(con, "carol")), "The re-encrypted password is expected to decrypt with a key that is derived from the configured password key with the target KDF.");
    }

    /**
     * Verifies that the public entry point refuses to 're-encrypt' with the KDF that the passwords were encrypted with.
     */
    @Test
    public void testRefusesIdenticalKdfs()
    {
        assertThrows(IllegalArgumentException.class, () -> EncryptedPasswordMigration.reencryptVerified(con, JiveGlobals.BLOWFISH_KDF_SHA1, JiveGlobals.BLOWFISH_KDF_SHA1, false), "Re-encrypting from a KDF to the same KDF is expected to be refused, as it cannot change anything and indicates a mistake.");
    }

    /**
     * Verifies that the cached password cipher only picks up a changed KDF through
     * {@link AuthFactory#replacePasswordCipher(Callable)}.
     */
    @Test
    public void testReplacePasswordCipherPicksUpChangedKdf() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("passwordKey", PASSWORD_KEY);
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);
        AuthFactory.resetCipher();
        assertEquals("secret", sha1.decryptString(AuthFactory.encryptPassword("secret")), "Precondition: while the configured KDF is SHA1, the cached cipher is expected to use SHA1.");

        // Execute system under test.
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
        final String beforeReplacement = AuthFactory.encryptPassword("secret");
        AuthFactory.replacePasswordCipher(() -> null);
        final String afterReplacement = AuthFactory.encryptPassword("secret");

        // Verify results.
        assertEquals("secret", sha1.decryptString(beforeReplacement), "The cached cipher is expected to still use the previously configured KDF.");
        assertEquals("secret", pbkdf2.decryptString(afterReplacement), "After a replacement, the cipher is expected to use the newly configured KDF.");
    }

    /**
     * Verifies that the cached password cipher is discarded even when the operation that replaces it fails, so that the
     * cipher always matches the configured KDF, whatever part of that operation did complete.
     */
    @Test
    public void testReplacePasswordCipherDiscardsCipherOnFailure()
    {
        // Setup test fixture.
        JiveGlobals.setProperty("passwordKey", PASSWORD_KEY);
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);
        AuthFactory.resetCipher();
        AuthFactory.encryptPassword("secret"); // caches a SHA1 cipher.

        // Execute system under test.
        assertThrows(IllegalStateException.class, () -> AuthFactory.replacePasswordCipher(() -> {
            JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
            throw new IllegalStateException("Simulated failure after the KDF changed.");
        }), "The failure of the operation is expected to be propagated to the caller.");

        // Verify results.
        assertEquals("secret", pbkdf2.decryptString(AuthFactory.encryptPassword("secret")), "After a failed replacement, the cached cipher is expected to have been discarded, and to use the KDF that is configured by then.");
    }

    /**
     * Verifies that no password is encrypted while the password cipher is being replaced, and that a password that is
     * encrypted concurrently is encrypted with the replacement cipher (OF-3374).
     */
    @Test
    public void testEncryptionWaitsForCipherReplacement() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("passwordKey", PASSWORD_KEY);
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);
        AuthFactory.resetCipher();
        AuthFactory.encryptPassword("secret"); // caches a SHA1 cipher.

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final CountDownLatch replacing = new CountDownLatch(1);
            final CountDownLatch mayComplete = new CountDownLatch(1);

            // Execute system under test.
            final Thread replacer = new Thread(() -> {
                try {
                    AuthFactory.replacePasswordCipher(() -> {
                        replacing.countDown();
                        assertTrue(mayComplete.await(10, TimeUnit.SECONDS), "The test stalled: it did not let the cipher replacement complete within 10 seconds.");
                        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
                        return null;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            replacer.start();
            assertTrue(replacing.await(10, TimeUnit.SECONDS), "The cipher replacement was expected to have started within 10 seconds.");

            final Future<String> encrypted = executor.submit(() -> AuthFactory.encryptPassword("secret"));

            // Verify results.
            assertThrows(TimeoutException.class, () -> encrypted.get(500, TimeUnit.MILLISECONDS), "Encryption is expected to wait while the cipher is being replaced.");
            mayComplete.countDown();
            replacer.join(10_000);
            assertEquals("secret", pbkdf2.decryptString(encrypted.get(10, TimeUnit.SECONDS)), "The waiting encryption is expected to use the replacement cipher.");
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Verifies that the password cipher is not replaced while it is in use for storing a password, as that password
     * would then not be re-encrypted by the replacement (OF-3374).
     */
    @Test
    public void testCipherReplacementWaitsForPasswordBeingStored() throws Exception
    {
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final Lock useLock = AuthFactory.getPasswordCipherUseLock();
        useLock.lock();
        boolean locked = true;
        try {
            // Execute system under test.
            final Future<String> replaced = executor.submit(() -> AuthFactory.replacePasswordCipher(() -> "replaced"));

            // Verify results.
            assertThrows(TimeoutException.class, () -> replaced.get(500, TimeUnit.MILLISECONDS), "Replacement is expected to wait while a password is being stored.");
            useLock.unlock();
            locked = false;
            assertEquals("replaced", replaced.get(10, TimeUnit.SECONDS), "Once no password is being stored anymore, the replacement is expected to complete.");
        } finally {
            if (locked) {
                useLock.unlock();
            }
            executor.shutdownNow();
        }
    }
    /**
     * Verifies that no repair is offered when no user password is stored encrypted, and that this is recorded, so that
     * it does not need to be determined again.
     */
    @Test
    public void testRepairNotNeededWithoutEncryptedPasswords() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
        forgetCachedEncryptedPasswordsExist();
        insertUser(con, "frank", "plain-frank", null);

        // Execute system under test.
        final boolean needed;
        try (final MockedStatic<DbConnectionManager> db = mockDatabase()) {
            needed = EncryptedPasswordMigration.isRepairNeeded();
        }

        // Verify results.
        assertFalse(needed, "Without any encrypted user password, there is nothing to repair.");
        assertFalse(JiveGlobals.isPasswordReencryptionNeeded(), "The absence of encrypted user passwords is expected to be recorded, as any password that is encrypted from now on uses PBKDF2.");
    }

    /**
     * Verifies that a repair is offered when user passwords are stored encrypted, and stored passwords are not known to
     * have been re-encrypted.
     */
    @Test
    public void testRepairNeededWithEncryptedPasswords() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
        forgetCachedEncryptedPasswordsExist();
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));

        // Execute system under test.
        final boolean needed;
        try (final MockedStatic<DbConnectionManager> db = mockDatabase()) {
            needed = EncryptedPasswordMigration.isRepairNeeded();
        }

        // Verify results.
        assertTrue(needed, "Encrypted user passwords that are not known to have been re-encrypted may still use SHA1, and are expected to warrant a repair.");
        assertTrue(JiveGlobals.isPasswordReencryptionNeeded(), "The presence of encrypted user passwords is expected not to be recorded as them having been re-encrypted.");
    }

    /**
     * Verifies that no repair is offered, and the database is not queried, once stored passwords are known to have
     * been re-encrypted.
     */
    @Test
    public void testRepairNotNeededOnceReencrypted() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
        JiveGlobals.setPasswordsReencrypted(true);
        forgetCachedEncryptedPasswordsExist();
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));

        // Execute system under test.
        final boolean needed;
        try (final MockedStatic<DbConnectionManager> db = mockDatabase()) {
            needed = EncryptedPasswordMigration.isRepairNeeded();

            // Verify results.
            db.verify(DbConnectionManager::getConnection, Mockito.never().description("The database is expected not to be queried once stored passwords are known to have been re-encrypted."));
        }
        assertFalse(needed, "Once stored passwords are known to have been re-encrypted, no repair is expected to be offered.");
    }

    /**
     * Has {@link DbConnectionManager} provide new connections to the database of this test (and do nothing else).
     *
     * @return the mocked connection manager, which must be closed by the caller
     */
    private MockedStatic<DbConnectionManager> mockDatabase()
    {
        final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class);
        db.when(DbConnectionManager::getConnection).thenAnswer(invocation -> DriverManager.getConnection(databaseUrl, "SA", ""));
        return db;
    }

    /**
     * Discards what {@link EncryptedPasswordMigration#isRepairNeeded()} caches about the existence of encrypted
     * passwords, which would otherwise carry over between tests.
     */
    private static void forgetCachedEncryptedPasswordsExist() throws Exception
    {
        final Field field = EncryptedPasswordMigration.class.getDeclaredField("encryptedPasswordsExist");
        field.setAccessible(true);
        field.setBoolean(null, false);
    }

    /**
     * Verifies that every user is evaluated on a database that, like Oracle, treats an empty string as NULL.
     */
    @Test
    public void testEvaluatesAllUsersWhenEmptyStringIsNull() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "alice", null, sha1.encryptString("secret-alice"));
        insertUser(con, "bob", null, sha1.encryptString("secret-bob"));

        // Execute system under test.
        final EncryptedPasswordMigration.Result result = EncryptedPasswordMigration.reencrypt(emptyStringIsNull(con), sha1, pbkdf2, false, false);

        // Verify results.
        assertEquals(2, result.reencrypted(), "Every user is expected to be re-encrypted, also when an empty string is treated as NULL.");
    }

    /**
     * Wraps a connection so that it binds an empty string as NULL, as the Oracle JDBC driver does.
     *
     * @param con the connection to wrap
     * @return the wrapped connection
     */
    private static Connection emptyStringIsNull(final Connection con)
    {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
            final Object result = invoke(con, method, args);
            if (!(result instanceof PreparedStatement pstmt)) {
                return result;
            }
            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
                if (m.getName().equals("setString") && "".equals(a[1])) {
                    pstmt.setNull((Integer) a[0], Types.VARCHAR);
                    return null;
                }
                return invoke(pstmt, m, a);
            });
        });
    }

    /**
     * Invokes a method, rethrowing what it throws rather than a wrapping exception.
     *
     * @param target the object to invoke the method on
     * @param method the method
     * @param args   the arguments (can be null)
     * @return the result of the method
     */
    private static Object invoke(final Object target, final java.lang.reflect.Method method, final Object[] args) throws Throwable
    {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
