/*
 * Copyright (C) 2004 Jive Software, 2017-2024 Ignite Realtime Foundation. All rights reserved.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Wraps a Connection object and collects statistics about the database queries
 * that are performed.
 * <p>
 * Statistics of the profiled Connections can be obtained from the static
 * methods of this class. Instances of this class are the actual wrappers that
 * perform profiling.
 *
 * @author Jive Software
 */
public class ProfiledConnection extends AbstractConnection {

    private static final Logger Log = LoggerFactory.getLogger(ProfiledConnection.class);

    public enum Type {

        /**
         * Constant for SELECT database queries.
         */
        select,

        /**
         * Constant for UPDATE database queries.
         */
        update,

        /**
         * Constant for INSERT database queries.
         */
        insert,

        /**
         * Constant for DELETE database queries.
         */
        delete

    }

    private static Instant startTime = null;
    private static Instant endTime = null;

    private static long insertCount = 0;
    private static long updateCount = 0;
    private static long selectCount = 0;
    private static long deleteCount = 0;

    private static Duration totalInsertTime = Duration.ZERO;
    private static Duration totalUpdateTime = Duration.ZERO;
    private static Duration totalSelectTime = Duration.ZERO;
    private static Duration totalDeleteTime = Duration.ZERO;

    private static final Map<String, ProfiledConnectionEntry> insertQueries = new Hashtable<>();
    private static final Map<String, ProfiledConnectionEntry> updateQueries = new Hashtable<>();
    private static final Map<String, ProfiledConnectionEntry> selectQueries = new Hashtable<>();
    private static final Map<String, ProfiledConnectionEntry> deleteQueries = new Hashtable<>();

    /**
     * Start profiling.
     */
    public static void start() {
        resetStatistics();
        startTime = Instant.now();
        endTime = null;
    }

    /**
     * Stop profiling.
     */
    public static void stop() {
        endTime = Instant.now();
    }

    /**
     * Returns the total number database queries of a particular type performed.
     * Valid types are ProfiledConnection.SELECT, ProfiledConnection.UPDATE,
     * ProfiledConnection.INSERT, and ProfiledConnection.DELETE.
     *
     * @param type the type of query to get the count for.
     * @return the number queries of type {@code type} performed.
     */
    public static long getQueryCount(Type type) {
        return switch (type) {
            case select -> selectCount;
            case update -> updateCount;
            case insert -> insertCount;
            case delete -> deleteCount;
        };
    }

    /**
     * Adds a query.
     *
     * @param type the type of the query.
     * @param sql the insert sql string.
     * @param time the length of time the query took in milliseconds
     */
    public static void addQuery(Type type, String sql, Duration time) {
        // Do nothing if we didn't receive a sql statement
        if (sql == null || sql.isEmpty()) {
            return;
        }

        // Do nothing if profiling has stopped.
        if (startTime == null || (endTime != null && endTime.isAfter(startTime))) {
            return;
        }

        // clean up sql to insert spaces after every ','
        sql = reformatQuery(sql);

        // remove values from query
        sql = removeQueryValues(sql);

        ProfiledConnectionEntry entry;
        switch (type) {
            case select:
                selectCount++;
                totalSelectTime = totalSelectTime.plus(time);
                entry = selectQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    selectQueries.put(sql, entry);
                }
                break;
            case update:
                updateCount++;
                totalUpdateTime = totalUpdateTime.plus(time);
                entry = updateQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    updateQueries.put(sql, entry);
                }
                break;
            case insert:
                insertCount++;
                totalInsertTime = totalInsertTime.plus(time);
                entry = insertQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    insertQueries.put(sql, entry);
                }
                break;
            case delete:
                deleteCount++;
                totalDeleteTime = totalDeleteTime.plus(time);
                entry = deleteQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    deleteQueries.put(sql, entry);
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid type");
        }

        entry.count++;
        entry.totalTime = entry.totalTime.plus(time);
    }

    /**
     * Returns the average number of queries of a certain type that have been
     * performed per second since profiling started. If profiling has been
     * stopped, that moment in time is used for the calculation. Otherwise,
     * the current moment in time is used.
     *
     * @param type the type of database query to check.
     * @return the average number of queries of a certain typed performed per
     *         second.
     */
    public static double getQueriesPerSecond(Type type) {
        long count = switch (type) {
            case select -> selectCount;
            case update -> updateCount;
            case insert -> insertCount;
            case delete -> deleteCount;
        };

        // if no queries yet, return 0;
        if (count == 0) {
            return 0;
        }

        if (startTime == null) {
            return 0;
        }

        // If the profiling hasn't been stopped yet, we want to give
        // profiling values up to the current time instead.
        Instant end = endTime == null ? Instant.now() : endTime;

        // Compute the number of seconds
        double time = Duration.between(startTime, end).toMillis() / 1000.0;

        // Finally, return the average.
        return count / time;
    }

    /**
     * Returns the average amount of time spent executing the specified type
     * of query.
     *
     * @param type the type of query.
     * @return a double representing the average time spent executing the type
     *         of query.
     */
    public static Duration getAverageQueryTime(Type type) {
        Duration time;
        long count;

        switch (type) {
            case select:
                count = selectCount;
                time = totalSelectTime;
                break;
            case update:
                count = updateCount;
                time = totalUpdateTime;
                break;
            case insert:
                count = insertCount;
                time = totalInsertTime;
                break;
            case delete:
                count = deleteCount;
                time = totalDeleteTime;
                break;
            default:
                throw new IllegalArgumentException("Invalid type");
        }

        if (count != 0) {
            return time.dividedBy(count);
        }
        else {
            return Duration.ZERO;
        }
    }

    /**
     * Returns the total amount of time in milliseconds spent doing a particular
     * type of query. Note that this isn't necessarily representative of actual real
     * time since db queries often occur in parallel.
     *
     * @param type the type of query to check.
     * @return the number of milliseconds spent executing the specified type of
     *         query.
     */
    public static Duration getTotalQueryTime(Type type) {
        return switch (type) {
            case select -> totalSelectTime;
            case update -> totalUpdateTime;
            case insert -> totalInsertTime;
            case delete -> totalDeleteTime;
        };
    }

    /**
     * Returns an array of sorted queries (as ProfiledConnectionEntry objects) by type
     *
     * @param type       the type of query to check
     * @param sortByTime sort the resulting list by Time if true,
     *                   otherwise sort by count if false (default)
     * @return an array of ProfiledConnectionEntry objects
     */
    public static ProfiledConnectionEntry[] getSortedQueries(Type type, boolean sortByTime) {
        Map<String, ProfiledConnectionEntry> queries = switch (type) {
            case select -> selectQueries;
            case update -> updateQueries;
            case insert -> insertQueries;
            case delete -> deleteQueries;
        };

        // No queries, return null
        if (queries.isEmpty()) {
            return null;
        }

        ProfiledConnectionEntry[] result = queries.values().toArray(new ProfiledConnectionEntry[0]);

        quickSort(result, sortByTime, 0, result.length - 1);
        return result;
    }


    /**
     * Reset all statistics.
     */
    public static void resetStatistics() {
        final boolean isRunning = startTime != null && (endTime == null || startTime.isAfter(endTime));
        startTime = isRunning ? Instant.now() : null;
        endTime = null;
        insertCount = updateCount = selectCount = deleteCount = 0;
        totalInsertTime = Duration.ZERO;
        totalUpdateTime = Duration.ZERO;
        totalSelectTime = Duration.ZERO;
        totalDeleteTime = Duration.ZERO;

        insertQueries.clear();
        updateQueries.clear();
        selectQueries.clear();
        deleteQueries.clear();
    }

    /**
     * @param entries    entries
     * @param sortByTime sort by time if true, otherwise sort by count
     * @param first      first index to sort on. Normally 0
     * @param last       last index to sort on. Normally length -1
     */
    private static void quickSort(ProfiledConnectionEntry[] entries, boolean sortByTime, int first, int last) {

        // do nothing if array contains fewer than two elements
        if (first >= last || entries.length < 2) {
            return;
        }

        swap(entries, first, (first + last) / 2);

        int index = first;
        for (int i = first + 1; i <= last; i++) {
            if (sortByTime && ((entries[first].totalTime.dividedBy(entries[first].count)).compareTo(entries[i].totalTime.dividedBy(entries[i].count))) < 0) {
                swap(entries, ++index, i);
            }
            else if (!sortByTime && entries[first].count < entries[i].count) {
                swap(entries, ++index, i);
            }
        }
        swap(entries, first, index);
        quickSort(entries, sortByTime, first, index - 1);
        quickSort(entries, sortByTime, index + 1, last);
    }

    private static void swap(Object[] list, int i, int j) {
        Object tmp = list[i];
        list[i] = list[j];
        list[j] = tmp;
    }

    private static String removeQueryValues(String _sql) {
        int length = _sql.length();

        if (!_sql.contains("=")) {
            return _sql;
        }

        StringBuilder sql = new StringBuilder(_sql);
        boolean inValue = false;
        boolean afterEquals = false;
        boolean hasQuotes = false;
        int startValue = -1;
        int endValue = -1;
        int charRemoved = 0;

        for (int x = 0; x < length; x++) {
            char c = _sql.charAt(x);

            switch (c) {
                case '=':
                {
                    if (!afterEquals) {
                        afterEquals = true;
                    }
                    break;
                }
                case ' ':
                {
                    if (!hasQuotes && inValue) {
                        endValue = x;
                        inValue = false;
                        hasQuotes = false;
                        afterEquals = false;
                    }
                    break;
                }
                case '\'':
                {
                    if (afterEquals && !inValue) {
                        startValue = x;
                        inValue = true;
                        hasQuotes = true;
                    }
                    else if (afterEquals && inValue && hasQuotes) {
                        endValue = x + 1;
                        inValue = false;
                        hasQuotes = false;
                        afterEquals = false;
                    }
                    break;
                }
                case '-', '9', '8', '7', '6', '5', '4', '3', '2', '1', '0', '+':
                {
                    if (afterEquals && !inValue) {
                        startValue = x;
                        inValue = true;

                    }
                    break;
                }
                default:
                {
                    if (afterEquals && !inValue) {
                        afterEquals = false;
                    }
                }
            }

            if (x == length - 1 && afterEquals) {
                endValue = x + 1;
            }

            if (startValue != -1 && endValue != -1) {
                sql.replace(startValue - charRemoved, endValue - charRemoved, "?");

                charRemoved += endValue - startValue - 1;
                startValue = -1;
                endValue = -1;
            }
        }

        return sql.toString();
    }

    private static String reformatQuery(String _sql) {
        int length = _sql.length();
        int charAdded = 0;
        StringBuilder sql = new StringBuilder(_sql);

        for (int x = 0; x < length; x++) {
            char c = _sql.charAt(x);

            if (c == ',' && x < length - 1 && _sql.charAt(x + 1) != ' ') {
                sql.replace(x + charAdded, x + 1 + charAdded, ", ");
                charAdded++;
            }
        }

        return sql.toString();
    }

    //--------------------- Connection Wrapping Code ---------------------//

    /**
     * All connections that are opened and not (yet) closed by the current thread.
     */
    private static final ThreadLocal<Map<Connection, Throwable>> connectionsOnThread = ThreadLocal.withInitial(HashMap::new);

    /**
     * Creates a new ProfiledConnection that wraps the specified connection.
     *
     * @param connection the Connection to wrap and collect stats for.
     */
    public ProfiledConnection(Connection connection) {
        super(connection);
        connectionsOnThread.get().put(connection, new Exception("StackTrace"));

        if (connectionsOnThread.get().size() > 1) {
            Log.warn("This thread currently has more than one (namely: {}) database connections open. Call flows that depend on having more than one open database connection can cause resource starvation. Stack traces for every invocation that opens a new connection are logged on DEBUG level.", connectionsOnThread.get().size());
            connectionsOnThread.get().values().forEach(t -> Log.debug("Stack trace for invocation of database connection constructor.", t));
        }
    }

    public void close() throws SQLException {
        // Close underlying connection.
        if (connection != null) {
            try {
                connection.close();
            } finally {
                connectionsOnThread.get().remove(connection);
                if (connectionsOnThread.get().isEmpty()) {
                    connectionsOnThread.remove();
                }
            }
        }
    }

    public Statement createStatement() throws SQLException {
        // Returned a TimedStatement so that we can do db timings.
        return new TimedStatement(connection.createStatement());
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        // Returned a TimedPreparedStatement so that we can do db timings.
        return new TimedPreparedStatement(connection.prepareStatement(sql), sql);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return new TimedStatement(connection.createStatement(resultSetType,
                resultSetConcurrency));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        return new TimedPreparedStatement(connection.prepareStatement(sql, resultSetType, resultSetConcurrency), sql);
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return new TimedCallableStatement(connection.prepareCall(sql), sql);
    }

    public CallableStatement prepareCall(String sql, int i, int i1) throws SQLException {
        return new TimedCallableStatement(connection.prepareCall(sql, i, i1), sql);
    }

    /**
     * An implementation of the Statement interface that wraps an underlying
     * Statement object and performs timings of the database queries. The class
     * does not handle batch queries but should generally work otherwise.
     */
    static class TimedStatement extends StatementWrapper {

        private final Statement stmt;

        /**
         * Creates a new TimedStatement that wraps {@code stmt}.
         *
         * @param stmt the statement.
         */
        public TimedStatement(Statement stmt) {
            super(stmt);
            this.stmt = stmt;
        }

        public boolean execute(String sql) throws SQLException {

            Instant start = Instant.now();
            boolean result = stmt.execute(sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, sql, Duration.between(start, end));
            }
            return result;
        }

        public ResultSet executeQuery(String sql) throws SQLException {
            Instant start = Instant.now();
            ResultSet result = stmt.executeQuery(sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, sql, Duration.between(start, end));
            }
            return result;
        }

        public int executeUpdate(String sql) throws SQLException {
            Instant start = Instant.now();
            int result = stmt.executeUpdate(sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, sql, Duration.between(start, end));
            }
            return result;
        }
    }

    /**
     * An implementation of the PreparedStatement interface that wraps an
     * underlying PreparedStatement object and performs timings of the database
     * queries.
     */
    static class TimedPreparedStatement extends PreparedStatementWrapper {

        private final String sql;
        private final Type type;

        public TimedPreparedStatement(PreparedStatement pstmt, String sql) {
            super(pstmt);
            this.sql = sql;

            // determine the type of query
            String sqlL = sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                type = Type.insert;
            }
            else if (sqlL.startsWith("update")) {
                type = Type.update;
            }
            else if (sqlL.startsWith("delete")) {
                type = Type.delete;
            }
            else {
                type = Type.select;
            }
        }

        public boolean execute() throws SQLException {
            // Perform timing of this method.
            Instant start = Instant.now();
            boolean result = pstmt.execute();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        /*
         * This is one of the methods that we wish to time
         */
        public ResultSet executeQuery() throws SQLException {

            Instant start = Instant.now();
            ResultSet result = pstmt.executeQuery();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        /*
         * This is one of the methods that we wish to time
         */
        public int executeUpdate() throws SQLException {

            Instant start = Instant.now();
            int result = pstmt.executeUpdate();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        // The following methods are from the Statement class - the
        // SuperInterface of PreparedStatement
        // without these this class won't compile

        public boolean execute(String _sql) throws SQLException {

            Instant start = Instant.now();
            boolean result = pstmt.execute(_sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = _sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, _sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, _sql, Duration.between(start, end));
            }
            return result;
        }

        public int[] executeBatch() throws SQLException {

            Instant start = Instant.now();
            int[] result = pstmt.executeBatch();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        public ResultSet executeQuery(String _sql) throws SQLException {
            Instant start = Instant.now();
            ResultSet result = pstmt.executeQuery(_sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = _sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, _sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, _sql, Duration.between(start, end));
            }
            return result;
        }

        public int executeUpdate(String _sql) throws SQLException {

            Instant start = Instant.now();
            int result = pstmt.executeUpdate(_sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = _sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, _sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, _sql, Duration.between(start, end));
            }
            return result;
        }
    }

    /**
     * An implementation of the CallableStatement interface that wraps an
     * underlying CallableStatement object and performs timings of the database
     * queries.
     */
    static class TimedCallableStatement extends CallableStatementWrapper {

        private final String sql;
        private final Type type;

        public TimedCallableStatement(CallableStatement cstmt, String sql) {
            super(cstmt);
            this.sql = sql;

            // determine the type of query
            String sqlL = sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                type = Type.insert;
            }
            else if (sqlL.startsWith("update")) {
                type = Type.update;
            }
            else if (sqlL.startsWith("delete")) {
                type = Type.delete;
            }
            else {
                type = Type.select;
            }
        }

        public boolean execute() throws SQLException
        {
            Instant start = Instant.now();
            boolean result = cstmt.execute();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        /*
         * This is one of the methods that we wish to time
         */
        public ResultSet executeQuery() throws SQLException
        {
            Instant start = Instant.now();
            ResultSet result = cstmt.executeQuery();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        /*
         * This is one of the methods that we wish to time
         */
        public int executeUpdate() throws SQLException
        {
            Instant start = Instant.now();
            int result = cstmt.executeUpdate();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        // The following methods are from the Statement class - the
        // SuperInterface of PreparedStatement
        // without these this class won't compile

        public boolean execute(String _sql) throws SQLException
        {
            Instant start = Instant.now();
            boolean result = cstmt.execute(_sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = _sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, _sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, _sql, Duration.between(start, end));
            }
            return result;
        }

        public int[] executeBatch() throws SQLException
        {
            Instant start = Instant.now();
            int[] result = cstmt.executeBatch();
            Instant end = Instant.now();

            switch (type) {
                case select:
                    addQuery(Type.select, sql, Duration.between(start, end));
                    break;
                case update:
                    addQuery(Type.update, sql, Duration.between(start, end));
                    break;
                case insert:
                    addQuery(Type.insert, sql, Duration.between(start, end));
                    break;
                case delete:
                    addQuery(Type.delete, sql, Duration.between(start, end));
                    break;
            }
            return result;
        }

        public ResultSet executeQuery(String _sql) throws SQLException
        {
            Instant start = Instant.now();
            ResultSet result = cstmt.executeQuery(_sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = _sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, _sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, _sql, Duration.between(start, end));
            }
            return result;
        }

        public int executeUpdate(String _sql) throws SQLException
        {
            Instant start = Instant.now();
            int result = cstmt.executeUpdate(_sql);
            Instant end = Instant.now();

            // determine the type of query
            String sqlL = _sql.toLowerCase().trim();

            if (sqlL.startsWith("insert")) {
                addQuery(Type.insert, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("update")) {
                addQuery(Type.update, _sql, Duration.between(start, end));
            }
            else if (sqlL.startsWith("delete")) {
                addQuery(Type.delete, _sql, Duration.between(start, end));
            }
            else {
                addQuery(Type.select, _sql, Duration.between(start, end));
            }
            return result;
        }
    }
}
