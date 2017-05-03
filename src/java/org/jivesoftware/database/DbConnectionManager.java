/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.MissingResourceException;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central manager of database connections. All methods are static so that they
 * can be easily accessed throughout the classes in the database package.<p>
 *
 * This class also provides a set of utility methods that abstract out
 * operations that may not work on all databases such as setting the max number
 * or rows that a query should return.
 *
 * @author Jive Software
 * @see ConnectionProvider
 */
public class DbConnectionManager {

    private static final Logger Log = LoggerFactory.getLogger(DbConnectionManager.class);

    private static ConnectionProvider connectionProvider;
    private static final Object providerLock = new Object();

    // True if connection profiling is turned on. Always false by default.
    private static boolean profilingEnabled = false;

    // True if the database support transactions.
    private static boolean transactionsSupported;
    // True if the database requires large text fields to be streamed.
    private static boolean streamTextRequired;
    /** True if the database supports the Statement.setMaxRows() method. */
    private static boolean maxRowsSupported;
    /** True if the database supports the rs.setFetchSize() method. */
    private static boolean fetchSizeSupported;
    // True if the database supports correlated subqueries.
    private static boolean subqueriesSupported;
    // True if the database supports scroll-insensitive results.
    private static boolean scrollResultsSupported;
    // True if the database supports batch updates.
    private static boolean batchUpdatesSupported;
    /** True if the database supports the Statement.setFetchSize()) method. */
    static boolean pstmt_fetchSizeSupported = true;

    private static final String SETTING_DATABASE_MAX_RETRIES = "database.maxRetries";
    private static final String SETTING_DATABASE_RETRY_DELAY = "database.retryDelay";

    private static DatabaseType databaseType = DatabaseType.unknown;

    private static SchemaManager schemaManager = new SchemaManager();

    /**
     * Ensures that the connection provider exists and is set
     */
    private static void ensureConnectionProvider() {
        if (connectionProvider != null) return;
        
        synchronized (providerLock) {
            if (connectionProvider != null) return;
            
            // Attempt to load the connection provider classname as a Jive property.
            String className = JiveGlobals.getXMLProperty("connectionProvider.className");
            if (className != null) {
                // Attempt to load the class.
                try {
                    Class conClass = ClassUtils.forName(className);
                    setConnectionProvider((ConnectionProvider)conClass.newInstance());
                } catch (Exception e) {
                    Log.warn("Failed to create the " +
                            "connection provider specified by connection" +
                            "Provider.className. Using the default pool.", e);
                    setConnectionProvider(new DefaultConnectionProvider());
                }
            } else {
                setConnectionProvider(new DefaultConnectionProvider());
            }
        }
    }

    /**
     * Returns a database connection from the currently active connection
     * provider. An exception will be thrown if no connection was found.
     * (auto commit is set to true).
     *
     * @return a connection.
     * @throws SQLException if a SQL exception occurs or no connection was found.
     */
    public static Connection getConnection() throws SQLException {
        ensureConnectionProvider();

        Integer currentRetryCount = 0;
        Integer maxRetries = JiveGlobals.getXMLProperty(SETTING_DATABASE_MAX_RETRIES, 10);
        Integer retryWait = JiveGlobals.getXMLProperty(SETTING_DATABASE_RETRY_DELAY, 250); // milliseconds
        SQLException lastException = null;
        do {
            try {
                Connection con = connectionProvider.getConnection();
                if (con != null) {
                    // Got one, lets hand it off.
                    // Usually profiling is not enabled. So we return a normal 
                    // connection unless profiling is enabled. If yes, wrap the
                    // connection with a profiled connection.
                    if (!profilingEnabled) {
                        return con;
                    } else {
                        return new ProfiledConnection(con); 
                    }
                }
            } catch (SQLException e) {
                // TODO distinguish recoverable from non-recoverable exceptions.
                lastException = e;
                Log.info("Unable to get a connection from the database pool " +
                        "(attempt " + currentRetryCount + " out of " + maxRetries + ").", e);
            }
            
            try {
                Thread.sleep(retryWait);
            } catch (Exception e) {
                // Ignored, the thread was interrupted while waiting, so no need to log either
            }
            currentRetryCount++;
        } while (currentRetryCount <= maxRetries);
        
        throw new SQLException("ConnectionManager.getConnection() " +
                "failed to obtain a connection after " + currentRetryCount + " retries. " +
                "The exception from the last attempt is as follows: " + lastException);
    }

    /**
     * Returns a Connection from the currently active connection provider that
     * is ready to participate in transactions (auto commit is set to false).
     *
     * @return a connection with transactions enabled.
     * @throws SQLException if a SQL exception occurs.
     */
    public static Connection getTransactionConnection() throws SQLException {
        Connection con = getConnection();
        if (isTransactionsSupported()) {
            con.setAutoCommit(false);
        }
        return con;
    }

    /**
     * Closes a PreparedStatement and Connection. However, it first rolls back the transaction or
     * commits it depending on the value of <code>abortTransaction</code>.
     *
     * @param pstmt the prepared statement to close.
     * @param con the connection to close.
     * @param abortTransaction true if the transaction should be rolled back.
     */
    public static void closeTransactionConnection(PreparedStatement pstmt, Connection con,
            boolean abortTransaction)
    {
        closeStatement(pstmt);
        closeTransactionConnection(con, abortTransaction);
    }

    /**
     * Closes a Connection. However, it first rolls back the transaction or
     * commits it depending on the value of <code>abortTransaction</code>.
     *
     * @param con the connection to close.
     * @param abortTransaction true if the transaction should be rolled back.
     */
    public static void closeTransactionConnection(Connection con, boolean abortTransaction) {
        // Rollback or commit the transaction
        if (isTransactionsSupported()) {
            try {
                if (abortTransaction) {
                    con.rollback();
                }
                else {
                    con.commit();
                }
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            // Reset the connection to auto-commit mode.
            try {
                con.setAutoCommit(true);              
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
        closeConnection(con);
    }

    /**
     * Closes a result set. This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      ResultSet rs = null;
     *      PreparedStatement pstmt = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from blah");
     *          rs = pstmt.executeQuery();
     *          ....
     *      }
     *      catch (SQLException sqle) {
     *          Log.error(sqle.getMessage(), sqle);
     *      }
     *      finally {
     *          ConnectionManager.closeResultSet(rs);
     *          ConnectionManager.closePreparedStatement(pstmt);
     *      }
     * } </pre>
     *
     * @param rs the result set to close.
     */
    public static void closeResultSet(ResultSet rs) {
        if (rs != null) {
            try {
                    rs.close();
                }
            catch (SQLException e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Closes a statement. This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      PreparedStatement pstmt = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from blah");
     *          ....
     *      }
     *      catch (SQLException sqle) {
     *          Log.error(sqle.getMessage(), sqle);
     *      }
     *      finally {
     *          ConnectionManager.closeStatement(pstmt);
     *      }
     * } </pre>
     *
     * @param stmt the statement.
     */
    public static void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            }       
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }
    
    /**
     * Closes a statement and a result set. This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      PreparedStatement pstmt = null;
     *      ResultSet rs = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from blah");
     *          rs = ...
     *          ....
     *      }
     *      catch (SQLException sqle) {
     *          Log.error(sqle.getMessage(), sqle);
     *      }
     *      finally {
     *          ConnectionManager.closeStatement(rs, pstmt);
     *      }
     * } </pre>
     *
     * @param stmt the statement.
     */
    public static void closeStatement(ResultSet rs, Statement stmt) {
        closeResultSet(rs);
        closeStatement(stmt);
    }
    /**
     * Closes a statement. This method should be called within the try section of
     * your database logic when you reuse a statement. It may throws an exception,
     * so don't place it in the finally section.<br>
     * Example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      PreparedStatement pstmt = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from dual");
     *          pstmt.executeUpdate();
     *          ...
     *          <b>ConnectionManager.fastcloseStmt(pstmt);</b>
     *          pstmt = con.prepareStatement("select * from blah");
     *          ...
     *      }
     *      ...
     * } </pre>
     *
     * @param pstmt the statement to close.
     */
    public static void fastcloseStmt(PreparedStatement pstmt) throws SQLException
    {
        pstmt.close();
    }
        
    /**
     * Closes a statement and a result set. This method should be called within the try section of
     * your database logic when you reuse a statement. It may throw an exception,
     * so don't place it in the finally section.<br>
     * Example:
     *
     * <pre>
     *  public void doSomething(Connection con) {
     *      PreparedStatement pstmt = null;
     *      try {
     *          pstmt = con.prepareStatement("select * from blah");
     *          rs = pstmt.executeQuery();
     *          ...
     *          ConnectionManager.fastcloseStmt(rs, pstmt);
     *          pstmt = con.prepareStatement("select * from blah");
     *          ...
     *      }
     *      ...
     * } </pre>
     *
     * @param pstmt the statement to close.
     */
    public static void fastcloseStmt(ResultSet rs, PreparedStatement pstmt) throws SQLException
    {
        rs.close();
        pstmt.close();
    }

    /**
     * Closes a result set, statement and database connection (returning the connection to
     * the connection pool). This method should be called within the finally section of
     * your database logic, as in the following example:
     *
     * <pre>
     * Connection con = null;
     * PrepatedStatment pstmt = null;
     * ResultSet rs = null;
     * try {
     *     con = ConnectionManager.getConnection();
     *     pstmt = con.prepareStatement("select * from blah");
     *     rs = psmt.executeQuery();
     *     ....
     * }
     * catch (SQLException sqle) {
     *     Log.error(sqle.getMessage(), sqle);
     * }
     * finally {
     *     ConnectionManager.closeConnection(rs, pstmt, con);
     * }</pre>
     *
     * @param rs the result set.
     * @param stmt the statement.
     * @param con the connection.
     */
    public static void closeConnection(ResultSet rs, Statement stmt, Connection con) {
        closeResultSet(rs);
        closeStatement(stmt);
        closeConnection(con);
    }

    /**
     * Closes a statement and database connection (returning the connection to
     * the connection pool). This method should be called within the finally section of
     * your database logic, as in the following example:
     * <pre>
     * Connection con = null;
     * PrepatedStatment pstmt = null;
     * try {
     *     con = ConnectionManager.getConnection();
     *     pstmt = con.prepareStatement("select * from blah");
     *     ....
     * }
     * catch (SQLException sqle) {
     *     Log.error(sqle.getMessage(), sqle);
     * }
     * finally {
     *     DbConnectionManager.closeConnection(pstmt, con);
     * }</pre>
     *
     * @param stmt the statement.
     * @param con the connection.
     */
    public static void closeConnection(Statement stmt, Connection con) {
        closeStatement(stmt);
        closeConnection(con);
    }

    /**
     * Closes a database connection (returning the connection to the connection pool). Any
     * statements associated with the connection should be closed before calling this method.
     * This method should be called within the finally section of your database logic, as
     * in the following example:
     * <pre>
     * Connection con = null;
     * try {
     *     con = ConnectionManager.getConnection();
     *     ....
     * }
     * catch (SQLException sqle) {
     *     Log.error(sqle.getMessage(), sqle);
     * }
     * finally {
     *     DbConnectionManager.closeConnection(con);
     * }</pre>
     *
     * @param con the connection.
     */
    public static void closeConnection(Connection con) {
        if (con != null) {
            try {
               con.close();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a scroll insensitive PreparedStatement if the JDBC driver supports it, or a normal
     * PreparedStatement otherwise.
     *
     * @param con the database connection.
     * @param sql the SQL to create the PreparedStatement with.
     * @return a PreparedStatement
     * @throws java.sql.SQLException if an error occurs.
     */
    public static PreparedStatement createScrollablePreparedStatement(Connection con, String sql)
            throws SQLException {
        if (isScrollResultsSupported()) {
            return con.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                    ResultSet.CONCUR_READ_ONLY);
        }
        else {
            return con.prepareStatement(sql);
        }
    }

    /**
     * Scrolls forward in a result set the specified number of rows. If the JDBC driver
     * supports the feature, the cursor will be moved directly. Otherwise, we scroll
     * through results one by one manually by calling <tt>rs.next()</tt>.
     *
     * @param rs the ResultSet object to scroll.
     * @param rowNumber the row number to scroll forward to.
     * @throws SQLException if an error occurs.
     */
    public static void scrollResultSet(ResultSet rs, int rowNumber) throws SQLException {
        // If the driver supports scrollable result sets, use that feature.
        if (isScrollResultsSupported()) {
            if (rowNumber > 0) {
                // We will attempt to do a relative fetch. This may fail in SQL Server if
                // <resultset-navigation-strategy> is set to absolute. It would need to be
                // set to looping to work correctly.
                // If so, manually scroll to the correct row.
                try {
                    rs.setFetchDirection(ResultSet.FETCH_FORWARD);
                    rs.relative(rowNumber);
                }
                catch (SQLException e) {
                    // TODO change "Error ..." to "Disabling ..."
                    Log.error("Error in JDBC method rs.relative(rowNumber).", e);
                    //Log.error("Disabling JDBC method rs.relative(rowNumber).", e);
                    //scrollResultsSupported = false;
                    for (int i = 0; i < rowNumber; i++) {
                        rs.next();
                    }
                }
            }
        }
        // Otherwise, manually scroll to the correct row.
        else {
            for (int i = 0; i < rowNumber; i++) {
                rs.next();
            }
        }
    }

    /**
     * Limits the number of the results in a result set (to startIndex + numResults).
     * Sets the fetch size depending on the features of the JDBC driver and make
     * sure that the size is not bigger than 500. 
     * @param pstmt the PreparedStatement
     * @param startIndex the first row with interesting data
     * @param numResults the number of interesting results
     */
    public static void limitRowsAndFetchSize(PreparedStatement pstmt, int startIndex, int numResults) {
        final int MAX_FETCHRESULTS = 500;
        final int maxRows = startIndex + numResults;
        setMaxRows(pstmt, maxRows);
        if (pstmt_fetchSizeSupported)
        {
            if (scrollResultsSupported) {
                setFetchSize(pstmt, Math.min(MAX_FETCHRESULTS, numResults));
            }
            else {
                setFetchSize(pstmt, Math.min(MAX_FETCHRESULTS, maxRows));            
            }
        }
    }
    
    /**
     * Sets the number of rows that the JDBC driver should buffer at a time.
     * The operation is automatically bypassed if Openfire knows that the
     * the JDBC driver or database doesn't support it.
     *
     * @param pstmt the PreparedStatement to set the fetch size for.
     * @param fetchSize the fetchSize.
     */
    public static void setFetchSize(PreparedStatement pstmt, int fetchSize) {
        if (pstmt_fetchSizeSupported) {
            try {
                pstmt.setFetchSize(fetchSize);
            }
            catch (Throwable t) {
                // Ignore. Exception may happen if the driver doesn't support
                // this operation and we didn't set meta-data correctly.
                // However, it is a good idea to update the meta-data so that
                // we don't have to incur the cost of catching an exception
                // each time.
                Log.error("Disabling JDBC method pstmt.setFetchSize(fetchSize).", t);
                pstmt_fetchSizeSupported = false;
            }
        }
    }

    /**
     * Returns the current connection provider. The only case in which this
     * method should be called is if more information about the current
     * connection provider is needed. Database connections should always be
     * obtained by calling the getConnection method of this class.
     *
     * @return the connection provider.
     */
    public static ConnectionProvider getConnectionProvider() {
        return connectionProvider;
    }

    /**
     * Sets the connection provider. The old provider (if it exists) is shut
     * down before the new one is started. A connection provider <b>should
     * not</b> be started before being passed to the connection manager
     * because the manager will call the start() method automatically.
     *
     * @param provider the ConnectionProvider that the manager should obtain
     *                 connections from.
     */
    public static void setConnectionProvider(ConnectionProvider provider) {
        synchronized (providerLock) {
            if (connectionProvider != null) {
                connectionProvider.destroy();
                connectionProvider = null;
            }
            connectionProvider = provider;
            connectionProvider.start();
            // Now, get a connection to determine meta data.
            Connection con = null;
            try {
                con = connectionProvider.getConnection();
                setMetaData(con);

                // Check to see if the database schema needs to be upgraded.
                schemaManager.checkOpenfireSchema(con);
            }
            catch (MissingResourceException mre) {
                Log.error(mre.getMessage());
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
            }
            finally {
                closeConnection(con);
            }
        }
        // Remember what connection provider we want to use for restarts.
        JiveGlobals.setXMLProperty("connectionProvider.className", provider.getClass().getName());
    }

    /**
     * Destroys the currennt connection provider. Future calls to
     * {@link #getConnectionProvider()} will return <tt>null</tt> until a new
     * ConnectionProvider is set, or one is automatically loaded by a call to
     * {@link #getConnection()}.
     */
    public static void destroyConnectionProvider() {
        synchronized (providerLock) {
            if (connectionProvider != null) {
                connectionProvider.destroy();
                connectionProvider = null;
            }
        }
    }

    /**
     * Retrives a large text column from a result set, automatically performing
     * streaming if the JDBC driver requires it. This is necessary because
     * different JDBC drivers have different capabilities and methods for
     * retrieving large text values.
     *
     * @param rs the ResultSet to retrieve the text field from.
     * @param columnIndex the column in the ResultSet of the text field.
     * @return the String value of the text field.
     * @throws SQLException if an SQL exception occurs.
     */
    public static String getLargeTextField(ResultSet rs, int columnIndex) throws SQLException {
        if (isStreamTextRequired()) {
            String value;
            try (Reader bodyReader = rs.getCharacterStream(columnIndex)) {
                if (bodyReader == null) {
                    return null;
                }
                char[] buf = new char[256];
                int len;
                StringWriter out = new StringWriter(256);
                while ((len = bodyReader.read(buf)) >= 0) {
                    out.write(buf, 0, len);
                }
                value = out.toString();
                out.close();
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
                throw new SQLException("Failed to load text field");
            }
            return value;
        }
        else {
            return rs.getString(columnIndex);
        }
    }

    /**
     * Sets a large text column in a result set, automatically performing
     * streaming if the JDBC driver requires it. This is necessary because
     * different JDBC drivers have different capabilities and methods for
     * setting large text values.
     *
     * @param pstmt the PreparedStatement to set the text field in.
     * @param parameterIndex the index corresponding to the text field.
     * @param value the String to set.
     * @throws SQLException if an SQL exception occurs.
     */
    public static void setLargeTextField(PreparedStatement pstmt, int parameterIndex,
                                         String value) throws SQLException {
        if (isStreamTextRequired()) {
            Reader bodyReader;
            try {
                bodyReader = new StringReader(value);
                pstmt.setCharacterStream(parameterIndex, bodyReader, value.length());
            }
            catch (Exception e) {
                Log.error(e.getMessage(), e);
                throw new SQLException("Failed to set text field.");
            }
            // Leave bodyReader open so that the db can read from it. It *should*
            // be garbage collected after it's done without needing to call close.
        }
        else {
            pstmt.setString(parameterIndex, value);
        }
    }

    /**
     * Sets the max number of rows that should be returned from executing a
     * statement. The operation is automatically bypassed if Jive knows that the
     * the JDBC driver or database doesn't support it.
     *
     * @param stmt    the Statement to set the max number of rows for.
     * @param maxRows the max number of rows to return.
     */
    public static void setMaxRows(Statement stmt, int maxRows) {
        if (isMaxRowsSupported()) {
            try {
                stmt.setMaxRows(maxRows);
            }
            catch (Throwable t) {
                // Ignore. Exception may happen if the driver doesn't support
                // this operation and we didn't set meta-data correctly.
                // However, it is a good idea to update the meta-data so that
                // we don't have to incur the cost of catching an exception
                // each time.
                Log.error("Disabling JDBC method stmt.setMaxRows(maxRows).", t);
                maxRowsSupported = false;
            }
        }
    }

    /**
     * Sets the number of rows that the JDBC driver should buffer at a time.
     * The operation is automatically bypassed if Jive knows that the
     * the JDBC driver or database doesn't support it.
     *
     * @param rs the ResultSet to set the fetch size for.
     * @param fetchSize the fetchSize.
     */
    public static void setFetchSize(ResultSet rs, int fetchSize) {
        if (isFetchSizeSupported()) {
            try {
                rs.setFetchSize(fetchSize);
            }
            catch (Throwable t) {
                // Ignore. Exception may happen if the driver doesn't support
                // this operation and we didn't set meta-data correctly.
                // However, it is a good idea to update the meta-data so that
                // we don't have to incur the cost of catching an exception
                // each time.
                Log.error("Disabling JDBC method rs.setFetchSize(fetchSize).", t);
                fetchSizeSupported = false;
            }
        }
    }

    /**
     * Returns a SchemaManager instance, which can be used to manage the database
     * schema information for Openfire and plugins.
     *
     * @return a SchemaManager instance.
     */
    public static SchemaManager getSchemaManager() {
        return schemaManager;
    }

    /**
     * Uses a connection from the database to set meta data information about
     * what different JDBC drivers and databases support.
     *
     * @param con the connection.
     * @throws SQLException if an SQL exception occurs.
     */
    private static void setMetaData(Connection con) throws SQLException {
        DatabaseMetaData metaData = con.getMetaData();
        // Supports transactions?
        transactionsSupported = metaData.supportsTransactions();
        // Supports subqueries?
        subqueriesSupported = metaData.supportsCorrelatedSubqueries();
        // Supports scroll insensitive result sets? Try/catch block is a
        // workaround for DB2 JDBC driver, which throws an exception on
        // the method call.
        try {
            scrollResultsSupported = metaData.supportsResultSetType(
                    ResultSet.TYPE_SCROLL_INSENSITIVE);
        }
        catch (Exception e) {
            scrollResultsSupported = false;
        }
        // Supports batch updates
        batchUpdatesSupported = metaData.supportsBatchUpdates();

        // Set defaults for other meta properties
        streamTextRequired = false;
        maxRowsSupported = true;
        fetchSizeSupported = true;

        // Get the database name so that we can perform meta data settings.
        String dbName = metaData.getDatabaseProductName().toLowerCase();
        String driverName = metaData.getDriverName().toLowerCase();

        // Oracle properties.
        if (dbName.indexOf("oracle") != -1) {
            databaseType = DatabaseType.oracle;
            streamTextRequired = true;
            scrollResultsSupported = false; /* TODO comment and test this, it should be supported since 10g */
            // The i-net AUGURO JDBC driver
            if (driverName.indexOf("auguro") != -1) {
                streamTextRequired = false;
                fetchSizeSupported = true;
                maxRowsSupported = false;
            }
        }
        // Postgres properties
        else if (dbName.indexOf("postgres") != -1) {
            databaseType = DatabaseType.postgresql;
            // Postgres blows, so disable scrolling result sets.
            scrollResultsSupported = false;
            fetchSizeSupported = false;
        }
        // Interbase properties
        else if (dbName.indexOf("interbase") != -1) {
            databaseType = DatabaseType.interbase;
            fetchSizeSupported = false;
            maxRowsSupported = false;
        }
        // SQLServer
        else if (dbName.indexOf("sql server") != -1) {
            databaseType = DatabaseType.sqlserver;
            // JDBC driver i-net UNA properties
            if (driverName.indexOf("una") != -1) {
                fetchSizeSupported = true;
                maxRowsSupported = false;
            }
        }
        // MySQL properties
        else if (dbName.indexOf("mysql") != -1) {
            databaseType = DatabaseType.mysql;
            transactionsSupported = false; /* TODO comment and test this, it should be supported since 5.0 */
        }
        // HSQL properties
        else if (dbName.indexOf("hsql") != -1) {
            databaseType = DatabaseType.hsqldb;
            // scrollResultsSupported = false; /* comment and test this, it should be supported since 1.7.2 */
        }
        // DB2 properties.
        else if (dbName.indexOf("db2") != 1) {
            databaseType = DatabaseType.db2;
        }
    }

    /**
     * Returns the database type. The possible types are constants of the
     * DatabaseType class. Any database that doesn't have its own constant
     * falls into the "Other" category.
     *
     * @return the database type.
     */
    public static DatabaseType getDatabaseType() {
        return databaseType;
    }

    /**
     * Returns true if connection profiling is turned on. You can collect
     * profiling statistics by using the static methods of the ProfiledConnection
     * class.
     *
     * @return true if connection profiling is enabled.
     */
    public static boolean isProfilingEnabled() {
        return profilingEnabled;
    }

    /**
     * Turns connection profiling on or off. You can collect profiling
     * statistics by using the static methods of the ProfiledConnection
     * class.
     *
     * @param enable true to enable profiling; false to disable.
     */
    public static void setProfilingEnabled(boolean enable) {
        // If enabling profiling, call the start method on ProfiledConnection
        if (!profilingEnabled && enable) {
            ProfiledConnection.start();
        }
        // Otherwise, if turning off, call stop method.
        else if (profilingEnabled && !enable) {
            ProfiledConnection.stop();
        }
        profilingEnabled = enable;
    }

    public static boolean isTransactionsSupported() {
        return transactionsSupported;
    }

    public static boolean isStreamTextRequired() {
        return streamTextRequired;
    }

    public static boolean isMaxRowsSupported() {
        return maxRowsSupported;
    }

    public static boolean isFetchSizeSupported() {
        return fetchSizeSupported;
    }
    
    public static boolean isPstmtFetchSizeSupported() {
        return pstmt_fetchSizeSupported;
    }

    public static boolean isSubqueriesSupported() {
        return subqueriesSupported;
    }

    public static boolean isScrollResultsSupported() {
        return scrollResultsSupported;
    }

    public static boolean isBatchUpdatesSupported() {
        return batchUpdatesSupported;
    }

    public static boolean isEmbeddedDB() {
        return connectionProvider != null && connectionProvider instanceof EmbeddedConnectionProvider;
    }

    public static String getTestSQL(String driver) {
        if (driver == null) {
            return "select 1";
        }
        else if (driver.contains("db2")) {
            return "select 1 from sysibm.sysdummy1";
        }
        else if (driver.contains("oracle")) {
            return "select 1 from dual";
        }
        else {
            return "select 1";
        }
    }

    /**
     * A class that identifies the type of the database that Jive is connected
     * to. In most cases, we don't want to make any database specific calls
     * and have no need to know the type of database we're using. However,
     * there are certain cases where it's critical to know the database for
     * performance reasons.
     */
    @SuppressWarnings({"UnnecessarySemicolon"}) // Support for QDox parsing
    public static enum DatabaseType {

        oracle,

        postgresql,

        mysql,

        hsqldb,

        db2,

        sqlserver,

        interbase,

        unknown;
    }
}