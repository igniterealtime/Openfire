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
import org.jivesoftware.database.DbConnectionManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
public class Group {

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
    private Date creationDate;
    private Date modificationDate;
    private Map<String, String> properties;
    private Collection<String> members;
    private Collection<String> administrators;

    /**
     * Constructs a new group.
     *
     * @param provider the group provider.
     * @param name the name.
     * @param description the description.
     * @param creationDate the date the group was created.
     * @param modificationDate the date the group was latest modified.
     * @param members a Collection of the group members (includes administrators).
     * @param administrators a Collection of the group administrators.
     */
    protected Group(GroupProvider provider, String name, String description, Date creationDate,
            Date modificationDate, Collection<String> members, Collection<String> administrators)
    {
        this.provider = provider;
        this.groupManager = GroupManager.getInstance();
        this.name = name;
        this.description = description;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.members = members;
        this.administrators = administrators;
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
            provider.setGroupName(this.name, name);
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
            provider.updateGroup(name, description, creationDate, modificationDate);
            this.description = description;
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the date that the group was created.
     *
     * @return the date the group was created.
     */
    public Date getCreationDate() {
        return creationDate;
    }

    /**
     * Sets the creation date of the group. In most cases, the
     * creation date will default to when the group was entered
     * into the system. However, the date needs to be set manually when
     * importing data.
     *
     * @param creationDate the date the group was created.
     */
    public void setCreationDate(Date creationDate) {
        try {
            provider.updateGroup(name, description, creationDate, modificationDate);
            this.creationDate = creationDate;
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the date that the group was last modified.
     *
     * @return the date the group record was last modified.
     */
    public Date getModificationDate() {
        return modificationDate;
    }

    /**
     * Sets the date the group was last modified. Skin authors
     * should ignore this method since it only intended for
     * system maintenance.
     *
     * @param modificationDate the date the group was modified.
     */
    public void setModificationDate(Date modificationDate) {
        try {
            provider.updateGroup(name, description, creationDate, modificationDate);
            this.modificationDate = modificationDate;
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
        if (properties == null) {
            synchronized (this) {
                if (properties == null) {
                    properties = new ConcurrentHashMap<String, String>();
                    loadProperties();
                }
            }
        }
        // Return a wrapper that will intercept add and remove commands.
        return new PropertiesMap();
    }

    /**
     * Returns a Collection of the group members that are administrators.
     *
     * @return a Collection of the group administrators.
     */
    public Collection<String> getAdministrators() {
        // Return a wrapper that will intercept add and remove commands.
        return new MemberCollection(administrators, true);
    }

    /**
     * Returns a Collection of the group members. Administrators are also
     * considered to be members, so are included in the Collection.
     *
     * @return a Collection of the group members.
     */
    public Collection<String> getMembers() {
        // Return a wrapper that will intercept add and remove commands.
        return new MemberCollection(members, false);
    }

    /**
     * Collection implementation that notifies the GroupProvider of any
     * changes to the collection.
     */
    private class MemberCollection extends AbstractCollection {

        private Collection<String> users;
        private boolean adminCollection;

        public MemberCollection(Collection<String> users, boolean adminCollection) {
            this.users = users;
            this.adminCollection = adminCollection;
        }

        public Iterator iterator() {
            return new Iterator() {

                Iterator iter = users.iterator();
                Object current = null;

                public boolean hasNext() {
                    return iter.hasNext();
                }

                public Object next() {
                    current = iter.next();
                    return current;
                }

                public void remove() {
                    if (current == null) {
                        throw new IllegalStateException();
                    }
                    provider.deleteMember(name, (String)current);
                    iter.remove();
                }
            };
        }

        public int size() {
            return users.size();
        }

        public boolean add(Object member) {
            provider.addMember(name, (String)member, adminCollection);
            return users.add((String)member);
        }
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