/*
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

package org.jivesoftware.admin;

import org.jivesoftware.openfire.ldap.LdapManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.SortControl;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Class that assists during the testing of the ldap groups.
 *
 * @author Gaston Dombiak
 */
public class LdapGroupTester {

    private static final Logger Log = LoggerFactory.getLogger(LdapGroupTester.class);

    private LdapManager manager;

    public LdapGroupTester(LdapManager manager) {
        this.manager = manager;
    }

    /**
     * Returns fist N groups found in LDAP. The returned groups are only able to return their name,
     * description and count of members. Count of members is considering all values that were found
     * in the member field.
     *
     * @param maxGroups max number of groups to return.
     * @return fist N groups found in the LDAP.
     */
    public Collection<Group> getGroups(int maxGroups) {
        Collection<Group> groups = new ArrayList<>();
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
            // Attributes to return for each group
            String[] standardAttributes = new String[3];
            standardAttributes[0] = manager.getGroupNameField();
            standardAttributes[1] = manager.getGroupDescriptionField();
            standardAttributes[2] = manager.getGroupMemberField();
            searchControls.setReturningAttributes(standardAttributes);
            // Limit results to those we'll need to process
            searchControls.setCountLimit(maxGroups);

            String filter = MessageFormat.format(manager.getGroupSearchFilter(), "*");
            NamingEnumeration answer = ctx.search("", filter, searchControls);
            while (answer.hasMoreElements()) {
                // Get the next group.
                Attributes attributes = ((SearchResult) answer.next()).getAttributes();
                String groupName = (String) attributes.get(manager.getGroupNameField()).get();
                String description = "";
                int elements = 0;
                try {
                    description = ((String) attributes.get(manager.getGroupDescriptionField()).get());
                } catch (NullPointerException e) {
                    // Do nothing since the group description field was not found
                } catch (Exception e) {
                    Log.error("Error retrieving group description", e);
                }

                Attribute memberField = attributes.get(manager.getGroupMemberField());
                if (memberField != null) {
                    NamingEnumeration ne = memberField.getAll();
                    while (ne.hasMore()) {
                        ne.next();
                        elements = elements + 1;
                    }
                }
                // Build Group with found information
                groups.add(new Group(groupName, description, elements));
            }
            // Close the enumeration.
            answer.close();
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
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
        return groups;
    }

    /**
     * Representation of a group found in LDAP. This representatio is read-only and only provides
     * some basic information: name, description and number of members in the group. Note that
     * group members are not validated (i.e. checked that they are valid JIDs and that the JID belongs
     * to an existing user).
     */
    public static class Group {
        private String name;
        private String description;
        /**
         * Elements that the group contains. This includes admins, members or anything listed
         * in the {@code member field}. At this point JIDs are not validated.
         */
        private int members;


        public Group(String name, String description, int members) {
            this.name = name;
            this.description = description;
            this.members = members;
        }


        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public int getMembers() {
            return members;
        }
    }
}
