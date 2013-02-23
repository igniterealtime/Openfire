/**
 * $Revision$
 * $Date$
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
package org.jivesoftware.openfire.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.ClassUtils;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * The AdminManager manages the AdminProvider configured for this server, caches knowledge of
 * accounts with admin permissions, and provides a single point of entry for handling
 * getting and setting administrative accounts.
 *
 * The provider can be specified using the system property:
 *
 * <ul>
 * <li><tt>provider.admin.className = my.admin.provider</tt></li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class AdminManager {

	private static final Logger Log = LoggerFactory.getLogger(AdminManager.class);

    // Wrap this guy up so we can mock out the AdminManager class.
    private static class AdminManagerContainer {
        private static AdminManager instance = new AdminManager();
    }

    /**
     * Returns the currently-installed AdminProvider. <b>Warning:</b> in virtually all
     * cases the admin provider should not be used directly. Instead, the appropriate
     * methods in AdminManager should be called. Direct access to the admin provider is
     * only provided for special-case logic.
     *
     * @return the current AdminProvider.
     */
    public static AdminProvider getAdminProvider() {
        return AdminManagerContainer.instance.provider;
    }

    /**
     * Returns a singleton instance of AdminManager.
     *
     * @return a AdminManager instance.
     */
    public static AdminManager getInstance() {
        return AdminManagerContainer.instance;
    }

    /* Cache of admin accounts */
    private List<JID> adminList;
    private AdminProvider provider;

    /**
     * Constructs a AdminManager, propery listener, and setting up the provider.
     */
    private AdminManager() {
        // Load an admin provider.
        initProvider();

        // Detect when a new admin provider class is set
        PropertyEventListener propListener = new PropertyEventListener() {
            public void propertySet(String property, Map<String, Object> params) {
                if ("provider.admin.className".equals(property)) {
                    initProvider();
                }
            }

            public void propertyDeleted(String property, Map<String, Object> params) {
                //Ignore
            }

            public void xmlPropertySet(String property, Map<String, Object> params) {
                //Ignore
            }

            public void xmlPropertyDeleted(String property, Map<String, Object> params) {
                //Ignore
            }
        };
        PropertyEventDispatcher.addListener(propListener);
    }

    /**
     * Initializes the server's admin provider, based on configuration and defaults to
     * DefaultAdminProvider if the specified provider is not valid or not specified.
     */
    private void initProvider() {
        // Convert XML based provider setup to Database based
        JiveGlobals.migrateProperty("provider.admin.className");

        String className = JiveGlobals.getProperty("provider.admin.className",
                "org.jivesoftware.openfire.admin.DefaultAdminProvider");
        // Check if we need to reset the provider class
        if (provider == null || !className.equals(provider.getClass().getName())) {
            try {
                Class c = ClassUtils.forName(className);
                provider = (AdminProvider) c.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading admin provider: " + className, e);
                provider = new DefaultAdminProvider();
            }
        }
    }

    /**
     * Reads the admin list from the provider and sets up the cache.
     */
    private void loadAdminList() {
        adminList = provider.getAdmins();
    }

    /**
     * Refreshs the list of admin users from the provider.
     */
    public void refreshAdminAccounts() {
        loadAdminList();
    }

    /**
     * Returns the list of admin users from the provider.
     *
     * @return The list of users with admin status.
     */
    public List<JID> getAdminAccounts() {
        if (adminList == null) {
            loadAdminList();
        }
        return adminList;
    }

    /**
     * Adds a new account to the list of Admin accounts, based off a username, which will be converted
     * into a JID.
     *
     * @param username Username of account to add to list of admins.
     */
    public void addAdminAccount(String username) {
        if (adminList == null) {
            loadAdminList();
        }
        JID userJID = XMPPServer.getInstance().createJID(username, null);
        if (adminList.contains(userJID)) {
            // Already have them.
            return;
        }
        // Add new admin to cache.
        adminList.add(userJID);
        // Store updated list of admins with provider.
        provider.setAdmins(adminList);
    }

    /**
     * Adds a new account to the list of Admin accounts, based off a JID.
     *
     * @param jid JID of account to add to list of admins.
     */
    public void addAdminAccount(JID jid) {
        if (adminList == null) {
            loadAdminList();
        }
        JID bareJID = jid.asBareJID();
        if (adminList.contains(bareJID)) {
            // Already have them.
            return;
        }
        // Add new admin to cache.
        adminList.add(bareJID);
        // Store updated list of admins with provider.
        provider.setAdmins(adminList);
    }

    /**
     * Removes an account from the list of Admin accounts, based off username, which will be converted
     * to a JID.
     *
     * @param username Username of user to remove from admin list.
     */
    public void removeAdminAccount(String username) {
        if (adminList == null) {
            loadAdminList();
        }
        JID userJID = XMPPServer.getInstance().createJID(username, null);
        if (!adminList.contains(userJID)) {
            return;
        }
        // Remove user from admin list cache.
        adminList.remove(userJID);
        // Store updated list of admins with provider.
        provider.setAdmins(adminList);
    }

    /**
     * Removes an account from the list of Admin accounts, based off JID.
     *
     * @param jid JID of user to remove from admin list.
     */
    public void removeAdminAccount(JID jid) {
        if (adminList == null) {
            loadAdminList();
        }
        
        JID bareJID = jid.asBareJID();
        if (!adminList.contains(bareJID)) {
            return;
        }
        // Remove user from admin list cache.
        adminList.remove(bareJID);
        // Store updated list of admins with provider.
        provider.setAdmins(adminList);
    }

    /**
     * Returns true if the user is an admin.
     *
     * @param username Username of user to check whether they are an admin or not.
     * @param allowAdminIfEmpty Allows the "admin" user to log in if the adminList is empty.
     * @return True or false if user is an admin.
     */
    public boolean isUserAdmin(String username, boolean allowAdminIfEmpty) {
        if (adminList == null) {
            loadAdminList();
        }
        if (allowAdminIfEmpty && adminList.isEmpty()) {
            return "admin".equals(username);
        }
        JID userJID = XMPPServer.getInstance().createJID(username, null);
        return adminList.contains(userJID);
    }

    /**
     * Returns true if the user is an admin.
     *
     * @param jid JID of user to check whether they are an admin or not.
     * @param allowAdminIfEmpty Allows the "admin" user to log in if the adminList is empty.
     * @return True or false if user is an admin.
     */
    public boolean isUserAdmin(JID jid, boolean allowAdminIfEmpty) {
        if (adminList == null) {
            loadAdminList();
        }
        if (allowAdminIfEmpty && adminList.isEmpty()) {
            return "admin".equals(jid.getNode());
        }
        JID bareJID = jid.asBareJID();
        return adminList.contains(bareJID);
    }

    /**
     * Clears the list of admin users.
     */
    public void clearAdminUsers() {
        // Clear the admin list cache.
        if (adminList == null) {
            adminList = new ArrayList<JID>();
        }
        else {
            adminList.clear();
        }
        // Store empty list of admins with provider.
        provider.setAdmins(adminList);
    }

    /**
     * Sets the list of admin users based off of a list of usernames.  Clears list first.
     *
     * @param usernames List of usernames to set as admins.
     */
    public void setAdminUsers(List<String> usernames) {
        if (adminList == null) {
            adminList = new ArrayList<JID>();
        }
        else {
            adminList.clear();
        }
        List<JID> admins = new ArrayList<JID>();
        for (String username : usernames) {
            admins.add(XMPPServer.getInstance().createJID(username, null));
        }
        adminList.addAll(admins);
        provider.setAdmins(admins);
    }

    /**
     * Sets the list of admin users based off of a list of jids.  Clears list first.
     *
     * @param jids List of jids to set as admins.
     */
    public void setAdminJIDs(List<JID> jids) {
        if (adminList == null) {
            adminList = new ArrayList<JID>();
        }
        else {
            adminList.clear();
        }

        List<JID> admins = new ArrayList<JID>();
        for (JID jid : jids)
		{
            if (jid != null) {
        	    admins.add(jid.asBareJID());
            }
		}
        adminList.addAll(admins);
        provider.setAdmins(admins);
    }
}
