/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.reporting.stats;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.jivesoftware.database.DbConnectionManager;
import org.jrobin.core.RrdBackend;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RrdSqlBackend extends RrdBackend {
	
	private static final Logger Log = LoggerFactory.getLogger(RrdSqlBackend.class);
	
    // SQL prepared statements
    static final String JDBC_SELECT = "SELECT bytes from ofRRDs where id = ?";
    static final String JDBC_INSERT = "INSERT INTO ofRRDs (id, updatedDate, bytes) VALUES (?, ?, ?)";
    static final String JDBC_UPDATE = "UPDATE ofRRDs SET bytes = ?, updatedDate=? WHERE id = ?";
    static final String JDBC_DELETE = "DELETE FROM ofRRDs WHERE id = ?";

    // this is the place where our RRD bytes will be stored
    private byte[] buffer = null;
    // When readOnly then the SQL DB is not updated
    private boolean readOnly;

    public static void importRRD(String id, File rrdFile) throws IOException {
        // Read content from file
        FileInputStream stream = null;
        byte[] bytes = null;
        try {
            stream = new FileInputStream(rrdFile);
            // Create the byte array to hold the data
            bytes = new byte[(int) rrdFile.length()];
            // Read in the bytes
            int offset = 0;
            int numRead;
            while (offset < bytes.length && (numRead = stream.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
        }
        finally {
            if (stream != null) {
                stream.close();
            }
        }
        // Save file content to the DB
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(JDBC_SELECT);
            pstmt.setString(1, id);
            rs = pstmt.executeQuery();
            if(rs.next()) {
                // Do not import since there is already an RRD in the DB
            }
            else {
                // RRD with the given id does not exist
                // we'll insert a new row in the table using the supplied id
                // but with no RRD bytes (null)
                insertStmt = con.prepareStatement(JDBC_INSERT);
                insertStmt.setString(1, id);
                insertStmt.setLong(2, System.currentTimeMillis());
                insertStmt.setBytes(3, bytes);
                insertStmt.executeUpdate();
            }
        }
        catch (Exception e) {
            Log.error("Error while accessing information in database: " + e);
        }
        finally {
            DbConnectionManager.closeStatement(insertStmt);
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    RrdSqlBackend(String id, boolean readOnly) throws IOException {
        super(id);
        this.readOnly = readOnly;
        Connection con = null;
        PreparedStatement pstmt = null;
        PreparedStatement insertStmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(JDBC_SELECT);
            pstmt.setString(1, id);
            rs = pstmt.executeQuery();
            if(rs.next()) {
                // RRD with the given id already exists
                // bring RRD data to our buffer
                buffer = rs.getBytes("bytes");
            }
            else {
                // RRD with the given id does not exist
                // we'll insert a new row in the table using the supplied id
                // but with no RRD bytes (null)
                insertStmt = con.prepareStatement(JDBC_INSERT);
                insertStmt.setString(1, id);
                insertStmt.setLong(2, System.currentTimeMillis());
                insertStmt.setBytes(3, null);
                insertStmt.executeUpdate();
            }
        }
        catch (Exception e) {
            Log.error("Error while accessing information in database: " + e);
        }
        finally {
            DbConnectionManager.closeStatement(insertStmt);
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    // this method writes bytes supplied from the JRobin frontend
    // to our memory buffer
    @Override
	protected void write(long offset, byte[] b) {
        int pos = (int) offset;
        for(int i = 0; i < b.length; i++) {
            buffer[pos++] = b[i];
        }
    }

    // this method reads bytes requested from the JRobin frontend
    // and stores them in the supplied byte[] array
    @Override
	protected void read(long offset, byte[] b) {
        int pos = (int) offset;
        for(int i = 0; i < b.length; i++) {
            b[i] = buffer[pos++];
        }
    }

    // returns the RRD size (since all RRD bytes are
    // in the buffer, it is equal to the buffer length
    @Override
	public long getLength() throws IOException {
        return buffer.length;
    }

    // provides enough space in memory for the RRD
    @Override
	protected void setLength(long length) {
        buffer = new byte[(int) length];
    }


    @Override
	public void close() throws IOException {
        super.close();
        // Save data to the SQL DB only if not read-only
        if (!readOnly) {
            sync();
        }
    }
    // sends bytes in memory to the database
    protected void sync() throws IOException {
    	// RRD id is here
        String id = super.getPath();
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(JDBC_UPDATE);
            pstmt.setBytes(1, buffer);
            pstmt.setLong(2, System.currentTimeMillis());
            pstmt.setString(3, id);
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error("Error while updating information in database: " + e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    // checks if RRD with the given id already exists in the database
    // used from RrdSqlBackendFactory class
    static boolean exists(String id) throws IOException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(JDBC_SELECT);
            pstmt.setString(1, id);
            rs = pstmt.executeQuery();
            return rs.next();
        }
        catch (Exception e) {
            Log.error("Error while accessing information in database: " + e);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return false;
    }
}