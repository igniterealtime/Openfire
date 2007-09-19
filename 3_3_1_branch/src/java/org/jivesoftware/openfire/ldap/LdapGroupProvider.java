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

package org.jivesoftware.openfire.ldap;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupProvider;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.SortControl;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LDAP implementation of the GroupProvider interface.  All data in the directory is treated as
 * read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker, Greg Ferguson and Cameron Moore
 */
public class LdapGroupProvider implements GroupProvider {

    private LdapManager manager;
    private UserManager userManager;
    private String[] standardAttributes;

    /**
     * Constructs a new LDAP group provider.
     */
    public LdapGroupProvider() {
        manager = LdapManager.getInstance();
        userManager = UserManager.getInstance();
        standardAttributes = new String[3];
        standardAttributes[0] = manager.getGroupNameField();
        standardAttributes[1] = manager.getGroupDescriptionField();
        standardAttributes[2] = manager.getGroupMemberField();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param name the name of the group to create.
     * @throws UnsupportedOperationException when called.
     */
    public Group createGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param name the name of the group to delete
     * @throws UnsupportedOperationException when called.
     */
    public void deleteGroup(String name) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public Group getGroup(String groupName) throws GroupNotFoundException {
        Collection<Group> groups;
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();

            // Search for the dn based on the group name.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(standardAttributes);
            String filter = MessageFormat.format(manager.getGroupSearchFilter(), groupName);
            NamingEnumeration<SearchResult> answer = ctx.search("", filter, searchControls);

            groups = populateGroups(answer);
            // Close the enumeration.
            answer.close();
            if (groups.size() == 1) {
                return groups.iterator().next();
            }
        }
        catch (Exception e) {
            Log.error(e);
            throw new GroupNotFoundException(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        if (groups.size() > 1) {
            // If multiple groups found, throw exception.
            throw new GroupNotFoundException(
                    "Too many groups with name " + groupName + " were found.");
        }
        throw new GroupNotFoundException("Group with name " + groupName + " not found.");
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param oldName the current name of the group.
     * @param newName the desired new name of the group.
     * @throws UnsupportedOperationException when called.
     */
    public void setName(String oldName, String newName) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param name the group name.
     * @param description the group description.
     * @throws UnsupportedOperationException when called.
     */
    public void setDescription(String name, String description)
            throws UnsupportedOperationException
    {
        throw new UnsupportedOperationException();
    }

    public int getGroupCount() {
        if (manager.isDebugEnabled()) {
            Log.debug("Trying to get the number of groups in the system.");
        }

        int count = 0;
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();

            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            while (answer.hasMoreElements()) {
                answer.next();
                count++;
            }
            // Close the enumeration.
            answer.close();
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }

        return count;
    }

    public Collection<String> getGroupNames() {
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on group name field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            while (answer.hasMoreElements()) {
                // Get the next group.
                String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getGroupNameField()).get();
                // Escape group name and add to results.
                groupNames.add(JID.escapeNode(groupName));
            }
            // Close the enumeration.
            answer.close();
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return groupNames;
    }

    public Collection<String> getGroupNames(int startIndex, int numResults) {
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on group name field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");

            // TODO: used paged results if supported by LDAP server.
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            for (int i=0; i < startIndex; i++) {
                if (answer.hasMoreElements()) {
                    answer.next();
                }
                else {
                    return Collections.emptyList();
                }
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next group.
                    String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getGroupNameField()).get();
                    // Escape group name and add to results.
                    groupNames.add(JID.escapeNode(groupName));
                }
                else {
                    break;
                }
            }
            // Close the enumeration.
            answer.close();
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return groupNames;
    }

    public Collection<String> getGroupNames(JID user) {
        // Get DN of specified user
        XMPPServer server = XMPPServer.getInstance();
        String username;
        if (!manager.isPosixMode()) {
            // Check if the user exists (only if user is a local user)
            if (!server.isLocal(user)) {
                return Collections.emptyList();
            }
            username = JID.unescapeNode(user.getNode());
            try {
                username = manager.findUserDN(username) + "," + manager.getBaseDN();
            }
            catch (Exception e) {
                Log.error("Could not find user in LDAP " + username);
                return Collections.emptyList();
            }
        }
        else {
            username = server.isLocal(user) ? JID.unescapeNode(user.getNode()) : user.toString();
        }
        // Do nothing if the user is empty or null
        if (username == null || "".equals(username)) {
            return Collections.emptyList();
        }
        // Perform the LDAP query
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Search for the dn based on the group name.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });

            StringBuilder filter = new StringBuilder();
            filter.append("(&");
            filter.append(MessageFormat.format(manager.getGroupSearchFilter(), "*"));
            filter.append("(").append(manager.getGroupMemberField()).append("=").append(username);
            filter.append("))");
            if (Log.isDebugEnabled()) {
                Log.debug("Trying to find group names for user: " + user + " using query: " + filter.toString());
            }
            NamingEnumeration answer = ctx.search("", filter.toString(), searchControls);
            while (answer.hasMoreElements()) {
                // Get the next group.
                String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getGroupNameField()).get();
                // Escape group name and add to results.
                groupNames.add(JID.escapeNode(groupName));
            }
            // Close the enumeration.
            answer.close();
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error("Error getting groups for user: " + user, e);
            return Collections.emptyList();
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return groupNames;
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
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
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param groupName the naame of a group.
     * @param user the JID of the user with new privileges
     * @param administrator true if is an administrator.
     * @throws UnsupportedOperationException when called.
     */
    public void updateMember(String groupName, JID user, boolean administrator)
            throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Always throws an UnsupportedOperationException because LDAP groups are read-only.
     *
     * @param groupName the name of a group.
     * @param user the JID of the user to delete.
     * @throws UnsupportedOperationException when called.
     */
    public void deleteMember(String groupName, JID user) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true because LDAP groups are read-only.
     *
     * @return true because all LDAP functions are read-only.
     */
    public boolean isReadOnly() {
        return true;
    }

    public Collection<String> search(String query) {
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        // Make the query be a wildcard search by default. So, if the user searches for
        // "Test", make the search be "Test*" instead.
        if (!query.endsWith("*")) {
            query = query + "*";
        }
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the group name.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            StringBuilder filter = new StringBuilder();
            filter.append("(").append(manager.getGroupNameField()).append("=").append(query).append(")");
            NamingEnumeration answer = ctx.search("", filter.toString(), searchControls);
            while (answer.hasMoreElements()) {
                // Get the next group.
                String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                        manager.getGroupNameField()).get();
                // Escape group name and add to results.
                groupNames.add(JID.escapeNode(groupName));
            }
            // Close the enumeration.
            answer.close();
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return groupNames;
    }

    public Collection<String> search(String query, int startIndex, int numResults) {
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        // Make the query be a wildcard search by default. So, if the user searches for
        // "Test", make the search be "Test*" instead.
        if (!query.endsWith("*")) {
            query = query + "*";
        }
        List<String> groupNames = new ArrayList<String>();
        LdapContext ctx = null;
        try {
            ctx = manager.getContext();
            // Sort on username field.
            Control[] searchControl = new Control[]{
                new SortControl(new String[]{manager.getGroupNameField()}, Control.NONCRITICAL)
            };
            ctx.setRequestControls(searchControl);

            // Search for the dn based on the group name.
            SearchControls searchControls = new SearchControls();
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }
            searchControls.setReturningAttributes(new String[] { manager.getGroupNameField() });
            StringBuilder filter = new StringBuilder();
            filter.append("(").append(manager.getGroupNameField()).append("=").append(query).append(")");

            // TODO: used paged results if supported by LDAP server.
            NamingEnumeration answer = ctx.search("", filter.toString(), searchControls);
            for (int i=0; i < startIndex; i++) {
                if (answer.hasMoreElements()) {
                    answer.next();
                }
                else {
                    return Collections.emptyList();
                }
            }
            // Now read in desired number of results (or stop if we run out of results).
            for (int i = 0; i < numResults; i++) {
                if (answer.hasMoreElements()) {
                    // Get the next group.
                    String groupName = (String)((SearchResult)answer.next()).getAttributes().get(
                            manager.getGroupNameField()).get();
                    // Escape group name and add to results.
                    groupNames.add(JID.escapeNode(groupName));
                }
                else {
                    break;
                }
            }
            // Close the enumeration.
            answer.close();
            // If client-side sorting is enabled, sort.
            if (Boolean.valueOf(JiveGlobals.getXMLProperty("ldap.clientSideSorting"))) {
                Collections.sort(groupNames);
            }
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ignored) {
                // Ignore.
            }
        }
        return groupNames;
    }

    public boolean isSearchSupported() {
        return true;
    }

    /**
     * An auxilary method used to populate LDAP groups based on a provided LDAP search result.
     *
     * @param answer LDAP search result.
     * @return a collection of groups.
     * @throws javax.naming.NamingException
     */
    private Collection<Group> populateGroups(Enumeration<SearchResult> answer) throws NamingException {
        if (manager.isDebugEnabled()) {
            Log.debug("Starting to populate groups with users.");
        }
        DirContext ctx = null;
        try {
            TreeMap<String, Group> groups = new TreeMap<String, Group>();

            ctx = manager.getContext();

            SearchControls searchControls = new SearchControls();
            searchControls.setReturningAttributes(new String[] { manager.getUsernameField() });
            // See if recursive searching is enabled. Otherwise, only search one level.
            if (manager.isSubTreeSearch()) {
                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            }
            else {
                searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
            }

            XMPPServer server = XMPPServer.getInstance();
            String serverName = server.getServerInfo().getName();
            // Build 3 groups.
            // group 1: uid=
            // group 2: rest of the text until first comma
            // group 3: rest of the text
            Pattern pattern =
                    Pattern.compile("(?i)(^" + manager.getUsernameField() + "=)([^,]+)(.+)");

            while (answer.hasMoreElements()) {
                String name = "";
                try {
                    Attributes a = answer.nextElement().getAttributes();
                    String description;
                    try {
                        name = ((String)((a.get(manager.getGroupNameField())).get()));
                        description =
                                ((String)((a.get(manager.getGroupDescriptionField())).get()));
                    }
                    catch (Exception e) {
                        description = "";
                    }
                    Set<JID> members = new TreeSet<JID>();
                    Attribute memberField = a.get(manager.getGroupMemberField());
                    if (memberField != null) {
                        NamingEnumeration ne = memberField.getAll();
                        while (ne.hasMore()) {
                            String username = (String) ne.next();
                            // If not posix mode, each group member is stored as a full DN.
                            if (!manager.isPosixMode()) {
                                try {
                                    // Try to find the username with a regex pattern match.
                                    Matcher matcher = pattern.matcher(username);
                                    if (matcher.matches() && matcher.groupCount() == 3) {
                                        // The username is in the DN, no additional search needed
                                        username = matcher.group(2);
                                    }
                                    // The regex pattern match failed. This will happen if the
                                    // the member DN's don't use the standard username field. For
                                    // example, Active Directory has a username field of
                                    // sAMAccountName, but stores group members as "CN=...".
                                    else {
                                        // Create an LDAP name with the full DN.
                                        LdapName ldapName = new LdapName(username);
                                        // Turn the LDAP name into something we can use in a
                                        // search by stripping off the comma.
                                        String userDNPart = ldapName.get(ldapName.size() - 1);
                                        NamingEnumeration usrAnswer = ctx.search("",
                                                userDNPart, searchControls);
                                        if (usrAnswer.hasMoreElements()) {
                                            username = (String) ((SearchResult) usrAnswer.next())
                                                    .getAttributes().get(
                                                    manager.getUsernameField()).get();
                                        }
                                        // Close the enumeration.
                                        usrAnswer.close();
                                    }
                                }
                                catch (Exception e) {
                                    Log.error(e);
                                }
                            }
                            // A search filter may have been defined in the LdapUserProvider.
                            // Therefore, we have to try to load each user we found to see if
                            // it passes the filter.
                            try {
                                JID userJID;
                                int position = username.indexOf("@" + serverName);
                                // Create JID of local user if JID does not match a component's JID
                                if (position == -1) {
                                    // In order to lookup a username from the manager, the username
                                    // must be a properly escaped JID node.
                                    String escapedUsername = JID.escapeNode(username);
                                    if (!escapedUsername.equals(username)) {
                                        // Check if escaped username is valid
                                        userManager.getUser(escapedUsername);
                                    }
                                    // No exception, so the user must exist. Add the user as a group
                                    // member using the escaped username.
                                    userJID = server.createJID(escapedUsername, null);
                                }
                                else {
                                    // This is a JID of a component or node of a server's component
                                    String node = username.substring(0, position);
                                    String escapedUsername = JID.escapeNode(node);
                                    userJID = new JID(escapedUsername + "@" + serverName);
                                }
                                members.add(userJID);
                            }
                            catch (UserNotFoundException e) {
                                // We can safely ignore this error. It likely means that
                                // the user didn't pass the search filter that's defined.
                                // So, we want to simply ignore the user as a group member.
                                if (manager.isDebugEnabled()) {
                                    Log.debug("User not found: " + username);
                                }
                            }
                        }
                        // Close the enumeration.
                        ne.close();
                    }
                    if (manager.isDebugEnabled()) {
                        Log.debug("Adding group \"" + name + "\" with " + members.size() +
                                " members.");
                    }
                    Collection<JID> admins = Collections.emptyList();
                    Group group = new Group(name, description, members, admins);
                    groups.put(name, group);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    if (manager.isDebugEnabled()) {
                        Log.debug("Error while populating group, " + name + ".", e);
                    }
                }
            }
            if (manager.isDebugEnabled()) {
                Log.debug("Finished populating group(s) with users.");
            }

            return groups.values();
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.close();
                }
            }
            catch (Exception e) {
                // Ignore.
            }
        }
    }
}