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

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.container.BasicModule;
import org.jivesoftware.messenger.container.Container;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XPPReader;
import org.jivesoftware.messenger.PrivateStore;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.messenger.auth.UnauthorizedException;

import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.dom4j.Document;
import org.dom4j.Element;

/**
 * <p>Store and retrieve data from the database.</p>
 * <p>The typical use cases don't give any advantage to caching the data so we won't. Direct
 * database access is used for all operations. We expect clients to retrieve private information
 * on login, and set private information on logout, or when the user changes settings in the
 * client application (occurs rarely and we expect the client to cache private data so we'll
 * only see a get, then a set).</p>
 * <p>We currently only match on namespace for backward compatibility,
 * although it is possible that future versions may make the element name a distinct
 * match requirement for requests. Therefore the element name as well as it's namespace
 * is stored in the database even though it is currently not used for matching purposes.</p>
 *
 * @author Iain Shigeoka
 */
public class DbPrivateStore extends BasicModule implements PrivateStore {

    private static final String LOAD_PRIVATE =
        "SELECT value FROM jivePrivate WHERE username=? AND namespace=?";
    private static final String INSERT_PRIVATE =
        "INSERT INTO jivePrivate (value,name,username,namespace) VALUES (?,?,?,?)";
    private static final String UPDATE_PRIVATE =
        "UPDATE jivePrivate SET value=?, name=? WHERE username=? AND namespace=?";

    // currently no delete supported, we can detect an add of an empty element and use that to
    // signal a delete but that optimization doesn't seem necessary.
    private static final String DELETE_PRIVATE =
            "DELETE FROM jivePrivate WHERE userID=? AND name=? AND namespace=?";

    // TODO: As with IQAuthHandler, this is not multi-server safe
    private static boolean enabled;

    public DbPrivateStore() {
        super("Private user data storage");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        DbPrivateStore.enabled = enabled;
        JiveGlobals.setProperty("xmpp.private", Boolean.toString(enabled));
    }

    public void add(String username, Element data) throws UnauthorizedException {
        if (enabled) {
            Connection con = null;
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

    public Element get(String username, Element data) throws UnauthorizedException {
        data.clearContent();
        if (enabled) {
            Connection con = null;
            PreparedStatement pstmt = null;
            try {
                con = DbConnectionManager.getConnection();
                pstmt = con.prepareStatement(LOAD_PRIVATE);
                pstmt.setString(1, username);
                pstmt.setString(2, data.getNamespaceURI());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    StringReader reader = new StringReader(rs.getString(1).trim());
                    Document doc = XPPReader.parseDocument(reader, this.getClass());
                    return doc.getRootElement();
                }
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
        return data;
    }

    public void initialize(Container container) {
        super.initialize(container);
        enabled = JiveGlobals.getBooleanProperty("xmpp.private");
    }
}