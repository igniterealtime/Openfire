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
import org.jivesoftware.database.ConnectionProvider;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.openfire.sasl.ScramSha1SaslServer;
import org.jivesoftware.openfire.sasl.ScramSha256SaslServer;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Blowfish;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.jivesoftware.openfire.auth.UserTableFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link DefaultAuthProvider} only derives credentials from a decrypted password that is known to be
 * correct: one that still uses SHA1 decrypts to a wrong value (OF-3374). Runs against an in-memory HSQLDB database, with
 * real ciphers and SCRAM credentials.
 *
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3374">OF-3374</a>
 */
public class DefaultAuthProviderDecryptedPasswordTest
{
    private static final String PASSWORD_KEY = "test-password-key";
    private static final String PASSWORD = "secret-alice";

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
     * Configures the password key and PBKDF2 as the KDF, creates SHA1 and PBKDF2 ciphers for that key, and creates an
     * empty in-memory database that holds the user tables, to which {@link DbConnectionManager} provides connections (on
     * any thread).
     */
    @BeforeEach
    public void setup() throws Exception
    {
        originalKdf = JiveGlobals.getBlowfishKdf();
        JiveGlobals.setProperty("passwordKey", PASSWORD_KEY);
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
        AuthFactory.resetCipher();

        sha1 = new Blowfish();
        sha1.setKey(PASSWORD_KEY, JiveGlobals.BLOWFISH_KDF_SHA1);
        pbkdf2 = new Blowfish();
        pbkdf2.setKey(PASSWORD_KEY, JiveGlobals.BLOWFISH_KDF_PBKDF2);

        databaseUrl = "jdbc:hsqldb:mem:" + UUID.randomUUID();
        con = DriverManager.getConnection(databaseUrl, "SA", "");
        createUserTables(con);

        setConnectionProvider(new ConnectionProvider() {
            public boolean isPooled() { return false; }
            public Connection getConnection() throws SQLException { return DriverManager.getConnection(databaseUrl, "SA", ""); }
            public void start() {}
            public void restart() {}
            public void destroy() {}
        });
    }

    /**
     * Discards the in-memory database and its connection provider, and restores the configured KDF, the cached
     * password cipher and the properties that were changed by a test.
     */
    @AfterEach
    public void tearDown() throws Exception
    {
        setConnectionProvider(null);
        try (final Statement stmt = con.createStatement()) {
            stmt.execute("SHUTDOWN");
        }
        con.close();

        AuthFactory.resetCipher();
        JiveGlobals.setBlowfishKdf(originalKdf);
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that SCRAM credentials are not regenerated from a stored password that does not match them (as one that
     * still uses SHA1), and that the stored password is left unchanged.
     */
    @Test
    public void testDoesNotRegenerateScramCredentialsFromUnverifiedPassword() throws Exception
    {
        // Setup test fixture.
        final String stored = sha1.encryptString(PASSWORD);
        insertUser(con, "alice", null, stored);
        insertScramCredential(con, "alice", PASSWORD, ScramSha1SaslServer.MECHANISM_NAME, ScramSha1SaslServer.HMAC_ALGORITHM_NAME, ScramSha1SaslServer.DIGEST_ALGORITHM_NAME);
        final Map<String, String> credentialsBefore = storedKeysByMechanism(con, "alice");

        // Execute system under test.
        new DefaultAuthProvider().getScramCredential("alice", ScramSha1SaslServer.MECHANISM_NAME);

        // Verify results.
        assertEquals(stored, storedEncryptedPassword(con, "alice"), "A stored password that does not match the SCRAM credential of its user is expected to be left unchanged, so that it can still be repaired.");
        assertEquals(credentialsBefore, storedKeysByMechanism(con, "alice"), "The SCRAM credentials of the user are expected not to be regenerated from a password that does not match them.");
    }

    /**
     * Verifies that missing SCRAM credentials are still regenerated from a stored password that matches the credential
     * that the user has.
     */
    @Test
    public void testRegeneratesScramCredentialsFromVerifiedPassword() throws Exception
    {
        // Setup test fixture.
        insertUser(con, "alice", null, pbkdf2.encryptString(PASSWORD));
        insertScramCredential(con, "alice", PASSWORD, ScramSha1SaslServer.MECHANISM_NAME, ScramSha1SaslServer.HMAC_ALGORITHM_NAME, ScramSha1SaslServer.DIGEST_ALGORITHM_NAME);

        // Execute system under test.
        new DefaultAuthProvider().getScramCredential("alice", ScramSha1SaslServer.MECHANISM_NAME);

        // Verify results.
        assertTrue(storedKeysByMechanism(con, "alice").containsKey(ScramSha256SaslServer.MECHANISM_NAME), "A missing SCRAM credential is expected to be regenerated from a stored password that matches the credential that the user has.");
    }

    /**
     * Verifies that no SCRAM credentials are generated for a user that has none, from a stored password that may still
     * be encrypted with SHA1, while stored passwords may still need to be re-encrypted.
     */
    @Test
    public void testDoesNotGenerateScramCredentialsWhileRepairIsNeeded() throws Exception
    {
        // Setup test fixture.
        final String stored = sha1.encryptString(PASSWORD);
        insertUser(con, "alice", null, stored);

        // Execute system under test.
        assertThrows(UserNotFoundException.class, () -> new DefaultAuthProvider().getScramCredential("alice", ScramSha1SaslServer.MECHANISM_NAME),
            "No SCRAM credential is expected to be available for a user that has none, when none can be generated.");

        // Verify results.
        assertEquals(stored, storedEncryptedPassword(con, "alice"), "A stored password that cannot be verified is expected to be left unchanged while stored passwords may still need to be re-encrypted.");
        assertTrue(storedKeysByMechanism(con, "alice").isEmpty(), "No SCRAM credential is expected to be generated from a password that cannot be verified, while stored passwords may still need to be re-encrypted.");
    }

    /**
     * Verifies that SCRAM credentials are generated for a user that has none, once stored passwords are known to be
     * encrypted with the configured KDF.
     */
    @Test
    public void testGeneratesScramCredentialsOnceRepairIsNotNeeded() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setPasswordsReencrypted(true);
        insertUser(con, "alice", null, pbkdf2.encryptString(PASSWORD));

        // Execute system under test.
        new DefaultAuthProvider().getScramCredential("alice", ScramSha1SaslServer.MECHANISM_NAME);

        // Verify results.
        assertTrue(storedKeysByMechanism(con, "alice").containsKey(ScramSha1SaslServer.MECHANISM_NAME), "SCRAM credentials are expected to be generated from the stored password once stored passwords are known to be encrypted with the configured KDF.");
    }

    /**
     * Verifies that in SCRAM-only mode, a stored password that does not decrypt to the password that the user provides
     * is not converted into SCRAM credentials, as it may still be encrypted with SHA1.
     */
    @Test
    public void testScramOnlyDoesNotConvertUnverifiedPassword() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("user.scramHashedPasswordOnly", "true");
        final String stored = sha1.encryptString(PASSWORD);
        insertUser(con, "alice", null, stored);

        // Execute system under test.
        final boolean authenticated = new DefaultAuthProvider().checkPassword("alice", PASSWORD);

        // Verify results.
        assertFalse(authenticated, "A password that is still encrypted with SHA1 is expected not to decrypt to the password of the user.");
        assertEquals(stored, storedEncryptedPassword(con, "alice"), "A stored password that does not decrypt to the provided password is expected to be left unchanged, so that it can still be repaired.");
        assertTrue(storedKeysByMechanism(con, "alice").isEmpty(), "No SCRAM credential is expected to be derived from a password that does not decrypt to the provided password.");
    }

    /**
     * Verifies that in SCRAM-only mode, a stored password that decrypts to the password that the user provides is
     * converted into SCRAM credentials.
     */
    @Test
    public void testScramOnlyConvertsVerifiedPassword() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("user.scramHashedPasswordOnly", "true");
        insertUser(con, "alice", null, pbkdf2.encryptString(PASSWORD));

        // Execute system under test.
        final boolean authenticated = new DefaultAuthProvider().checkPassword("alice", PASSWORD);

        // Verify results.
        assertTrue(authenticated, "A password that is encrypted with the configured KDF is expected to decrypt to the password of the user.");
        assertNull(storedEncryptedPassword(con, "alice"), "In SCRAM-only mode, the stored password is expected to be removed once it is converted into SCRAM credentials.");
        assertTrue(storedKeysByMechanism(con, "alice").containsKey(ScramSha1SaslServer.MECHANISM_NAME), "In SCRAM-only mode, a verified password is expected to be converted into SCRAM credentials.");
    }

    /**
     * Verifies that in SCRAM-only mode, a stored password is not converted into SCRAM credentials when the user
     * provides a different password, as the stored password is not known to decrypt correctly in that case.
     */
    @Test
    public void testScramOnlyDoesNotConvertOnWrongPassword() throws Exception
    {
        // Setup test fixture.
        JiveGlobals.setProperty("user.scramHashedPasswordOnly", "true");
        final String stored = pbkdf2.encryptString(PASSWORD);
        insertUser(con, "alice", null, stored);

        // Execute system under test.
        final boolean authenticated = new DefaultAuthProvider().checkPassword("alice", "a-wrong-password");

        // Verify results.
        assertFalse(authenticated, "A wrong password is expected to be rejected.");
        assertEquals(stored, storedEncryptedPassword(con, "alice"), "The stored password is expected to be left unchanged when the user provides a different password.");
        assertTrue(storedKeysByMechanism(con, "alice").isEmpty(), "No SCRAM credential is expected to be derived when the user provides a different password.");
    }


    /**
     * Verifies that a login that starts while the password cipher is being replaced does not decrypt the password that
     * it read before the replacement with the replacement cipher, and derive credentials from the wrong value.
     */
    @Test
    public void testLoginDuringCipherReplacementUsesReplacedPassword() throws Exception
    {
        // Setup test fixture: an installation that is about to be migrated, with a user without SCRAM credentials.
        JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_SHA1);
        AuthFactory.resetCipher();
        insertUser(con, "alice", null, sha1.encryptString(PASSWORD));

        final CountDownLatch replacing = new CountDownLatch(1);
        final CountDownLatch mayComplete = new CountDownLatch(1);
        final Thread migration = new Thread(() -> {
            try {
                AuthFactory.replacePasswordCipher(() -> {
                    replacing.countDown();
                    mayComplete.await(10, TimeUnit.SECONDS);
                    try (final Statement stmt = con.createStatement()) {
                        stmt.execute("UPDATE ofUser SET encryptedPassword = '" + pbkdf2.encryptString(PASSWORD) + "' WHERE username = 'alice'");
                    }
                    JiveGlobals.setBlowfishKdf(JiveGlobals.BLOWFISH_KDF_PBKDF2);
                    JiveGlobals.setPasswordsReencrypted(true);
                    return null;
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        migration.start();
        assertTrue(replacing.await(10, TimeUnit.SECONDS), "The simulated migration was expected to have started within 10 seconds.");

        // Execute system under test: a login that has to wait for the migration.
        final AtomicReference<Exception> loginFailure = new AtomicReference<>();
        final Thread login = new Thread(() -> {
            try {
                new DefaultAuthProvider().getScramCredential("alice", ScramSha1SaslServer.MECHANISM_NAME);
            } catch (Exception e) {
                loginFailure.set(e);
            }
        });
        login.start();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (login.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(Thread.State.WAITING, login.getState(), "The login was expected to wait for the migration.");
        mayComplete.countDown();
        migration.join(10_000);
        login.join(10_000);

        // Verify results.
        assertNull(loginFailure.get(), "The login is expected to succeed once the migration completed.");
        assertEquals(PASSWORD, pbkdf2.decryptString(storedEncryptedPassword(con, "alice")), "The stored password is expected to still be the actual password, encrypted with PBKDF2.");
        assertTrue(DefaultAuthProvider.matchesAnyScramCredential("alice", PASSWORD, EncryptedPasswordMigration.loadScramCredentials(con, "alice")), "The generated SCRAM credentials are expected to be derived from the actual password.");
    }

    /**
     * Sets the connection provider of {@link DbConnectionManager} directly, which (unlike its setter) does not
     * validate the database schema.
     *
     * @param provider the connection provider (can be null)
     */
    private static void setConnectionProvider(final ConnectionProvider provider) throws Exception
    {
        final Field field = DbConnectionManager.class.getDeclaredField("connectionProvider");
        field.setAccessible(true);
        field.set(null, provider);
    }
}
