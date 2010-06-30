/**
 * $RCSfile$
 * $Revision: 3191 $
 * $Date: 2005-12-12 13:41:22 -0300 (Mon, 12 Dec 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.ldap;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupProvider;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * LDAP implementation of the GroupProvider interface.  All data in the directory is treated as
 * read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker, Greg Ferguson and Cameron Moore
 */
public class LdapGroupProvider implements GroupProvider {

	private static final Logger Log = LoggerFactory.getLogger(LdapGroupProvider.class);

    private LdapManager manager;
    private UserManager userManager;
    private String[] standardAttributes;
    private int groupCount = -1;
    private long expiresStamp = System.currentTimeMillis();

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
        LdapContext ctx = null;
        try {
            String groupDN = manager.findGroupDN(groupName);
            // Load record.
            ctx = manager.getContext(manager.getGroupsBaseDN(groupName));
            Attributes attrs = ctx.getAttributes(groupDN, standardAttributes);

            return processGroup(ctx, attrs);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
            throw new GroupNotFoundException("Group with name " + groupName + " not found.", e);
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
            Log.debug("LdapGroupProvider: Trying to get the number of groups in the system.");
        }
        // Cache user count for 5 minutes.
        if (groupCount != -1 && System.currentTimeMillis() < expiresStamp) {
            return groupCount;
        }
        this.groupCount = manager.retrieveListCount(
                manager.getGroupNameField(),
                MessageFormat.format(manager.getGroupSearchFilter(), "*")
        );
        this.expiresStamp = System.currentTimeMillis() + JiveConstants.MINUTE *5;
        return this.groupCount;
    }

    public Collection<String> getSharedGroupsNames() {
        // Get the list of shared groups from the database
        return Group.getSharedGroupsNames();
    }

    public Collection<String> getGroupNames() {
        return getGroupNames(-1, -1);
    }

    public Collection<String> getGroupNames(int startIndex, int numResults) {
        return manager.retrieveList(
                manager.getGroupNameField(),
                MessageFormat.format(manager.getGroupSearchFilter(), "*"),
                startIndex,
                numResults,
                null
        );
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
                username = manager.findUserDN(username) + "," + manager.getUsersBaseDN(username);
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
        StringBuilder filter = new StringBuilder();
        filter.append("(&");
        filter.append(MessageFormat.format(manager.getGroupSearchFilter(), "*"));
        filter.append("(").append(manager.getGroupMemberField()).append("=").append(username);
        filter.append("))");
        if (Log.isDebugEnabled()) {
            Log.debug("Trying to find group names for user: " + user + " using query: " + filter.toString());
        }
        // Perform the LDAP query
        return manager.retrieveList(
                manager.getGroupNameField(),
                filter.toString(),
                -1,
                -1,
                null
        );
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
        return search(query, -1, -1);
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
        StringBuilder filter = new StringBuilder();
        filter.append("(").append(manager.getGroupNameField()).append("=").append(query).append(")");
        // Perform the LDAP query
        return manager.retrieveList(
                manager.getGroupNameField(),
                filter.toString(),
                startIndex,
                numResults,
                null
        );
    }

    public boolean isSearchSupported() {
        return true;
    }

    private Group processGroup(LdapContext ctx, Attributes a) throws NamingException {
        XMPPServer server = XMPPServer.getInstance();
        String serverName = server.getServerInfo().getXMPPDomain();
        // Build `3 groups.
        // group 1: uid=
        // group 2: rest of the text until first comma
        // group 3: rest of the text
        Pattern pattern =
                Pattern.compile("(?i)(^" + manager.getUsernameField() + "=)([^,]+)(.+)");

        SearchControls searchControls = new SearchControls();
        searchControls.setReturningAttributes(new String[] { manager.getUsernameField() });
        // See if recursive searching is enabled. Otherwise, only search one level.
        if (manager.isSubTreeSearch()) {
            searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        }
        else {
            searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        }

        String name;
        String description;
        try {
            name = ((String)((a.get(manager.getGroupNameField())).get()));
        }
        catch (Exception e) {
            name = "";
        }
        try {
            description = ((String)((a.get(manager.getGroupDescriptionField())).get()));
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
                            if (usrAnswer != null && usrAnswer.hasMoreElements()) {
                                Attribute usernameAttr = ((SearchResult)usrAnswer.next()).getAttributes().get(manager.getUsernameField());
                                if (usernameAttr != null) {
                                    username = (String)usernameAttr.get();
                                }
                            }
                            // Close the enumeration.
                            usrAnswer.close();
                        }
                    }
                    catch (Exception e) {
                        // TODO: A NPE is occuring here
                        Log.error(e.getMessage(), e);
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
                        Log.debug("LdapGroupProvider: User not found: " + username);
                    }
                }
            }
            // Close the enumeration.
            ne.close();
        }
        if (manager.isDebugEnabled()) {
            Log.debug("LdapGroupProvider: Adding group \"" + name + "\" with " + members.size() +
                    " members.");
        }
        Collection<JID> admins = Collections.emptyList();
        return new Group(name, description, members, admins);
    }
}
