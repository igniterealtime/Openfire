/*
 * Copyright (C) 2012 Issa Gorissen <issa-gorissen@usa.net>. All rights reserved.
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
package org.jivesoftware.openfire.crowd;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.admin.AdminProvider;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupProvider;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Admin provider which will map a crowd group with openfire authorized admin users
 */
public class CrowdAdminProvider implements AdminProvider {
    private static final Logger LOG = LoggerFactory.getLogger(CrowdAdminProvider.class);
    private static final String JIVE_AUTHORIZED_GROUPS = "admin.authorizedGroups";

    @Override
    public List<JID> getAdmins() {
        List<JID> results = new ArrayList<>();
        
        GroupProvider provider = GroupManager.getInstance().getProvider();
        
        String groups = JiveGlobals.getProperty(JIVE_AUTHORIZED_GROUPS);
        groups = (groups == null || groups.trim().length() == 0) ? "" : groups;
        JiveGlobals.setProperty(JIVE_AUTHORIZED_GROUPS, groups); // make sure the property is created
        StringTokenizer tokenizer = new StringTokenizer(groups, ",");
        while (tokenizer.hasMoreTokens()) {
            String groupName = tokenizer.nextToken().trim().toLowerCase();
            
            if (groupName != null && groupName.length() > 0) {
                try {
                    LOG.info("Adding admin users from group: " + groupName);
                    Group group = provider.getGroup(groupName);
                    if (group != null) {
                        results.addAll(group.getMembers());
                    }
                    
                } catch (GroupNotFoundException gnfe) {
                    LOG.error("Error when trying to load the members of group:" + String.valueOf(groupName), gnfe);
                }
            }
        }
        
        
        if (results.isEmpty()) {
            // Add default admin account when none was specified
            results.add(new JID("admin", XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null, true));
        }
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("admin users: " + results.toString());
        }
        
        return results;
    }

    @Override
    public void setAdmins(List<JID> admins) {
        return;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

}
