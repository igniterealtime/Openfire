/**
 * $Revision: 3034 $
 * $Date: 2005-11-04 21:02:33 -0300 (Fri, 04 Nov 2005) $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software.
 * Use is subject to license terms.
 */

package org.jivesoftware.openfire.plugin.spark;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.database.JiveID;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents a Bookmark. Each bookmark can apply to a set of users and groups, or to
 * everyone in the system. There are two types of bookmarks:<ul>
 * <p/>
 * <li>{@link Type#url url} -- a URL.
 * <li>{@link Type#group_chat group chat} -- a group chat conference room.
 * </ul>
 * <p/>
 * Each bookmark has a name and value. The value of the bokmark is either a URL or
 * a conference room address, depending on the bookmark type. Each bookmark type has
 * optional attributes stored as properties:<ul>
 * <p/>
 * <li><tt>autojoin</tt> ({@link Type#group_chat group chat} bookmarks): when set
 * to <tt>true</tt>, the client is instructed to automatically join the
 * conference room when starting up.</li>
 * <li><tt>rss</tt> ({@link Type#url url} bookmarks): when set
 * to <tt>true</tt>, indicates that the bookmark is for an RSS feed.</li>
 * <li><tt>atom</tt> ({@link Type#url url} bookmarks): when set
 * to <tt>true</tt>, indicates that the bookmark is for an ATOM feed.</li>
 *
 * @author Derek DeMoro, Matt Tucker
 */
@JiveID(55)
public class Bookmark {

    private static final String INSERT_BOOKMARK =
            "INSERT INTO ofBookmark(bookmarkID, bookmarkType, bookmarkName, bookmarkValue, " +
                    "isGlobal) VALUES (?,?,?,?,?)";
    private static final String INSERT_BOOKMARK_PERMISSIONS =
            "INSERT INTO ofBookmarkPerm(bookmarkID, bookmarkType, name) VALUES(?,?,?)";
    private static final String LOAD_BOOKMARK_PERMISSIONS =
            "SELECT bookmarkType, name FROM ofBookmarkPerm WHERE bookmarkID=?";
    private static final String SAVE_BOOKMARK_PERMISSIONS =
            "UPDATE ofBookmarkPerm SET bookmarkType=?, name=? WHERE bookmarkID=?";
    private static final String DELETE_BOOKMARK_PERMISSIONS =
            "DELETE from ofBookmarkPerm WHERE bookmarkID=?";
    private static final String SAVE_BOOKMARK =
            "UPDATE ofBookmark SET bookmarkType=?, bookmarkName=?, bookmarkValue=?, isGlobal=? " +
                    "WHERE bookmarkID=?";
    private static final String LOAD_BOOKMARK =
            "SELECT bookmarkType, bookmarkName, bookmarkValue, isGlobal FROM " +
                    "ofBookmark WHERE bookmarkID=?";

    private static final String LOAD_PROPERTIES =
            "SELECT name, propValue FROM ofBookmarkProp WHERE bookmarkID=?";
    private static final String INSERT_PROPERTY =
            "INSERT INTO ofBookmarkProp (bookmarkID,name,propValue) VALUES (?,?,?)";
    private static final String UPDATE_PROPERTY =
            "UPDATE ofBookmarkProp SET propValue=? WHERE name=? AND bookmarkID=?";
    private static final String DELETE_PROPERTY =
            "DELETE FROM ofBookmarkProp WHERE bookmarkID=? AND name=?";


    private long bookmarkID;
    private Type type;
    private String name;
    private String value;
    private boolean global;
    private Collection<String> users;
    private Collection<String> groups;
    private Map<String, String> properties;

    private static int USERS = 0;
    private static int GROUPS = 1;

    /**
     * Creates a new bookmark.
     *
     * @param type  the bookmark type.
     * @param name  the name of the bookmark.
     * @param value the value of the bookmark.
     */
    public Bookmark(Type type, String name, String value) {
        this.type = type;
        this.name = name;
        this.value = value;
        properties = new HashMap<String, String>();
        try {
            insertIntoDb();
            insertBookmarkPermissions();
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Loads an existing bookmark based on its ID.
     *
     * @param bookmarkID the bookmark ID.
     * @throws NotFoundException if the bookmark does not exist or could not be loaded.
     */
    public Bookmark(long bookmarkID) throws NotFoundException {
        this.bookmarkID = bookmarkID;
        loadFromDb();
        loadPermissions();
    }

    /**
     * Returns the unique ID of the bookmark.
     *
     * @return the bookmark ID.
     */
    public long getBookmarkID() {
        return bookmarkID;
    }

    /**
     * Returns the {@link Type type} of the bookmark.
     *
     * @return the bookmark type.
     */
    public Type getType() {
        return type;
    }

    /**
     * Sets the bookmark {@link Type type}.
     *
     * @param type the type of the bookmark.
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * Returns the name of the bookmark; either the name of the URL or name of the
     * conference room.
     *
     * @return the name of the bookmark.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the bookmark; either the name of the URL or name of the
     * conference room.
     *
     * @param name the name of the bookmark.
     */
    public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Bookmark name must not be null.");
        }
        this.name = name;
        saveToDb();
    }

    /**
     * Returns the value of the bookmark; either a URL or a conference room address.
     *
     * @return the value of the bookmark.
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value of the bookmark; either a URL or a conference room address.
     *
     * @param value the value of the bookmark.
     */
    public void setValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Bookmark value must not be null.");
        }
        this.value = value;
        saveToDb();
    }

    /**
     * Returns the collection of usersnames that have been assigned the bookmark.
     *
     * @return the collection of usernames that have been assigned the bookmark.
     */
    public Collection<String> getUsers() {
        return users;
    }

    /**
     * Sets the collection of usernames that have been assigned the bookmark.
     *
     * @param users the collection of usernames.
     */
    public void setUsers(Collection<String> users) {
        this.users = users;
        saveToDb();
        insertBookmarkPermissions();
    }

    /**
     * Returns the collection of group names that have been assigned the the bookmark.
     *
     * @return a collection of group names.
     */
    public Collection<String> getGroups() {
        return groups;
    }

    public void setGroups(Collection<String> groups) {
        this.groups = groups;
        saveToDb();
        insertBookmarkPermissions();
    }

    /**
     * Returns true if this bookmark is applied to all users on the server. When true,
     * the values for {@link #getGroups()} and {@link #getUsers()} have no effect.
     *
     * @return true if a global bookmark.
     */
    public boolean isGlobalBookmark() {
        return global;
    }

    /**
     * Sets whether the bookmark is applied to all users on the server. When true,
     * the values for {@link #getGroups()} and {@link #getUsers()} have no effect.
     *
     * @param global true if this is a global bookmark.
     */
    public void setGlobalBookmark(boolean global) {
        this.global = global;
        saveToDb();
    }

    /**
     * Returns an extended property. Each bookmark can have
     * an arbitrary number of extended properties. This allows for enhanced
     * functionality that is not part of the base interface.
     *
     * @param name the name of the property to get.
     * @return the value of the property specified by <tt>name</tt>.
     */
    public String getProperty(String name) {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        return properties.get(name);
    }

    /**
     * Return all immediate children property values of a parent property as an
     * unmodifiable Collection of String values. A parent/child relationship is
     * denoted by the "." character. For example, given the properties <tt>X.Y.A</tt>,
     * <tt>X.Y.B</tt>, <tt>X.Y.C</tt> and <tt>X.Y.C.D</tt>, then the immediate child
     * properties of <tt>X.Y</tt> are <tt>X.Y.A</tt>, <tt>X.Y.B</tt>, and <tt>X.Y.C</tt>
     * (the value of <tt>X.Y.C.D</tt> would not be returned using this method).
     *
     * @param parentName the name of the parent property to return the children for.
     * @return all Collection of all child property values for the given parent.
     */
    public Collection<String> getProperties(String parentName) {
        Object [] keys = properties.keySet().toArray();
        ArrayList<String> results = new ArrayList<String>();
        for (int i = 0, n = keys.length; i < n; i++) {
            String key = (String)keys[i];
            if (key.startsWith(parentName)) {
                if (key.equals(parentName)) {
                    continue;
                }
                if (key.substring(parentName.length()).lastIndexOf(".") == 0) {
                    results.add(properties.get(key));
                }
            }
        }
        return Collections.unmodifiableCollection(results);
    }

    /**
     * Sets an extended property. Each bookmark can have an
     * arbitrary number of extended properties. This allows for enhanced
     * functionality that is not part of the base interface.<p>
     * <p/>
     * If the property referenced by <code>name</code> already exists, its
     * value will be updated.
     *
     * @param name  the name of the property to set.
     * @param value the new value for the property.
     */
    public void setProperty(String name, String value) {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        // See if we need to update a property value or insert a new one.
        if (properties.containsKey(name)) {
            // Only update the value in the database if the property value
            // has changed.
            if (!(value.equals(properties.get(name)))) {
                properties.put(name, value);
                updatePropertyInDb(name, value);
            }
        }
        else {
            properties.put(name, value);
            insertPropertyIntoDb(name, value);
        }
    }

    /**
     * Deletes an extended property. If the property specified by
     * <code>name</code> does not exist, this method will do nothing.
     *
     * @param name the name of the property to delete.
     */
    public void deleteProperty(String name) {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        // Only delete the property if it exists.
        if (properties.containsKey(name)) {
            properties.remove(name);
            deletePropertyFromDb(name);
        }
    }

    /**
     * Returns an Iterator for the names of the extended properties.
     *
     * @return an Iterator for the names of the extended properties.
     */
    public Iterator getPropertyNames() {
        if (properties == null) {
            loadPropertiesFromDb();
        }
        return Collections.unmodifiableSet(properties.keySet()).iterator();
    }

    /**
     * Tye type of the bookmark.
     */
    @SuppressWarnings({"UnnecessarySemicolon"}) // Fix for QDox Source inspector
    public enum Type {

        /**
         * A URL (typically HTTP).
         */
        url,

        /**
         * A group chat conference room address.
         */
        group_chat;
    }


    /**
     * Inserts a new bookmark into the database.
     */
    private void insertIntoDb() throws SQLException {
        this.bookmarkID = SequenceManager.nextID(this);
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_BOOKMARK);
            pstmt.setLong(1, bookmarkID);
            pstmt.setString(2, type.toString());
            pstmt.setString(3, name);
            pstmt.setString(4, value);
            pstmt.setInt(5, global ? 1 : 0);
            pstmt.executeUpdate();
            pstmt.close();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    private void insertBookmarkPermissions() {
        // Delete other permission.
        try {
            deleteBookmarkPermissions();
        }
        catch (SQLException e) {
            Log.error(e);
        }

        // Persist users
        if (users != null) {
            for (String user : users) {
                try {
                    insertBookmarkPermission(USERS, user);
                }
                catch (SQLException e) {
                    Log.error(e);
                }
            }
        }

        if (groups != null) {
            for (String group : groups) {
                try {
                    insertBookmarkPermission(GROUPS, group);
                }
                catch (SQLException e) {
                    Log.error(e);
                }
            }
        }
    }

    private void insertBookmarkPermission(int type, String name) throws SQLException {
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            PreparedStatement pstmt = con.prepareStatement(INSERT_BOOKMARK_PERMISSIONS);
            pstmt.setLong(1, bookmarkID);
            pstmt.setInt(2, type);
            pstmt.setString(3, name);
            pstmt.executeUpdate();
            pstmt.close();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    private void deleteBookmarkPermissions() throws SQLException {
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            PreparedStatement pstmt = con.prepareStatement(DELETE_BOOKMARK_PERMISSIONS);
            pstmt.setLong(1, bookmarkID);
            pstmt.executeUpdate();
            pstmt.close();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            throw sqle;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    private void loadPermissions() {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        List<String> usersList = new ArrayList<String>();
        List<String> groupList = new ArrayList<String>();
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_BOOKMARK_PERMISSIONS);
            pstmt.setLong(1, bookmarkID);
            rs = pstmt.executeQuery();
            while (rs.next()) {
                int type = rs.getInt(1);
                String name = rs.getString(2);
                if (type == USERS) {
                    usersList.add(name);
                }
                else {
                    groupList.add(name);
                }
            }

            rs.close();
            pstmt.close();
            users = usersList;
            groups = groupList;
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }


    /**
     * Loads a bookmark from the database.
     *
     * @throws NotFoundException if the bookmark could not be loaded.
     */
    private void loadFromDb() throws NotFoundException {
        Connection con = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_BOOKMARK);
            pstmt.setLong(1, bookmarkID);
            rs = pstmt.executeQuery();
            if (!rs.next()) {
                throw new NotFoundException("Bookmark not found: " + bookmarkID);
            }
            this.type = Type.valueOf(rs.getString(1));
            this.name = rs.getString(2);
            this.value = rs.getString(3);
            this.global = rs.getInt(4) == 1;
            rs.close();
            pstmt.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(rs, pstmt, con);
        }
    }

    /**
     * Saves a bookmark to the database.
     */
    private void saveToDb() {
        Connection con = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            PreparedStatement pstmt = con.prepareStatement(SAVE_BOOKMARK);
            pstmt.setString(1, type.toString());
            pstmt.setString(2, name);
            pstmt.setString(3, value);
            pstmt.setInt(4, global ? 1 : 0);
            pstmt.setLong(5, bookmarkID);
            pstmt.executeUpdate();
            pstmt.close();
        }
        catch (SQLException sqle) {
            abortTransaction = true;
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeTransactionConnection(con, abortTransaction);
        }
    }

    /**
     * Loads properties from the database.
     */
    private synchronized void loadPropertiesFromDb() {
        this.properties = new Hashtable<String, String>();
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            pstmt.setLong(1, bookmarkID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String name = rs.getString(1);
                String value = rs.getString(2);
                properties.put(name, value);
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
    }

    /**
     * Inserts a new property into the datatabase.
     */
    private void insertPropertyIntoDb(String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setLong(1, bookmarkID);
            pstmt.setString(2, name);
            pstmt.setString(3, value);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    /**
     * Updates a property value in the database.
     */
    private void updatePropertyInDb(String name, String value) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, value);
            pstmt.setString(2, name);
            pstmt.setLong(3, bookmarkID);
            pstmt.executeUpdate();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }

    /**
     * Deletes a property from the db.
     */
    private synchronized void deletePropertyFromDb(String name) {
        Connection con = null;
        PreparedStatement pstmt = null;
        boolean abortTransaction = false;
        try {
            con = DbConnectionManager.getTransactionConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setLong(1, bookmarkID);
            pstmt.setString(2, name);
            pstmt.execute();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
            abortTransaction = true;
        }
        finally {
            DbConnectionManager.closeTransactionConnection(pstmt, con, abortTransaction);
        }
    }
}
