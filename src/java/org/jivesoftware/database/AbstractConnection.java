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

import org.jivesoftware.util.Log;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.lang.reflect.Method;

/**
 * An implementation of the Connection interface that wraps an underlying
 * Connection object.
 *
 * @author Gaston Dombiak
 */
public abstract class AbstractConnection implements Connection {

    protected Connection connection;

    public AbstractConnection(Connection connection) {
        this.connection = connection;
    }

    public Statement createStatement() throws SQLException {
        return connection.createStatement();
    }

    public PreparedStatement prepareStatement(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

    public CallableStatement prepareCall(String sql) throws SQLException {
        return connection.prepareCall(sql);
    }

    public String nativeSQL(String sql) throws SQLException {
        return connection.nativeSQL(sql);
    }

    public void setAutoCommit(boolean autoCommit) throws SQLException {
        connection.setAutoCommit(autoCommit);
    }

    public boolean getAutoCommit() throws SQLException {
        return connection.getAutoCommit();
    }

    public void close() throws SQLException {
        connection.close();
    }

    public void commit() throws SQLException {
        connection.commit();
    }

    public void rollback() throws SQLException {
        connection.rollback();
    }

    public boolean isClosed() throws SQLException {
        return connection.isClosed();
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        return connection.getMetaData();
    }

    public void setReadOnly(boolean readOnly) throws SQLException {
        connection.setReadOnly(readOnly);
    }

    public boolean isReadOnly() throws SQLException {
        return connection.isReadOnly();
    }

    public void setCatalog(String catalog) throws SQLException {
        connection.setCatalog(catalog);
    }

    public String getCatalog() throws SQLException {
        return connection.getCatalog();
    }

    public void setTransactionIsolation(int level) throws SQLException {
        connection.setTransactionIsolation(level);
    }

    public int getTransactionIsolation() throws SQLException {
        return connection.getTransactionIsolation();
    }

    public SQLWarning getWarnings() throws SQLException {
        return connection.getWarnings();
    }

    public void clearWarnings() throws SQLException {
        connection.clearWarnings();
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return connection.createStatement(resultSetType, resultSetConcurrency);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
                                              int resultSetConcurrency) throws SQLException {
        return connection.prepareStatement(sql, resultSetType, resultSetConcurrency);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
            throws SQLException {
        return connection.prepareCall(sql, resultSetType, resultSetConcurrency);
    }

    public Map<String, Class<?>> getTypeMap() throws SQLException {
        return connection.getTypeMap();
    }

    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        connection.setTypeMap(map);
    }

    public void setHoldability(int holdability) throws SQLException {
        connection.setHoldability(holdability);
    }

    public int getHoldability() throws SQLException {
        return connection.getHoldability();
    }

    public Savepoint setSavepoint() throws SQLException {
        return connection.setSavepoint();
    }

    public Savepoint setSavepoint(String name) throws SQLException {
        return connection.setSavepoint(name);
    }

    public void rollback(Savepoint savepoint) throws SQLException {
        connection.rollback(savepoint);
    }

    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        connection.releaseSavepoint(savepoint);
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException
    {
        return connection.createStatement(resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    public PreparedStatement prepareStatement(String sql, int resultSetType,
            int resultSetConcurrency, int resultSetHoldability) throws SQLException
    {
        return connection.prepareStatement(sql, resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency,
            int resultSetHoldability) throws SQLException
    {
        return connection.prepareCall(sql, resultSetType, resultSetConcurrency,
                resultSetHoldability);
    }

    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
            throws SQLException
    {
        return connection.prepareStatement(sql, autoGeneratedKeys);
    }

    public PreparedStatement prepareStatement(String sql, int columnIndexes[]) throws SQLException {
        return connection.prepareStatement(sql, columnIndexes);
    }

    public PreparedStatement prepareStatement(String sql, String columnNames[])
            throws SQLException {
        return connection.prepareStatement(sql, columnNames);
    }

    // JDK 1.6 Methods. We must handle these using reflection so that the code will compile on
    // both JDK 1.6 and 1.5.

    public Clob createClob() throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createClob", new Class[] {});
            return (Clob)method.invoke(connection);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public Blob createBlob() throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createBlob", new Class[] {});
            return (Blob)method.invoke(connection);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public NClob createNClob() throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createNClob", new Class[] {});
            return (NClob)method.invoke(connection);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public SQLXML createSQLXML() throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createSQLXML", new Class[] {});
            return (SQLXML)method.invoke(connection);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public boolean isValid(int timeout) throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createClob", Integer.class);
            return (Boolean)method.invoke(connection, timeout);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return false;
        }
    }

    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        try {
            Method method = connection.getClass().getMethod("setClientInfo", String.class, String.class);
            method.invoke(connection, name, value);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLClientInfoException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
        }
    }

    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        try {
            Method method = connection.getClass().getMethod("setClientInfo", Properties.class);
            method.invoke(connection, properties);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLClientInfoException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
        }
    }

    public String getClientInfo(String name) throws SQLException {
        try {
            Method method = connection.getClass().getMethod("getClientInfo", String.class);
            return (String)method.invoke(connection, name);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public Properties getClientInfo() throws SQLException {
        try {
            Method method = connection.getClass().getMethod("getClientInfo", new Class[] {});
            return (Properties)method.invoke(connection);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createArrayOf", String.class, Object[].class);
            return (Array)method.invoke(connection, typeName, elements);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        try {
            Method method = connection.getClass().getMethod("createStruct", String.class, Object[].class);
            return (Struct)method.invoke(connection, typeName, attributes);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        try {
            Method method = connection.getClass().getMethod("unwrap", Class.class);
            return (T)method.invoke(connection, iface);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return null;
        }
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        try {
            Method method = connection.getClass().getMethod("isWrapperFor", Class.class);
            return (Boolean)method.invoke(connection, iface);
        }
        catch (Exception e) {
            if (e instanceof SQLException) {
                throw (SQLException)e;
            }
            // Simply log reflection exceptions.
            Log.error(e);
            return false;
        }
    }
}