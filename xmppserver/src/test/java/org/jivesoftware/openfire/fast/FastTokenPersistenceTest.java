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
package org.jivesoftware.openfire.fast;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the database-facing behaviour of {@link FastTokenManager}: token issuance, proof
 * validation, the current/new token slot protocol, rotation, and invalidation.
 *
 * The two-slot protocol is the invariant most of these tests defend. A newly issued token is
 * written to the 'N' slot and only promoted to 'C' once the client proves possession of it, so
 * that a lost {@code <success/>} does not leave the client without a usable credential.
 *
 * JDBC is mocked, so these tests pin the statements issued and the values bound to them, not the
 * behaviour of a real database. Concurrency, isolation and the correctness of the SQL against the
 * ten shipped dialects are out of scope here and want an integration test against HSQLDB.
 */
class FastTokenPersistenceTest
{
    private static final String USER = "user";
    private static final String CLIENT = "client-a";

    // -------------------------------------------------------------------------
    // Issuance
    // -------------------------------------------------------------------------

    /** Issuance must replace only the unacknowledged 'N' token, never the current one. */
    @Test
    void issuanceReplacesOnlyTheNewSlotForThisClient() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).startsWith("DELETE") ? delete : insert);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            FastTokenManager.issueToken(USER, CLIENT, FastTokenManager.HT2_SHA_256_NONE);
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(false)));
        }

        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("tokenSlot='N'"),
            "Issuance deleted a token outside the new slot; the current token must survive until acknowledged.");
        verify(delete).setString(3, CLIENT);
        verify(insert).setString(3, CLIENT);
    }

    /** The value written to the database must be the encrypted envelope, never the token itself. */
    @Test
    void issuancePersistsAnEncryptedEnvelopeRatherThanThePlaintextToken() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement statement = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        final FastToken issued;
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            issued = FastTokenManager.issueToken(USER, CLIENT, FastTokenManager.HT2_SHA_256_NONE);
        }

        final ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(4), stored.capture());
        assertTrue(stored.getValue().startsWith("v1:"),
            "Stored token is not in the versioned encrypted envelope format.");
        assertFalse(stored.getValue().contains(issued.getTokenString()),
            "The plaintext token leaked into the stored value; a database dump would yield live credentials.");
    }

    /** A failed insert must abort the transaction rather than leave a half-written token pair. */
    @Test
    void issuanceAbortsTheTransactionWhenPersistenceFails() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).startsWith("DELETE") ? delete : insert);
        when(insert.executeUpdate()).thenThrow(new SQLException("disk full"));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertThrows(IllegalStateException.class,
                () -> FastTokenManager.issueToken(USER, CLIENT, FastTokenManager.HT2_SHA_256_NONE),
                "A token that could not be persisted was returned to the caller as if it were usable.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(true)));
        }
    }

    /** An unknown mechanism must be rejected before any database work is done. */
    @Test
    void issuanceRejectsAnUnsupportedMechanism()
    {
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            assertThrows(IllegalArgumentException.class,
                () -> FastTokenManager.issueToken(USER, CLIENT, "HT-NOT-A-MECHANISM"),
                "An unsupported mechanism was accepted for token issuance.");
            db.verify(DbConnectionManager::getTransactionConnection, never());
        }
    }

    // -------------------------------------------------------------------------
    // Validation: proof handling
    // -------------------------------------------------------------------------

    /** A valid proof must yield the responder HMAC that gives the client mutual authentication. */
    @Test
    void validationReturnsTheResponderProofForAValidInitiatorProof() throws Exception
    {
        final String token = "current-token";
        final Connection connection = singleRow(token, "C", inDays(2));

        final FastTokenManager.Ht2ValidationResult result;
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            result = FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                proof(token, "Initiator"), new byte[0], "", "");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(false)));
        }

        assertNotNull(result, "A correct initiator proof was rejected.");
        assertArrayEquals(proof(token, "Responder"), result.getResponderHashedToken(),
            "Responder proof does not match HMAC(token, \"Responder\"); the client cannot authenticate the server.");
        assertEquals(CLIENT, result.getClientId(), "Result is attributed to the wrong client.");
    }

    /** A proof computed from a different token must not authenticate. */
    @Test
    void validationRejectsAProofThatDoesNotMatchTheStoredToken() throws Exception
    {
        final Connection connection = singleRow("stored-token", "C", inDays(2));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                    proof("some-other-token", "Initiator"), new byte[0], "", ""),
                "A proof derived from the wrong token was accepted.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(true)));
        }
    }

    /** Channel-binding data must be covered by the proof, or the binding provides no protection. */
    @Test
    void validationRejectsAProofComputedOverDifferentChannelBindingData() throws Exception
    {
        final String token = "bound-token";
        final byte[] clientBinding = "tls-channel-A".getBytes(StandardCharsets.UTF_8);
        final byte[] serverBinding = "tls-channel-B".getBytes(StandardCharsets.UTF_8);
        final Connection connection = singleRow(token, "C", inDays(2));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_UNIQ,
                    hmac(token, "Initiator", clientBinding, ""), serverBinding, "", ""),
                "A proof bound to a different TLS channel was accepted; channel binding is not enforced.");
        }
    }

    /** HT2 extra-initiator-values must be covered by the proof, or they are not authenticated. */
    @Test
    void validationRejectsAProofComputedOverDifferentExtraInitiatorValues() throws Exception
    {
        final String token = "ht2-token";
        final Connection connection = singleRow(token, "C", inDays(2));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT2_SHA_256_NONE,
                    hmac(token, "Initiator", new byte[0], "counter=1"), new byte[0], "counter=2", ""),
                "Extra initiator values were not covered by the verified proof; they can be altered in transit.");
        }
    }

    /** A matching but expired token must be reported as expired, not as an unknown credential. */
    @Test
    void validationReportsAMatchingButExpiredTokenAsExpired() throws Exception
    {
        final String token = "stale-token";
        final Connection connection = singleRow(token, "C", inDays(-1));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                USER, CLIENT, FastTokenManager.HT_SHA_256_NONE, proof(token, "Initiator"), new byte[0], "", "");

            assertNotNull(result, "An expired token was reported as unknown, so the client cannot tell it must re-authenticate.");
            assertTrue(result.isExpired(), "Expired token was not flagged as expired.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(true)));
        }
    }

    /** Lookup is scoped to the presented client id, so one client's token cannot be used by another. */
    @Test
    void validationScopesLookupToThePresentedClientId() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(false);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, "client-b", FastTokenManager.HT2_SHA_256_NONE,
                proof("token-for-a", "Initiator"), new byte[0], "", ""), "A token belonging to another client was accepted.");
        }
        verify(select).setString(3, "client-b");
    }

    /** A row with an unparseable expiry must be skipped, not treated as valid or fatal. */
    @Test
    void validationSkipsRowsWithAMalformedExpiry() throws Exception
    {
        final String token = "good-token";
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getString("expiry")).thenReturn("not-a-timestamp", inDays(2));
        when(rows.getString("tokenHash")).thenReturn(storedToken(token));
        when(rows.getString("tokenSlot")).thenReturn("C");

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNotNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                    proof(token, "Initiator"), new byte[0], "", ""),
                "One corrupt row prevented a valid token in a later row from authenticating.");
        }
    }

    /** A row that cannot be decrypted must be skipped rather than aborting the whole lookup. */
    @Test
    void validationSkipsRowsWhoseStoredTokenCannotBeRead() throws Exception
    {
        final String token = "good-token";
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getString("expiry")).thenReturn(inDays(2));
        when(rows.getString("tokenHash")).thenReturn("not-an-envelope", storedToken(token));
        when(rows.getString("tokenSlot")).thenReturn("C");

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNotNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                    proof(token, "Initiator"), new byte[0], "", ""),
                "An unreadable row prevented a valid token in a later row from authenticating.");
        }
    }

    // -------------------------------------------------------------------------
    // Validation: the current/new slot protocol
    // -------------------------------------------------------------------------

    /** The current token stays usable while an issued-but-unacknowledged token sits in the new slot. */
    @Test
    void currentTokenRemainsValidWhileANewTokenAwaitsAcknowledgement() throws Exception
    {
        final String current = "current-token";
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getString("expiry")).thenReturn(inDays(2));
        when(rows.getString("tokenHash")).thenReturn(storedToken(current), storedToken("unacknowledged-new-token"));
        when(rows.getString("tokenSlot")).thenReturn("C");

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNotNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                    proof(current, "Initiator"), new byte[0], "", ""),
                "The current token stopped working as soon as a replacement was issued but not yet used.");
        }
        verify(connection, never()).prepareStatement(contains("DELETE"));
        verify(connection, never()).prepareStatement(startsWith("UPDATE ofFastToken SET tokenSlot"));
    }

    /** Using the new token deletes the current one and promotes the new one, in that order. */
    @Test
    void successfulUseOfTheNewTokenDeletesCurrentThenPromotesNew() throws Exception
    {
        final String token = "new-token";
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final PreparedStatement promote = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation -> {
            final String sql = invocation.getArgument(0);
            if (sql.startsWith("SELECT")) return select;
            if (sql.startsWith("DELETE")) return delete;
            return promote;
        });
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("expiry")).thenReturn(inDays(2));
        when(rows.getString("tokenHash")).thenReturn(storedToken(token));
        when(rows.getString("tokenSlot")).thenReturn("N");

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                USER, CLIENT, FastTokenManager.HT_SHA_256_NONE, proof(token, "Initiator"), new byte[0], "", "");

            assertNotNull(result, "The newly issued token was rejected on first use.");
            assertNull(result.getRotatedToken(), "A token was rotated during promotion; only current tokens rotate.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(false)));
        }

        final org.mockito.InOrder order = inOrder(delete, promote);
        order.verify(delete).executeUpdate();
        order.verify(promote).executeUpdate();
        verify(delete).setString(3, CLIENT);
        verify(promote).setString(3, CLIENT);
    }

    // -------------------------------------------------------------------------
    // Validation: rotation
    // -------------------------------------------------------------------------

    /** A current token close to expiry is replaced, and the replacement is handed back to the caller. */
    @Test
    void currentTokenNearingExpiryIsRotated() throws Exception
    {
        final String token = "expiring-token";
        final Connection connection = singleRow(token, "C", inSeconds(600));
        when(connection.prepareStatement(startsWith("INSERT"))).thenReturn(mock(PreparedStatement.class));
        when(connection.prepareStatement(startsWith("DELETE"))).thenReturn(mock(PreparedStatement.class));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                USER, CLIENT, FastTokenManager.HT_SHA_256_NONE, proof(token, "Initiator"), new byte[0], "", "");

            assertNotNull(result, "A valid token near expiry was rejected.");
            assertNotNull(result.getRotatedToken(),
                "No replacement was issued for a token inside the rotation threshold; the client will be locked out at expiry.");
        }
    }

    /** A current token far from expiry must not be rotated on every authentication. */
    @Test
    void currentTokenFarFromExpiryIsNotRotated() throws Exception
    {
        final String token = "fresh-token";
        final Connection connection = singleRow(token, "C", inDays(5));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                USER, CLIENT, FastTokenManager.HT_SHA_256_NONE, proof(token, "Initiator"), new byte[0], "", "");

            assertNull(result.getRotatedToken(),
                "A token far from expiry was rotated, needlessly emitting a fresh credential.");
        }
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
    }

    /** A failure to issue a replacement must not fail the authentication that has already succeeded. */
    @Test
    void failureToRotateStillAuthenticatesTheClient() throws Exception
    {
        final String token = "expiring-token";
        final Connection connection = singleRow(token, "C", inSeconds(600));
        final PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(startsWith("INSERT"))).thenReturn(insert);
        when(connection.prepareStatement(startsWith("DELETE"))).thenReturn(mock(PreparedStatement.class));
        when(insert.executeUpdate()).thenThrow(new SQLException("disk full"));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                USER, CLIENT, FastTokenManager.HT_SHA_256_NONE, proof(token, "Initiator"), new byte[0], "", "");

            assertNotNull(result, "A failed rotation rejected an otherwise valid authentication.");
            assertNull(result.getRotatedToken(), "A rotated token was reported despite the rotation failing.");
        }
    }

    // -------------------------------------------------------------------------
    // Replay counter
    //
    // Delete this section along with the replayCounter column if the counter is dropped. As
    // written the counter is not covered by the verified proof, so it constrains only a client
    // that chooses to send an honest value.
    // -------------------------------------------------------------------------

    /** A counter greater than the stored value is accepted and written in the same transaction. */
    @Test
    void aHigherReplayCounterIsAcceptedAndStored() throws Exception
    {
        final String token = "counted-token";
        final Connection connection = singleRow(token, "C", inDays(2));
        final PreparedStatement counter = mock(PreparedStatement.class);
        when(connection.prepareStatement(startsWith("UPDATE ofFastToken SET replayCounter"))).thenReturn(counter);
        when(counter.executeUpdate()).thenReturn(1);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNotNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                proof(token, "Initiator"), new byte[0], "", "", 9L), "A counter above the stored value was rejected.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(false)));
        }
        verify(counter).setLong(1, 9L);
        verify(counter).setLong(5, 9L);
    }

    /** A counter at or below the stored value is a replay and must be rejected. */
    @Test
    void aReplayedCounterIsRejected() throws Exception
    {
        final String token = "counted-token";
        final Connection connection = singleRow(token, "C", inDays(2));
        when(resultSetOf(connection).getLong("replayCounter")).thenReturn(9L);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                    proof(token, "Initiator"), new byte[0], "", "", 9L),
                "A replayed counter value was accepted; a captured exchange can be reused.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(true)));
        }
    }

    /** If a concurrent authentication won the counter update, this one must not authenticate. */
    @Test
    void losingTheRaceOnTheCounterUpdateRejectsTheAuthentication() throws Exception
    {
        final String token = "counted-token";
        final Connection connection = singleRow(token, "C", inDays(2));
        final PreparedStatement counter = mock(PreparedStatement.class);
        when(connection.prepareStatement(startsWith("UPDATE ofFastToken SET replayCounter"))).thenReturn(counter);
        when(counter.executeUpdate()).thenReturn(0);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                    proof(token, "Initiator"), new byte[0], "", "", 5L),
                "Authentication succeeded even though the counter update affected no rows.");
            db.verify(() -> DbConnectionManager.closeTransactionConnection(any(), eq(connection), eq(true)));
        }
    }

    /** A non-positive counter is never valid. */
    @Test
    void aNonPositiveReplayCounterIsRejected() throws Exception
    {
        final String token = "counted-token";
        final Connection connection = singleRow(token, "C", inDays(2));

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getTransactionConnection).thenReturn(connection);
            assertNull(FastTokenManager.validateTokenHt2(USER, CLIENT, FastTokenManager.HT_SHA_256_NONE,
                proof(token, "Initiator"), new byte[0], "", "", 0L), "A counter of zero was accepted.");
        }
    }

    // -------------------------------------------------------------------------
    // Invalidation and purging
    // -------------------------------------------------------------------------

    /** Invalidating one credential must not touch the user's other clients or mechanisms. */
    @Test
    void invalidationIsScopedToOneMechanismAndClient() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(delete);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            FastTokenManager.invalidateToken(USER, FastTokenManager.HT2_SHA_512_EXPR, CLIENT);
        }

        verify(delete).setString(1, USER);
        verify(delete).setString(2, FastTokenManager.HT2_SHA_512_EXPR);
        verify(delete).setString(3, CLIENT);
        verify(delete).executeUpdate();
    }

    /** Account-level invalidation must remove every token the user holds, on every client. */
    @Test
    void invalidatingAUserRemovesEveryTokenTheyHold() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        when(connection.prepareStatement(anyString())).thenReturn(delete);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            FastTokenManager.invalidateTokens(USER);
        }

        verify(connection).prepareStatement(sql.capture());
        assertFalse(sql.getValue().contains("clientID"),
            "Account-level invalidation was scoped to a client, leaving other clients' tokens alive.");
        verify(delete).setString(1, USER);
    }

    /** Purging must delete by expiry using the same encoding the expiry column is written in. */
    @Test
    void purgingDeletesTokensByExpiry() throws Exception
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final ArgumentCaptor<String> cutoff = ArgumentCaptor.forClass(String.class);
        when(connection.prepareStatement(anyString())).thenReturn(delete);

        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            FastTokenManager.purgeExpiredTokens();
        }

        verify(delete).setString(eq(1), cutoff.capture());
        assertDoesNotThrow(() -> new XMPPDateTimeFormat().parseString(cutoff.getValue()),
            "The purge cutoff is not in the format the expiry column is written in, so the comparison is meaningless.");
        verify(delete).executeUpdate();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A connection whose SELECT returns exactly one token row in the given slot. */
    private static Connection singleRow(final String token, final String slot, final String expiry) throws SQLException
    {
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(startsWith("SELECT"))).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, false);
        when(rows.getString("tokenHash")).thenReturn(storedToken(token));
        when(rows.getString("tokenSlot")).thenReturn(slot);
        when(rows.getString("expiry")).thenReturn(expiry);
        return connection;
    }

    /** Reaches the {@link ResultSet} behind a connection built by {@link #singleRow}. */
    private static ResultSet resultSetOf(final Connection connection) throws SQLException
    {
        return connection.prepareStatement("SELECT").executeQuery();
    }

    private static byte[] proof(final String token, final String role)
    {
        return hmac(token, role, new byte[0], "");
    }

    private static byte[] hmac(final String token, final String role, final byte[] binding, final String extras)
    {
        final byte[] prefix = role.getBytes(StandardCharsets.UTF_8);
        final byte[] extra = extras.getBytes(StandardCharsets.UTF_8);
        final byte[] message = new byte[prefix.length + binding.length + extra.length];
        System.arraycopy(prefix, 0, message, 0, prefix.length);
        System.arraycopy(binding, 0, message, prefix.length, binding.length);
        System.arraycopy(extra, 0, message, prefix.length + binding.length, extra.length);
        return FastTokenManager.hmac(token.getBytes(StandardCharsets.UTF_8), message, "HmacSHA256");
    }

    private static String storedToken(final String token)
    {
        return FastTokenManager.protectToken(token, JiveGlobals.getPropertyEncryptor(), new byte[16]);
    }

    private static String inDays(final long days)
    {
        return inSeconds(days * 86400);
    }

    private static String inSeconds(final long seconds)
    {
        return XMPPDateTimeFormat.format(Date.from(Instant.now().plusSeconds(seconds)));
    }
}
