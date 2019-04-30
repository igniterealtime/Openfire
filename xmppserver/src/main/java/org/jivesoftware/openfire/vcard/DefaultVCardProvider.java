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

package org.jivesoftware.openfire.vcard;

import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.AlreadyExistsException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of the VCardProvider interface, which reads and writes data
 * from the {@code ofVCard} database table.
 *
 * @author Gaston Dombiak
 */
public class DefaultVCardProvider implements VCardProvider {

    private static final Logger Log = LoggerFactory.getLogger(DefaultVCardProvider.class);

    private static final String MUTEX_SUFFIX = " dvcp";
    
    private static final String LOAD_PROPERTIES =
        "SELECT vcard FROM ofVCard WHERE username=?";
    private static final String DELETE_PROPERTIES =
        "DELETE FROM ofVCard WHERE username=?";
    private static final String UPDATE_PROPERTIES =
        "UPDATE ofVCard SET vcard=? WHERE username=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO ofVCard (username, vcard) VALUES (?, ?)";

    private static final int POOL_SIZE = 10;
    /**
     * Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers.
     */
    private BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<>(POOL_SIZE);


    public DefaultVCardProvider() {
        super();
        // Initialize the pool of sax readers
        for (int i=0; i<POOL_SIZE; i++) {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlReaders.add(xmlReader);
        }
    }

    @Override
    public Element loadVCard(String username) {
        synchronized ((username + MUTEX_SUFFIX).intern()) {
            Connection con = null;
            PreparedStatement pstmt = null;
            ResultSet rs = null;
            Element vCardElement = null;
            SAXReader xmlReader = null;
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
                Log.error("Error loading vCard of username: " + username, e);
            }
            finally {
                // Return the sax reader to the pool
                if (xmlReader != null) {
                    xmlReaders.add(xmlReader);
                }
                DbConnectionManager.closeConnection(rs, pstmt, con);
            }

            if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_LOAD, PhotoResizer.PROPERTY_RESIZE_ON_LOAD_DEFAULT ) )
            {
                PhotoResizer.resizeAvatar( vCardElement );
            }

            return vCardElement;
        }
    }

    @Override
    public Element createVCard(String username, Element vCardElement) throws AlreadyExistsException {
        if (loadVCard(username) != null) {
            // The user already has a vCard
            throw new AlreadyExistsException("Username " + username + " already has a vCard");
        }

        if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_CREATE, PhotoResizer.PROPERTY_RESIZE_ON_CREATE_DEFAULT ) )
        {
            PhotoResizer.resizeAvatar( vCardElement );
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
            Log.error("Error creating vCard for username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return vCardElement;
    }

    @Override
    public Element updateVCard(String username, Element vCardElement) throws NotFoundException {
        if (loadVCard(username) == null) {
            // The user does not have a vCard
            throw new NotFoundException("Username " + username + " does not have a vCard");
        }

        if ( JiveGlobals.getBooleanProperty( PhotoResizer.PROPERTY_RESIZE_ON_CREATE, PhotoResizer.PROPERTY_RESIZE_ON_CREATE_DEFAULT ) )
        {
            PhotoResizer.resizeAvatar( vCardElement );
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
            Log.error("Error updating vCard of username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return vCardElement;
    }

    @Override
    public void deleteVCard(String username) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTIES);
            pstmt.setString(1, username);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error("Error deleting vCard of username: " + username, e);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
}
