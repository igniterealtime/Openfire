/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.database;

import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Database connection pool.
 *
 * @author Jive Software
 */
public class ConnectionPool implements Runnable {

    private String driver;
    private String serverURL;
    private String username;
    private String password;
    private int minCon;
    private int maxCon;
    private int conTimeout;
    private boolean mysqlUseUnicode;

    private Thread houseKeeper;
    private boolean shutdownStarted = false;

    private int conCount = 0;
    private int waitingForCon = 0;
    private Connection[] cons;
    private ConnectionWrapper[] wrappers;
    private Object waitLock = new Object();
    private Object conCountLock = new Object();

    private AtomicInteger used = new AtomicInteger(0);

    public ConnectionPool(String driver, String serverURL, String username,
                          String password, int minCon, int maxCon,
                          double conTimeout, boolean mysqlUseUnicode) throws IOException {
        this.driver = driver;
        this.serverURL = serverURL;
        this.username = username;
        this.password = password;
        this.minCon = minCon;
        this.maxCon = maxCon;
        // Setting the timeout to 3 hours
        this.conTimeout = (int)(conTimeout * 1000 * 60 * 60 * 3); // convert to milliseconds
        this.mysqlUseUnicode = mysqlUseUnicode;

        if (driver == null) {
            Log.error("JDBC driver value is null.");
        }
        try {
            ClassUtils.forName(driver);
            DriverManager.getDriver(serverURL);
        }
        catch (ClassNotFoundException e) {
            Log.error("Could not load JDBC driver class: " + driver);
        }
        catch (SQLException e) {
            Log.error("Error starting connection pool.", e);
        }

        // Setup pool, open minimum number of connections
        wrappers = new ConnectionWrapper[maxCon];
        cons = new Connection[maxCon];

        boolean success = false;
        int maxTry = 3;

        for (int i = 0; i < maxTry; i++) {
            try {
                for (int j = 0; j < minCon; j++) {
                    createCon(j);
                    conCount++;
                }

                success = true;
                break;
            }
            catch (SQLException e) {
                // close any open connections
                for (int j = 0; j < minCon; j++) {
                    if (cons[j] != null) {
                        try {
                            cons[j].close();
                            cons[j] = null;
                            wrappers[j] = null;
                            conCount--;
                        }
                        catch (SQLException e1) { /* ignore */
                        }
                    }
                }

                // let admin know that there was a problem
                Log.error("Failed to create new connections on startup. " +
                        "Attempt " + i + " of " + maxTry, e);

                try {
                    Thread.sleep(10000);
                }
                catch (InterruptedException e1) { /* ignore */
                }
            }
        }

        if (!success) {
            throw new IOException();
        }

        // Start the background housekeeping thread
        houseKeeper = new Thread(this);
        houseKeeper.setDaemon(true);
        houseKeeper.start();
    }

    public Connection getConnection() throws SQLException {
        // if we're shutting down, don't create any connections
        if (shutdownStarted) {
            return null;
        }

        // Check to see if there are any connections available. If not, then enter wait-based
        // retry loop
        ConnectionWrapper wrapper = getCon();

        if (wrapper != null) {
            synchronized (wrapper) {
                wrapper.checkedout = true;
                wrapper.lockTime = System.currentTimeMillis();
            }
            return wrapper;
        }
        else {
            synchronized (waitLock) {
                try {
                    waitingForCon++;
                    while (true) {
                        wrapper = getCon();

                        if (wrapper != null) {
                            --waitingForCon;
                            synchronized (wrapper) {
                                wrapper.checkedout = true;
                                wrapper.lockTime = System.currentTimeMillis();
                            }
                            return wrapper;
                        }
                        else {
                            waitLock.wait();
                        }
                    }
                }
                catch (InterruptedException ex) {
                    --waitingForCon;
                    waitLock.notifyAll();

                    throw new SQLException("Interrupted while waiting for connection to " +
                            "become available.");
                }
            }
        }
    }

    public void freeConnection() {
        used.decrementAndGet();
        synchronized (waitLock) {
            if (waitingForCon > 0) {
                waitLock.notifyAll();
            }
        }
    }

    public void destroy() throws SQLException {
        // set shutdown flag
        shutdownStarted = true;

        // shut down the background housekeeping thread
        houseKeeper.interrupt();

        // wait 1/2 second for housekeeper to die
        try {
            houseKeeper.join(500);
        }
        catch (InterruptedException e) { /* ignore */
        }

        // check to see if there's any currently open connections to close
        for (int i = 0; i < conCount; i++) {
            ConnectionWrapper wrapper = wrappers[i];

            // null means that the connection hasn't been initialized, which will only occur
            // if the current index is greater than the current connection count
            if (wrapper == null) {
                break;
            }

            // if it's currently checked out, wait 1/2 second then close it anyways
            if (wrapper.checkedout) {
                try {
                    Thread.sleep(500);
                }
                catch (InterruptedException e) {/* ignore */
                }

                if (wrapper.checkedout) {
                    Log.info("Forcefully closing connection " + i);
                }
            }

            cons[i].close();
            cons[i] = null;
            wrappers[i] = null;
        }
    }

    public int getSize() {
        return conCount;
    }

    /**
     * Housekeeping thread. This thread runs every 30 seconds and checks connections for the
     * following conditions:<BR>
     * <p/>
     * <ul>
     * <li>Connection has been open too long - it'll be closed and another connection created.
     * <li>Connection hasn't been used for 30 seconds and the number of open connections is
     * greater than the minimum number of connections. The connection will be closed. This
     * is done so that the pool can shrink back to the minimum number of connections if the
     * pool isn't being used extensively.
     * <li>Unable to create a statement with the connection - it'll be reset.
     * </ul>
     */
    public void run() {
        while (true) {
            // print warnings on connections
            for (int i = 0; i < maxCon; i++) {
                if (cons[i] == null) {
                    continue;
                }

                try {
                    SQLWarning warning = cons[i].getWarnings();
                    if (warning != null) {
                        Log.warn("Connection " + i + " had warnings: " + warning);
                        cons[i].clearWarnings();
                    }
                }
                catch (SQLException e) {
                    Log.warn("Unable to get warning for connection: ", e);
                }
            }

            int lastOpen = -1;

            // go over every connection, check it's health
            for (int i = maxCon - 1; i >= 0; i--) {
                if (wrappers[i] == null) {
                    continue;
                }

                try {
                    long time = System.currentTimeMillis();

                    synchronized (wrappers[i]) {
                        if (wrappers[i].checkedout) {
                            if (lastOpen < i) {
                                lastOpen = i;
                            }


                            // if the jive property "database.defaultProvider.checkOpenConnections"
                            // is true check open connections to make sure they haven't been open
                            // for more than XX seconds (600 by default)
                            if ("true".equals(JiveGlobals.getXMLProperty("database.defaultProvider.checkOpenConnections"))
                                    && !wrappers[i].hasLoggedException)
                            {
                                int timeout = 600;
                                try { timeout = Integer.parseInt(JiveGlobals.getXMLProperty("database.defaultProvider.openConnectionTimeLimit")); }
                                catch (Exception e) { /* ignore */ }

                                if (time - wrappers[i].lockTime > timeout * 1000) {
                                    wrappers[i].hasLoggedException = true;
                                    Log.warn("Connection has been held open for too long: ",
                                            wrappers[i].exception);
                                }
                            }

                            continue;
                        }
                        wrappers[i].checkedout = true;
                    }

                    // test health of connection
                    Statement stmt = null;
                    try {
                        stmt = cons[i].createStatement();
                    }
                    finally {
                        if (stmt != null) {
                            stmt.close();
                        }
                    }

                    // Can never tell
                    if (cons[i].isClosed()) {
                        throw new SQLException();
                    }

                    // check the age of the connection
                    if (time - wrappers[i].createTime > conTimeout) {
                        throw new SQLException();
                    }

                    // check to see if it's the last connection and if it's been idle for
                    // more than 60 secounds
                    if ((time - wrappers[i].checkinTime > 60 * 1000) && i > minCon &&
                            lastOpen <= i) {
                        synchronized (conCountLock) {
                            cons[i].close();
                            wrappers[i] = null;
                            cons[i] = null;
                            conCount--;
                        }
                    }

                    // Flag the last open connection
                    lastOpen = i;

                    // Unlock the connection
                    if (wrappers[i] != null) {
                        wrappers[i].checkedout = false;
                    }

                }
                catch (SQLException e) {
                    try {
                        synchronized (conCountLock) {
                            cons[i].close();
                            wrappers[i] = createCon(i);

                            // unlock the connection
                            wrappers[i].checkedout = false;
                        }
                    }
                    catch (SQLException sqle) {
                        Log.warn("Failed to reopen connection", sqle);

                        synchronized (conCountLock) {
                            wrappers[i] = null;
                            cons[i] = null;
                            conCount--;
                        }
                    }
                }
            }

            try {
                Thread.sleep(30 * 1000);
            }
            catch (InterruptedException e) {
                return;
            }
        }
    }

    private synchronized ConnectionWrapper getCon() throws SQLException {
        // check to see if there's a connection already available
        for (int i = 0; i < conCount; i++) {
            ConnectionWrapper wrapper = wrappers[i];

            // null means that the connection hasn't been initialized, which will only occur
            // if the current index is greater than the current connection count
            if (wrapper == null) {
                break;
            }

            synchronized (wrapper) {
                if (!wrapper.checkedout) {
                    wrapper.setConnection(cons[i]);
                    wrapper.checkedout = true;
                    wrapper.lockTime = System.currentTimeMillis();
                    if ("true".equals(JiveGlobals.getXMLProperty("database.defaultProvider.checkOpenConnections"))) {
                        wrapper.exception = new Exception();
                        wrapper.hasLoggedException = false;
                    }
                    used.incrementAndGet();

                    return wrapper;
                }
            }
        }

        // won't create more than maxConnections
        synchronized (conCountLock) {
            if (conCount >= maxCon) {
                return null;
            }

            ConnectionWrapper con = createCon(conCount);
            conCount++;
            used.incrementAndGet();
            return con;
        }
    }

    /**
     * Create a connection, wrap it and add it to the array of open wrappers
     */
    private ConnectionWrapper createCon(int index) throws SQLException {
        try {
            Connection con = null;
            ClassUtils.forName(driver);

            if (mysqlUseUnicode) {
                Properties props = new Properties();
                props.put("characterEncoding", "UTF-8");
                props.put("useUnicode", "true");
                if (username != null) {
                    props.put("user", username);
                }
                if (password != null) {
                    props.put("password", password);
                }
                con = DriverManager.getConnection(serverURL, props);
            }
            else {
                con = DriverManager.getConnection(serverURL, username, password);
            }

            if (con == null) {
                throw new SQLException("Unable to retrieve connection from DriverManager");
            }


            try {
                con.setAutoCommit(true);
            }
            catch (SQLException e) {/* ignored */
            }


            // A few people have been having problems because the default transaction
            // isolation level on databases is too high. READ_COMMITTED is a good
            // value for everyone to use because it provides the minimum amount of
            // locking that Jive needs to work well.
            try {
                // Supports transactions?
                if (con.getMetaData().supportsTransactions()) {
                    con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                }
            }
            catch (SQLException e) {
                // Ignore errors. A few databases don't support setting the transaction
                // isolation level, but ignoring the error shouldn't cause problems.
            }

            // create the wrapper object and mark it as checked out
            ConnectionWrapper wrapper = new ConnectionWrapper(con, this);
            if ("true".equals(JiveGlobals.getXMLProperty("database.defaultProvider.checkOpenConnections"))) {
                wrapper.exception = new Exception();
            }

            synchronized (conCountLock) {
                cons[index] = con;
                wrappers[index] = wrapper;
            }

            return wrapper;
        }
        catch (ClassNotFoundException e) {
            Log.error(e);
            throw new SQLException(e.getMessage());
        }
    }

    public String toString() {
        return minCon + "," + maxCon + "," + conCount + "," + used.intValue(); 
    }
}