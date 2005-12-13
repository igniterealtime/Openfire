/**
 * $RCSfile$
 * $Revision: 3191 $
 * $Date: 2005-12-12 13:41:22 -0300 (Mon, 12 Dec 2005) $
 *
 * Copyright (C) 2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.ldap;

import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.group.Group;
import org.jivesoftware.wildfire.group.GroupProvider;
import org.jivesoftware.wildfire.group.GroupNotFoundException;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;
import java.text.MessageFormat;
import java.util.*;

/**
 * LDAP implementation of the GroupProvider interface.  All data in the directory is
 * treated as read-only so any set operations will result in an exception.
 *
 * @author Greg Ferguson and Cameron Moore
 */
public class LdapGroupProvider implements GroupProvider {

    private LdapManager manager;
    private UserManager userManager;
    private int groupCount;
    private long expiresStamp;
    private String[] standardAttributes;

    /**
     * Constructor of the LdapGroupProvider class.
     * Gets an LdapManager instance from the LdapManager class.
     */
    public LdapGroupProvider() {
        manager = LdapManager.getInstance();
        userManager = UserManager.getInstance();
        groupCount = -1;
        expiresStamp = System.currentTimeMillis();
        standardAttributes = new String[3];
        standardAttributes[0] = manager.getGroupNameField();
        standardAttributes[1] = manager.getGroupDescriptionField();
        standardAttributes[2] = manager.getGroupMemberField();
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param name the name of the group to create.
     * @throws UnsupportedOperationException when called.
     */
    public Group createGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param name the name of the group to delete
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public Group getGroup(String group) throws GroupNotFoundException {
        String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        String searchFilter = "(&" + filter + "(" +
                manager.getGroupNameField() + "=" + group + "))";
        Collection<Group> groups = populateGroups(searchForGroups(searchFilter, standardAttributes));
        if (groups.size() > 1) {
            //if multiple groups found throw exception
            throw new GroupNotFoundException("Too many groups with name " + group + " were found.");
        }
        for (Group g : groups) {
            return g; //returns the first group found
        }
        throw new GroupNotFoundException("Group with name " + group + " not found.");
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws UnsupportedOperationException when called.
     */
    public void setName(String oldName, String newName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws UnsupportedOperationException when called.
     */
    public void setDescription(String name, String description) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public int getGroupCount() {
        // Cache group count for 5 minutes.
        if (groupCount != -1 && System.currentTimeMillis() < expiresStamp) {
            return groupCount;
        }
        int count = 0;

        if (manager.isDebugEnabled()) {
            Log.debug("Trying to get the number of groups in the system.");
        }

        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        String returningAttributes[] = {manager.getGroupNameField()};
        NamingEnumeration<SearchResult> answer = searchForGroups(searchFilter, returningAttributes);
        for (; answer.hasMoreElements(); count++) {
            try {
                answer.next();
            }
            catch (Exception e) {
            }
        }

        this.groupCount = count;
        this.expiresStamp = System.currentTimeMillis() + JiveConstants.MINUTE * 5;
        return count;
    }

    public Collection<Group> getGroups() {
        String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        return populateGroups(searchForGroups(filter, standardAttributes));
    }

    public Collection<Group> getGroups(int start, int num) {
        // Get an enumeration of all groups in the system
        String searchFilter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
        NamingEnumeration<SearchResult> answer = searchForGroups(searchFilter, standardAttributes);

        // Place all groups that are wanted into an enumeration
        Vector<SearchResult> v = new Vector<SearchResult>();
        for (int i = 1; answer.hasMoreElements() && i <= (start + num); i++) {
            try {
                SearchResult sr = answer.next();
                if (i >= start) {
                    v.add(sr);
                }
            }
            catch (Exception e) {
                // Ignore.
            }
        }

        return populateGroups(v.elements());
    }

    public Collection<Group> getGroups(JID user) {
        XMPPServer server = XMPPServer.getInstance();
        String username = server.isLocal(user) ? JID.unescapeNode(user.getNode()) : user.toString();
        if (!manager.isPosixMode()) {
            try {
                username = manager.findUserDN(username) + "," +
                        manager.getBaseDN();
            }
            catch (Exception e) {
                return new ArrayList<Group>();
            }
        }

        String filter = MessageFormat.format(manager.getGroupSearchFilter(), username);
        return populateGroups(searchForGroups(filter, standardAttributes));
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param groupName name of a group.
     * @param user the JID of the user to add
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void addMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param groupName the naame of a group.
     * @param user the JID of the user with new privileges
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void updateMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @param groupName the name of a group.
     * @param user the JID of the user to delete.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteMember(String groupName, JID user)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because
     * LDAP groups are read-only.
     *
     * @return true because all LDAP functions are read-only.
     */
    public boolean isReadOnly() {
        return true;
    }

    /**
     * An auxilary method used to perform LDAP queries based on a
     * provided LDAP search filter.
     *
     * @param searchFilter LDAP search filter used to query.
     * @return an enumeration of SearchResult.
     */
    private NamingEnumeration<SearchResult> searchForGroups(String searchFilter,
            String[] returningAttributes) {
        if (manager.isDebugEnabled()) {
            Log.debug("Trying to find all groups in the system.");
        }
        DirContext ctx = null;
        NamingEnumeration<SearchResult> answer = null;
        try {
            ctx = manager.getContext();
            if (manager.isDebugEnabled()) {
                Log.debug("Starting LDAP search...");
                Log.debug("Using groupSearchFilter: " + searchFilter);
            }

            // Search for the dn based on the groupname.
            SearchControls searchControls = new SearchControls();
            searchControls.setReturningAttributes(returningAttributes);
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            answer = ctx.search("", searchFilter, searchControls);

            if (manager.isDebugEnabled()) {
                Log.debug("... search finished");
            }
        }
        catch (Exception e) {
            if (manager.isDebugEnabled()) {
                Log.debug("Error while searching for groups.", e);
            }
        }
        return answer;
    }

    /**
     * An auxilary method used to populate LDAP groups based on a
     * provided LDAP search result.
     *
     * @param answer LDAP search result.
     * @return a collection of groups.
     */
    private Collection<Group> populateGroups(Enumeration<SearchResult> answer) {
        if (manager.isDebugEnabled()) {
            Log.debug("Starting to populate groups with users.");
        }

        TreeMap<String, Group> groups = new TreeMap<String, Group>();

        DirContext ctx = null;
        try {
            ctx = manager.getContext();
        }
        catch (Exception e) {
            return new ArrayList<Group>();
        }

        SearchControls ctrls = new SearchControls();
        ctrls.setReturningAttributes(new String[]{manager.getUsernameField()});
        ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        String userSearchFilter = MessageFormat.format(manager.getSearchFilter(), "*");
        XMPPServer server = XMPPServer.getInstance();
        String serverName = server.getServerInfo().getName();

        while (answer.hasMoreElements()) {
            String name = "";
            try {
                Attributes a = (((SearchResult) answer.nextElement()).getAttributes());
                String description;
                try {
                    name = ((String) ((a.get(manager.getGroupNameField())).get()));
                    description = ((String) ((a.get(manager.getGroupDescriptionField())).get()));
                }
                catch (Exception e) {
                    description = "";
                }
                TreeSet<JID> members = new TreeSet<JID>();
                Attribute member = a.get(manager.getGroupMemberField());
                NamingEnumeration ne = member.getAll();
                while (ne.hasMore()) {
                    String username = (String) ne.next();
                    if (!manager.isPosixMode()) {   //userName is full dn if not posix
                        try {
                            // Get the CN using LDAP
                            LdapName ldapname = new LdapName(username);
                            String ldapcn = ldapname.get(ldapname.size() - 1);

                            // We have to do a new search to find the username field

                            String combinedFilter = "(&(" + ldapcn + ")" + userSearchFilter + ")";
                            NamingEnumeration usrAnswer = ctx.search("", combinedFilter, ctrls);
                            if (usrAnswer.hasMoreElements()) {
                                username = (String) ((SearchResult) usrAnswer.next()).getAttributes().get(
                                        manager.getUsernameField()).get();
                            }
                            else {
                                throw new UserNotFoundException();
                            }
                        }
                        catch (Exception e) {
                            if (manager.isDebugEnabled()) {
                                Log.debug("Error populating user with DN: " + username, e);
                            }
                        }
                    }
                    // A search filter may have been defined in the LdapUserProvider.
                    // Therefore, we have to try to load each user we found to see if
                    // it passes the filter.
                    try {
                        JID userJID = null;
                        // Create JID of local user if JID does not match a component's JID
                        if (!username.contains(serverName)) {
                            // In order to lookup a username from the manager, the username
                            // must be a properly escaped JID node.
                            String escapedUsername = JID.escapeNode(username);
                            userManager.getUser(escapedUsername);
                            // No exception, so the user must exist. Add the user as a group
                            // member using the escaped username.
                            userJID = server.createJID(escapedUsername, null);
                        }
                        else {
                            // This is a JID of a component or node of a server's component
                            userJID = new JID(username);
                        }
                        members.add(userJID);
                    }
                    catch (UserNotFoundException e) {
                        if (manager.isDebugEnabled()) {
                            Log.debug("User not found: " + username);
                        }
                    }
                }
                if (manager.isDebugEnabled()) {
                    Log.debug("Adding group \"" + name + "\" with " + members.size() + " members.");
                }
                Group g = new Group(this, name, description, members, new ArrayList<JID>());
                groups.put(name, g);
            }
            catch (Exception e) {
                if (manager.isDebugEnabled()) {
                    Log.debug("Error while populating group, " + name + ".", e);
                }
            }
        }
        if (manager.isDebugEnabled()) {
            Log.debug("Finished populating group(s) with users.");
        }
        try {
            ctx.close();
        }
        catch (Exception e) {
        }

        return groups.values();
    }
}