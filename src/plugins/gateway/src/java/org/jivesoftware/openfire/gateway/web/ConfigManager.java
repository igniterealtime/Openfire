/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.gateway.web;

import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.gateway.GatewayPlugin;
import org.jivesoftware.openfire.gateway.TransportType;
import org.jivesoftware.openfire.gateway.PermissionManager;
import org.jivesoftware.openfire.gateway.Registration;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.LocaleUtils;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Attribute;
import org.xmpp.packet.JID;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

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
            Attribute desckey = node.attribute("desckey");
            Attribute var = node.attribute("var");
            Attribute sysprop = node.attribute("sysprop");

            if (desckey == null || var == null || sysprop == null) {
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
            Attribute desckey = node.attribute("desckey");
            Attribute var = node.attribute("var");
            Attribute sysprop = node.attribute("sysprop");

            if (desckey == null || var == null || sysprop == null) {
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
     * @return List of usernames and groups (@ preceded) that were rejected.
     */
    public List<String> savePermissions(String transportName, Integer overallSetting, List<String> users, List<String> groups) {
        JiveGlobals.setProperty("plugin.gateway."+transportName+".registration", overallSetting.toString());
        PermissionManager permissionManager = new PermissionManager(TransportType.valueOf(transportName));
        List<String> errorList = new ArrayList<String>();
        ArrayList<User> userList = new ArrayList<User>();
        UserManager userManager = UserManager.getInstance();
        for (String username : users) {
            if (username.matches("\\s*")) { continue; }
            try {
                if (username.contains("@")) {
                    JID jid = new JID(username);
                    if (!jid.getDomain().equals(XMPPServer.getInstance().getServerInfo().getName())) {
                        throw new UserNotFoundException();
                    }
                    username = username.substring(0, username.indexOf("@"));
                }
                User user = userManager.getUser(username);
                if (user == null || user.getUsername() == null) { throw new UserNotFoundException(); }
                userList.add(user);
            }
            catch (UserNotFoundException e) {
                Log.warn("User "+username+" not found while adding access rules.");
                errorList.add(username);
            }
        }
        permissionManager.storeUserList(userList);
        
        ArrayList<Group> groupList = new ArrayList<Group>();
        GroupManager groupManager = GroupManager.getInstance();
        for (String grpname : groups) {
            if (grpname.matches("\\s*")) { continue; }
            try {
                Group group = groupManager.getGroup(grpname);
                if (group == null || group.getName() == null) { throw new GroupNotFoundException(); }
                groupList.add(group);
            }
            catch (GroupNotFoundException e) {
                Log.warn("Group "+grpname+" not found while adding access rules.");
                errorList.add("@"+grpname);
            }
        }
        permissionManager.storeGroupList(groupList);

        return errorList;
    }

    /**
     * Adds a new registration via the web interface.
     *
     * @param user Username or full JID of user who is getting an account registered.
     * @param transportType Type of transport to add user to.
     * @param legacyUsername User's username on the legacy service.
     * @param legacyPassword User's password on the legacy service.
     * @param legacyNickname User's nickname on the legacy service.
     * @return Error message or null on success.
     */
    public String addRegistration(String user, String transportType, String legacyUsername, String legacyPassword, String legacyNickname) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        GatewayPlugin plugin = (GatewayPlugin)pluginManager.getPlugin("gateway");
        JID jid;
        if (user.contains("@")) {
            jid = new JID(user);
        }
        else {
            jid = new JID(user, XMPPServer.getInstance().getServerInfo().getName(), null);
        }
        if (!plugin.getTransportInstance(transportType).isEnabled()) {
            return LocaleUtils.getLocalizedString("gateway.web.registrations.notenabled", "gateway");
        }
        try {
            plugin.getTransportInstance(transportType).getTransport().addNewRegistration(jid, legacyUsername, legacyPassword, legacyNickname, false);
            return null;
        }
        catch (UserNotFoundException e) {
            Log.error("Not found while adding account for "+jid.toString());
            return LocaleUtils.getLocalizedString("gateway.web.registrations.xmppnotfound", "gateway");
        }
        catch (IllegalAccessException e) {
            Log.error("Domain of JID specified for registration is not on this server: "+jid.toString());
            return LocaleUtils.getLocalizedString("gateway.web.registrations.illegaldomain", "gateway");
        }
        catch (IllegalArgumentException e) {
            Log.error("Username specified for registration is not valid.");
            return LocaleUtils.getLocalizedString("gateway.web.registrations.invaliduser", "gateway");
        }
    }

    /**
     * Deletes a registration via the web interface.
     *
     * @param registrationID ID number associated with registration to delete.
     * @return Error message or null on success.
     */
    public String deleteRegistration(Integer registrationID) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        GatewayPlugin plugin = (GatewayPlugin)pluginManager.getPlugin("gateway");
        try {
            Registration reg = new Registration(registrationID);
            if (!plugin.getTransportInstance(reg.getTransportType().toString()).isEnabled()) {
                return LocaleUtils.getLocalizedString("gateway.web.registrations.notenabled", "gateway");
            }
            plugin.getTransportInstance(reg.getTransportType().toString()).getTransport().deleteRegistration(reg.getJID());
            return null;
        }
        catch (NotFoundException e) {
            // Ok, nevermind.
            Log.error("Not found while deleting id "+registrationID, e);
            return LocaleUtils.getLocalizedString("gateway.web.registrations.xmppnotfound", "gateway");
        }
        catch (UserNotFoundException e) {
            // Ok, nevermind.
            Log.error("Not found while deleting id "+registrationID, e);
            return LocaleUtils.getLocalizedString("gateway.web.registrations.regnotfound", "gateway");
        }
    }

    /**
     * Updates a registration via the web interface.
     *
     *
     * @param registrationID ID number associated with registration to modify.
     * @param legacyUsername User's updated username on the legacy service.
     * @param legacyPassword User's updated password on the legacy service, null if no change.
     * @param legacyNickname User's updated nickname on the legacy service.
     * @return Error message or null on success.
     */
    public String updateRegistration(Integer registrationID, String legacyUsername, String legacyPassword, String legacyNickname) {
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        GatewayPlugin plugin = (GatewayPlugin)pluginManager.getPlugin("gateway");
        try {
            Registration reg = new Registration(registrationID);
            if (!plugin.getTransportInstance(reg.getTransportType().toString()).isEnabled()) {
                return LocaleUtils.getLocalizedString("gateway.web.registrations.notenabled", "gateway");
            }
            reg.setUsername(legacyUsername);
            if (legacyPassword != null) {
                reg.setPassword(legacyPassword);
            }
            reg.setNickname(legacyNickname);
            return null;
        }
        catch (NotFoundException e) {
            // Ok, nevermind.
            Log.error("Not found while editing id "+registrationID, e);
            return LocaleUtils.getLocalizedString("gateway.web.registrations.regnotfound", "gateway");
        }
    }

}
