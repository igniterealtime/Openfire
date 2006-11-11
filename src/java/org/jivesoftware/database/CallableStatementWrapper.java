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

    public CallableStatementWrapper(CallableStatement cstmt) {
        super(cstmt);
        this.cstmt = cstmt;
    }

    public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
        cstmt.registerOutParameter(parameterIndex, sqlType);
    }

    public void registerOutParameter(int parameterIndex, int sqlType, int scale)
            throws SQLException {
        cstmt.registerOutParameter(parameterIndex, sqlType, scale);
    }

    public boolean wasNull() throws SQLException {
        return cstmt.wasNull();
    }

    public String getString(int parameterIndex) throws SQLException {
        return cstmt.getString(parameterIndex);
    }

    public boolean getBoolean(int parameterIndex) throws SQLException {
        return cstmt.getBoolean(parameterIndex);
    }

    public byte getByte(int parameterIndex) throws SQLException {
        return cstmt.getByte(parameterIndex);
    }

    public short getShort(int parameterIndex) throws SQLException {
        return cstmt.getShort(parameterIndex);
    }

    public int getInt(int parameterIndex) throws SQLException {
        return cstmt.getInt(parameterIndex);
    }

    public long getLong(int parameterIndex) throws SQLException {
        return cstmt.getLong(parameterIndex);
    }

    public float getFloat(int parameterIndex) throws SQLException {
        return cstmt.getFloat(parameterIndex);
    }

    public double getDouble(int parameterIndex) throws SQLException {
        return cstmt.getDouble(parameterIndex);
    }

    @Deprecated public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
        return cstmt.getBigDecimal(parameterIndex, scale);
    }

    public byte[] getBytes(int parameterIndex) throws SQLException {
        return cstmt.getBytes(parameterIndex);
    }

    public Date getDate(int parameterIndex) throws SQLException {
        return cstmt.getDate(parameterIndex);
    }

    public Time getTime(int parameterIndex) throws SQLException {
        return cstmt.getTime(parameterIndex);
    }

    public Timestamp getTimestamp(int parameterIndex) throws SQLException {
        return cstmt.getTimestamp(parameterIndex);
    }

    public Object getObject(int parameterIndex) throws SQLException {
        return cstmt.getObject(parameterIndex);
    }

    public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
        return cstmt.getBigDecimal(parameterIndex);
    }

    public Object getObject(int i, Map<String, Class<?>> map) throws SQLException {
        return cstmt.getObject(i, map);
    }

    public Ref getRef(int i) throws SQLException {
        return cstmt.getRef(i);
    }

    public Blob getBlob(int i) throws SQLException {
        return cstmt.getBlob(i);
    }

    public Clob getClob(int i) throws SQLException {
        return cstmt.getClob(i);
    }

    public Array getArray(int i) throws SQLException {
        return cstmt.getArray(i);
    }

    public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
        return cstmt.getDate(parameterIndex, cal);
    }

    public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
        return cstmt.getTime(parameterIndex, cal);
    }

    public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
        return cstmt.getTimestamp(parameterIndex, cal);
    }

    public void registerOutParameter(int paramIndex, int sqlType, String typeName)
            throws SQLException {
        cstmt.registerOutParameter(paramIndex, sqlType, typeName);
    }

    public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
        cstmt.registerOutParameter(parameterName, sqlType);
    }

    public void registerOutParameter(String parameterName, int sqlType, int scale)
            throws SQLException {
        cstmt.registerOutParameter(parameterName, sqlType, scale);
    }

    public void registerOutParameter(String parameterName, int sqlType, String typeName)
            throws SQLException {
        cstmt.registerOutParameter(parameterName, sqlType, typeName);
    }

    public URL getURL(int parameterIndex) throws SQLException {
        return cstmt.getURL(parameterIndex);
    }

    public void setURL(String parameterName, URL val) throws SQLException {
        cstmt.setURL(parameterName, val);
    }

    public void setNull(String parameterName, int sqlType) throws SQLException {
        cstmt.setNull(parameterName, sqlType);
    }

    public void setBoolean(String parameterName, boolean x) throws SQLException {
        cstmt.setBoolean(parameterName, x);
    }

    public void setByte(String parameterName, byte x) throws SQLException {
        cstmt.setByte(parameterName, x);
    }

    public void setShort(String parameterName, short x) throws SQLException {
        cstmt.setShort(parameterName, x);
    }

    public void setInt(String parameterName, int x) throws SQLException {
        cstmt.setInt(parameterName, x);
    }

    public void setLong(String parameterName, long x) throws SQLException {
        cstmt.setLong(parameterName, x);
    }

    public void setFloat(String parameterName, float x) throws SQLException {
        cstmt.setFloat(parameterName, x);
    }

    public void setDouble(String parameterName, double x) throws SQLException {
        cstmt.setDouble(parameterName, x);
    }

    public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
        cstmt.setBigDecimal(parameterName, x);
    }

    public void setString(String parameterName, String x) throws SQLException {
        cstmt.setString(parameterName, x);
    }

    public void setBytes(String parameterName, byte x[]) throws SQLException {
        cstmt.setBytes(parameterName, x);
    }

    public void setDate(String parameterName, Date x) throws SQLException {
        cstmt.setDate(parameterName, x);
    }

    public void setTime(String parameterName, Time x) throws SQLException {
        cstmt.setTime(parameterName, x);
    }

    public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
        cstmt.setTimestamp(parameterName, x);
    }

    public void setAsciiStream(String parameterName, InputStream x, int length)
            throws SQLException {
        cstmt.setAsciiStream(parameterName, x, length);
    }

    public void setBinaryStream(String parameterName, InputStream x, int length)
            throws SQLException {
        cstmt.setBinaryStream(parameterName, x, length);
    }

    public void setObject(String parameterName, Object x, int targetSqlType, int scale)
            throws SQLException {
        cstmt.setObject(parameterName, x, targetSqlType, scale);
    }

    public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
        cstmt.setObject(parameterName, x, targetSqlType);
    }

    public void setObject(String parameterName, Object x) throws SQLException {
        cstmt.setObject(parameterName, x);
    }

    public void setCharacterStream(String parameterName, Reader reader, int length)
            throws SQLException {
        cstmt.setCharacterStream(parameterName, reader, length);
    }

    public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
        cstmt.setDate(parameterName, x, cal);
    }

    public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
        cstmt.setTime(parameterName, x, cal);
    }

    public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
        cstmt.setTimestamp(parameterName, x, cal);
    }

    public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
        cstmt.setNull(parameterName, sqlType, typeName);
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

    public ResultSet executeQuery() throws SQLException {
        return cstmt.executeQuery();
    }

    public int executeUpdate() throws SQLException {
        return cstmt.executeUpdate();
    }

    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        cstmt.setNull(parameterIndex, sqlType);
    }

    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        cstmt.setBoolean(parameterIndex, x);
    }

    public void setByte(int parameterIndex, byte x) throws SQLException {
        cstmt.setByte(parameterIndex, x);
    }

    public void setShort(int parameterIndex, short x) throws SQLException {
        cstmt.setShort(parameterIndex, x);
    }

    public void setInt(int parameterIndex, int x) throws SQLException {
        cstmt.setInt(parameterIndex, x);
    }

    public void setLong(int parameterIndex, long x) throws SQLException {
        cstmt.setLong(parameterIndex, x);
    }

    public void setFloat(int parameterIndex, float x) throws SQLException {
        cstmt.setFloat(parameterIndex, x);
    }

    public void setDouble(int parameterIndex, double x) throws SQLException {
        cstmt.setDouble(parameterIndex, x);
    }

    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        cstmt.setBigDecimal(parameterIndex, x);
    }

    public void setString(int parameterIndex, String x) throws SQLException {
        cstmt.setString(parameterIndex, x);
    }

    public void setBytes(int parameterIndex, byte x[]) throws SQLException {
        cstmt.setBytes(parameterIndex, x);
    }

    public void setDate(int parameterIndex, Date x) throws SQLException {
        cstmt.setDate(parameterIndex, x);
    }

    public void setTime(int parameterIndex, Time x) throws SQLException {
        cstmt.setTime(parameterIndex, x);
    }

    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        cstmt.setTimestamp(parameterIndex, x);
    }

    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        cstmt.setAsciiStream(parameterIndex, x, length);
    }

    @Deprecated public void setUnicodeStream(int parameterIndex, InputStream x, int length)
            throws SQLException {
        cstmt.setUnicodeStream(parameterIndex, x, length);
    }

    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        cstmt.setBinaryStream(parameterIndex, x, length);
    }

    public void clearParameters() throws SQLException {
        cstmt.clearParameters();
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType, int scale)
            throws SQLException {
        cstmt.setObject(parameterIndex, x, targetSqlType, scale);
    }

    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        cstmt.setObject(parameterIndex, x, targetSqlType);
    }

    public void setObject(int parameterIndex, Object x) throws SQLException {
        cstmt.setObject(parameterIndex, x);
    }

    public boolean execute() throws SQLException {
        return cstmt.execute();
    }

    public void addBatch() throws SQLException {
        cstmt.addBatch();
    }

    public void setCharacterStream(int parameterIndex, Reader reader, int length)
            throws SQLException {
        cstmt.setCharacterStream(parameterIndex, reader, length);
    }

    public void setRef(int i, Ref x) throws SQLException {
        cstmt.setRef(i, x);
    }

    public void setBlob(int i, Blob x) throws SQLException {
        cstmt.setBlob(i, x);
    }

    public void setClob(int i, Clob x) throws SQLException {
        cstmt.setClob(i, x);
    }

    public void setArray(int i, Array x) throws SQLException {
        cstmt.setArray(i, x);
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        return cstmt.getMetaData();
    }

    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        cstmt.setDate(parameterIndex, x, cal);
    }

    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        cstmt.setTime(parameterIndex, x, cal);
    }

    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        cstmt.setTimestamp(parameterIndex, x, cal);
    }

    public void setNull(int paramIndex, int sqlType, String typeName) throws SQLException {
        cstmt.setNull(paramIndex, sqlType, typeName);
    }

    public void setURL(int parameterIndex, URL x) throws SQLException {
        cstmt.setURL(parameterIndex, x);
    }

    public ParameterMetaData getParameterMetaData() throws SQLException {
        return cstmt.getParameterMetaData();
    }
}
