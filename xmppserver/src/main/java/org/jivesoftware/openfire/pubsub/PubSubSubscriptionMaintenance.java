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

package org.jivesoftware.openfire.pubsub;

import org.jivesoftware.database.DbConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Analyzes and (optionally) cleans up redundant rows in the {@code ofPubsubSubscription} table.
 *
 * Some installations have accumulated very large numbers of redundant subscription rows: rows that share the same node,
 * subscription JID, owner and subscription type, differing only by their generated subscription ID. On a node that does
 * not allow multiple subscriptions for the same subscription JID (XEP-0060 §6.1.6) - most notably PEP services
 * (XEP-0163) - at most one such subscription can be meaningful, so the surplus rows carry no functional value. In
 * extreme cases their sheer number exhausts the Java heap when the data is loaded into memory (OF-3306).
 *
 * This utility is intended to be driven from an admin-console page. It offers three operations:
 * <ul>
 *     <li>{@link #analyze()} - a read-only assessment of how much redundant data exists (safe to call on page load).</li>
 *     <li>{@link #startCleanup()} - launches a batched, background deletion of the redundant rows.</li>
 *     <li>{@link #getProgress()} - a thread-safe snapshot of an in-progress or completed cleanup, for a progress bar.</li>
 * </ul>
 *
 * <h2>What is deleted</h2>
 * For each group of rows that share {@code (serviceID, nodeID, jid, owner, subscriptionType)}, exactly one row is kept
 * (the one with the lexicographically greatest {@code id}); all others in the group are removed. Groups with only a
 * single row are never touched. The deletion is performed in bounded batches, each in its own transaction, so that it
 * can run against a live server without producing an unmanageably large transaction.
 *
 * <h2>Safety with respect to multiple-subscription services</h2>
 * Same-key rows are only redundant on a service that does <em>not</em> allow multiple subscriptions for the same
 * subscription JID (XEP-0060 §6.1.6). On a service that <em>does</em> allow them, such rows are legitimate and are
 * differentiated by their subscription ID; deleting them would destroy live subscriptions. Whether multiple
 * subscriptions are allowed is a service-wide setting ({@code PubSubService.isMultipleSubscriptionsEnabled()}): PEP
 * services always return {@code false}, while the main pubsub service is governed by the
 * {@code xmpp.pubsub.multiple-subscriptions} property.
 *
 * Because this deletion runs at the database level, it cannot itself consult that in-memory, per-service setting.
 * Instead the caller - which runs inside the server and can enumerate the live services - must supply the set of
 * service IDs that permit multiple subscriptions via the constructor. Those services are excluded from both the
 * analysis and the deletion, so their rows are never counted as removable and never deleted. Inverting the dependency
 * this way keeps the authority for the safety decision with the code that can actually answer the question, rather than
 * having this utility guess.
 *
 * This utility performs no deletion until {@link #startCleanup()} is explicitly invoked, and administrators should be
 * advised to take a database backup first.
 *
 * Instances are not designed to run concurrent cleanups; {@link #startCleanup()} guards against launching a second
 * cleanup while one is already running.
 */
public class PubSubSubscriptionMaintenance
{
    /**
     * Logger for this class.
     */
    private static final Logger Log = LoggerFactory.getLogger(PubSubSubscriptionMaintenance.class);

    /**
     * Number of rows deleted per transaction. Kept modest so that no single transaction grows unmanageably large on
     * installations with millions of redundant rows.
     */
    private static final int DELETE_BATCH_SIZE = 1000;

    /**
     * Counts the total rows and the number of distinct logical subscriptions.
     *
     * {@code COUNT(DISTINCT a, b, c)} over multiple columns is not portable across the databases Openfire supports, so
     * the distinct count is derived from a grouped subquery instead: the inner query produces one row per logical
     * subscription, and the outer query counts those rows. This is portable to MySQL/MariaDB, PostgreSQL, Oracle,
     * SQL Server and HSQLDB.
     *
     * The service-exclusion predicate (if any) is appended to the inner query's WHERE clause and to a parallel total
     * count, so excluded services contribute to neither figure.
     */
    private static final String COUNT_TOTAL =
        "SELECT COUNT(*) AS total_rows FROM ofPubsubSubscription";

    /**
     * Template for counting distinct logical subscriptions: a grouped subquery yields one row per logical
     * subscription and the outer query counts them. The single {@code %s} carries the optional service-exclusion
     * predicate. Uses {@code COUNT}/{@code GROUP BY} over a derived table (with a non-{@code AS} table alias for Oracle
     * compatibility), which is portable across all supported databases.
     */
    private static final String COUNT_DISTINCT_TEMPLATE =
        "SELECT COUNT(*) AS distinct_subs FROM (" +
            "  SELECT 1 FROM ofPubsubSubscription" +
            "%s" + // optional WHERE service-exclusion
            "  GROUP BY serviceID, nodeID, jid, owner, subscriptionType" +
            ") grouped";

    /**
     * Selects exactly one surviving subscription id per logical subscription: the greatest id within each
     * {@code (serviceID, nodeID, jid, owner, subscriptionType)} group. This is a single grouped scan (one survivor per
     * group), as opposed to a per-row correlated lookup, and uses only {@code MAX}/{@code GROUP BY}, which are portable
     * across all supported databases.
     *
     * The survivors are held in memory; non-survivor rows are then deleted in bounded batches. With at most one
     * surviving id per logical subscription, the in-memory set is small (on the order of the distinct-subscription
     * count) even when the table itself is enormous.
     */
    private static final String SELECT_SURVIVOR_IDS =
        "SELECT serviceID, nodeID, MAX(id) AS keep_id " +
            "FROM ofPubsubSubscription" +
            "%s" + // optional WHERE service-exclusion (begins with a leading space)
            " GROUP BY serviceID, nodeID, jid, owner, subscriptionType";

    /**
     * The columns selected for each page of candidate rows (the primary key).
     *
     * Paging works through candidate rows in primary-key order, in bounded chunks, rather than holding one large
     * result set open. This avoids relying on driver-side streaming (which several supported drivers do not provide for
     * auto-commit connections), keeping every query bounded and memory use flat regardless of table size.
     *
     * The row-limit keyword is dialect-specific ({@code LIMIT} / {@code TOP} / {@code FETCH FIRST}), so the limited
     * SELECT is assembled at runtime via {@link DbConnectionManager#getResultSetLimitKeyword()}. The key predicate is
     * written in expanded boolean form rather than as a row-value comparison {@code (a,b,c) > (?,?,?)}, because the
     * latter is not supported by all databases Openfire targets (for example SQL Server and older Oracle).
     */
    private static final String PAGE_COLUMNS = "serviceID, nodeID, id";

    /**
     * The table that pages are read from.
     */
    private static final String PAGE_TABLE = "ofPubsubSubscription";

    /**
     * The primary-key ordering applied to every page, so that paging by a key cursor is well-defined.
     */
    private static final String PAGE_ORDER = " ORDER BY serviceID, nodeID, id";

    /**
     * Expanded-boolean "primary key strictly greater than (?, ?, ?)" predicate, portable across all supported
     * databases. Parameters, in order: serviceID, serviceID, nodeID, serviceID, nodeID, id.
     */
    private static final String PAGE_KEY_AFTER =
        "(serviceID > ? OR (serviceID = ? AND nodeID > ?) OR (serviceID = ? AND nodeID = ? AND id > ?))";

    /**
     * Deletes a single subscription row by its full primary key. Parameters, in order: serviceID, nodeID, id.
     */
    private static final String DELETE_ONE =
        "DELETE FROM ofPubsubSubscription WHERE serviceID = ? AND nodeID = ? AND id = ?";

    /**
     * Service IDs that permit multiple subscriptions for the same subscription JID, and whose rows must therefore never
     * be treated as redundant. Immutable after construction.
     */
    @Nonnull
    private final Set<String> excludedServiceIds;

    /**
     * Holds the live progress of a cleanup. Replaced atomically so that readers always see a consistent snapshot.
     */
    private final AtomicReference<Progress> progress = new AtomicReference<>(Progress.idle());

    /**
     * Creates a maintenance utility that excludes the supplied multiple-subscription services from analysis and
     * cleanup.
     *
     * @param multipleSubscriptionServiceIds the IDs of services for which {@code isMultipleSubscriptionsEnabled()} is
     *                                       true. Rows belonging to these services are never counted as removable and
     *                                       never deleted. Must not be null; pass an empty collection only when the
     *                                       deployment is known to have no service that permits multiple subscriptions.
     *                                       In practice this set is very small (often just the single main pubsub
     *                                       service); it is rendered into a SQL {@code IN} list, so it is not intended
     *                                       to hold thousands of entries.
     */
    public PubSubSubscriptionMaintenance(@Nonnull final Collection<String> multipleSubscriptionServiceIds)
    {
        this.excludedServiceIds = Collections.unmodifiableSet(new LinkedHashSet<>(multipleSubscriptionServiceIds));
    }

    /**
     * @return the service IDs excluded from analysis and cleanup (those permitting multiple subscriptions). Never null.
     */
    @Nonnull
    public Set<String> getExcludedServiceIds()
    {
        return excludedServiceIds;
    }

    /**
     * Performs a read-only assessment of the redundant-row situation.
     *
     * This issues a single aggregate query. On a very large table it can take some seconds (a full scan), but it
     * neither locks rows for writing nor modifies any data, so it is safe to call when rendering an admin page.
     *
     * @return the analysis result, never null.
     * @throws SQLException if the database could not be queried.
     */
    @Nonnull
    public Analysis analyze() throws SQLException
    {
        Connection con = null;
        try {
            con = DbConnectionManager.getConnection();
            final long total = queryLong(con, COUNT_TOTAL + serviceExclusionClause("WHERE", "serviceID"));
            final String distinctExclusion = serviceExclusionClause("WHERE", "serviceID");
            final long distinct = queryLong(con, String.format(COUNT_DISTINCT_TEMPLATE, distinctExclusion));
            return new Analysis(total, distinct);
        }
        finally {
            DbConnectionManager.closeConnection(con);
        }
    }

    /**
     * Executes a single-aggregate query whose only parameters (if any) are the excluded service IDs, and returns the
     * first column of the single result row as a long.
     */
    private long queryLong(@Nonnull final Connection con, @Nonnull final String sql) throws SQLException
    {
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            pstmt = con.prepareStatement(sql);
            bindExcludedServiceIds(pstmt, 1);
            rs = pstmt.executeQuery();
            return rs.next() ? rs.getLong(1) : 0L;
        }
        finally {
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeStatement(pstmt);
        }
    }

    /**
     * Builds a SQL fragment that excludes the configured services, or an empty string when there are none to exclude.
     *
     * @param keyword the clause keyword to introduce the predicate with - "WHERE" when the query has no other
     *                predicate, "AND" when appending to an existing one.
     * @param column  the (possibly table-qualified) column to test, e.g. "serviceID" or "a.serviceID".
     * @return a fragment such as " WHERE serviceID NOT IN (?, ?)", or "" when no services are excluded.
     */
    @Nonnull
    private String serviceExclusionClause(@Nonnull final String keyword, @Nonnull final String column)
    {
        if (excludedServiceIds.isEmpty()) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(" ").append(keyword).append(' ').append(column).append(" NOT IN (");
        for (int i = 0; i < excludedServiceIds.size(); i++) {
            sb.append(i == 0 ? "?" : ",?");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Binds the excluded service IDs as consecutive parameters, starting at the given index.
     *
     * @param pstmt      the statement whose parameters are set.
     * @param startIndex the 1-based index of the first excluded-service parameter.
     * @return the next free parameter index after the bound values.
     * @throws SQLException if a parameter could not be set.
     */
    private int bindExcludedServiceIds(@Nonnull final PreparedStatement pstmt, final int startIndex) throws SQLException
    {
        int index = startIndex;
        for (final String serviceId : excludedServiceIds) {
            pstmt.setString(index++, serviceId);
        }
        return index;
    }

    /**
     * Launches a cleanup on a background thread, unless one is already running.
     *
     * The cleanup deletes redundant rows in batches (see {@link #DELETE_BATCH_SIZE}), committing after each batch and
     * updating {@link #getProgress()} as it goes. Control returns to the caller immediately; the admin page should poll
     * {@link #getProgress()} to render a progress indicator.
     *
     * @return true if a new cleanup was started; false if one was already running.
     */
    public boolean startCleanup()
    {
        final Progress current = progress.get();
        if (current.phase == Phase.RUNNING) {
            return false;
        }
        // Reset to a running state before spawning the worker, so a rapid second call is rejected.
        if (!progress.compareAndSet(current, Progress.starting())) {
            return false; // Another caller won the race.
        }

        final Thread worker = new Thread(this::runCleanup, "pubsub-subscription-cleanup");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /**
     * @return a snapshot of the current (or most recently completed) cleanup progress. Never null.
     */
    @Nonnull
    public Progress getProgress()
    {
        return progress.get();
    }

    /**
     * The background cleanup routine. Collects the ids to remove, then deletes them in bounded, individually-committed
     * batches, updating progress throughout.
     */
    private void runCleanup()
    {
        Log.info("Starting cleanup of redundant pubsub subscription rows.");
        try {
            // First, determine the total amount of work, so the progress indicator has a meaningful denominator.
            final Analysis analysis = analyze();
            final long toRemove = analysis.getRemovableRows();
            if (toRemove == 0) {
                Log.info("No redundant pubsub subscription rows found; nothing to clean up.");
                progress.set(Progress.completed(0, 0));
                return;
            }

            progress.set(Progress.running(toRemove, 0));
            final long removed = deleteRedundantRows(toRemove);

            Log.info("Cleanup of redundant pubsub subscription rows finished. Removed {} row(s).", removed);
            progress.set(Progress.completed(toRemove, removed));
        }
        catch (Exception e) {
            Log.error("Cleanup of redundant pubsub subscription rows failed.", e);
            final Progress last = progress.get();
            progress.set(Progress.failed(last.totalToRemove, last.removed, e.getMessage()));
        }
    }

    /**
     * Collects the surviving subscription ids into memory, then pages through all candidate rows in primary-key order
     * and deletes those that are not survivors, in bounded batches.
     *
     * The survivor set is computed with a single grouped scan (one row per logical subscription), so it is small even
     * when the table is enormous. Determining whether a row is a survivor is then an in-memory set lookup, avoiding the
     * per-row correlated subquery that makes the naive approach quadratic. Candidate rows are read in bounded,
     * key-ordered pages rather than as one large result set, so memory use stays flat and the work does not depend on
     * driver-side streaming.
     *
     * @param expectedTotal the analysis-derived removable total, used only to keep the progress denominator stable.
     * @return the number of rows actually removed.
     * @throws SQLException if the database interaction failed.
     */
    private long deleteRedundantRows(final long expectedTotal) throws SQLException
    {
        final Set<String> survivors = collectSurvivorKeys();
        long removed = 0;

        // Batch buffer of (serviceID, nodeID, id) keys for non-survivor rows.
        final String[][] batch = new String[DELETE_BATCH_SIZE][3];
        int batchCount = 0;

        // Cursor over the primary key; null until the first page is read.
        String cursorService = null;
        String cursorNode = null;
        String cursorId = null;

        boolean morePages = true;
        while (morePages) {
            final Page page = readPage(cursorService, cursorNode, cursorId);
            morePages = page.full; // A full page implies there may be more rows.

            for (final String[] row : page.rows) {
                cursorService = row[0];
                cursorNode = row[1];
                cursorId = row[2];

                if (survivors.contains(survivorKey(row[0], row[1], row[2]))) {
                    continue; // Keep exactly one row per logical subscription.
                }

                batch[batchCount][0] = row[0];
                batch[batchCount][1] = row[1];
                batch[batchCount][2] = row[2];
                batchCount++;

                if (batchCount == DELETE_BATCH_SIZE) {
                    removed += deleteBatch(batch, batchCount);
                    batchCount = 0;
                    progress.set(Progress.running(expectedTotal, removed));
                }
            }
        }

        // Flush any partial final batch.
        if (batchCount > 0) {
            removed += deleteBatch(batch, batchCount);
            progress.set(Progress.running(expectedTotal, removed));
        }

        return removed;
    }

    /**
     * Reads one page of rows, in primary-key order, strictly after the supplied cursor key (or from the start when the
     * cursor is null). Each page is a bounded query, so no driver-side streaming is required.
     *
     * @param afterService the serviceID of the last row read, or null for the first page.
     * @param afterNode    the nodeID of the last row read, or null for the first page.
     * @param afterId      the id of the last row read, or null for the first page.
     * @return the page of rows (never null); {@link Page#full} indicates the page was filled to the page size, so more
     *         rows may remain.
     * @throws SQLException if the page could not be read.
     */
    @Nonnull
    private Page readPage(@Nullable final String afterService, @Nullable final String afterNode, @Nullable final String afterId) throws SQLException
    {
        final boolean first = afterService == null;
        final String sql = buildPageSql(first);

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(sql);
            DbConnectionManager.setFetchSize(pstmt, DELETE_BATCH_SIZE);
            DbConnectionManager.setMaxRows(pstmt, DELETE_BATCH_SIZE);

            // The row-limit count is rendered as a literal in buildPageSql (portable across dialects), so the only
            // bind parameters are the excluded service IDs and, for non-first pages, the key-after predicate.
            int index = bindExcludedServiceIds(pstmt, 1);
            if (!first) {
                // Expanded-boolean key predicate parameters: service, service, node, service, node, id.
                pstmt.setString(index++, afterService);
                pstmt.setString(index++, afterService);
                pstmt.setString(index++, afterNode);
                pstmt.setString(index++, afterService);
                pstmt.setString(index++, afterNode);
                pstmt.setString(index, afterId);
            }

            rs = pstmt.executeQuery();
            final List<String[]> rows = new ArrayList<>(DELETE_BATCH_SIZE);
            while (rs.next()) {
                rows.add(new String[] { rs.getString(1), rs.getString(2), rs.getString(3) });
            }
            return new Page(rows, rows.size() == DELETE_BATCH_SIZE);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Builds a dialect-correct, row-limited SELECT for one page, applying the service exclusion and (for non-first
     * pages) the key-after predicate.
     *
     * The row-limit keyword position and spelling vary by database, so this uses
     * {@link DbConnectionManager#getResultSetLimitKeyword()} and {@link DbConnectionManager#isResultSetLimitKeywordPrefix()}.
     * The limit count is rendered as a literal integer (it is a fixed, trusted constant, never user input), which keeps
     * the parameter list identical across dialects.
     *
     * @param first true for the first page (no key-after predicate), false otherwise.
     * @return the SQL for one page.
     */
    @Nonnull
    private String buildPageSql(final boolean first)
    {
        final StringBuilder where = new StringBuilder();
        final String exclusion = serviceExclusionClause("WHERE", "serviceID");
        where.append(exclusion);
        if (!first) {
            where.append(exclusion.isEmpty() ? " WHERE " : " AND ").append(PAGE_KEY_AFTER);
        }

        final DbConnectionManager.ResultSetLimitKeyword limit = DbConnectionManager.getResultSetLimitKeyword();
        final StringBuilder sql = new StringBuilder();
        switch (limit) {
            case TOP -> // e.g. SQL Server: SELECT TOP (n) cols ...
                sql.append("SELECT TOP (").append(DELETE_BATCH_SIZE).append(") ").append(PAGE_COLUMNS)
                    .append(" FROM ").append(PAGE_TABLE).append(where).append(PAGE_ORDER);
            case FETCH_FIRST -> // e.g. Oracle/DB2/Firebird: ... FETCH FIRST n ROWS ONLY
                sql.append("SELECT ").append(PAGE_COLUMNS).append(" FROM ").append(PAGE_TABLE).append(where)
                    .append(PAGE_ORDER).append(" FETCH FIRST ").append(DELETE_BATCH_SIZE).append(" ROWS ONLY");
            default -> // LIMIT style: MySQL/MariaDB/PostgreSQL/HSQLDB/CockroachDB
                sql.append("SELECT ").append(PAGE_COLUMNS).append(" FROM ").append(PAGE_TABLE).append(where)
                    .append(PAGE_ORDER).append(" LIMIT ").append(DELETE_BATCH_SIZE);
        }
        return sql.toString();
    }

    /**
     * One page of read rows.
     */
    private static final class Page
    {
        /**
         * The rows in this page, each a {serviceID, nodeID, id} triple, in primary-key order.
         */
        @Nonnull private final List<String[]> rows;

        /**
         * True when the page was filled to the page size, indicating that more rows may follow.
         */
        private final boolean full;

        /**
         * Creates a page.
         *
         * @param rows the rows read for this page, in primary-key order.
         * @param full whether the page was filled to the page size (more rows may remain).
         */
        private Page(@Nonnull final List<String[]> rows, final boolean full)
        {
            this.rows = rows;
            this.full = full;
        }
    }

    /**
     * Computes, in memory, the set of surviving subscription ids: one per logical subscription, identified by the
     * full primary key {@code (serviceID, nodeID, id)}. Built from a single grouped scan.
     *
     * The scan runs on a transaction connection (auto-commit disabled) so that the fetch-size hint can engage a
     * server-side cursor on drivers that ignore it for auto-commit connections. The survivor set is small (one entry
     * per logical subscription), so this is not a memory concern, but the cursor keeps the driver from buffering the
     * full grouped result up front on large tables.
     *
     * @return a set of survivor keys, in the encoding produced by {@link #survivorKey(String, String, String)}.
     * @throws SQLException if the survivor query failed.
     */
    @Nonnull
    private Set<String> collectSurvivorKeys() throws SQLException
    {
        final String sql = String.format(SELECT_SURVIVOR_IDS, serviceExclusionClause("WHERE", "serviceID"));
        final Set<String> survivors = new HashSet<>();

        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            DbConnectionManager.setFetchSize(pstmt, DELETE_BATCH_SIZE);
            bindExcludedServiceIds(pstmt, 1);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                survivors.add(survivorKey(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
        }
        catch (SQLException e) {
            abortTransaction = true;
            throw e;
        }
        finally {
            DbConnectionManager.closeResultSet(rs);
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
        return survivors;
    }

    /**
     * Encodes a primary key into the string form used for the in-memory survivor set. The separator is a NUL character,
     * which cannot occur in a JID, node id or service id, so the encoding is unambiguous.
     */
    @Nonnull
    private static String survivorKey(@Nonnull final String serviceID, @Nonnull final String nodeID, @Nonnull final String id)
    {
        return serviceID + '\u0000' + nodeID + '\u0000' + id;
    }

    /**
     * Deletes a single batch of subscription rows in its own transaction.
     *
     * @param batch a reusable buffer of {serviceID, nodeID, id} triples.
     * @param count the number of valid entries in the buffer.
     * @return the number of rows deleted in this batch.
     * @throws SQLException if the batch could not be committed.
     */
    private long deleteBatch(final String[][] batch, final int count) throws SQLException
    {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(DELETE_ONE);
            for (int i = 0; i < count; i++) {
                pstmt.setString(1, batch[i][0]);
                pstmt.setString(2, batch[i][1]);
                pstmt.setString(3, batch[i][2]);
                pstmt.addBatch();
            }
            final int[] results = pstmt.executeBatch();
            long deleted = 0;
            for (final int r : results) {
                // Some drivers return SUCCESS_NO_INFO (-2) instead of an exact count; treat that as one deleted row,
                // since each statement targets a single primary key.
                deleted += (r >= 0) ? r : 1;
            }
            return deleted;
        }
        catch (SQLException e) {
            abortTransaction = true;
            throw e;
        }
        finally {
            DbConnectionManager.closeStatement(pstmt);
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Read-only assessment of the redundant-row situation. Immutable.
     */
    public static final class Analysis
    {
        /**
         * The total number of rows in the table (within the analyzed scope).
         */
        private final long totalRows;

        /**
         * The number of distinct logical subscriptions (the rows that would remain after cleanup).
         */
        private final long distinctSubscriptions;

        /**
         * Creates an analysis result.
         *
         * @param totalRows             the total number of rows counted.
         * @param distinctSubscriptions the number of distinct logical subscriptions counted.
         */
        Analysis(final long totalRows, final long distinctSubscriptions)
        {
            this.totalRows = totalRows;
            this.distinctSubscriptions = distinctSubscriptions;
        }

        /** @return the total number of rows currently in the table. */
        public long getTotalRows()
        {
            return totalRows;
        }

        /** @return the number of distinct logical subscriptions (rows remaining after cleanup). */
        public long getDistinctSubscriptions()
        {
            return distinctSubscriptions;
        }

        /** @return the number of rows that cleanup would remove. */
        public long getRemovableRows()
        {
            return totalRows - distinctSubscriptions;
        }

        /**
         * @return true if cleanup is worthwhile (there is at least one removable row). An admin page can use this to
         *         decide whether to show a maintenance alert.
         */
        public boolean isCleanupRecommended()
        {
            return getRemovableRows() > 0;
        }

        /**
         * @return the fraction of the table that is redundant, in the range [0.0, 1.0]; 0.0 when the table is empty.
         *         Useful for phrasing the alert (e.g. "84% of rows are redundant").
         */
        public double getRedundancyRatio()
        {
            return totalRows == 0 ? 0.0 : (double) getRemovableRows() / (double) totalRows;
        }
    }

    /**
     * Phase of a cleanup operation.
     */
    public enum Phase
    {
        /** No cleanup has been started in this server session. */
        IDLE,
        /** A cleanup has been requested but has not yet reported progress. */
        RUNNING,
        /** A cleanup finished successfully. */
        COMPLETED,
        /** A cleanup terminated with an error. */
        FAILED
    }

    /**
     * Immutable snapshot of cleanup progress. New instances are published atomically as the cleanup proceeds.
     */
    public static final class Progress
    {
        /**
         * The current phase of the cleanup.
         */
        private final Phase phase;

        /**
         * The number of rows the cleanup set out to remove (the denominator for progress), or 0 until known.
         */
        private final long totalToRemove;

        /**
         * The number of rows removed so far (or in total, once completed).
         */
        private final long removed;

        /**
         * The failure message when the phase is {@link Phase#FAILED}, otherwise null.
         */
        @Nullable private final String error;

        /**
         * Creates a progress snapshot.
         *
         * @param phase         the current phase.
         * @param totalToRemove the number of rows targeted for removal (0 if not yet known).
         * @param removed       the number of rows removed so far.
         * @param error         the failure message, or null when not failed.
         */
        private Progress(final Phase phase, final long totalToRemove, final long removed, @Nullable final String error)
        {
            this.phase = phase;
            this.totalToRemove = totalToRemove;
            this.removed = removed;
            this.error = error;
        }

        /**
         * @return a snapshot representing the idle state (no cleanup started).
         */
        static Progress idle()
        {
            return new Progress(Phase.IDLE, 0, 0, null);
        }

        /**
         * @return a snapshot representing a cleanup that has been requested but has not yet reported a total.
         */
        static Progress starting()
        {
            return new Progress(Phase.RUNNING, 0, 0, null);
        }

        /**
         * @param totalToRemove the number of rows targeted for removal.
         * @param removed       the number of rows removed so far.
         * @return a snapshot representing a running cleanup.
         */
        static Progress running(final long totalToRemove, final long removed)
        {
            return new Progress(Phase.RUNNING, totalToRemove, removed, null);
        }

        /**
         * @param totalToRemove the number of rows that were targeted for removal.
         * @param removed       the number of rows removed in total.
         * @return a snapshot representing a successfully completed cleanup.
         */
        static Progress completed(final long totalToRemove, final long removed)
        {
            return new Progress(Phase.COMPLETED, totalToRemove, removed, null);
        }

        /**
         * @param totalToRemove the number of rows that were targeted for removal.
         * @param removed       the number of rows removed before the failure.
         * @param error         the failure message.
         * @return a snapshot representing a failed cleanup.
         */
        static Progress failed(final long totalToRemove, final long removed, @Nullable final String error)
        {
            return new Progress(Phase.FAILED, totalToRemove, removed, error);
        }

        /**
         * @return the current phase of the cleanup.
         */
        public Phase getPhase()
        {
            return phase;
        }

        /** @return the number of rows the cleanup set out to remove (0 until known). */
        public long getTotalToRemove()
        {
            return totalToRemove;
        }

        /** @return the number of rows removed so far (or in total, when completed). */
        public long getRemoved()
        {
            return removed;
        }

        /**
         * @return completion as a percentage in [0, 100]. Returns 100 for a completed cleanup, and 0 when the total is
         *         not yet known.
         */
        public int getPercentComplete()
        {
            if (phase == Phase.COMPLETED) {
                return 100;
            }
            if (totalToRemove <= 0) {
                return 0;
            }
            final long pct = (removed * 100) / totalToRemove;
            return (int) Math.min(100, Math.max(0, pct));
        }

        /** @return the error message if the cleanup failed, otherwise null. */
        @Nullable
        public String getError()
        {
            return error;
        }
    }
}
