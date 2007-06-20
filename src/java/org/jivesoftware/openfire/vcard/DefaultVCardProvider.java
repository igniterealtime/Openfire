/**
 * $RCSfile: DefaultVCardProvider.java,v $
 * $Revision: 3062 $
 * $Date: 2005-11-11 13:26:30 -0300 (Fri, 11 Nov 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.vcard;

import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.InternalServerErrorException;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Default implementation of the VCardProvider interface, which reads and writes data from the
 * <tt>jiveVCard</tt> database table.
 *
 * @author Gaston Dombiak
 */
public class DefaultVCardProvider implements VCardProvider {

    private static final String LOAD_PROPERTIES =
            "SELECT value FROM jiveVCard WHERE username=?";
    private static final String DELETE_PROPERTIES =
            "DELETE FROM jiveVCard WHERE username=?";
    private static final String UPDATE_PROPERTIES =
            "UPDATE jiveVCard SET value=? WHERE username=?";
    private static final String INSERT_PROPERTY =
            "INSERT INTO jiveVCard (username, value) VALUES (?, ?)";

    /** Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers. */
    private BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<SAXReader>();


    public DefaultVCardProvider() {
        super();
        // Initialize the pool of sax readers
        for (int i = 0; i < 10; i++) {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlReaders.add(xmlReader);
        }
    }

    public Element loadVCard(String username) {
        Element vCardElement = null;
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        SAXReader xmlReader = null;
        ResultSet rs = null;
        try {
            // Get a sax reader from the pool
            xmlReader = xmlReaders.take();
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            pstmt.setString(1, username);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                vCardElement =
                        xmlReader.read(new StringReader(rs.getString(1))).getRootElement();
            }
        }
        catch (Exception e) {
            throw new InternalServerErrorException("Error loading vCard of username: "
                    + username, e);
        }
        finally {
            // Return the sax reader to the pool
            if (xmlReader != null) {
                xmlReaders.add(xmlReader);
            }
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
        return vCardElement;
    }

    public void createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        if (username == null || vCardElement == null) {
            throw new NullPointerException("Parameters cannot be null.");
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, username);
            pstmt.setString(2, vCardElement.asXML());
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            throw new InternalServerErrorException("Error creating vCard for username: "
                    + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }

    }

    public void updateVCard(String username, Element vCardElement) throws NotFoundException {
        if (username == null || vCardElement == null) {
            throw new NullPointerException("Parameters cannot be null.");
        }
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTIES);
            pstmt.setString(1, vCardElement.asXML());
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            throw new InternalServerErrorException("Error updating vCard of username: "
                    + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }


    public void setVCard(String username, Element vCardElement) {
        if (username == null || vCardElement == null) {
            throw new NullPointerException("Parameters cannot be null.");
        }
        Element oldVcard = loadVCard(username);
        try {
            if (oldVcard == null) {
                createVCard(username, vCardElement);
            }
            else {
                updateVCard(username, vCardElement);
            }
        }
        catch (AlreadyExistsException e) {
            throw new InternalServerErrorException("Unable to set vCard for user:"
                    + username, e);
        }
        catch (NotFoundException e) {
            throw new InternalServerErrorException("Unable to set vCard for user:"
                    + username, e);
        }
    }

    public void deleteVCard(String username) {
        if (username == null) {
            throw new NullPointerException("Username cannot be null.");
        }

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTIES);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            throw new InternalServerErrorException("Error deleting vCard of username: "
                    + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    public boolean isReadOnly() {
        return false;
    }
}
