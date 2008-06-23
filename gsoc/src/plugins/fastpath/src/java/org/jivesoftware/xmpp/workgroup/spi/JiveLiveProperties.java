/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.xmpp.workgroup.spi;

import org.jivesoftware.xmpp.workgroup.DbProperties;
import org.jivesoftware.xmpp.workgroup.UnauthorizedException;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.xmpp.component.ComponentManagerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic implementation of entity property manager will work against any standard
 * jiveXProp table. Several entities require property management. This class will
 * generically create, read, update, and delete these properties.
 *
 * @author Derek DeMoro
 */
public class JiveLiveProperties implements DbProperties {

    private long id;
    private Map<String, String> properties = new ConcurrentHashMap<String, String>();
    private String tableName;

    public JiveLiveProperties(String tableName, long id) {
        this.id = id;
        this.tableName = tableName;
        loadProperties();
    }

    public String getProperty(String name) {
        return properties.get(name);
    }

    public void setProperty(String name, String value) throws UnauthorizedException {
        // Make sure the property name and value aren't null.
        if (name == null || value == null || "".equals(name) || "".equals(value)) {
            throw new NullPointerException("Cannot set property with empty or null value.");
        }
        boolean update = properties.containsKey(name);
        properties.put(name, value);
        if (update) {
            updateProperty(name);
        }
        else {
            insertProperty(name);
        }
    }

    public void deleteProperty(String name) throws UnauthorizedException {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("DELETE FROM " + tableName + " WHERE ownerID=? AND name=?");
            pstmt.setLong(1, id);
            pstmt.setString(2, name);
            pstmt.executeUpdate();
            properties.remove(name);
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public Collection<String> getPropertyNames() {
        return properties.keySet();
    }


    private void loadProperties() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("SELECT name,propValue FROM " + tableName + " WHERE ownerID=?");
            pstmt.setLong(1, id);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                properties.put(rs.getString(1), DbConnectionManager.getLargeTextField(rs, 2));
            }
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    private void insertProperty(String data) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("INSERT INTO " + tableName + " (ownerID,name,propValue) VALUES (?,?,?)");
            pstmt.setLong(1, id);
            pstmt.setString(2, data);
            DbConnectionManager.setLargeTextField(pstmt, 3, properties.get(data));
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    private void updateProperty(String data) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement("UPDATE " + tableName + " set propValue=? WHERE ownerID=? AND name=?");
            pstmt.setString(1, properties.get(data));
            pstmt.setLong(2, id);
            DbConnectionManager.setLargeTextField(pstmt, 3, data);
            pstmt.executeUpdate();
        }
        catch (SQLException ex) {
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }
}