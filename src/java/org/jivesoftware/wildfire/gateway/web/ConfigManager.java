/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.gateway.web;

import org.jivesoftware.wildfire.container.PluginManager;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.user.UserManager;
import org.jivesoftware.wildfire.user.UserNotFoundException;
import org.jivesoftware.wildfire.user.User;
import org.jivesoftware.wildfire.group.Group;
import org.jivesoftware.wildfire.group.GroupManager;
import org.jivesoftware.wildfire.group.GroupNotFoundException;
import org.jivesoftware.wildfire.gateway.GatewayPlugin;
import org.jivesoftware.wildfire.gateway.TransportType;
import org.jivesoftware.wildfire.gateway.PermissionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Attribute;

import java.util.HashMap;
import java.util.ArrayList;

/**
 * Transport Configuration Manager (for web interface)
 *
 * Handles web interface interactions with the transport instances, such as enabling or
 * disabling them, configuring options, etc.
 *
 * @author Daniel Henninger
 */
public class ConfigManager {

    /**
     * Toggles whether a transport is enabled or disabled.
     *
     * @param transportName Name of the transport to be enabled or disabled (type of transport)
     * @return True or false if the transport is enabled after this call.
     */
    public boolean toggleTransport(String transportName) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        GatewayPlugin plugin = (GatewayPlugin)pluginManager.getPlugin("gateway");
        if (!plugin.serviceEnabled(transportName)) {
            plugin.enableService(transportName);
            return true;
        }
        else {
            plugin.disableService(transportName);
            return false;
        }
    }

    /**
     * Saves settings from options screen.
     *
     * @param transportName Name of the transport to have it's options saved (type of transport)
     * @param options Options passed from options form.
     */
    public void saveSettings(String transportName, HashMap<String,String> options) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        GatewayPlugin plugin = (GatewayPlugin)pluginManager.getPlugin("gateway");
        Document optConfig = plugin.getOptionsConfig(TransportType.valueOf(transportName));

        Element leftPanel = optConfig.getRootElement().element("leftpanel");
        if (leftPanel != null && leftPanel.nodeCount() > 0) {
            for (Object nodeObj : leftPanel.elements("item")) {
                Element node = (Element)nodeObj;
                saveOptionSetting(node, options);
            }
        }

        Element rightPanel = optConfig.getRootElement().element("rightpanel");
        if (rightPanel != null && rightPanel.nodeCount() > 0) {
            for (Object nodeObj : rightPanel.elements("item")) {
                Element node = (Element)nodeObj;
                saveOptionSetting(node, options);
            }
        }
    }

    /**
     * Helper function designed to handle saving option types.
     *
     * @param node Node describing the configuration item.
     * @param options Options passed from form.
     */
    private void saveOptionSetting(Element node, HashMap<String,String> options) {
        Attribute type = node.attribute("type");
        if (type.getText().equals("text")) {
            // Required fields
            Attribute desc = node.attribute("desc");
            Attribute var = node.attribute("var");
            Attribute sysprop = node.attribute("sysprop");

            if (desc == null || var == null || sysprop == null) {
                Log.error("Missing variable from options config.");
                return;
            }

            // Process any variables that we are setting.
            if (var.getText().equals("host")) {
                JiveGlobals.setProperty(sysprop.getText(), options.get("host"));
            }
            else if (var.getText().equals("port")) {
                JiveGlobals.setProperty(sysprop.getText(), options.get("port"));
            }
            else if (var.getText().equals("encoding")) {
                JiveGlobals.setProperty(sysprop.getText(), options.get("encoding"));
            }
        }
        else if (type.getText().equals("toggle")) {
            // Required fields
            Attribute desc = node.attribute("desc");
            Attribute var = node.attribute("var");
            Attribute sysprop = node.attribute("sysprop");

            if (desc == null || var == null || sysprop == null) {
                Log.error("Missing variable from options config.");
                return;
            }

            // Process any variables that we are setting.
            // None yet.

            for (Object itemObj : node.elements("item")) {
                Element item = (Element)itemObj;
                saveOptionSetting(item, options);
            }
        }
    }

    /**
     * Saves permissions settings from web interface.
     *
     * We validate all of the groups before actually adding them.
     *
     * @param transportName Name of the transport to have it's options saved (type of transport)
     * @param overallSetting The general "all(1), some(2), or none(3)" setting for the permissions.
     * @param users List of specific users that have access.
     * @param groups List of specific groups that have access.
     */
    public void savePermissions(String transportName, Integer overallSetting, ArrayList<String> users, ArrayList<String> groups) {
        JiveGlobals.setProperty("plugin.gateway."+transportName+".registration", overallSetting.toString());
        PermissionManager permissionManager = new PermissionManager(TransportType.valueOf(transportName));

        ArrayList<User> userList = new ArrayList<User>();
        UserManager userManager = UserManager.getInstance();
        for (String username : users) {
            if (username.matches("\\s+")) { continue; }
            try {
                User user = userManager.getUser(username);
                if (user == null || user.getUsername() == null) { throw new UserNotFoundException(); }
                userList.add(user);
            }
            catch (UserNotFoundException e) {
                Log.warn("User "+username+" not found while adding access rules.");
            }
        }
        permissionManager.storeUserList(userList);
        
        ArrayList<Group> groupList = new ArrayList<Group>();
        GroupManager groupManager = GroupManager.getInstance();
        for (String grpname : groups) {
            if (grpname.matches("\\s+")) { continue; }
            try {
                Group group = groupManager.getGroup(grpname);
                if (group == null || group.getName() == null) { throw new GroupNotFoundException(); }
                groupList.add(group);
            }
            catch (GroupNotFoundException e) {
                Log.warn("Group "+grpname+" not found while adding access rules.");
            }
        }
        permissionManager.storeGroupList(groupList);
    }

}
