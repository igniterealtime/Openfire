/**
 * $RCSfile$
 * $Revision: 1759 $
 * $Date: 2005-08-09 19:32:51 -0300 (Tue, 09 Aug 2005) $
 *
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

package org.jivesoftware.openfire;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.User;

import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Private storage for user accounts (JEP-0049). It is used by some XMPP systems
 * for saving client settings on the server.
 *
 * @author Iain Shigeoka
 */
public class PrivateStorage extends BasicModule implements UserEventListener {

    private static final String LOAD_PRIVATE =
        "SELECT privateData FROM ofPrivate WHERE username=? AND namespace=?";
    private static final String INSERT_PRIVATE =
        "INSERT INTO ofPrivate (privateData,name,username,namespace) VALUES (?,?,?,?)";
    private static final String UPDATE_PRIVATE =
        "UPDATE ofPrivate SET privateData=?, name=? WHERE username=? AND namespace=?";
    private static final String DELETE_PRIVATES =
        "DELETE FROM ofPrivate WHERE username=?";

    // Currently no delete supported, we can detect an add of an empty element and
    // use that to signal a delete but that optimization doesn't seem necessary.
    // private static final String DELETE_PRIVATE =
    //     "DELETE FROM ofPrivate WHERE userID=? AND name=? AND namespace=?";

    private boolean enabled = JiveGlobals.getBooleanProperty("xmpp.privateStorageEnabled", true);

    /**
     * Pool of SAX Readers. SAXReader is not thread safe so we need to have a pool of readers.
     */
    private BlockingQueue<SAXReader> xmlReaders = new LinkedBlockingQueue<SAXReader>();

    /**
     * Constructs a new PrivateStore instance.
     */
    public PrivateStorage() {
        super("Private user data storage");
    }

    /**
     * Returns true if private storage is enabled.
     *
     * @return true if private storage is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether private storage is enabled.
     *
     * @param enabled true if this private store is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("xmpp.privateStorageEnabled", Boolean.toString(enabled));
    }

    /**
     * Stores private data. If the name and namespace of the element matches another
     * stored private data XML document, then replace it with the new one.
     *
     * @param data the data to store (XML element)
     * @param username the username of the account where private data is being stored
     */
    public void add(String username, Element data) {
        if (enabled) {
            java.sql.Connection con = null;
            PreparedStatement pstmt = null;
            try {
                StringWriter writer = new StringWriter();
                data.write(writer);
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_PRIVATE);
                pstmt.setString(1, username);
                pstmt.setString(2, data.getNamespaceURI());
                ResultSet rs = pstmt.executeQuery();
                boolean update = false;
                if (rs.next()) {
                    update = true;
                }
                rs.close();
                pstmt.close();

                if (update) {
                    pstmt = con.prepareStatement(UPDATE_PRIVATE);
                }
                else {
                    pstmt = con.prepareStatement(INSERT_PRIVATE);
                }
                pstmt.setString(1, writer.toString());
                pstmt.setString(2, data.getName());
                pstmt.setString(3, username);
                pstmt.setString(4, data.getNamespaceURI());
                pstmt.executeUpdate();
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            finally {
                try { if (pstmt != null) { pstmt.close(); } }
                catch (Exception e) { Log.error(e); }
                try { if (con != null) { con.close(); } }
                catch (Exception e) { Log.error(e); }
            }
        }
    }

    /**
     * Returns the data stored under a key corresponding to the name and namespace
     * of the given element. The Element must be in the form:<p>
     *
     * <code>&lt;name xmlns='namespace'/&gt;</code><p>
     *
     * If no data is currently stored under the given key, an empty element will be
     * returned.
     *
     * @param data an XML document who's element name and namespace is used to
     *      match previously stored private data.
     * @param username the username of the account where private data is being stored.
     * @return the data stored under the given key or the data element.
     */
    public Element get(String username, Element data) {
        if (enabled) {
            Connection con = null;
            PreparedStatement pstmt = null;
            SAXReader xmlReader = null;
            try {
                // Get a sax reader from the pool
                xmlReader = xmlReaders.take();
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_PRIVATE);
                pstmt.setString(1, username);
                pstmt.setString(2, data.getNamespaceURI());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    data.clearContent();
                    String result = rs.getString(1).trim();
                    Document doc = xmlReader.read(new StringReader(result));
                    data = doc.getRootElement();
                }
                rs.close();
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
            finally {
                // Return the sax reader to the pool
                if (xmlReader != null) {
                    xmlReaders.add(xmlReader);
                }
                try { if (pstmt != null) { pstmt.close(); } }
                catch (Exception e) { Log.error(e); }
                try { if (con != null) { con.close(); } }
                catch (Exception e) { Log.error(e); }
            }
        }
        return data;
    }

    public void userCreated(User user, Map params) {
        //Do nothing
    }

    public void userDeleting(User user, Map params) {
        // Delete all private properties of the user
        java.sql.Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PRIVATES);
            pstmt.setString(1, user.getUsername());
            pstmt.executeUpdate();
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        finally {
            try { if (pstmt != null) { pstmt.close(); } }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) { con.close(); } }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void userModified(User user, Map params) {
        //Do nothing
    }

    public void start() throws IllegalStateException {
        super.start();
        // Initialize the pool of sax readers
        for (int i=0; i<10; i++) {
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlReaders.add(xmlReader);
        }
        // Add this module as a user event listener so we can delete
        // all user properties when a user is deleted
        UserEventDispatcher.addListener(this);
    }

    public void stop() {
        super.stop();
        // Clean up the pool of sax readers
        xmlReaders.clear();
        // Remove this module as a user event listener
        UserEventDispatcher.removeListener(this);
    }
}