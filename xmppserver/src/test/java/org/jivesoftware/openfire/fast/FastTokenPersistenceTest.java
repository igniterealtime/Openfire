/* Copyright (C) 2026 Ignite Realtime Foundation. Licensed under the Apache License, Version 2.0. */
package org.jivesoftware.openfire.fast;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.XMPPDateTimeFormat;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FastTokenPersistenceTest {
    @Test
    void replayCounterAdvancesOnlyWhenDatabaseAcceptsGreaterValue() throws Exception {
        final Connection connection = mock(Connection.class);
        final PreparedStatement update = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(update);
        when(update.executeUpdate()).thenReturn(1, 0);
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            assertTrue(FastTokenManager.advanceReplayCounter("user", FastTokenManager.HT_SHA_256_NONE, "client", 7));
            assertFalse(FastTokenManager.advanceReplayCounter("user", FastTokenManager.HT_SHA_256_NONE, "client", 7));
        }
        verify(update, times(2)).setLong(1, 7);
        verify(update, times(2)).setLong(5, 7);
    }

    @Test
    void issuanceIsAtomicAndScopedToClientNewSlot() throws Exception {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).startsWith("DELETE") ? delete : insert);
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            FastTokenManager.issueToken("user", "client-a", FastTokenManager.HT2_SHA_256_NONE);
        }
        verify(connection).setAutoCommit(false);
        verify(connection).commit();
        verify(delete).setString(3, "client-a");
        verify(insert).setString(3, "client-a");
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(connection, times(2)).prepareStatement(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("tokenSlot='N'"),
            "Issuance must replace only the unacknowledged new token, preserving the current token if success is lost.");
    }

    @Test
    void issuanceRollsBackAndDoesNotReturnAnUnusableToken() throws Exception {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        final PreparedStatement insert = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenAnswer(invocation ->
            invocation.getArgument(0, String.class).startsWith("DELETE") ? delete : insert);
        when(insert.executeUpdate()).thenThrow(new SQLException("disk full"));
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            assertThrows(IllegalStateException.class, () -> FastTokenManager.issueToken(
                "user", "client-a", FastTokenManager.HT2_SHA_256_NONE));
        }
        verify(connection).rollback();
        verify(connection, never()).commit();
    }

    @Test
    void validationFindsTheCorrectClientWithoutInvalidatingAnotherClient() throws Exception {
        final String[] tokens = {"token-for-a", "token-for-b"};
        final String[] clients = {"client-a", "client-b"};
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        final AtomicInteger row = new AtomicInteger(-1);
        when(rows.next()).thenAnswer(i -> row.incrementAndGet() < 2);
        when(rows.getString("tokenHash")).thenAnswer(i -> tokens[row.get()]);
        when(rows.getString("clientID")).thenAnswer(i -> clients[row.get()]);
        when(rows.getString("tokenSlot")).thenReturn("C");
        when(rows.getString("expiry")).thenReturn(XMPPDateTimeFormat.format(Date.from(Instant.now().plusSeconds(172800))));
        final byte[] proof = FastTokenManager.hmac(tokens[1].getBytes(StandardCharsets.UTF_8),
            "Initiator".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                "user", FastTokenManager.HT2_SHA_256_NONE, proof, new byte[0], "", "");
            assertNotNull(result);
            assertEquals("client-b", result.getClientId());
        }
        verify(connection).commit();
        verify(connection, never()).prepareStatement(contains("DELETE"));
    }

    @Test
    void currentTokenRemainsValidWhileANewTokenAwaitsAcknowledgement() throws Exception {
        final String current = "current-token";
        final Connection connection = mock(Connection.class);
        final PreparedStatement select = mock(PreparedStatement.class);
        final ResultSet rows = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(select);
        when(select.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true, true, false);
        when(rows.getString("tokenHash")).thenReturn(current, "unacknowledged-new-token");
        when(rows.getString("clientID")).thenReturn("client-a");
        when(rows.getString("tokenSlot")).thenReturn("C", "N");
        when(rows.getString("expiry")).thenReturn(
            XMPPDateTimeFormat.format(Date.from(Instant.now().plusSeconds(172800))));
        final byte[] proof = FastTokenManager.hmac(current.getBytes(StandardCharsets.UTF_8),
            "Initiator".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            final FastTokenManager.Ht2ValidationResult result = FastTokenManager.validateTokenHt2(
                "user", FastTokenManager.HT_SHA_256_NONE, proof, new byte[0], "", "");
            assertNotNull(result);
            assertEquals("client-a", result.getClientId());
        }
        verify(connection, never()).prepareStatement(contains("DELETE"));
        verify(connection, never()).prepareStatement(startsWith("UPDATE ofFastToken SET tokenSlot"));
        verify(connection).commit();
    }

    @Test
    void successfulUseOfNewTokenDeletesCurrentAndPromotesNew() throws Exception {
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
        when(rows.getString("tokenHash")).thenReturn(token);
        when(rows.getString("clientID")).thenReturn("client-a");
        when(rows.getString("tokenSlot")).thenReturn("N");
        when(rows.getString("expiry")).thenReturn(
            XMPPDateTimeFormat.format(Date.from(Instant.now().plusSeconds(172800))));
        final byte[] proof = FastTokenManager.hmac(token.getBytes(StandardCharsets.UTF_8),
            "Initiator".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            assertNotNull(FastTokenManager.validateTokenHt2(
                "user", FastTokenManager.HT_SHA_256_NONE, proof, new byte[0], "", ""));
        }
        verify(delete).setString(3, "client-a");
        verify(promote).setString(3, "client-a");
        verify(delete).executeUpdate();
        verify(promote).executeUpdate();
        verify(connection).commit();
    }

    @Test
    void invalidationIsScopedToOneMechanismAndClient() throws Exception {
        final Connection connection = mock(Connection.class);
        final PreparedStatement delete = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString())).thenReturn(delete);
        try (MockedStatic<DbConnectionManager> db = mockStatic(DbConnectionManager.class)) {
            db.when(DbConnectionManager::getConnection).thenReturn(connection);
            FastTokenManager.invalidateToken("user", FastTokenManager.HT2_SHA_512_EXPR, "client-a");
        }
        verify(delete).setString(1, "user");
        verify(delete).setString(2, FastTokenManager.HT2_SHA_512_EXPR);
        verify(delete).setString(3, "client-a");
        verify(delete).executeUpdate();
    }
}
