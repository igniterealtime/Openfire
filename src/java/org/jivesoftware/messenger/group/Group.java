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

package org.jivesoftware.messenger.group;

import org.jivesoftware.util.Log;
import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.XMPPServer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Groups organize users into a single entity for easier management.
 *
 * @see GroupManager#createGroup(String)
 *
 * @author Matt Tucker
 */
public class Group implements Cacheable {

    private static final String LOAD_PROPERTIES =
        "SELECT name, propValue FROM jiveGroupProp WHERE groupID=?";
    private static final String DELETE_PROPERTY =
        "DELETE FROM jiveGroupProp WHERE groupID=? AND name=?";
    private static final String UPDATE_PROPERTY =
        "UPDATE jiveGroupProp SET propValue=? WHERE name=? AND groupID=?";
    private static final String INSERT_PROPERTY =
        "INSERT INTO jiveGroupProp (groupID, name, propValue) VALUES (?, ?, ?)";

    private GroupProvider provider;
    private GroupManager groupManager;
    private String name;
    private String description;
    private Map<String, String> properties;
    private Collection<String> members = new CopyOnWriteArrayList<String>();
    private Collection<String> administrators = new CopyOnWriteArrayList<String>();

    /**
     * Constructs a new group.
     *
     * @param provider the group provider.
     * @param name the name.
     * @param description the description.
     * @param members a Collection of the group members.
     * @param administrators a Collection of the group administrators.
     */
    protected Group(GroupProvider provider, String name, String description,
            Collection<String> members, Collection<String> administrators)
    {
        this.provider = provider;
        this.groupManager = GroupManager.getInstance();
        this.name = name;
        this.description = description;
        this.members.addAll(members);
        this.administrators.addAll(administrators);
    }

    /**
     * Returns the name of the group. For example, 'XYZ Admins'.
     *
     * @return the name of the group.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the group. For example, 'XYZ Admins'. This
     * method is restricted to those with group administration permission.
     *
     * @param name the name for the group.
     */
    public void setName(String name) {
        try {
            provider.setName(this.name, name);
            groupManager.groupCache.remove(this.name);
            this.name = name;
            groupManager.groupCache.put(name, this);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of the XYZ forum'.
     *
     * @return the description of the group.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of the group. The description often
     * summarizes a group's function, such as 'Administrators of
     * the XYZ forum'. This method is restricted to those with group
     * administration permission.
     *
     * @param description the description of the group.
     */
    public void setDescription(String description) {
        try {
            provider.setDescription(name, description);
            this.description = description;
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns all extended properties of the group. Groups
     * have an arbitrary number of extended properties.
     *
     * @return the extended properties.
     */
    public Map<String,String> getProperties() {
        synchronized (this) {
            if (properties == null) {
                properties = new ConcurrentHashMap<String, String>();
                loadProperties();
            }
        }
        // Return a wrapper that will intercept add and remove commands.
        return new PropertiesMap();
    }

    /**
     * Returns a Collection of the group administrators. Use <code>getUsers()</code> to get the
     * complete list of group users.
     *
     * @return a Collection of the group administrators.
     */
    public Collection<String> getAdmins() {
        return Collections.unmodifiableCollection(administrators);
    }

    /**
     * Returns a Collection of the group members. Use <code>getUsers()</code> to get the complete
     * list of group users.
     *
     * @return a Collection of the group members.
     */
    public Collection<String> getMembers() {
        return Collections.unmodifiableCollection(members);
    }

    /**
     * Returns a Collection with all the group users. The collection will include group members
     * as well as group administrators.
     *
     * @return a Collection with all the group users.
     */
    public Collection<String> getUsers() {
        Collection<String> answer = new ArrayList<String>(members);
        answer.addAll(administrators);
        return Collections.unmodifiableCollection(answer);
    }

    /**
     * Returns true if the provided username belongs to a user of the group.
     *
     * @param username the username to check.
     * @return true if the provided username belongs to a user of the group.
     */
    public boolean isUser(String username) {
        return members.contains(username) || administrators.contains(username);
    }

    /**
     * Returns true if the provided username belongs to a member of the group.
     *
     * @param username the username to check.
     * @return true if the provided username belongs to a member of the group.
     */
    public boolean isMember(String username) {
        return members.contains(username);
    }

    /**
     * Returns true if the provided username belongs to an administrator of the group.
     *
     * @param username the username to check.
     * @return true if the provided username belongs to an administrator of the group.
     */
    public boolean isAdmin(String username) {
        return administrators.contains(username);
    }

    /**
     * Adds a new user as a member of the group. The roster of all group users that are currently
     * logged into the server will be updated.
     *
     * @param user the user to add as a member of the goup.
     */
    public void addMember(String user) {
        if (members.contains(user)) {
            return;
        }
        members.add(user);
        userAdded(user, false);
    }

    /**
     * Removes a member from the group.  The roster of all group users that are currently
     * logged into the server will be updated.
     *
     * @param user the user to remove as a member of the group.
     */
    public void removeMember(String user) {
        if (members.remove(user)) {
            userRemoved(user);
        }
    }

    /**
     * Adds a new user as an administrator of the group. The roster of all group users that are
     * currently logged into the server will be updated.
     *
     * @param user the user to add as an administrator of the goup.
     */
    public void addAdmin(String user) {
        if (administrators.contains(user)) {
            return;
        }
        administrators.add(user);
        userAdded(user, true);
    }

    /**
     * Removes an administrator from the group.  The roster of all group users that are currently
     * logged into the server will be updated.
     *
     * @param user the user to remove as an administrator of the group.
     */
    public void removeAdmin(String user) {
        if (administrators.remove(user)) {
            userRemoved(user);
        }
    }

    /**
     * Update backend store and update group users' roster.
     *
     * @param user the user that was added to the group.
     */
    private void userAdded(String user, boolean administrator) {
        // Add the new group user to the backend store
        provider.addMember(name, user, administrator);
        // Update the group users' roster
        XMPPServer.getInstance().getRosterManager().groupUserAdded(this, user);
    }

    /**
     * Update backend store and update group users' roster.
     *
     * @param user the user that was removed from the group.
     */
    private void userRemoved(String user) {
        // Remove the group user from the backend store
        provider.deleteMember(name, user);
        // Update the group users' roster
        XMPPServer.getInstance().getRosterManager().groupUserDeleted(this, user);
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(name);
        size += CacheSizes.sizeOfString(description);
        return size;
    }

    /**
     * Map implementation that updates the database when properties are modified.
     */
    private class PropertiesMap extends AbstractMap {

        public Object put(Object key, Object value) {
            if (properties.containsKey(key)) {
                updateProperty((String)key, (String)value);
            }
            else {
                insertProperty((String)key, (String)value);
            }
            return properties.put((String)key, (String)value);
        }

        public Set<Entry> entrySet() {
            return new PropertiesEntrySet();
        }
    }

    /**
     * Set implementation that updates the database when properties are deleted.
     */
    private class PropertiesEntrySet extends AbstractSet {

        public int size() {
            return properties.entrySet().size();
        }

        public Iterator iterator() {
            return new Iterator() {

                Iterator iter = properties.entrySet().iterator();
                Map.Entry current = null;

                public boolean hasNext() {
                    return iter.hasNext();
                }

                public Object next() {
                    current = (Map.Entry)iter.next();
                    return iter.next();
                }

                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    deleteProperty((String)current.getKey());
                    iter.remove();
                }
            };
        }
    }

    private void loadProperties() {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(LOAD_PROPERTIES);
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                properties.put(rs.getString(1), rs.getString(2));
            }
            rs.close();
        }
        catch (SQLException sqle) {
            Log.error(sqle);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void insertProperty(String propName, String propValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(INSERT_PROPERTY);
            pstmt.setString(1, name);
            pstmt.setString(2, propName);
            pstmt.setString(3, propValue);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void updateProperty(String propName, String propValue) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(UPDATE_PROPERTY);
            pstmt.setString(1, propValue);
            pstmt.setString(2, propName);
            pstmt.setString(3, name);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }

    public void deleteProperty(String propName) {
        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(DELETE_PROPERTY);
            pstmt.setString(1, name);
            pstmt.setString(2, propName);
            pstmt.executeUpdate();
        }
        catch (SQLException e) {
            Log.error(e);
        }
        finally {
            try { if (pstmt != null) pstmt.close(); }
            catch (Exception e) { Log.error(e); }
            try { if (con != null) con.close(); }
            catch (Exception e) { Log.error(e); }
        }
    }
}