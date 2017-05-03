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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Allows PreparedStatement information to be cached. A prepared statement consists of
 * a SQL statement containing bind variables as well as variable values. For example,
 * the SQL statement <tt>"SELECT * FROM person WHERE age &gt; ?"</tt> would have the integer
 * variable <tt>18</tt> (which replaces the "?" chracter) to find all adults. This class
 * encapsulates both the SQL string and bind variable values so that actual
 * PreparedStatement can be created from that information later.
 *
 * @author Matt Tucker
 */
public class CachedPreparedStatement  {

	private static final Logger Log = LoggerFactory.getLogger(CachedPreparedStatement.class);

    private String sql;
    private List<Object> params;
    private List<Integer> types;

    /**
     * Constructs a new CachedPreparedStatement.
     */
    public CachedPreparedStatement() {
        params = new ArrayList<>();
        types = new ArrayList<>();
    }

    /**
     * Constructs a new CachedPreparedStatement.
     *
     * @param sql the SQL.
     */
    public CachedPreparedStatement(String sql) {
        this();
        setSQL(sql);
    }

    /**
     * Returns the SQL.
     *
     * @return the SQL.
     */
    public String getSQL() {
        return sql;
    }

    /**
     * Sets the SQL.
     *
     * @param sql the SQL.
     */
    public void setSQL(String sql) {
        this.sql = sql;
    }

    /**
     * Adds a boolean parameter to the prepared statement.
     *
     * @param value the boolean value.
     */
    public void addBoolean(boolean value) {
        params.add(value);
        types.add(Types.BOOLEAN);
    }

    /**
     * Adds an integer parameter to the prepared statement.
     *
     * @param value the int value.
     */
    public void addInt(int value) {
        params.add(value);
        types.add(Types.INTEGER);
    }

    /**
     * Adds a long parameter to the prepared statement.
     *
     * @param value the long value.
     */
    public void addLong(long value) {
        params.add(value);
        types.add(Types.BIGINT);
    }

    /**
     * Adds a String parameter to the prepared statement.
     *
     * @param value the String value.
     */
    public void addString(String value) {
        params.add(value);
        types.add(Types.VARCHAR);
    }

    /**
     * Sets all parameters on the given PreparedStatement. The standard code block
     * for turning a CachedPreparedStatement into a PreparedStatement is as follows:
     *
     * <pre>
     * PreparedStatement pstmt = con.prepareStatement(cachedPstmt.getSQL());
     * cachedPstmt.setParams(pstmt);
     * </pre>
     *
     * @param pstmt the prepared statement.
     * @throws java.sql.SQLException if an SQL Exception occurs.
     */
    public void setParams(PreparedStatement pstmt) throws SQLException {
        for (int i=0; i<params.size(); i++) {
            Object param = params.get(i);
            int type = types.get(i);
            // Set param, noting fact that params start at 1 and not 0.
            switch(type) {
                case Types.INTEGER:
                    pstmt.setInt(i+1, (Integer)param);
                    break;
                case Types.BIGINT:
                    pstmt.setLong(i+1, (Long)param);
                    break;
                case Types.VARCHAR:
                    pstmt.setString(i+1, (String)param);
                    break;
                case Types.BOOLEAN:
                    pstmt.setBoolean(i+1, (Boolean)param);
            }
        }
    }

    @Override
	public boolean equals(Object object) {
        if (object == null) {
            return false;
        }
        if (!(object instanceof CachedPreparedStatement)) {
            return false;
        }
        if (this == object) {
            return true;
        }
        CachedPreparedStatement otherStmt = (CachedPreparedStatement)object;
        return (sql == null && otherStmt.sql == null) || sql != null && sql.equals(otherStmt.sql)
                && types.equals(otherStmt.types) && params.equals(otherStmt.params);
    }

    @Override
	public int hashCode() {
        int hashCode = 1;
        if (sql != null) {
            hashCode += sql.hashCode();
        }
        hashCode = hashCode * 31 + types.hashCode();
        hashCode = hashCode * 31 + params.hashCode();
        return hashCode;
    }

    @Override
	public String toString() {
        String toStringSql = sql;
        try {
            int index = toStringSql.indexOf('?');
            int count = 0;

            while (index > -1) {
                Object param = params.get(count);
                int type = types.get(count);
                String val = null;

                // Get param
                switch(type) {
                    case Types.INTEGER:
                        val = "" + param;
                        break;
                    case Types.BIGINT:
                        val = "" + param;
                        break;
                    case Types.VARCHAR:
                        val =  '\'' + (String) param + '\'';
                        break;
                    case Types.BOOLEAN:
                        val = "" + param;
                }

                toStringSql = toStringSql.substring(0, index) + val +
                        ((index == toStringSql.length() -1) ? "" : toStringSql.substring(index + 1));
                index = toStringSql.indexOf('?', index + val.length());
                count++;
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }

        return "CachedPreparedStatement{ sql=" + toStringSql + '}';
    }
}