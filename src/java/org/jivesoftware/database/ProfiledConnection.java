/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import java.sql.*;
import java.util.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Wraps a Connection object and collects statistics about the database queries
 * that are performed.<p/>
 * 
 * Statistics of the profiled Connections can be obtained from the static
 * methods of this class. Instances of this class are the actual wrappers that
 * perform profiling.
 *
 * @author Jive Software
 */
public class ProfiledConnection extends AbstractConnection {

    public enum Type {

        /**
         * SELECT database queries.
         */
        select,

        /**
         * UPDATE database queries.
         */
        update,

        /**
         * INSERT database queries.
         */
        insert,

        /**
         * DLETE database queries.
         */
        delete
    }

    private static long startInsertTime = 0;
    private static long startUpdateTime = 0;
    private static long startSelectTime = 0;
    private static long startDeleteTime = 0;

    private static long endInsertTime = 0;
    private static long endUpdateTime = 0;
    private static long endSelectTime = 0;
    private static long endDeleteTime = 0;

    private static long insertCount = 0;
    private static long updateCount = 0;
    private static long selectCount = 0;
    private static long deleteCount = 0;

    private static long totalInsertTime = 0;
    private static long totalUpdateTime = 0;
    private static long totalSelectTime = 0;
    private static long totalDeleteTime = 0;

    private static Map<String, ProfiledConnectionEntry> insertQueries = 
            new Hashtable<String, ProfiledConnectionEntry>();
    private static Map<String, ProfiledConnectionEntry> updateQueries =
            new Hashtable<String, ProfiledConnectionEntry>();
    private static Map<String, ProfiledConnectionEntry> selectQueries =
            new Hashtable<String, ProfiledConnectionEntry>();
    private static Map<String, ProfiledConnectionEntry> deleteQueries =
            new Hashtable<String, ProfiledConnectionEntry>();

    /**
     * Start profiling.
     */
    public static void start() {
        long now = System.currentTimeMillis();
        startInsertTime = startUpdateTime = startSelectTime = startDeleteTime = now;
    }

    /**
     * Stop profiling.
     */
    public static void stop() {
        endInsertTime = endUpdateTime = endSelectTime = endDeleteTime = 0;
    }

    /**
     * Returns the total number database queries of a particular type performed.
     * Valid types are {@link ProfiledConnection.Type#select},
     * {@link ProfiledConnection.Type#update},  {@link ProfiledConnection.Type#insert},
     * and {@link ProfiledConnection.Type#delete}
     *
     * @param type the type of query to get the count for.
     * @return the number queries of type <tt>type</tt> performed.
     */
    public static long getQueryCount(Type type) {
        switch (type) {
            case select:
                return selectCount;
            case update:
                return updateCount;
            case insert:
                return insertCount;
            case delete:
                return deleteCount;
            default:
                throw new IllegalArgumentException("Invalid type");
        }
    }

    /**
     * Adds a query to the list of those that have been run.
     *
     * @param type the query type.
     * @param sql the insert sql string.
     * @param time the length of time the query took in milliseconds
     */
    public static void addQuery(Type type, String sql, long time) {
        // Do nothing if we didn't receive a sql statement
        if (sql == null || sql.equals("")) {
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
                totalSelectTime += time;
                entry = selectQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    selectQueries.put(sql, entry);
                }
                break;
            case update:
                updateCount++;
                totalUpdateTime += time;
                entry = updateQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    updateQueries.put(sql, entry);
                }
                break;
            case insert:
                insertCount++;
                totalInsertTime += time;
                entry = insertQueries.get(sql);
                if (entry == null) {
                    entry = new ProfiledConnectionEntry(sql);
                    insertQueries.put(sql, entry);
                }
                break;
            case delete:
                deleteCount++;
                totalDeleteTime += time;
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
        entry.totalTime += time;
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
        long count, start, end;

        switch (type) {
            case select:
                count = selectCount;
                start = startSelectTime;
                end = endSelectTime;
                break;
            case update:
                count = updateCount;
                start = startUpdateTime;
                end = endUpdateTime;
                break;
            case insert:
                count = insertCount;
                start = startInsertTime;
                end = endInsertTime;
                break;
            case delete:
                count = deleteCount;
                start = startDeleteTime;
                end = endDeleteTime;
                break;
            default:
                throw new IllegalArgumentException("Invalid type");
        }
        // if no queries yet, return 0;
        if (count == 0) {
            return 0;
        }
        // If the profiling hasn't been stopped yet, we want to give
        // profiling values up to the current time instead.
        if (end == 0) {
            end = System.currentTimeMillis();
        }
        // Compute the number of seconds
        double time = (end - start) / 1000.0;
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
    public static double getAverageQueryTime(Type type) {
        long time, count;

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
            return time / (double)count;
        }
        else {
            return 0.0;
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
    public static long getTotalQueryTime(Type type) {
        switch (type) {
            case select:
                return totalSelectTime;
            case update:
                return totalUpdateTime;
            case insert:
                return totalInsertTime;
            case delete:
                return totalDeleteTime;
            default:
                throw new IllegalArgumentException("Invalid type");
        }
    }

    /**
     * Returns an array of sorted queries (as ProfiledConnectionEntry objects) by type
     *
     * @param type the type of query to check
     * @param sortByTime sort the resulting list by Time if true,
     *      otherwise sort by count if false (default)
     * @return an array of ProfiledConnectionEntry objects
     */
    public static ProfiledConnectionEntry[] getSortedQueries(Type type, boolean sortByTime) {
        Map<String, ProfiledConnectionEntry> queries;

        switch (type) {
            case select:
                queries = selectQueries;
                break;
            case update:
                queries = updateQueries;
                break;
            case insert:
                queries = insertQueries;
                break;
            case delete:
                queries = deleteQueries;
                break;
            default:
                throw new IllegalArgumentException("Invalid type");
        }

        // No queries, return null
        if (queries.isEmpty()) {
            return null;
        }

        ProfiledConnectionEntry [] result = queries.values().toArray(
                new ProfiledConnectionEntry[queries.size()]);

        quickSort(result, sortByTime, 0, result.length - 1);
        return result;
    }

    /**
     * Reset all statistics.
     */
    public static void resetStatistics() {
        startInsertTime = startUpdateTime = startSelectTime = startDeleteTime = 0;
        endInsertTime = endUpdateTime = endSelectTime = endDeleteTime = 0;
        insertCount = updateCount = selectCount = deleteCount = 0;
        totalInsertTime = totalUpdateTime = totalSelectTime = totalDeleteTime = 0;

        insertQueries.clear();
        updateQueries.clear();
        selectQueries.clear();
        deleteQueries.clear();
    }

    /**
     * @param entries entries
     * @param sortByTime sort by time if true, otherwise sort by count
     * @param first first index to sort on. Normally 0
     * @param last last index to sort on. Normally length -1
     */
    private static void quickSort(ProfiledConnectionEntry[] entries, boolean sortByTime, int first, int last) {

        // do nothing if array contains fewer than two elements
        if (first >= last || entries.length < 2) {
            return;
        }

        swap(entries, first, (first + last) / 2);

        int index = first;
        for (int i = first + 1; i <= last; i++) {
            if (sortByTime && ((entries[first].totalTime / entries[first].count) < (entries[i].totalTime / entries[i].count))) {
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

        if (_sql.indexOf("=") == -1) {
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
                case '-':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;

                        }
                        break;
                    }
                case '+':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '0':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '1':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '2':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '3':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '4':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '5':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '6':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '7':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '8':
                    {
                        if (afterEquals && !inValue) {
                            startValue = x;
                            inValue = true;
                        }
                        break;
                    }
                case '9':
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
     * Creates a new ProfiledConnection that wraps the specified connection.
     *
     * @param connection the Connection to wrap and collect stats for.
     */
    public ProfiledConnection(Connection connection) {
        super(connection);
    }

    public void close() throws SQLException {
        // Close underlying connection.
        if (connection != null) {
            connection.close();
        }
    }

    public Statement createStatement() throws SQLException {
        // Returned a TimedStatement so that we can do db timings.
        return (Statement)TimedStatement.newInstance(connection.createStatement());
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        // Returned a TimedPreparedStatement so that we can do db timings.
        return (PreparedStatement)TimedPreparedStatement.newInstance(
                connection.prepareStatement(sql), sql);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency)
            throws SQLException
     {
         return (Statement)TimedStatement.newInstance(connection.createStatement(resultSetType,
                resultSetConcurrency));
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        return (PreparedStatement)TimedPreparedStatement.newInstance(
                connection.prepareStatement(sql, resultSetType, resultSetConcurrency), sql);
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return (CallableStatement)TimedCallableStatement.newInstance(
                connection.prepareCall(sql), sql);
    }

    public CallableStatement prepareCall(String sql, int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        return (CallableStatement)TimedCallableStatement.newInstance(
                connection.prepareCall(sql, resultSetType, resultSetConcurrency), sql);
    }

    /**
     * A dynamic proxy for the Statement interface that times usage. The class
     * does not handle batch queries but should generally work otherwise.
     */
    public static class TimedStatement implements InvocationHandler {

        // Preloaded Method objects that we override.
        private static Method execute;
        private static Method executeQuery;
        private static Method executeUpdate;

        static {
            try {
                execute = Statement.class.getMethod("execute", String.class);
                executeQuery = Statement.class.getMethod("executeQuery", String.class);
                executeUpdate = Statement.class.getMethod("executeUpdate", String.class);
            }
            catch (NoSuchMethodException e) {
                throw new NoSuchMethodError(e.getMessage());
            }
        }

        public static Object newInstance(Statement stmt) {
            return java.lang.reflect.Proxy.newProxyInstance(
                stmt.getClass().getClassLoader(),
                stmt.getClass().getInterfaces(),
                new TimedStatement(stmt));
        }

        private Statement stmt;

        /**
         * Creates a new TimedStatement dynamic proxy.
         *
         * @param stmt the Statement to proxy.
         */
        private TimedStatement(Statement stmt) {
            this.stmt = stmt;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.equals(execute) || method.equals(executeQuery) || method.equals(executeUpdate))
            {
                long t1 = System.currentTimeMillis();
                Object result = method.invoke(stmt, args);
                long t2 = System.currentTimeMillis();

                String sql = ((String)args[0]).toLowerCase().trim();

                if (sql.startsWith("insert")) {
                    addQuery(Type.insert, sql, t2 - t1);
                }
                else if (sql.startsWith("update")) {
                    addQuery(Type.update, sql, t2 - t1);
                }
                else if (sql.startsWith("delete")) {
                    addQuery(Type.delete, sql, t2 - t1);
                }
                else {
                    addQuery(Type.select, sql, t2 - t1);
                }

                return result;
            }

            // Invoke the method normally if all else fails.
            return method.invoke(stmt, args);
        }
    }

    /**
     * A dynamic proxy for the PreparedStatement interface that times usage.
     */
    public static class TimedPreparedStatement implements InvocationHandler {

        // Preloaded Method objects that we override.
        private static Method execute;
        private static Method executeWithParam;
        private static Method executeQuery;
        private static Method executeQueryWithParam;
        private static Method executeUpdate;
        private static Method executeUpdateWithParam;
        private static Method executeBatch;

        static {
            try {
                execute = PreparedStatement.class.getMethod("execute");
                executeWithParam = PreparedStatement.class.getMethod("execute", String.class);
                executeQuery = PreparedStatement.class.getMethod("executeQuery");
                executeQueryWithParam = PreparedStatement.class.getMethod("executeQuery", String.class);
                executeUpdate = PreparedStatement.class.getMethod("executeUpdate");
                executeUpdateWithParam = PreparedStatement.class.getMethod("executeUpdate", String.class);
                executeBatch = PreparedStatement.class.getMethod("executeBatch");
            }
            catch (NoSuchMethodException e) {
                throw new NoSuchMethodError(e.getMessage());
            }
        }

        public static Object newInstance(PreparedStatement pstmt, String sql) {
            return java.lang.reflect.Proxy.newProxyInstance(
                pstmt.getClass().getClassLoader(),
                pstmt.getClass().getInterfaces(),
                new TimedPreparedStatement(pstmt, sql));
        }

        private PreparedStatement pstmt;
        private String sql;
        private Type type = Type.select;

        /**
         * Creates a new TimedStatement dynamic proxy.
         *
         * @param pstmt the PreparedStatement to proxy.
         * @param sql the SQL.
         */
        private TimedPreparedStatement(PreparedStatement pstmt, String sql) {
            this.pstmt = pstmt;
            this.sql = sql;

            // Determine the type of query
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

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.equals(execute) || method.equals(executeQuery) ||
                    method.equals(executeUpdate) || method.equals(executeBatch))
            {
                long t1 = System.currentTimeMillis();
                Object result = method.invoke(pstmt, args);
                long t2 = System.currentTimeMillis();

                switch (type) {
                    case select:
                        addQuery(Type.select, sql, t2 - t1);
                        break;
                    case update:
                        addQuery(Type.update, sql, t2 - t1);
                        break;
                    case insert:
                        addQuery(Type.insert, sql, t2 - t1);
                        break;
                    case delete:
                        addQuery(Type.delete, sql, t2 - t1);
                        break;
                }

                return result;
            }
            else if (method.equals(executeWithParam) || method.equals(executeQueryWithParam) ||
                    method.equals(executeUpdateWithParam))
            {
                long t1 = System.currentTimeMillis();
                Object result = method.invoke(pstmt, args);
                long t2 = System.currentTimeMillis();

                String sql = ((String)args[0]).toLowerCase().trim();

                if (sql.startsWith("insert")) {
                    addQuery(Type.insert, sql, t2 - t1);
                }
                else if (sql.startsWith("update")) {
                    addQuery(Type.update, sql, t2 - t1);
                }
                else if (sql.startsWith("delete")) {
                    addQuery(Type.delete, sql, t2 - t1);
                }
                else {
                    addQuery(Type.select, sql, t2 - t1);
                }

                return result;
            }

            // Invoke the method normally if all else fails.
            return method.invoke(pstmt, args);
        }
    }

    /**
     * A dynamic proxy for the CallableStatement interface that times usage.
     */
    public static class TimedCallableStatement implements InvocationHandler {

        // Preloaded Method objects that we override.
        private static Method execute;
        private static Method executeWithParam;
        private static Method executeQuery;
        private static Method executeQueryWithParam;
        private static Method executeUpdate;
        private static Method executeUpdateWithParam;
        private static Method executeBatch;

        static {
            try {
                execute = CallableStatement.class.getMethod("execute");
                executeWithParam = CallableStatement.class.getMethod("execute", String.class);
                executeQuery = CallableStatement.class.getMethod("executeQuery");
                executeQueryWithParam = CallableStatement.class.getMethod("executeQuery", String.class);
                executeUpdate = CallableStatement.class.getMethod("executeUpdate");
                executeUpdateWithParam = CallableStatement.class.getMethod("executeUpdate", String.class);
                executeBatch = CallableStatement.class.getMethod("executeBatch");
            }
            catch (NoSuchMethodException e) {
                throw new NoSuchMethodError(e.getMessage());
            }
        }

        public static Object newInstance(CallableStatement cstmt, String sql) {
            return java.lang.reflect.Proxy.newProxyInstance(
                cstmt.getClass().getClassLoader(),
                cstmt.getClass().getInterfaces(),
                new TimedCallableStatement(cstmt, sql));
        }

        private CallableStatement cstmt;
        private String sql;
        private Type type = Type.select;

        /**
         * Creates a new TimedStatement dynamic proxy.
         *
         * @param cstmt the CallableStatement to proxy.
         * @param sql the SQL.
         */
        private TimedCallableStatement(CallableStatement cstmt, String sql) {
            this.cstmt = cstmt;
            this.sql = sql;

            // Determine the type of query
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

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.equals(execute) || method.equals(executeQuery) ||
                    method.equals(executeUpdate) || method.equals(executeBatch))
            {
                long t1 = System.currentTimeMillis();
                Object result = method.invoke(cstmt, args);
                long t2 = System.currentTimeMillis();

                switch (type) {
                    case select:
                        addQuery(Type.select, sql, t2 - t1);
                        break;
                    case update:
                        addQuery(Type.update, sql, t2 - t1);
                        break;
                    case insert:
                        addQuery(Type.insert, sql, t2 - t1);
                        break;
                    case delete:
                        addQuery(Type.delete, sql, t2 - t1);
                        break;
                }

                return result;
            }
            else if (method.equals(executeWithParam) || method.equals(executeQueryWithParam) ||
                    method.equals(executeUpdateWithParam))
            {
                long t1 = System.currentTimeMillis();
                Object result = method.invoke(cstmt, args);
                long t2 = System.currentTimeMillis();

                String sql = ((String)args[0]).toLowerCase().trim();

                if (sql.startsWith("insert")) {
                    addQuery(Type.insert, sql, t2 - t1);
                }
                else if (sql.startsWith("update")) {
                    addQuery(Type.update, sql, t2 - t1);
                }
                else if (sql.startsWith("delete")) {
                    addQuery(Type.delete, sql, t2 - t1);
                }
                else {
                    addQuery(Type.select, sql, t2 - t1);
                }

                return result;
            }

            // Invoke the method normally if all else fails.
            return method.invoke(cstmt, args);
        }
    }
}