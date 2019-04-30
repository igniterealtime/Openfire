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
package org.jivesoftware.openfire.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Handles default management of admin users, which stores the list if accounts as a system property.
 *
 * @author Daniel Henninger
 */
public class DefaultAdminProvider implements AdminProvider {

    private static final SystemProperty<List<JID>> ADMIN_JIDS = SystemProperty.Builder.ofType(List.class)
        .setKey("admin.authorizedJIDs")
        .setDefaultValue(Collections.emptyList())
        .setSorted(true)
        .setDynamic(true)
        .addListener(jids -> AdminManager.getInstance().refreshAdminAccounts())
        .buildList(JID.class);
    private static final Logger Log = LoggerFactory.getLogger(DefaultAdminProvider.class);

    /**
     * Constructs a new DefaultAdminProvider
     */
    public DefaultAdminProvider() {

        // Convert old openfire.xml style to new provider style, if necessary.
        Log.debug("DefaultAdminProvider: Convert XML to provider.");
        convertXMLToProvider();
    }

    /**
     * The default provider retrieves the comma separated list from the system property
     * {@code admin.authorizedJIDs}
     * @see org.jivesoftware.openfire.admin.AdminProvider#getAdmins()
     */
    @Override
    public List<JID> getAdmins() {
        // Add bare JIDs of users that are admins (may include remote users), primarily used to override/add to list of admin users
        final List<JID> adminList = ADMIN_JIDS.getValue();

        // Prior to 4.4.0, the return value was mutable. To prevent issues, we'll keep that characteristic.
        final List<JID> returnValue = new ArrayList<>();

        if (adminList.isEmpty()) {
            // Add default admin account when none was specified
            returnValue.add( new JID("admin", XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null, true));
        } else {
            returnValue.addAll( adminList );
        }

        return returnValue;
    }

    /**
     * The default provider sets a comma separated list as the system property
     * {@code admin.authorizedJIDs}
     * @see org.jivesoftware.openfire.admin.AdminProvider#setAdmins(java.util.List)
     */
    @Override
    public void setAdmins(final List<JID> admins) {
        ADMIN_JIDS.setValue(admins);
    }

    /**
     * The default provider is not read only
     * @see org.jivesoftware.openfire.admin.AdminProvider#isReadOnly()
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Converts the old openfire.xml style admin list to use the new provider mechanism.
     */
    private void convertXMLToProvider() {
        if (JiveGlobals.getXMLProperty("admin.authorizedJIDs") == null &&
                JiveGlobals.getXMLProperty("admin.authorizedUsernames") == null &&
                JiveGlobals.getXMLProperty("adminConsole.authorizedUsernames") == null) {
            // No settings in openfire.xml.
            return;
        }

        final List<JID> adminList = new ArrayList<>();

        // Add bare JIDs of users that are admins (may include remote users), primarily used to override/add to list of admin users
        String jids = JiveGlobals.getXMLProperty("admin.authorizedJIDs");
        jids = (jids == null || jids.trim().length() == 0) ? "" : jids;
        StringTokenizer tokenizer = new StringTokenizer(jids, ",");
        while (tokenizer.hasMoreTokens()) {
            final String jid = tokenizer.nextToken().toLowerCase().trim();
            try {
                adminList.add(new JID(jid));
            }
            catch (final IllegalArgumentException e) {
                Log.warn("Invalid JID found in authorizedJIDs at openfire.xml: " + jid, e);
            }
        }

        // Add the JIDs of the local users that are admins, primarily used to override/add to list of admin users
        String usernames = JiveGlobals.getXMLProperty("admin.authorizedUsernames");
        if (usernames == null) {
            // Fall back to old method for defining admins (i.e. using adminConsole prefix
            usernames = JiveGlobals.getXMLProperty("adminConsole.authorizedUsernames");
        }
        // Add default of admin user if no other users were listed as admins.
        usernames = (usernames == null || usernames.trim().length() == 0) ? (adminList.size() == 0 ? "admin" : "") : usernames;
        tokenizer = new StringTokenizer(usernames, ",");
        while (tokenizer.hasMoreTokens()) {
            final String username = tokenizer.nextToken();
            try {
                adminList.add(XMPPServer.getInstance().createJID(username.toLowerCase().trim(), null));
            }
            catch (final IllegalArgumentException e) {
                // Ignore usernames that when appended @server.com result in an invalid JID
                Log.warn("Invalid username found in authorizedUsernames at openfire.xml: " +
                        username, e);
            }
        }
        setAdmins(adminList);

        // Clear out old XML property settings
        JiveGlobals.deleteXMLProperty("admin.authorizedJIDs");
        JiveGlobals.deleteXMLProperty("admin.authorizedUsernames");
        JiveGlobals.deleteXMLProperty("adminConsole.authorizedUsernames");
    }

}
