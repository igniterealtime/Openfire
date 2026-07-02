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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link DefaultAuthProvider} sources SCRAM credentials from the dedicated {@code ofUserScram} table
 * (rather than from columns on {@code ofUser}, which is where they lived prior to this change).
 */
public class DefaultAuthProviderScramStorageTest
{
    private static final String PASSWORD = "pencil";
    private static final String SALT_BASE64 = "QSXCR+Q6sek8bf92";
    private static final int ITERATIONS = 4096;

    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    /**
     * Computes the base64 stored key for a password using the same SCRAM-SHA-1 derivation as production code, so the
     * test verifies real cryptographic agreement rather than an echoed constant.
     */
    private static String storedKeyFor(final String password) throws Exception
    {
        final byte[] salt = DatatypeConverter.parseBase64Binary(SALT_BASE64);
        final byte[] saltedPassword = ScramUtils.createSaltedPassword(salt, password, ITERATIONS);
        final byte[] clientKey = ScramUtils.computeHmac(saltedPassword, "Client Key");
        return DatatypeConverter.printBase64Binary(MessageDigest.getInstance("SHA-1").digest(clientKey));
    }

    /**
     * Builds a mock Connection whose prepareStatement() dispatches to a SCRAM statement or a password statement based
     * on whether the SQL references {@code ofUserScram}.
     *
     * The {@code ofUser} password lookup always returns a single row with null plaintext and null encrypted password,
     * forcing the provider down the SCRAM-verification path. The {@code ofUserScram} lookup returns the supplied
     * credential row.
     */
    private static Connection mockConnection(final PreparedStatement loadPassword, final PreparedStatement loadScram) throws Exception
    {
        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            final String sql = invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT);
            return sql.contains("ofuserscram") ? loadScram : loadPassword;
        });
        return connection;
    }

    /**
     * Configures the {@code ofUser} password lookup to return a single row with no plaintext/encrypted password. A
     * fresh ResultSet is returned on each executeQuery() so that repeated LOAD_PASSWORD calls (checkPassword's own
     * lookup and the lookup performed while resolving the SCRAM credential) each see a valid, independent cursor.
     */
    private static void stubEmptyPasswordRow(final PreparedStatement loadPassword) throws Exception
    {
        when(loadPassword.executeQuery()).thenAnswer(invocation -> {
            final ResultSet rs = Mockito.mock(ResultSet.class);
            when(rs.next()).thenReturn(true, false);
            when(rs.getString(1)).thenReturn(null); // plainPassword
            when(rs.getString(2)).thenReturn(null); // encryptedPassword
            return rs;
        });
    }

    /**
     * Verifies that a correct password is accepted by verifying against SCRAM material held in {@code ofUserScram},
     * and that the {@code ofUserScram} table was actually consulted.
     */
    @Test
    void checkPassword_acceptsCorrectPassword_fromOfUserScram() throws Exception
    {
        // Setup test fixture.
        final PreparedStatement loadPassword = Mockito.mock(PreparedStatement.class);
        final PreparedStatement loadScram = Mockito.mock(PreparedStatement.class);
        stubEmptyPasswordRow(loadPassword);

        final ResultSet scramRow = Mockito.mock(ResultSet.class);
        when(loadScram.executeQuery()).thenReturn(scramRow);
        when(scramRow.next()).thenReturn(true);
        when(scramRow.getInt(1)).thenReturn(ITERATIONS);
        when(scramRow.getString(2)).thenReturn(SALT_BASE64);
        when(scramRow.getString(3)).thenReturn(storedKeyFor(PASSWORD));
        when(scramRow.getString(4)).thenReturn("server-key-not-used-for-password-check");

        final Connection connection = mockConnection(loadPassword, loadScram);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);

            // Execute system under test.
            final DefaultAuthProvider provider = new DefaultAuthProvider();
            final boolean result = provider.checkPassword("user", PASSWORD);

            // Verify result.
            assertTrue(result, "A correct password should verify against SCRAM material stored in ofUserScram.");
            verify(connection, atLeastOnce()).prepareStatement(
                ArgumentMatchers.argThat(sql -> sql != null && sql.toLowerCase(Locale.ROOT).contains("ofuserscram")));
        }
    }

    /**
     * Verifies that an incorrect password is rejected when checked against SCRAM material in {@code ofUserScram}.
     */
    @Test
    void checkPassword_rejectsIncorrectPassword_fromOfUserScram() throws Exception
    {
        // Setup test fixture.
        final PreparedStatement loadPassword = Mockito.mock(PreparedStatement.class);
        final PreparedStatement loadScram = Mockito.mock(PreparedStatement.class);
        stubEmptyPasswordRow(loadPassword);

        final ResultSet scramRow = Mockito.mock(ResultSet.class);
        when(loadScram.executeQuery()).thenReturn(scramRow);
        when(scramRow.next()).thenReturn(true);
        when(scramRow.getInt(1)).thenReturn(ITERATIONS);
        when(scramRow.getString(2)).thenReturn(SALT_BASE64);
        when(scramRow.getString(3)).thenReturn(storedKeyFor(PASSWORD)); // stored key for the *correct* password
        when(scramRow.getString(4)).thenReturn("server-key-not-used-for-password-check");

        final Connection connection = mockConnection(loadPassword, loadScram);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);

            // Execute system under test.
            final DefaultAuthProvider provider = new DefaultAuthProvider();
            final boolean result = provider.checkPassword("user", "not-the-password");

            // Verify result.
            assertFalse(result, "An incorrect password must not verify against SCRAM material stored in ofUserScram.");
        }
    }
}
