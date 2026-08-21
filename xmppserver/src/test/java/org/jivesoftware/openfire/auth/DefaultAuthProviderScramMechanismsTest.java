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
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Verifies which SCRAM mechanism names {@link DefaultAuthProvider} reports as usable, both for an identified user
 * ({@link DefaultAuthProvider#getScramMechanisms(String)}) and for an unidentified one
 * ({@link DefaultAuthProvider#getFallbackScramMechanisms()}).
 *
 * Three properties matter beyond the plain lookup:
 *
 * <ul>
 *     <li>When a password can be retrieved, credentials for every mechanism can be derived on demand, so the stored set does not constrain what can be offered.</li>
 *     <li>A user for whom nothing usable can be determined falls back to SCRAM-SHA-1. That keeps the result from revealing whether the claimed user exists, and keeps a database failure from denying authentication outright.</li>
 *     <li>Mechanisms that are stored but that this implementation cannot service are removed <em>before</em> that fallback is considered, so that a user holding only such credentials is not left with nothing.</li>
 * </ul>
 */
public class DefaultAuthProviderScramMechanismsTest
{
    private static final String USERNAME = "juliet";

    @BeforeAll
    public static void beforeAll() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();

        // Force class initialization here, while JiveGlobals is not mocked. These classes define SystemProperty
        // instances in their static initializer, which resolve localized strings and property values. Left to happen
        // on first use, that would occur inside the mocked-static scope of a test, where those lookups do not behave.
        Class.forName(ScramSha1SaslServer.class.getName());
        Class.forName(ScramSha256SaslServer.class.getName());
        Class.forName(ScramSha512SaslServer.class.getName());
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that all implemented mechanisms are reported when a password can be retrieved, as credentials for each
     * of them can then be derived on demand.
     */
    @Test
    void getScramMechanisms_returnsAllMechanismsWhenPasswordIsRetrievable() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = mechanismResultSet(ScramSha1SaslServer.MECHANISM_NAME);

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, false, rs);

        // Verify result.
        assertEquals(allImplementedMechanisms(), result, "When a password can be retrieved, credentials for every implemented mechanism can be derived, so all of them must be reported.");
    }

    /**
     * Verifies that the mechanisms that are stored for a user are reported, when no password can be retrieved.
     */
    @Test
    void getScramMechanisms_returnsStoredMechanisms() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = mechanismResultSet(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME);

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), result, "The mechanisms that are stored for the user must be reported.");
    }

    /**
     * Verifies that a mechanism that is stored, but that this implementation cannot service, is not reported. Its
     * credentials cannot be retrieved through #getScramCredential, so offering it would present the peer with a
     * mechanism that cannot complete.
     */
    @Test
    void getScramMechanisms_omitsUnrecognizedStoredMechanism() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = mechanismResultSet(ScramSha256SaslServer.MECHANISM_NAME, "SCRAM-SHA3-512");

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha256SaslServer.MECHANISM_NAME), result, "A stored mechanism that this implementation cannot service must not be reported.");
    }

    /**
     * Verifies that a user for whom nothing is stored falls back to SCRAM-SHA-1. This is also the answer for a user
     * that does not exist, which is what keeps the result from revealing whether the claimed user exists.
     */
    @Test
    void getScramMechanisms_fallsBackWhenNothingIsStored() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = mechanismResultSet();

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user for which no mechanisms are stored (which includes a user that does not exist) must fall back to SCRAM-SHA-1.");
    }

    /**
     * Verifies that all implemented mechanisms are assumed usable by any user when a password can be retrieved.
     */
    @Test
    void getFallbackScramMechanisms_returnsAllMechanismsWhenPasswordIsRetrievable()
    {
        // Setup test fixture.
        // (see helper: password retrieval is enabled)

        // Execute system under test.
        final Set<String> result = runGetFallbackScramMechanisms(false);

        // Verify result.
        assertEquals(allImplementedMechanisms(), result, "When a password can be retrieved, credentials for every implemented mechanism can be derived for any user.");
    }

    /**
     * Verifies that only SCRAM-SHA-1 is assumed usable by any user when no password can be retrieved. It was the sole
     * mechanism when SCRAM support was first added, so it is the only one that every user can be assumed to hold.
     */
    @Test
    void getFallbackScramMechanisms_returnsLowestCommonDenominator()
    {
        // Setup test fixture.
        // (see helper: password retrieval is disabled)

        // Execute system under test.
        final Set<String> result = runGetFallbackScramMechanisms(true);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "Without password retrieval, only the mechanism that every user can be assumed to hold may be reported.");
    }

    /**
     * Invokes {@link DefaultAuthProvider#getScramMechanisms(String)} with the {@link DbConnectionManager} and
     * {@link JiveGlobals} statics mocked, using a connection that yields the provided result set.
     *
     * @param username the username to look up.
     * @param scramOnly the value of the 'user.scramHashedPasswordOnly' property, which governs password retrieval.
     * @param resultSet the result set that the mechanism query is to yield.
     * @return the reported mechanism names.
     */
    @SuppressWarnings("SqlSourceToSinkFlow")
    private static Set<String> runGetScramMechanisms(final String username, final boolean scramOnly, final ResultSet resultSet) throws Exception
    {
        final PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        when(stmt.executeQuery()).thenReturn(resultSet);

        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);

        return runGetScramMechanisms(username, scramOnly, connection);
    }

    /**
     * Invokes {@link DefaultAuthProvider#getScramMechanisms(String)} with the {@link DbConnectionManager} and
     * {@link JiveGlobals} statics mocked, using the provided connection.
     *
     * @param username the username to look up.
     * @param scramOnly the value of the 'user.scramHashedPasswordOnly' property, which governs password retrieval.
     * @param connection the connection that the provider is to obtain.
     * @return the reported mechanism names.
     */
    private static Set<String> runGetScramMechanisms(final String username, final boolean scramOnly, final Connection connection)
    {
        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class);
             final MockedStatic<JiveGlobals> globals = Mockito.mockStatic(JiveGlobals.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            globals.when(() -> JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly")).thenReturn(scramOnly);
            return new DefaultAuthProvider().getScramMechanisms(username);
        }
    }

    /**
     * Invokes {@link DefaultAuthProvider#getFallbackScramMechanisms()} with the {@link JiveGlobals} static mocked.
     *
     * @param scramOnly the value of the 'user.scramHashedPasswordOnly' property, which governs password retrieval.
     * @return the reported mechanism names.
     */
    private static Set<String> runGetFallbackScramMechanisms(final boolean scramOnly)
    {
        try (final MockedStatic<JiveGlobals> globals = Mockito.mockStatic(JiveGlobals.class)) {
            globals.when(() -> JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly")).thenReturn(scramOnly);
            return new DefaultAuthProvider().getFallbackScramMechanisms();
        }
    }

    /**
     * Returns a result set that yields one row per provided mechanism name, as the mechanism query does.
     *
     * @param mechanismNames the mechanism names to yield, in order.
     * @return a result set.
     */
    private static ResultSet mechanismResultSet(final String... mechanismNames) throws SQLException
    {
        final ResultSet rs = Mockito.mock(ResultSet.class);

        OngoingStubbing<Boolean> hasNext = when(rs.next());
        for (final String ignored : mechanismNames) {
            hasNext = hasNext.thenReturn(true);
        }
        hasNext.thenReturn(false);

        if (mechanismNames.length > 0) {
            OngoingStubbing<String> value = when(rs.getString(1));
            for (final String mechanismName : mechanismNames) {
                value = value.thenReturn(mechanismName);
            }
        }

        return rs;
    }

    /**
     * Returns the names of every SCRAM mechanism that {@link DefaultAuthProvider} implements.
     *
     * @return mechanism names.
     */
    private static Set<String> allImplementedMechanisms()
    {
        return Set.of(
            ScramSha1SaslServer.MECHANISM_NAME,
            ScramSha256SaslServer.MECHANISM_NAME,
            ScramSha512SaslServer.MECHANISM_NAME);
    }
}
