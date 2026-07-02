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
package org.jivesoftware.openfire.user;

import org.jivesoftware.Fixtures;
import org.jivesoftware.database.DbConnectionManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link DefaultUserProvider#loadUser(String)} sources a user's descriptive fields from {@code ofUser} and
 * its SCRAM-SHA-1 credential fields from the dedicated {@code ofUserScram} table.
 *
 * Prior to the storage refactor, the SCRAM columns lived on {@code ofUser} and were selected in the same query. They
 * now live in {@code ofUserScram}, keyed by {@code (username, mechanism)}, and are loaded via a second query. A user
 * without a SCRAM row (for example, the admin account during initial auto-setup, before any password has been set) must
 * still load successfully, with the SCRAM fields left unset. That case is the one that regressed during development: the
 * old query selected columns that no longer existed, causing a hard failure at server startup.
 */
@SuppressWarnings("removal") // User's SCRAM accessors are deprecated for removal; this test verifies loadUser still populates them until removed.
public class DefaultUserProviderScramStorageTest
{
    @BeforeAll
    public static void setupClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    /**
     * Builds a mock Connection whose prepareStatement() dispatches to the SCRAM-credential statement or the user
     * statement based on whether the SQL references {@code ofUserScram}.
     */
    private static Connection mockConnection(final PreparedStatement loadUser, final PreparedStatement loadScram) throws Exception
    {
        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            final String sql = invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT);
            return sql.contains("ofuserscram") ? loadScram : loadUser;
        });
        return connection;
    }

    /**
     * Stubs the {@code ofUser} lookup to return a single row of descriptive fields: name, email, creationDate,
     * modificationDate. The dates are Openfire's millisecond-string format.
     */
    private static void stubUserRow(final PreparedStatement loadUser, final String name, final String email) throws Exception
    {
        final ResultSet userRow = Mockito.mock(ResultSet.class);
        when(loadUser.executeQuery()).thenReturn(userRow);
        when(userRow.next()).thenReturn(true);
        when(userRow.getString(1)).thenReturn(name);            // name
        when(userRow.getString(2)).thenReturn(email);           // email
        when(userRow.getString(3)).thenReturn("1000");          // creationDate (millis)
        when(userRow.getString(4)).thenReturn("2000");          // modificationDate (millis)
    }

    /**
     * Verifies that when an {@code ofUserScram} row exists, its salt, serverKey, storedKey and iterations are hydrated
     * onto the returned {@link User}.
     */
    @Test
    void loadUser_populatesScramFieldsFromOfUserScram() throws Exception
    {
        // Setup test fixture.
        final PreparedStatement loadUser = Mockito.mock(PreparedStatement.class);
        final PreparedStatement loadScram = Mockito.mock(PreparedStatement.class);
        stubUserRow(loadUser, "Test User", "user@example.org");

        final ResultSet scramRow = Mockito.mock(ResultSet.class);
        when(loadScram.executeQuery()).thenReturn(scramRow);
        when(scramRow.next()).thenReturn(true);
        when(scramRow.getString(1)).thenReturn("salt-value");   // salt
        when(scramRow.getString(2)).thenReturn("server-key");   // serverKey
        when(scramRow.getString(3)).thenReturn("stored-key");   // storedKey
        when(scramRow.getInt(4)).thenReturn(4096);              // iterations

        final Connection connection = mockConnection(loadUser, loadScram);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);

            // Execute system under test.
            final DefaultUserProvider provider = new DefaultUserProvider();
            final User user = provider.loadUser("user");

            // Verify result.
            assertEquals("salt-value", user.getSalt(), "User salt should be sourced from ofUserScram.");
            assertEquals("server-key", user.getServerKey(), "User server key should be sourced from ofUserScram.");
            assertEquals("stored-key", user.getStoredKey(), "User stored key should be sourced from ofUserScram.");
            assertEquals(4096, user.getIterations(), "User iteration count should be sourced from ofUserScram.");
        }
    }

    /**
     * Verifies that a user with no {@code ofUserScram} row loads successfully, with SCRAM fields left unset. This is the
     * auto-setup / admin case: the account exists in {@code ofUser} but has no SCRAM credential yet, and must not cause
     * loadUser to fail.
     */
    @Test
    void loadUser_succeedsWithoutScramRow() throws Exception
    {
        // Setup test fixture.
        final PreparedStatement loadUser = Mockito.mock(PreparedStatement.class);
        final PreparedStatement loadScram = Mockito.mock(PreparedStatement.class);
        stubUserRow(loadUser, "Admin", "admin@example.org");

        final ResultSet scramRow = Mockito.mock(ResultSet.class);
        when(loadScram.executeQuery()).thenReturn(scramRow);
        when(scramRow.next()).thenReturn(false); // no SCRAM credential for this user

        final Connection connection = mockConnection(loadUser, loadScram);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);

            // Execute system under test.
            final DefaultUserProvider provider = new DefaultUserProvider();
            final User user = provider.loadUser("admin");

            // Verify result.
            assertEquals("Admin", user.getName(), "User without a SCRAM row should still load its descriptive fields.");
            assertNull(user.getSalt(), "Salt should be unset when the user has no ofUserScram row.");
            assertNull(user.getServerKey(), "Server key should be unset when the user has no ofUserScram row.");
            assertNull(user.getStoredKey(), "Stored key should be unset when the user has no ofUserScram row.");
            assertEquals(0, user.getIterations(), "Iteration count should default to 0 when the user has no ofUserScram row.");
        }
    }
}
