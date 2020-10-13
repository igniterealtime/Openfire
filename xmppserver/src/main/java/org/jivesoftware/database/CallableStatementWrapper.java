/*
 * Copyright (C) 2004 Jive Software. All rights reserved.
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

import java.sql.*;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Calendar;
import java.net.URL;
import java.io.InputStream;
import java.io.Reader;

/**
 * An implementation of the CallableStatement interface that wraps an underlying
 * CallableStatement object.
 *
 * @author Gaston Dombiak
 */
public abstract class CallableStatementWrapper extends StatementWrapper
        implements CallableStatement {

    protected CallableStatement cstmt;

    public ResultSet executeQuery(String sql) throws SQLException {
        return cstmt.executeQuery(sql);
    }

    public <T> T unwrap(Class<T> iface) throws SQLException {
        return cstmt.unwrap(iface);
    }

    public ResultSet executeQuery() throws SQLException {
        return cstmt.executeQuery();
    }

    public int executeUpdate(String sql) throws SQLException {
        return cstmt.executeUpdate(sql);
    }

    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        cstmt.registerOutParameter(parameterIndex, sqlType);
    }

    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return cstmt.isWrapperFor(iface);
    }

    public int executeUpdate() throws SQLException {
        return cstmt.executeUpdate();
    }

    public void close() throws SQLException {
        cstmt.close();
    }

    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        cstmt.setNull(parameterIndex, sqlType);
    }

    public int getMaxFieldSize() throws SQLException {
        return cstmt.getMaxFieldSize();
    }

    public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
        cstmt.registerOutParameter(parameterIndex, sqlType, scale);
    }

    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        cstmt.setBoolean(parameterIndex, x);
    }

    public void setMaxFieldSize(int max) throws SQLException {
        cstmt.setMaxFieldSize(max);
    }

    public void setByte(int parameterIndex, byte x) throws SQLException {
        cstmt.setByte(parameterIndex, x);
    }

    public void setShort(int parameterIndex, short x) throws SQLException {
        cstmt.setShort(parameterIndex, x);
    }

    public int getMaxRows() throws SQLException {
        return cstmt.getMaxRows();
    }

    public boolean wasNull() throws SQLException {
        return cstmt.wasNull();
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        cstmt.setInt(parameterIndex, x);
    }

    public void setMaxRows(int max) throws SQLException {
        cstmt.setMaxRows(max);
    }

    public String getString(int parameterIndex) throws SQLException {
        return cstmt.getString(parameterIndex);
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        cstmt.setLong(parameterIndex, x);
    }

    public void setEscapeProcessing(boolean enable) throws SQLException {
        cstmt.setEscapeProcessing(enable);
    }

    public boolean getBoolean(int parameterIndex) throws SQLException {
        return cstmt.getBoolean(parameterIndex);
    }

    public void setFloat(int parameterIndex, float x) throws SQLException {
        cstmt.setFloat(parameterIndex, x);
    }

    public int getQueryTimeout() throws SQLException {
        return cstmt.getQueryTimeout();
    }

    public void setDouble(int parameterIndex, double x) throws SQLException {
        cstmt.setDouble(parameterIndex, x);
    }

    public byte getByte(int parameterIndex) throws SQLException {
        return cstmt.getByte(parameterIndex);
    }

    public void setQueryTimeout(int seconds) throws SQLException {
        cstmt.setQueryTimeout(seconds);
    }

    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        cstmt.setBigDecimal(parameterIndex, x);
    }

    public short getShort(int parameterIndex) throws SQLException {
        return cstmt.getShort(parameterIndex);
    }

    public void cancel() throws SQLException {
        cstmt.cancel();
    }

    public void setString(int parameterIndex, String x) throws SQLException {
        cstmt.setString(parameterIndex, x);
    }

    public int getInt(int parameterIndex) throws SQLException {
        return cstmt.getInt(parameterIndex);
    }

    public SQLWarning getWarnings() throws SQLException {
        return cstmt.getWarnings();
    }

    public long getLong(int parameterIndex) throws SQLException {
        return cstmt.getLong(parameterIndex);
    }

    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        cstmt.setBytes(parameterIndex, x);
    }

    public float getFloat(int parameterIndex) throws SQLException {
        return cstmt.getFloat(parameterIndex);
    }

    public void clearWarnings() throws SQLException {
        cstmt.clearWarnings();
    }

    public void setDate(int parameterIndex, Date x) throws SQLException {
        cstmt.setDate(parameterIndex, x);
    }

    public void setCursorName(String name) throws SQLException {
        cstmt.setCursorName(name);
    }

    public double getDouble(int parameterIndex) throws SQLException {
        return cstmt.getDouble(parameterIndex);
    }

    public void setTime(int parameterIndex, Time x) throws SQLException {
        cstmt.setTime(parameterIndex, x);
    }

    @Deprecated
    public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return cstmt.getBigDecimal(parameterIndex, scale);
    }

    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        cstmt.setTimestamp(parameterIndex, x);
    }

    public boolean execute(String sql) throws SQLException {
        return cstmt.execute(sql);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        cstmt.setAsciiStream(parameterIndex, x, length);
    }

    public byte[] getBytes(int parameterIndex) throws SQLException {
        return cstmt.getBytes(parameterIndex);
    }

    public Date getDate(int parameterIndex) throws SQLException {
        return cstmt.getDate(parameterIndex);
    }

    public ResultSet getResultSet() throws SQLException {
        return cstmt.getResultSet();
    }

    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        cstmt.setUnicodeStream(parameterIndex, x, length);
    }

    public Time getTime(int parameterIndex) throws SQLException {
        return cstmt.getTime(parameterIndex);
    }

    public int getUpdateCount() throws SQLException {
        return cstmt.getUpdateCount();
    }

    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return cstmt.getTimestamp(parameterIndex);
    }

    public boolean getMoreResults() throws SQLException {
        return cstmt.getMoreResults();
    }

    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        cstmt.setBinaryStream(parameterIndex, x, length);
    }

    public Object getObject(int parameterIndex) throws SQLException {
        return cstmt.getObject(parameterIndex);
    }

    public void setFetchDirection(int direction) throws SQLException {
        cstmt.setFetchDirection(direction);
    }

    public void clearParameters() throws SQLException {
        cstmt.clearParameters();
    }

    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return cstmt.getBigDecimal(parameterIndex);
    }

    public int getFetchDirection() throws SQLException {
        return cstmt.getFetchDirection();
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        cstmt.setObject(parameterIndex, x, targetSqlType);
    }

    public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
        return cstmt.getObject(parameterIndex, map);
    }

    public void setFetchSize(int rows) throws SQLException {
        cstmt.setFetchSize(rows);
    }

    public int getFetchSize() throws SQLException {
        return cstmt.getFetchSize();
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        cstmt.setObject(parameterIndex, x);
    }

    public Ref getRef(int parameterIndex) throws SQLException {
        return cstmt.getRef(parameterIndex);
    }

    public int getResultSetConcurrency() throws SQLException {
        return cstmt.getResultSetConcurrency();
    }

    public Blob getBlob(int parameterIndex) throws SQLException {
        return cstmt.getBlob(parameterIndex);
    }

    public int getResultSetType() throws SQLException {
        return cstmt.getResultSetType();
    }

    public void addBatch(String sql) throws SQLException {
        cstmt.addBatch(sql);
    }

    public Clob getClob(int parameterIndex) throws SQLException {
        return cstmt.getClob(parameterIndex);
    }

    public void clearBatch() throws SQLException {
        cstmt.clearBatch();
    }

    public boolean execute() throws SQLException {
        return cstmt.execute();
    }

    public Array getArray(int parameterIndex) throws SQLException {
        return cstmt.getArray(parameterIndex);
    }

    public int[] executeBatch() throws SQLException {
        return cstmt.executeBatch();
    }

    public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return cstmt.getDate(parameterIndex, cal);
    }

    public void addBatch() throws SQLException {
        cstmt.addBatch();
    }

    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        cstmt.setCharacterStream(parameterIndex, reader, length);
    }

    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return cstmt.getTime(parameterIndex, cal);
    }

    public void setRef(int parameterIndex, Ref x) throws SQLException {
        cstmt.setRef(parameterIndex, x);
    }

    public Connection getConnection() throws SQLException {
        return cstmt.getConnection();
    }

    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return cstmt.getTimestamp(parameterIndex, cal);
    }

    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        cstmt.setBlob(parameterIndex, x);
    }

    public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
        cstmt.registerOutParameter(parameterIndex, sqlType, typeName);
    }

    public void setClob(int parameterIndex, Clob x) throws SQLException {
        cstmt.setClob(parameterIndex, x);
    }

    public boolean getMoreResults(int current) throws SQLException {
        return cstmt.getMoreResults(current);
    }

    public void setArray(int parameterIndex, Array x) throws SQLException {
        cstmt.setArray(parameterIndex, x);
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        return cstmt.getMetaData();
    }

    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        cstmt.registerOutParameter(parameterName, sqlType);
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        return cstmt.getGeneratedKeys();
    }

    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        cstmt.setDate(parameterIndex, x, cal);
    }

    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        return cstmt.executeUpdate(sql, autoGeneratedKeys);
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        cstmt.setTime(parameterIndex, x, cal);
    }

    public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
        cstmt.registerOutParameter(parameterName, sqlType, scale);
    }

    public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
        return cstmt.executeUpdate(sql, columnIndexes);
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        cstmt.setTimestamp(parameterIndex, x, cal);
    }

    public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
        cstmt.registerOutParameter(parameterName, sqlType, typeName);
    }

    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        cstmt.setNull(parameterIndex, sqlType, typeName);
    }

    public int executeUpdate(String sql, String[] columnNames) throws SQLException {
        return cstmt.executeUpdate(sql, columnNames);
    }

    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        return cstmt.execute(sql, autoGeneratedKeys);
    }

    public URL getURL(int parameterIndex) throws SQLException {
        return cstmt.getURL(parameterIndex);
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        cstmt.setURL(parameterIndex, x);
    }

    public void setURL(String parameterName, URL val) throws SQLException {
        cstmt.setURL(parameterName, val);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        return cstmt.getParameterMetaData();
    }

    public void setNull(String parameterName, int sqlType) throws SQLException {
        cstmt.setNull(parameterName, sqlType);
    }

    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        cstmt.setRowId(parameterIndex, x);
    }

    public boolean execute(String sql, int[] columnIndexes) throws SQLException {
        return cstmt.execute(sql, columnIndexes);
    }

    public void setBoolean(String parameterName, boolean x) throws SQLException {
        cstmt.setBoolean(parameterName, x);
    }

    public void setNString(int parameterIndex, String value) throws SQLException {
        cstmt.setNString(parameterIndex, value);
    }

    public void setByte(String parameterName, byte x) throws SQLException {
        cstmt.setByte(parameterName, x);
    }

    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        cstmt.setNCharacterStream(parameterIndex, value, length);
    }

    public void setShort(String parameterName, short x) throws SQLException {
        cstmt.setShort(parameterName, x);
    }

    public void setInt(String parameterName, int x) throws SQLException {
        cstmt.setInt(parameterName, x);
    }

    public boolean execute(String sql, String[] columnNames) throws SQLException {
        return cstmt.execute(sql, columnNames);
    }

    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        cstmt.setNClob(parameterIndex, value);
    }

    public void setLong(String parameterName, long x) throws SQLException {
        cstmt.setLong(parameterName, x);
    }

    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        cstmt.setClob(parameterIndex, reader, length);
    }

    public void setFloat(String parameterName, float x) throws SQLException {
        cstmt.setFloat(parameterName, x);
    }

    public void setDouble(String parameterName, double x) throws SQLException {
        cstmt.setDouble(parameterName, x);
    }

    public int getResultSetHoldability() throws SQLException {
        return cstmt.getResultSetHoldability();
    }

    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        cstmt.setBlob(parameterIndex, inputStream, length);
    }

    public boolean isClosed() throws SQLException {
        return cstmt.isClosed();
    }

    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
        cstmt.setBigDecimal(parameterName, x);
    }

    public void setPoolable(boolean poolable) throws SQLException {
        cstmt.setPoolable(poolable);
    }

    public void setString(String parameterName, String x) throws SQLException {
        cstmt.setString(parameterName, x);
    }

    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        cstmt.setNClob(parameterIndex, reader, length);
    }

    public boolean isPoolable() throws SQLException {
        return cstmt.isPoolable();
    }

    public void setBytes(String parameterName, byte[] x) throws SQLException {
        cstmt.setBytes(parameterName, x);
    }

    public void setDate(String parameterName, Date x) throws SQLException {
        cstmt.setDate(parameterName, x);
    }

    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        cstmt.setSQLXML(parameterIndex, xmlObject);
    }

    public void setTime(String parameterName, Time x) throws SQLException {
        cstmt.setTime(parameterName, x);
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        cstmt.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        cstmt.setTimestamp(parameterName, x);
    }

    public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
        cstmt.setAsciiStream(parameterName, x, length);
    }

    public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
        cstmt.setBinaryStream(parameterName, x, length);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        cstmt.setAsciiStream(parameterIndex, x, length);
    }

    public void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {
        cstmt.setObject(parameterName, x, targetSqlType, scale);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        cstmt.setBinaryStream(parameterIndex, x, length);
    }

    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        cstmt.setCharacterStream(parameterIndex, reader, length);
    }

    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        cstmt.setObject(parameterName, x, targetSqlType);
    }

    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        cstmt.setAsciiStream(parameterIndex, x);
    }

    public void setObject(String parameterName, Object x) throws SQLException {
        cstmt.setObject(parameterName, x);
    }

    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        cstmt.setBinaryStream(parameterIndex, x);
    }

    public void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {
        cstmt.setCharacterStream(parameterName, reader, length);
    }

    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        cstmt.setCharacterStream(parameterIndex, reader);
    }

    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
        cstmt.setDate(parameterName, x, cal);
    }

    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        cstmt.setNCharacterStream(parameterIndex, value);
    }

    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
        cstmt.setTime(parameterName, x, cal);
    }

    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        cstmt.setClob(parameterIndex, reader);
    }

    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
        cstmt.setTimestamp(parameterName, x, cal);
    }

    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        cstmt.setBlob(parameterIndex, inputStream);
    }

    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        cstmt.setNull(parameterName, sqlType, typeName);
    }

    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        cstmt.setNClob(parameterIndex, reader);
    }

    public String getString(String parameterName) throws SQLException {
        return cstmt.getString(parameterName);
    }

    public boolean getBoolean(String parameterName) throws SQLException {
        return cstmt.getBoolean(parameterName);
    }

    public byte getByte(String parameterName) throws SQLException {
        return cstmt.getByte(parameterName);
    }

    public short getShort(String parameterName) throws SQLException {
        return cstmt.getShort(parameterName);
    }

    public int getInt(String parameterName) throws SQLException {
        return cstmt.getInt(parameterName);
    }

    public long getLong(String parameterName) throws SQLException {
        return cstmt.getLong(parameterName);
    }

    public float getFloat(String parameterName) throws SQLException {
        return cstmt.getFloat(parameterName);
    }

    public double getDouble(String parameterName) throws SQLException {
        return cstmt.getDouble(parameterName);
    }

    public byte[] getBytes(String parameterName) throws SQLException {
        return cstmt.getBytes(parameterName);
    }

    public Date getDate(String parameterName) throws SQLException {
        return cstmt.getDate(parameterName);
    }

    public Time getTime(String parameterName) throws SQLException {
        return cstmt.getTime(parameterName);
    }

    public Timestamp getTimestamp(String parameterName) throws SQLException {
        return cstmt.getTimestamp(parameterName);
    }

    public Object getObject(String parameterName) throws SQLException {
        return cstmt.getObject(parameterName);
    }

    public BigDecimal getBigDecimal(String parameterName) throws SQLException {
        return cstmt.getBigDecimal(parameterName);
    }

    public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
        return cstmt.getObject(parameterName, map);
    }

    public Ref getRef(String parameterName) throws SQLException {
        return cstmt.getRef(parameterName);
    }

    public Blob getBlob(String parameterName) throws SQLException {
        return cstmt.getBlob(parameterName);
    }

    public Clob getClob(String parameterName) throws SQLException {
        return cstmt.getClob(parameterName);
    }

    public Array getArray(String parameterName) throws SQLException {
        return cstmt.getArray(parameterName);
    }

    public Date getDate(String parameterName, Calendar cal) throws SQLException {
        return cstmt.getDate(parameterName, cal);
    }

    public Time getTime(String parameterName, Calendar cal) throws SQLException {
        return cstmt.getTime(parameterName, cal);
    }

    public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
        return cstmt.getTimestamp(parameterName, cal);
    }

    public URL getURL(String parameterName) throws SQLException {
        return cstmt.getURL(parameterName);
    }

    public RowId getRowId(int parameterIndex) throws SQLException {
        return cstmt.getRowId(parameterIndex);
    }

    public RowId getRowId(String parameterName) throws SQLException {
        return cstmt.getRowId(parameterName);
    }

    public void setRowId(String parameterName, RowId x) throws SQLException {
        cstmt.setRowId(parameterName, x);
    }

    public void setNString(String parameterName, String value) throws SQLException {
        cstmt.setNString(parameterName, value);
    }

    public void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {
        cstmt.setNCharacterStream(parameterName, value, length);
    }

    public void setNClob(String parameterName, NClob value) throws SQLException {
        cstmt.setNClob(parameterName, value);
    }

    public void setClob(String parameterName, Reader reader, long length) throws SQLException {
        cstmt.setClob(parameterName, reader, length);
    }

    public void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {
        cstmt.setBlob(parameterName, inputStream, length);
    }

    public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
        cstmt.setNClob(parameterName, reader, length);
    }

    public NClob getNClob(int parameterIndex) throws SQLException {
        return cstmt.getNClob(parameterIndex);
    }

    public NClob getNClob(String parameterName) throws SQLException {
        return cstmt.getNClob(parameterName);
    }

    public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
        cstmt.setSQLXML(parameterName, xmlObject);
    }

    public SQLXML getSQLXML(int parameterIndex) throws SQLException {
        return cstmt.getSQLXML(parameterIndex);
    }

    public SQLXML getSQLXML(String parameterName) throws SQLException {
        return cstmt.getSQLXML(parameterName);
    }

    public String getNString(int parameterIndex) throws SQLException {
        return cstmt.getNString(parameterIndex);
    }

    public String getNString(String parameterName) throws SQLException {
        return cstmt.getNString(parameterName);
    }

    public Reader getNCharacterStream(int parameterIndex) throws SQLException {
        return cstmt.getNCharacterStream(parameterIndex);
    }

    public Reader getNCharacterStream(String parameterName) throws SQLException {
        return cstmt.getNCharacterStream(parameterName);
    }

    public Reader getCharacterStream(int parameterIndex) throws SQLException {
        return cstmt.getCharacterStream(parameterIndex);
    }

    public Reader getCharacterStream(String parameterName) throws SQLException {
        return cstmt.getCharacterStream(parameterName);
    }

    public void setBlob(String parameterName, Blob x) throws SQLException {
        cstmt.setBlob(parameterName, x);
    }

    public void setClob(String parameterName, Clob x) throws SQLException {
        cstmt.setClob(parameterName, x);
    }

    public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
        cstmt.setAsciiStream(parameterName, x, length);
    }

    public void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {
        cstmt.setBinaryStream(parameterName, x, length);
    }

    public void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {
        cstmt.setCharacterStream(parameterName, reader, length);
    }

    public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
        cstmt.setAsciiStream(parameterName, x);
    }

    public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
        cstmt.setBinaryStream(parameterName, x);
    }

    public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
        cstmt.setCharacterStream(parameterName, reader);
    }

    public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
        cstmt.setNCharacterStream(parameterName, value);
    }

    public void setClob(String parameterName, Reader reader) throws SQLException {
        cstmt.setClob(parameterName, reader);
    }

    public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
        cstmt.setBlob(parameterName, inputStream);
    }

    public void setNClob(String parameterName, Reader reader) throws SQLException {
        cstmt.setNClob(parameterName, reader);
    }

    public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
        return cstmt.getObject(parameterIndex, type);
    }

    public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
        return cstmt.getObject(parameterName, type);
    }

    public CallableStatementWrapper(CallableStatement cstmt) {
        super(cstmt);
        this.cstmt = cstmt;
    }
}
