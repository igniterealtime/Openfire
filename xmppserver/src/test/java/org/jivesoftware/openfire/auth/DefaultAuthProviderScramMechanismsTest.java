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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies which SCRAM mechanism names {@link DefaultAuthProvider} reports as usable, both for an identified user
 * ({@link DefaultAuthProvider#getScramMechanisms(String)}) and for an unidentified one
 * ({@link DefaultAuthProvider#getFallbackScramMechanisms()}).
 *
 * Four properties matter beyond the plain lookup:
 *
 * <ul>
 *     <li>When a password can be recovered for the user and the deployment permits retrieving it, credentials for every mechanism can be derived on demand, so the stored set does not constrain what can be offered for that user. This does not extend to a user that cannot be identified: the deployment-wide setting says nothing about any particular user.</li>
 *     <li>A user for whom nothing usable can be determined falls back to SCRAM-SHA-1. That keeps the result from revealing whether the claimed user exists, and keeps a database failure from denying authentication outright.</li>
 *     <li>Mechanisms that are stored but that this implementation cannot service are removed <em>before</em> that fallback is considered, so that a user holding only such credentials is not left with nothing.</li>
 *     <li>For a user that cannot be identified, a mechanism beyond SCRAM-SHA-1 is reported only where a credential for it is stored for <em>every</em> user. Anything less than that unanimity, and any failure to establish it, leaves SCRAM-SHA-1 as the answer.</li>
 * </ul>
 */
public class DefaultAuthProviderScramMechanismsTest
{
    private static final String USERNAME = "juliet";

    private static final String PASSWORD = "pencil";

    private static final String ENCRYPTED_PASSWORD = "encrypted-value";

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

    @BeforeEach
    public void setUp()
    {
        // The determination of which mechanisms every user holds is cached statically, so it outlives an instance and
        // would otherwise carry over from one test to the next.
        DefaultAuthProvider.invalidateUniversalScramMechanisms();
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    /**
     * Verifies that all implemented mechanisms are reported when a plaintext password is stored for the user, as
     * credentials for each of them can then be derived on demand.
     */
    @Test
    void getScramMechanisms_returnsAllMechanismsWhenPasswordIsRetrievable() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = storedCredentialsResultSet(PASSWORD, null, ScramSha1SaslServer.MECHANISM_NAME);

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
        final ResultSet rs = storedCredentialsResultSet(null, null, ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME);

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
        final ResultSet rs = storedCredentialsResultSet(null, null, ScramSha256SaslServer.MECHANISM_NAME, "SCRAM-SHA3-512");

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
        final ResultSet rs = storedCredentialsResultSet(null, null);

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user for which no mechanisms are stored (which includes a user that does not exist) must fall back to SCRAM-SHA-1.");
    }

    /**
     * Verifies that a user for which only unrecognized mechanisms are stored falls back to SCRAM-SHA-1, rather than
     * being left with no mechanism at all. The removal of unrecognized mechanisms must therefore be applied before the
     * fallback is considered.
     */
    @Test
    void getScramMechanisms_fallsBackWhenOnlyUnrecognizedMechanismsAreStored() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = storedCredentialsResultSet(null, null, "SCRAM-SHA3-512");

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user for which only unrecognized mechanisms are stored must fall back to SCRAM-SHA-1, not be left without any mechanism.");
    }

    /**
     * Verifies that a database failure falls back to SCRAM-SHA-1 rather than reporting no mechanisms. Reporting none
     * would leave every authenticating client without a usable mechanism for the duration of the failure.
     */
    @Test
    void getScramMechanisms_fallsBackWhenLookupFails() throws Exception
    {
        // Setup test fixture.
        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Simulated database failure."));

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, connection);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A failed lookup must fall back to SCRAM-SHA-1, rather than deny every mechanism.");
    }

    /**
     * Verifies that a user that does not exist is answered the same way as one that has no credentials stored, so that
     * the response does not reveal which of the two applies.
     */
    @Test
    void getScramMechanisms_fallsBackForUnknownUser() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = noSuchUserResultSet();

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, true, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user that does not exist must be answered with the same mechanisms as one that has no credentials stored.");
    }

    /**
     * Verifies that a user without a stored password of its own is reported as holding only the mechanisms that are
     * stored for it, even where the deployment is configured to allow password retrieval. Such a user arises when
     * 'user.scramHashedPasswordOnly' was set at the time the user's password was last stored, and was disabled
     * afterwards: the deployment-wide setting then says nothing about what this user holds.
     */
    @Test
    void getScramMechanisms_ignoresPasswordRetrievalForUserWithoutStoredPassword() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = storedCredentialsResultSet(null, null, ScramSha1SaslServer.MECHANISM_NAME);

        // Execute system under test.
        final Set<String> result = runGetScramMechanisms(USERNAME, false, rs);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A user that has no stored password of its own must be reported as holding only its stored mechanisms, whatever the deployment-wide password retrieval setting says.");
    }

    /**
     * Verifies that an encrypted password that can be decrypted counts as a stored password, as it can be resolved to
     * a plaintext from which every mechanism's credentials can be derived.
     */
    @Test
    void getScramMechanisms_returnsAllMechanismsForUserWithDecryptableEncryptedPassword() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = storedCredentialsResultSet(null, ENCRYPTED_PASSWORD, ScramSha1SaslServer.MECHANISM_NAME);

        // Execute system under test.
        final Set<String> result = runGetScramMechanismsWithEncryptedPassword(USERNAME, rs, true);

        // Verify result.
        assertEquals(allImplementedMechanisms(), result, "An encrypted password that can be decrypted can be resolved to a plaintext, so every implemented mechanism's credentials can be derived from it.");
    }

    /**
     * Verifies that an encrypted password from which no plaintext can be recovered does not count as a stored
     * password. Decryption depends on a cipher that is not always available, and where the plaintext cannot be
     * recovered, missing credentials cannot be regenerated from it either.
     */
    @Test
    void getScramMechanisms_ignoresEncryptedPasswordThatCannotBeDecrypted() throws Exception
    {
        // Setup test fixture.
        final ResultSet rs = storedCredentialsResultSet(null, ENCRYPTED_PASSWORD, ScramSha1SaslServer.MECHANISM_NAME);

        // Execute system under test.
        final Set<String> result = runGetScramMechanismsWithEncryptedPassword(USERNAME, rs, false);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "An encrypted password that cannot be decrypted must not be treated as a password from which credentials can be derived.");
    }

    /**
     * Verifies that every implemented mechanism is assumed usable by any user where a credential for each of them is
     * stored for every user. A client that does not identify itself can then safely be offered the stronger
     * mechanisms.
     */
    @Test
    void getFallbackScramMechanisms_returnsAllMechanismsHeldByEveryUser() throws Exception
    {
        // Setup test fixture.
        final Connection connection = connectionYieldingCounts(10, usersPerMechanism(10, 10, 10));

        // Execute system under test.
        final Set<String> result = runGetFallbackScramMechanisms(true, connection);

        // Verify result.
        assertEquals(allImplementedMechanisms(), result, "A mechanism for which every user holds a credential can be assumed usable by a user that cannot be identified.");
    }

    /**
     * Verifies that a mechanism that not every user holds a credential for is not assumed usable. A single user
     * lacking it is enough: a client that does not identify itself could be that user.
     */
    @Test
    void getFallbackScramMechanisms_omitsMechanismNotHeldByEveryUser() throws Exception
    {
        // Setup test fixture.
        final Connection connection = connectionYieldingCounts(10, usersPerMechanism(10, 10, 9));

        // Execute system under test.
        final Set<String> result = runGetFallbackScramMechanisms(true, connection);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), result, "A mechanism that even one user lacks a credential for must not be assumed usable by a user that cannot be identified.");
    }

    /**
     * Verifies that no mechanism beyond SCRAM-SHA-1 is assumed usable where there are no users at all, as nothing can
     * be established from an empty user table.
     */
    @Test
    void getFallbackScramMechanisms_returnsLowestCommonDenominatorWithoutUsers() throws Exception
    {
        // Setup test fixture.
        final Connection connection = connectionYieldingCounts(0, usersPerMechanism(0, 0, 0));

        // Execute system under test.
        final Set<String> result = runGetFallbackScramMechanisms(true, connection);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "Without any users, no mechanism can be established as being held by every user.");
    }

    /**
     * Verifies that a database failure leaves SCRAM-SHA-1 as the answer, rather than reporting nothing. A mechanism is
     * only ever added on positive evidence, so a failure to gather it degrades to the inherited answer.
     */
    @Test
    void getFallbackScramMechanisms_returnsLowestCommonDenominatorWhenLookupFails() throws Exception
    {
        // Setup test fixture.
        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenThrow(new SQLException("Simulated database failure."));

        // Execute system under test.
        final Set<String> result = runGetFallbackScramMechanisms(true, connection);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME), result, "A failed determination must leave the inherited answer in place, rather than report no mechanism at all.");
    }

    /**
     * Verifies that the mechanisms assumed usable by any user do not depend on the deployment-wide password retrieval
     * setting. That setting says nothing about a particular user: one whose password was last stored while
     * 'user.scramHashedPasswordOnly' was set retains no password when it is later disabled, so a mechanism can only be
     * assumed usable if it holds for such a user too.
     */
    @Test
    void getFallbackScramMechanisms_ignoresPasswordRetrievalSetting() throws Exception
    {
        // Setup test fixture. A separate connection per invocation: the result sets are stubbed with single-use
        // sequences, so one cannot answer two determinations.
        final Connection withRetrievalConnection = connectionYieldingCounts(10, usersPerMechanism(10, 10, 9));
        final Connection withoutRetrievalConnection = connectionYieldingCounts(10, usersPerMechanism(10, 10, 9));

        // Execute system under test.
        final Set<String> withRetrieval = runGetFallbackScramMechanisms(false, withRetrievalConnection);
        DefaultAuthProvider.invalidateUniversalScramMechanisms();
        final Set<String> withoutRetrieval = runGetFallbackScramMechanisms(true, withoutRetrievalConnection);

        // Verify result.
        assertEquals(Set.of(ScramSha1SaslServer.MECHANISM_NAME, ScramSha256SaslServer.MECHANISM_NAME), withRetrieval, "Only the mechanisms that every user holds may be reported, whatever the deployment-wide password retrieval setting says.");
        assertEquals(withRetrieval, withoutRetrieval, "The deployment-wide password retrieval setting must not affect which mechanisms are assumed usable by any user.");
    }

    /**
     * Verifies that the determination is not repeated on every invocation. It scans the user table, which is not
     * something to do for each session that is being negotiated.
     */
    @Test
    void getFallbackScramMechanisms_cachesDetermination() throws Exception
    {
        // Setup test fixture.
        final Connection connection = connectionYieldingCounts(10, usersPerMechanism(10, 10, 10));
        final Set<String> first = runGetFallbackScramMechanisms(true, connection);

        // Execute system under test.
        final Set<String> second = runGetFallbackScramMechanisms(true, connection);

        // Verify result.
        assertEquals(first, second, "A repeated invocation must yield the same mechanisms.");
        verify(connection, times(2)).prepareStatement(anyString()); // Two statements are used per determination.
    }

    /**
     * Invokes {@link DefaultAuthProvider#getScramMechanisms(String)} with the {@link DbConnectionManager} and
     * {@link JiveGlobals} statics mocked, using a connection that yields the provided result set.
     *
     * @param username the username to look up.
     * @param scramOnly the value of the 'user.scramHashedPasswordOnly' property, which governs password retrieval.
     * @param resultSet the result set that the query is to yield.
     * @return the reported mechanism names.
     */
    private static Set<String> runGetScramMechanisms(final String username, final boolean scramOnly, final ResultSet resultSet) throws Exception
    {
        return runGetScramMechanisms(username, scramOnly, connectionYielding(resultSet));
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
     * Invokes {@link DefaultAuthProvider#getScramMechanisms(String)} as
     * {@link #runGetScramMechanisms(String, boolean, ResultSet)} does, with {@link AuthFactory} additionally mocked to
     * report whether a stored encrypted password can be resolved to a plaintext.
     *
     * Password retrieval is enabled for these invocations, as an encrypted password is only consulted when it is.
     *
     * @param username the username to look up.
     * @param resultSet the result set that the query is to yield.
     * @param canDecrypt whether an encrypted password can be resolved to a plaintext.
     * @return the reported mechanism names.
     */
    private static Set<String> runGetScramMechanismsWithEncryptedPassword(final String username, final ResultSet resultSet, final boolean canDecrypt) throws Exception
    {
        final Connection connection = connectionYielding(resultSet);

        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class);
             final MockedStatic<JiveGlobals> globals = Mockito.mockStatic(JiveGlobals.class);
             final MockedStatic<AuthFactory> authFactory = Mockito.mockStatic(AuthFactory.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            globals.when(() -> JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly")).thenReturn(false);
            authFactory.when(() -> AuthFactory.canDecryptPassword(anyString())).thenReturn(canDecrypt);
            return new DefaultAuthProvider().getScramMechanisms(username);
        }
    }

    /**
     * Invokes {@link DefaultAuthProvider#getFallbackScramMechanisms()} with the {@link DbConnectionManager} and
     * {@link JiveGlobals} statics mocked, using the provided connection.
     *
     * @param scramOnly the value of the 'user.scramHashedPasswordOnly' property, which governs password retrieval.
     * @param connection the connection that the provider is to obtain.
     * @return the reported mechanism names.
     */
    private static Set<String> runGetFallbackScramMechanisms(final boolean scramOnly, final Connection connection)
    {
        try (final MockedStatic<DbConnectionManager> db = Mockito.mockStatic(DbConnectionManager.class);
             final MockedStatic<JiveGlobals> globals = Mockito.mockStatic(JiveGlobals.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            globals.when(() -> JiveGlobals.getBooleanProperty("user.scramHashedPasswordOnly")).thenReturn(scramOnly);
            return new DefaultAuthProvider().getFallbackScramMechanisms();
        }
    }

    /**
     * Returns a connection whose statements yield the provided result set.
     *
     * @param resultSet the result set to yield.
     * @return a connection.
     */
    @SuppressWarnings("SqlSourceToSinkFlow")
    private static Connection connectionYielding(final ResultSet resultSet) throws SQLException
    {
        final PreparedStatement stmt = Mockito.mock(PreparedStatement.class);
        when(stmt.executeQuery()).thenReturn(resultSet);

        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenReturn(stmt);

        return connection;
    }

    /**
     * Returns a connection that answers the two queries used to determine which mechanisms every user holds: one that
     * counts the users holding a credential for each mechanism, and one that counts the users.
     *
     * @param userCount the number of users to report.
     * @param usersPerMechanism the number of users holding a credential, per mechanism name.
     * @return a connection.
     */
    @SuppressWarnings("SqlSourceToSinkFlow")
    private static Connection connectionYieldingCounts(final long userCount, final Map<String, Long> usersPerMechanism) throws SQLException
    {
        final ResultSet mechanismRs = Mockito.mock(ResultSet.class);
        OngoingStubbing<Boolean> hasNext = when(mechanismRs.next());
        for (int i = 0; i < usersPerMechanism.size(); i++) {
            hasNext = hasNext.thenReturn(true);
        }
        hasNext.thenReturn(false);

        if (!usersPerMechanism.isEmpty()) {
            // Each stubbing must be completed before the next is started: invoking the mock again while a stubbing is
            // still open is what Mockito reports as unfinished stubbing. The map preserves insertion order, so the
            // names and the counts stay aligned across the two loops.
            OngoingStubbing<String> names = when(mechanismRs.getString(1));
            for (final String mechanismName : usersPerMechanism.keySet()) {
                names = names.thenReturn(mechanismName);
            }

            OngoingStubbing<Long> counts = when(mechanismRs.getLong(2));
            for (final Long count : usersPerMechanism.values()) {
                counts = counts.thenReturn(count);
            }
        }

        final PreparedStatement mechanismStmt = Mockito.mock(PreparedStatement.class);
        when(mechanismStmt.executeQuery()).thenReturn(mechanismRs);

        final ResultSet userCountRs = Mockito.mock(ResultSet.class);
        when(userCountRs.next()).thenReturn(true, false);
        when(userCountRs.getLong(1)).thenReturn(userCount);

        final PreparedStatement userCountStmt = Mockito.mock(PreparedStatement.class);
        when(userCountStmt.executeQuery()).thenReturn(userCountRs);

        // Routing is done in a single answer rather than by two competing argument matchers: registering a second
        // matcher-based stubbing invokes the mock again, and Mockito then offers that invocation's (null) argument to
        // the matcher registered before it.
        final Connection connection = Mockito.mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT).contains("group by") ? mechanismStmt : userCountStmt);

        return connection;
    }

    /**
     * Returns the number of users holding a credential for each implemented mechanism, in the order in which the
     * mechanisms were introduced.
     *
     * @param sha1 the number of users holding a SCRAM-SHA-1 credential.
     * @param sha256 the number of users holding a SCRAM-SHA-256 credential.
     * @param sha512 the number of users holding a SCRAM-SHA-512 credential.
     * @return a mapping of mechanism name to user count, in a map that preserves insertion order.
     */
    private static Map<String, Long> usersPerMechanism(final long sha1, final long sha256, final long sha512)
    {
        final Map<String, Long> result = new LinkedHashMap<>();
        result.put(ScramSha1SaslServer.MECHANISM_NAME, sha1);
        result.put(ScramSha256SaslServer.MECHANISM_NAME, sha256);
        result.put(ScramSha512SaslServer.MECHANISM_NAME, sha512);
        return result;
    }

    /**
     * Returns a result set for a user that exists, as the combined password-and-mechanisms query yields it.
     *
     * A user that has no SCRAM credential still yields one row, in which the mechanism column is null: that is what the
     * LEFT JOIN produces when there is no matching row in ofUserScram.
     *
     * @param plainPassword the stored plaintext password, or null when none is stored.
     * @param encryptedPassword the stored encrypted password, or null when none is stored.
     * @param mechanismNames the mechanism names for which a credential is stored, in order.
     * @return a result set.
     */
    private static ResultSet storedCredentialsResultSet(final String plainPassword, final String encryptedPassword, final String... mechanismNames) throws SQLException
    {
        final ResultSet rs = Mockito.mock(ResultSet.class);

        final int rowCount = Math.max(1, mechanismNames.length);
        OngoingStubbing<Boolean> hasNext = when(rs.next());
        for (int i = 0; i < rowCount; i++) {
            hasNext = hasNext.thenReturn(true);
        }
        hasNext.thenReturn(false);

        // The password columns repeat on every row of the join.
        when(rs.getString(1)).thenReturn(plainPassword);
        when(rs.getString(2)).thenReturn(encryptedPassword);

        if (mechanismNames.length == 0) {
            when(rs.getString(3)).thenReturn(null);
        } else {
            OngoingStubbing<String> value = when(rs.getString(3));
            for (final String mechanismName : mechanismNames) {
                value = value.thenReturn(mechanismName);
            }
        }

        return rs;
    }

    /**
     * Returns a result set for a user that does not exist, which yields no rows at all.
     *
     * @return a result set.
     */
    private static ResultSet noSuchUserResultSet() throws SQLException
    {
        final ResultSet rs = Mockito.mock(ResultSet.class);
        when(rs.next()).thenReturn(false);
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
