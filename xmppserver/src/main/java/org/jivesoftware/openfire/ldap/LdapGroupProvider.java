/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.group.AbstractGroupProvider;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.naming.Name;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jivesoftware.util.SystemProperty;

/**
 * LDAP implementation of the GroupProvider interface.  All data in the directory is treated as
 * read-only so any set operations will result in an exception.
 *
 * @author Matt Tucker, Greg Ferguson and Cameron Moore
 */
public class LdapGroupProvider extends AbstractGroupProvider {

    private static final Logger Log = LoggerFactory.getLogger(LdapGroupProvider.class);

    private LdapManager manager;
    private UserManager userManager;
    private String[] standardAttributes;
    private int groupCount = -1;
    private Instant expiresStamp = Instant.now();

    public static SystemProperty<Boolean> PROCESSBIGGROUPS = SystemProperty.Builder.ofType(Boolean.class)
            .setKey("ldap.useRangeRetrieval")
            .setDefaultValue(false)
            .setDynamic(true)
            .build();

    /**
     * Constructs a new LDAP group provider.
     */
    public LdapGroupProvider() {
        super();
        manager = LdapManager.getInstance();
        userManager = UserManager.getInstance();
        standardAttributes = new String[3];
        standardAttributes[0] = manager.getGroupNameField();
        standardAttributes[1] = manager.getGroupDescriptionField();
        standardAttributes[2] = manager.getGroupMemberField();
    }

    @Override
    public Group getGroup(String groupName) throws GroupNotFoundException {
        try {
            LdapName groupDN = manager.findGroupAbsoluteDN(groupName);
            return getGroupByDN(groupDN, new HashSet<>(Collections.singleton(groupDN.toString())));
        }
        catch (Exception e) {
            Log.error("Unable to load group: {}", groupName, e);
            throw new GroupNotFoundException("Group with name " + groupName + " not found.", e);
        }
    }

    public String genNextRangePart(int lasthigh)
    {
        String groupMemberField = manager.getGroupMemberField();

        int pageSize = LdapManager.LDAP_PAGE_SIZE.getValue();
        if (pageSize==-1)
        {
            pageSize=1500;
        }

        return groupMemberField+";range=" + String.valueOf(lasthigh+1) + "-" + String.valueOf(lasthigh + pageSize+1);
    }

    /**
     * Reads the group with the given DN
     *
     * @param groupDN         the absolute DN of the group
     * @param membersToIgnore A mutable set of DNs and/or UIDs (for Posix mode) to ignore. This set will be
     *                        filled with visited DNs. If flatten of hierarchies of groups is active
     *                        ({@link LdapManager#isFlattenNestedGroups()}, this will prevent endless loops
     *                        for cyclic hierarchies.
     * @return A group (never null)
     * @throws NamingException When a group can't be read from LDAP.
     */
    private Group getGroupByDN(LdapName groupDN, Set<String> membersToIgnore) throws NamingException {
        LdapContext ctx = null;
        try {
            LdapName baseDN;
            Name relativeDN;
            if (manager.getAlternateBaseDN() != null
                && groupDN.startsWith(manager.getAlternateBaseDN())) {
                baseDN = manager.getAlternateBaseDN();
            } else if (groupDN.startsWith(manager.getBaseDN())) {
                baseDN = manager.getBaseDN();
            }
            else {
                throw new IllegalArgumentException("GroupDN does not match any baseDN");
            }
            relativeDN = groupDN.getSuffix(baseDN.size());
            membersToIgnore.add(groupDN.toString());
            // Load record.
            ctx = manager.getContext(baseDN);
            Attributes attrs = ctx.getAttributes(relativeDN, standardAttributes);

            if (!PROCESSBIGGROUPS.getValue())
            {
                return processGroup(ctx, attrs, membersToIgnore);
            }
            else
            {
                boolean paging = false;
    
                NamingEnumeration<? extends Attribute> i = attrs.getAll();
                int rangehigh=-1;
                int oldrangehigh=-1;
    
                while (i.hasMore())
                {
                    Attribute attribute = i.next();
                    String key = attribute.getID().toLowerCase();
                    if (key.startsWith(manager.getGroupMemberField())&&key.contains(";range="))
                    {
                        rangehigh = Integer.parseInt(key.substring(key.indexOf(";range=")+";range=".length()).split("\\-")[1]);
    
                        paging=true;
                        Log.debug("using range retrival for group: {}", relativeDN);
                        break;
                    }
                }
    
                if (!paging)
                {
                    return processGroup(ctx, attrs, membersToIgnore);
                }
                else
                {
                    //retrieve first part with attributes that came from AD
                    ArrayList<String> stdAttr=new ArrayList<String>();
                    for (int n=0;n<standardAttributes.length;n++)
                    {
                        if (!standardAttributes[n].contains(manager.getGroupMemberField()))
                        {
                            stdAttr.add(standardAttributes[n]);
                        }
                    }
    
                    stdAttr.add(manager.getGroupMemberField()+";range=0-"+String.valueOf(rangehigh));
                    Log.debug("first range will be 0-{}",rangehigh);
    
                    //reload Attributes as said in MSDN will prepare range retrival
                    attrs = ctx.getAttributes(relativeDN, stdAttr.toArray(new String[stdAttr.size()]));
    
                    //get the first part of the group
                    Group tmpGroup = processGroup(ctx, attrs, membersToIgnore);
                    Collection<JID> admins = tmpGroup.getAdmins();
                    String description = tmpGroup.getDescription();
                    String name = tmpGroup.getName();
                    ArrayList<JID> members = new ArrayList<>();
                    members.addAll(tmpGroup.getMembers());
    
                    //now load the rest
                    do
                    {
                        try
                        {
                            stdAttr.clear();
                            for (int n=0;n<standardAttributes.length;n++)
                            {
                                if (!standardAttributes[n].contains(manager.getGroupMemberField()))
                                {
                                    stdAttr.add(standardAttributes[n]);
                                }
                            }
                            //Add the next range rangehigh+1 to rangehigh+1+ldap.pagedResultsSize
                            stdAttr.add(genNextRangePart(rangehigh));
    
                            attrs = ctx.getAttributes(relativeDN, stdAttr.toArray(new String[stdAttr.size()]));
                            NamingEnumeration<? extends Attribute> inext = attrs.getAll();
                            while (inext.hasMore())
                            {
                                Attribute attribute = inext.next();
                                String key = attribute.getID().toLowerCase();
                                if (key.startsWith(manager.getGroupMemberField())&&key.contains(";range="))
                                {
                                    //get next rangehigh
                                    oldrangehigh=rangehigh;
                                    if (key.substring(key.indexOf(";range=")+";range=".length()).split("\\-")[1].equals("*")==false)
                                    {
                                        //we will have a minimum of two more ranges
                                        rangehigh = Integer.parseInt(key.substring(key.indexOf(";range=")+";range=".length()).split("\\-")[1]);
                                    }
                                    else
                                    {
                                        //* means to the end of the range
                                        rangehigh = -1;
                                    }
                                    break;
                                }
                            }
    
                            //load the group members now
                            Log.debug("next range will be {}-{}",oldrangehigh,rangehigh!=-1?rangehigh:"*");
    
                            tmpGroup=processGroup(ctx, attrs, membersToIgnore);
                            if (tmpGroup!=null&&tmpGroup.getMembers().size()>0)
                            {
                                members.addAll(tmpGroup.getMembers());
                            }
                            else
                            {
                                // no next found
                                break;
                            }
    
                        }
                        catch (Exception e)
                        {
                            Log.debug("error while reading the ldap group with range retrival",e); // no next found, cause of missing attribute
                            break;
                        }
                    }while (rangehigh!=-1);  //The last part was received
    
                    return new Group(name, description, members, admins);
                }
            }
        }
        finally {
            try {
                if (ctx != null) {
                    ctx.setRequestControls(null);
                    ctx.close();
                }
            }
            catch (Exception ex) {
                Log.debug( "An exception was ignored while trying to close the Ldap context after trying to get a group.", ex );
            }
        }
    }

    @Override
    public int getGroupCount() {
        if (manager.isDebugEnabled()) {
            Log.debug("LdapGroupProvider: Trying to get the number of groups in the system.");
        }
        // Cache user count for 5 minutes.
        if (groupCount != -1 && Instant.now().isBefore(expiresStamp)) {
            return groupCount;
        }
        this.groupCount = manager.retrieveListCount(
                manager.getGroupNameField(),
                MessageFormat.format(manager.getGroupSearchFilter(), "*")
        );
        this.expiresStamp = Instant.now().plus(Duration.ofMinutes(5));
        return this.groupCount;
    }

    @Override
    public Collection<String> getGroupNames() {
        return getGroupNames(-1, -1);
    }

    @Override
    public Collection<String> getGroupNames(int startIndex, int numResults) {
        return manager.retrieveList(
                manager.getGroupNameField(),
                MessageFormat.format(manager.getGroupSearchFilter(), "*"),
                startIndex,
                numResults,
                null
        );
    }

    @Override
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
                final String relativePart =
                    Arrays.stream(manager.findUserRDN(username))
                    .map(Rdn::toString)
                    .collect(Collectors.joining(","));

                username = relativePart + "," + manager.getUsersBaseDN(username);
            }
            catch (Exception e) {
                Log.error("Could not find user in LDAP " + username);
                return Collections.emptyList();
            }
        }
        else {
            username = server.isLocal(user) ? JID.unescapeNode(user.getNode()) : user.toBareJID();
        }
        // Do nothing if the user is empty or null
        if (username == null || "".equals(username)) {
            return Collections.emptyList();
        }

        Set<String> groupNames = new LinkedHashSet<>(search(manager.getGroupMemberField(), username));

        if (manager.isFlattenNestedGroups()) {
            // search groups that contain the given groups

            Set<String> checkedGroups = new HashSet<>();
            Deque<String> todo = new ArrayDeque<>(groupNames);
            String group;
            while (null != (group = todo.pollFirst())) {
                if (checkedGroups.contains(group)) {
                    continue;
                }
                checkedGroups.add(group);
                try {
                    // get the DN of the group
                    LdapName groupDN = manager.findGroupAbsoluteDN(group);
                    if(manager.isPosixMode()){
                        // in posix mode we need to search for the "uid" of the group.
                        List<String> uids = manager.retrieveAttributeOf(manager.getUsernameField(), groupDN);
                        if(uids.isEmpty()) {
                            // group not there or has not the "uid" attribute
                            continue;
                        }
                        group = uids.get(0);
                    } else {
                        group = groupDN.toString();
                    }
                    // search for groups that have the given group (DN normal, UID posix) as member
                    Collection<String> containingGroupNames = search(manager.getGroupMemberField(), group);
                    // add the found groups to the result and to the groups to be checked transitively
                    todo.addAll(containingGroupNames);
                    groupNames.addAll(containingGroupNames);
                }
                catch (Exception e) {
                    Log.warn("Error looking up group: {}", group);
                }
            }
        }
        return groupNames;
    }

    @Override
    public Collection<String> search(String key, String value) {
        if (key.equals(Group.SHARED_ROSTER_DISPLAY_NAME_PROPERTY_KEY)){
            return super.search(key,value);
        }

        StringBuilder filter = new StringBuilder();
        filter.append("(&");
        filter.append(MessageFormat.format(manager.getGroupSearchFilter(), "*"));
        filter.append('(').append(key).append('=').append(LdapManager.sanitizeSearchFilter(value, false));
        filter.append("))");
        
        if (!PROCESSBIGGROUPS.getValue())
        {
            if (Log.isDebugEnabled()) {
                Log.debug("Trying to find group names using query: " + filter.toString());
            }

            // Perform the LDAP query
            return manager.retrieveList(
                    manager.getGroupNameField(),
                    filter.toString(),
                    -1,
                    -1,
                    null);
        }
        else
        {
            String ldapfilter = filter.toString();
            String searchRangeStr = ";range=";
            if (ldapfilter.contains(searchRangeStr))
            {
                ldapfilter=ldapfilter.substring(0,ldapfilter.indexOf(searchRangeStr))+
                        ldapfilter.substring(ldapfilter.indexOf("=",
                                ldapfilter.indexOf(searchRangeStr)+searchRangeStr.length()));
            }

            Log.debug("Trying to find group names using query: {}", ldapfilter);

            // Perform the LDAP query
            return manager.retrieveList(
                    manager.getGroupNameField(),
                    ldapfilter,
                    -1,
                    -1,
                    null);
        }
    }

    @Override
    public Collection<String> search(String query) {
        return search(query, -1, -1);
    }

    @Override
    public Collection<String> search(String query, int startIndex, int numResults) {
        if (query == null || "".equals(query)) {
            return Collections.emptyList();
        }
        StringBuilder filter = new StringBuilder();
        // Make the query be a wildcard search by default. So, if the user searches for
        // "Test", make the sanitized search be "Test*" instead.
        filter.append('(').append(manager.getGroupNameField()).append('=')
                .append(LdapManager.sanitizeSearchFilter(query, false)).append("*)");
        // Perform the LDAP query
        return manager.retrieveList(
                manager.getGroupNameField(),
                filter.toString(),
                startIndex,
                numResults,
                null
        );
    }

    @Override
    public boolean isSearchSupported() {
        return true;
    }

    private Group processGroup(LdapContext ctx, Attributes a, Set<String> membersToIgnore) throws NamingException {
        XMPPServer server = XMPPServer.getInstance();
        String serverName = server.getServerInfo().getXMPPDomain();
        // Build `3 groups.
        // group 1: uid=
        // group 2: rest of the text until first comma
        // group 3: rest of the text
        Pattern pattern =
                Pattern.compile("(?i)(^" + manager.getUsernameField() + "=)([^,]+)(.+)");

        // We have to process Active Directory differently.
        boolean isAD = manager.getUsernameField().equals("sAMAccountName");
        String[] returningAttributes = isAD ? new String[] { "distinguishedName", manager.getUsernameField() } : new String[] { manager.getUsernameField() };

        SearchControls searchControls = new SearchControls();
        searchControls.setReturningAttributes(returningAttributes);
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
        Set<JID> members = new TreeSet<>();
        Attribute memberField = a.get(manager.getGroupMemberField());

        Log.debug("Loading members of group: {}", name);

        if (memberField==null&&PROCESSBIGGROUPS.getValue())
        {
            NamingEnumeration<? extends Attribute> i = a.getAll();

            while (i.hasMore())
            {
                Attribute attribute = i.next();
                String key = attribute.getID().toLowerCase();
                if (key.startsWith(manager.getGroupMemberField()))
                {
                    memberField=attribute;
                    break;
                }
            }
        }

        if (memberField != null) {
            NamingEnumeration ne = memberField.getAll();
            while (ne.hasMore()) {
                String username = (String) ne.next();
                LdapName userDN = null;
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
                            userDN = new LdapName(username);
                            // Turn the LDAP name into something we can use in a
                            // search by stripping off the comma.
                            StringBuilder userFilter = new StringBuilder();
                            userFilter.append("(&(");
                            userFilter.append(userDN.get(userDN.size() - 1));
                            userFilter.append(')');
                            userFilter.append(MessageFormat.format(manager.getSearchFilter(), "*"));
                            userFilter.append(')');
                            NamingEnumeration usrAnswer = ctx.search("",
                                    userFilter.toString(), searchControls);
                            if (usrAnswer.hasMoreElements()) {
                                SearchResult searchResult = null;
                                // We may get multiple search results for the same user CN.
                                // Iterate through the entire set to find a matching distinguished name.
                                while(usrAnswer.hasMoreElements()) {
                                    searchResult = (SearchResult) usrAnswer.nextElement();
                                    Attributes attrs = searchResult.getAttributes();
                                    if (isAD) {
                                        Attribute userdnAttr = attrs.get("distinguishedName");
                                        if (username.equals((String)userdnAttr.get())) {
                                            // Exact match found, use it.
                                            username = (String)attrs.get(manager.getUsernameField()).get();
                                            break;
                                        }
                                    }
                                    else {
                                        // No iteration occurs here, which is probably a bug.
                                        username = (String)attrs.get(manager.getUsernameField()).get();
                                        break;
                                    }
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
                    if (!membersToIgnore.contains(username)) {
                        JID userJID;
                        int position = username.indexOf("@" + serverName);
                        // Create JID of local user if JID does not match a component's JID
                        if (position == -1) {
                            // In order to lookup a username from the manager, the username
                            // must be a properly escaped JID node.
                            String escapedUsername = JID.escapeNode(username);
                            // Check if escaped username is valid
                            userManager.getUser(escapedUsername);
                            // No exception, so the user must exist. Add the user as a group
                            // member using the escaped username.
                            userJID = server.createJID(escapedUsername, null);
                        }
                        else {
                            // This is a JID of a component or node of a server's component
                            String node = username.substring(0, position);
                            String escapedUsername = JID.escapeNode(node);
                            userJID = new JID(escapedUsername + "@" + serverName).asBareJID();
                        }
                        members.add(userJID);
                    }
                }
                catch (UserNotFoundException e) {
                    // not a user!
                    // maybe it is a group?
                    boolean isGroup = false;
                    if (manager.isFlattenNestedGroups()) {
                        if (manager.isPosixMode()) { // in posix mode, the DN has not been set...
                            // Look up the userDN using the UID
                            String userDNStr = manager.retrieveSingle(null,
                                "(" + manager.getUsernameField()
                                    + "=" + LdapManager.sanitizeSearchFilter(username) + ")",
                                true);
                            if (userDNStr != null)
                                userDN = new LdapName(userDNStr);
                        } else if (userDN == null) {
                            // Create an LDAP name with the full DN.
                            userDN = new LdapName(username);
                        }
                        if (userDN != null && manager.isGroupDN(userDN)) {
                            isGroup = true;
                            if (!membersToIgnore.contains(userDN.toString())
                                && !membersToIgnore.contains(username)) {
                                // prevent endless adding of cyclic referenced groups
                                membersToIgnore.add(username);
                                membersToIgnore.add(userDN.toString());
                                // it's a sub group not already added, so add its members
                                Log.debug("Adding members of sub-group: {}", userDN);
                                Group subGroup = getGroupByDN(userDN, membersToIgnore);
                                members.addAll(subGroup.getMembers());
                            }
                        }
                    }
                    if (!isGroup) {
                        // We can safely ignore this error. It likely means that
                        // the user didn't pass the search filter that's defined.
                        // So, we want to simply ignore the user as a group member.
                        if (manager.isDebugEnabled()) {
                            Log.debug("LdapGroupProvider: User not found: " + username);
                        }
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
