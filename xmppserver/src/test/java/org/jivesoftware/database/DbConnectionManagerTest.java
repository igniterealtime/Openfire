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
package org.jivesoftware.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DbConnectionManagerTest {

    /**
     * Verifies that SQL Server uses TOP as a prefix-style row limiting keyword.
     */
    @Test
    public void sqlServerUsesTopAsAPrefixKeyword() {
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.TOP, DbConnectionManager.DatabaseType.sqlserver.getResultSetLimitKeyword(), "SQL Server should map to the TOP limit keyword.");
        assertTrue(DbConnectionManager.DatabaseType.sqlserver.isResultSetLimitKeywordPrefix(), "TOP should be marked as a prefix-style keyword.");
    }

    /**
     * Verifies that Oracle uses FETCH FIRST as a suffix-style row limiting keyword.
     */
    @Test
    public void oracleUsesFetchFirstAsASuffixKeyword() {
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.FETCH_FIRST, DbConnectionManager.DatabaseType.oracle.getResultSetLimitKeyword(), "Oracle should map to the FETCH_FIRST limit keyword.");
        assertFalse(DbConnectionManager.DatabaseType.oracle.isResultSetLimitKeywordPrefix(), "FETCH_FIRST should be marked as a suffix-style keyword.");
    }

    /**
     * Verifies that DB2 uses FETCH FIRST as a suffix-style row limiting keyword.
     */
    @Test
    public void db2UsesFetchFirstAsASuffixKeyword() {
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.FETCH_FIRST, DbConnectionManager.DatabaseType.db2.getResultSetLimitKeyword(), "DB2 should map to the FETCH_FIRST limit keyword.");
        assertFalse(DbConnectionManager.DatabaseType.db2.isResultSetLimitKeywordPrefix(), "DB2 should use a suffix-style limit keyword.");
    }

    /**
     * Verifies that common ANSI-style databases map to LIMIT.
     */
    @Test
    public void commonAnsiStyleDatabasesUseLimit() {
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.LIMIT, DbConnectionManager.DatabaseType.mysql.getResultSetLimitKeyword(), "MySQL should map to the LIMIT keyword.");
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.LIMIT, DbConnectionManager.DatabaseType.postgresql.getResultSetLimitKeyword(), "PostgreSQL should map to the LIMIT keyword.");
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.LIMIT, DbConnectionManager.DatabaseType.hsqldb.getResultSetLimitKeyword(), "HSQLDB should map to the LIMIT keyword.");
        assertEquals(DbConnectionManager.ResultSetLimitKeyword.LIMIT, DbConnectionManager.DatabaseType.unknown.getResultSetLimitKeyword(), "Unknown database types should default to LIMIT.");
    }

    /**
     * Verifies prefix-position metadata for all supported row limiting keywords.
     */
    @Test
    public void enumKnowsWhetherItIsPrefixStyle() {
        assertTrue(DbConnectionManager.ResultSetLimitKeyword.TOP.isPrefix(), "TOP should be treated as a prefix-style keyword.");
        assertFalse(DbConnectionManager.ResultSetLimitKeyword.FETCH_FIRST.isPrefix(), "FETCH_FIRST should be treated as a suffix-style keyword.");
        assertFalse(DbConnectionManager.ResultSetLimitKeyword.LIMIT.isPrefix(), "LIMIT should be treated as a suffix-style keyword.");
    }
}


