/**
 * $RCSfile$
 * $Revision: 23995 $
 * $Date: 2005-11-21 13:48:54 -0800 (Mon, 21 Nov 2005) $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath.settings.chat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import org.dom4j.Element;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.util.Log;
import org.jivesoftware.xmpp.workgroup.AgentSession;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventDispatcher;
import org.jivesoftware.xmpp.workgroup.event.WorkgroupEventListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

/**
 * Utility class for doing all Database operations related to the ChatSettings.
 * You would use this class to retrieve, update or insert new settings.
 */
public class ChatSettingsManager implements WorkgroupEventListener {

    private static final String GET_SETTINGS =
            "SELECT * FROM fpChatSetting WHERE workgroupNode=?";
    private static final String INSERT_CHAT_SETTING =
            "INSERT INTO fpChatSetting VALUES(?,?,?,?,?,?,?)";
    private static final String UPDATE_CHAT_SETTING =
            "UPDATE fpChatSetting SET value=? WHERE name=? AND workgroupNode=?";
    private static final String DELETE_CHAT_SETTINGS =
            "DELETE FROM fpChatSetting WHERE workgroupNode=?";
    private static final String DELETE_SINGLE_CHAT_SETTING = 
            "DELETE FROM fpChatSetting WHERE name=? AND workgroupNode=?";

    private static ChatSettingsManager singleton = new ChatSettingsManager();

    /**
     * Map for caching settings. This will map by workgroup node.
     */
    private final Map<String, ChatSettings> cachedSettings = new HashMap<String, ChatSettings>();

    /**
     * Returns the singleton instance of <CODE>ChatSettingsManager</CODE>,
     * creating it if necessary.
     * <p/>
     *
     * @return the singleton instance of <Code>ChatSettingsManager</CODE>
     */
    public static ChatSettingsManager getInstance() {
        return singleton;
    }

    private ChatSettingsManager() {
        // Private constructor for singleton design.
        WorkgroupEventDispatcher.addListener(this);
    }

    public static void shutdown() {
        WorkgroupEventDispatcher.removeListener(singleton);
        singleton = null;
    }

    /**
     * Retrieves the settings for a given workgroup.
     *
     * @param workgroup the owning workgroup of the settings.
     * @return the ChatSettings object mapped to the workgroup.
     */
    private ChatSettings getChatSettingsFromDb(Workgroup workgroup) {
        final ChatSettings chatSettings = new ChatSettings(workgroup);
        cachedSettings.put(workgroup.getJID().getNode(), chatSettings);

        Connection con = null;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            pstmt = con.prepareStatement(GET_SETTINGS);

            String workgroupName = workgroup.getJID().getNode();

            pstmt.setString(1, workgroupName);
            ResultSet result = pstmt.executeQuery();
            if (result.next()) {
                do {
                    String wg = result.getString("workgroupNode");
                    int type = result.getInt("type");
                    String label = result.getString("label");
                    String description = result.getString("description");
                    String name = result.getString("name");
                    String value = result.getString("value");
                    String defaultValue = result.getString("defaultValue");

                    ChatSetting setting = new ChatSetting(name);
                    setting.setWorkgroupNode(workgroupName);
                    setting.setType(type);

                    setting.setValue(value);
                    setting.setDefaultValue(defaultValue);
                    setting.setLabel(label);
                    setting.setDescription(description);
                    chatSettings.addChatSetting(setting);
                }
                while (result.next());
            }
            else {
                // If no settings were found in the DB then try creating the default settings for
                // this workgroup
                ChatSettingsCreator.getInstance().createDefaultSettings(workgroup.getJID());
            }
        }
        catch (Exception ex) {
            cachedSettings.remove(workgroup.getJID().getNode());
            Log.error(ex);
        }
        finally {
            DbConnectionManager.closeConnection(pstmt, con);
        }
        return chatSettings;
    }

    /**
     * Retrieves the ChatSettings mapped to a particular workgroup.
     *
     * @param workgroup the owning workgroup of the settings.
     * @return the ChatSettings found.
     */
    public ChatSettings getChatSettings(Workgroup workgroup) {
        String workgroupNode = workgroup.getJID().getNode();
        ChatSettings chatSettings = cachedSettings.get(workgroupNode);
        if (chatSettings == null) {
            synchronized (workgroupNode.intern()) {
                chatSettings = cachedSettings.get(workgroupNode);
                if (chatSettings == null) {
                    chatSettings = getChatSettingsFromDb(workgroup);
                }
            }
        }
        return chatSettings;
    }

    /**
     * Adds a new ChatSetting, persisting to the database and cache objects.
     *
     * @param settings the <code>ChatSetting</code> to add.
     */
    public void addChatSetting(ChatSetting settings) {
        Connection con;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            try {
                pstmt = con.prepareStatement(INSERT_CHAT_SETTING);
                pstmt.setString(1, settings.getWorkgroupNode());
                pstmt.setInt(2, settings.getType().getType());
                pstmt.setString(3, settings.getLabel());
                pstmt.setString(4, settings.getDescription());
                pstmt.setString(5, settings.getKey().toString());

                DbConnectionManager.setLargeTextField(pstmt, 6, settings.getValue());
                pstmt.setString(7, settings.getDefaultValue());
                pstmt.executeUpdate();
            }
            catch (Exception ex) {
                Log.error(ex);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }

        // Add to cache
        ChatSettings chatSettings = cachedSettings.get(settings.getWorkgroupNode());
        if (chatSettings != null) {
            chatSettings.addChatSetting(settings);
        }
    }

    /**
     * Update a WebChatSetting in the Database.
     *
     * @param settings the <code>WebChatSetting</code> to update.
     */
    public void updateChatSetting(ChatSetting settings) {
        Connection con;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            try {
                pstmt = con.prepareStatement(UPDATE_CHAT_SETTING);
                DbConnectionManager.setLargeTextField(pstmt, 1, settings.getValue());
                pstmt.setString(2, settings.getKey().toString());
                pstmt.setString(3, settings.getWorkgroupNode());
                pstmt.executeUpdate();
            }
            catch (Exception ex) {
                Log.error(ex);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }

        // Add to cache
        ChatSettings chatSettings = cachedSettings.get(settings.getWorkgroupNode());
        if (chatSettings != null) {
            chatSettings.addChatSetting(settings);
        }
    }

    /**
     * Removes a <code>WebChatSetting</code> from the database.
     *
     * @param setting the WebChatSetting to remove.
     */
    public void removeChatSetting(ChatSetting setting) {
        Connection con;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            try {
                pstmt = con.prepareStatement(DELETE_SINGLE_CHAT_SETTING);
                pstmt.setString(1, setting.getKey().toString());
                pstmt.setString(2, setting.getWorkgroupNode());
                pstmt.executeUpdate();
            }
            catch (Exception ex) {
                Log.error(ex);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }

        // Add to cache
        ChatSettings chatSettings = cachedSettings.get(setting.getWorkgroupNode());
        if (chatSettings != null) {
            chatSettings.removeChatSetting(setting);
        }
    }

    /**
     * Removes a <code>WebChatSetting</code> from the database.
     *
     * @param key the key of the chat setting to remove.
     * @param workgroup the workgroup the key belongs to.
     */
    public void removeChatSetting(KeyEnum key, Workgroup workgroup) {
        String workgroupNode = workgroup.getJID().getNode();
        Connection con;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            try {
                pstmt = con.prepareStatement(DELETE_SINGLE_CHAT_SETTING);
                pstmt.setString(1, key.toString());
                pstmt.setString(2, workgroupNode);
                pstmt.executeUpdate();
            }
            catch (Exception ex) {
                Log.error(ex);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }

        // Add to cache
        ChatSettings chatSettings = cachedSettings.get(workgroupNode);
        if (chatSettings != null) {
            ChatSetting setting = chatSettings.getChatSetting(key);
            if (setting != null) {
                chatSettings.removeChatSetting(setting);
            }
        }
    }

    /**
     * Retrieves a <code>WebChatSetting</code> based on the owning workgroup and setting key.
     *
     * @param workgroup the owning workgroup.
     * @param key       the setting key to find.
     * @return the ChatSetting found, otherwise null is returned.
     */
    public ChatSetting getChatSetting(Workgroup workgroup, String key) {
        ChatSettings chatSettings = getChatSettings(workgroup);
        if (chatSettings != null) {
            return chatSettings.getChatSetting(key);
        }
        return null;
    }

    /**
     * Send all WebChat settings for a particular Workgroup.
     *
     * @param packet    the original packet that made the request.
     * @param workgroup the workgroup the packet was sent to.
     */
    public void getAllChatSettings(IQ packet, Workgroup workgroup) {
        IQ reply = IQ.createResultIQ(packet);

        // Retrieve the web chat setting.

        ChatSettings chatSettings = getChatSettings(workgroup);
        if (chatSettings == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        Element webSettings = reply.setChildElement("chat-settings", "http://jivesoftware.com/protocol/workgroup");

        for (ChatSetting setting : chatSettings.getChatSettings()) {
            Element root = webSettings.addElement("chat-setting");

            try {
                root.addElement("key").setText(setting.getKey().toString());
                root.addElement("value").setText(setting.getValue());
                root.addElement("type").setText(Integer.toString(setting.getType().getType()));
            }
            catch (Exception e) {
                Log.error(e);
            }
        }

        workgroup.send(reply);
    }

    /**
     * Send all WebChat settings for a particular Workgroup and type.
     *
     * @param packet the original packet that made the request.
     * @param workgroup the workgroup the packet was sent to.
     * @param type the type.
     */
    public void getChatSettingsByType(IQ packet, Workgroup workgroup, int type) {
        IQ reply = IQ.createResultIQ(packet);

        // Retrieve the web chat setting.

        ChatSettings chatSettings = getChatSettings(workgroup);
        if (chatSettings == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        Element webSettings = reply.setChildElement("chat-settings", "http://jivesoftware.com/protocol/workgroup");

        for (ChatSetting setting : chatSettings.getChatSettings()) {
            if (setting.getType().getType() == type) {
                Element root = webSettings.addElement("chat-setting");

                root.addElement("key").setText(setting.getKey().toString());
                root.addElement("value").setText(setting.getValue());
                root.addElement("type").setText(Integer.toString(setting.getType().getType()));
            }
        }

        workgroup.send(reply);
    }

    /**
     * Send a single WebChat setting a given workgroup.
     *
     * @param packet the original packet that made the request.
     * @param workgroup the workgroup the packet was sent to.
     * @param key the mapped setting key.
     */
    public void getChatSettingByKey(IQ packet, Workgroup workgroup, String key) {
        IQ reply = IQ.createResultIQ(packet);

        // Retrieve the web chat setting.
        ChatSetting setting = getChatSetting(workgroup, key);
        if (setting == null) {
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(new PacketError(PacketError.Condition.item_not_found));
            workgroup.send(reply);
            return;
        }

        Element webSettings = reply.setChildElement("chat-settings", "http://jivesoftware.com/protocol/workgroup");
        Element root = webSettings.addElement("chat-setting");
        root.addElement("key").setText(setting.getKey().toString());
        root.addElement("value").setText(setting.getValue());
        root.addElement("type").setText(Integer.toString(setting.getType().getType()));

        workgroup.send(reply);
    }

    public void workgroupCreated(Workgroup workgroup) {

    }

    public void workgroupDeleting(Workgroup workgroup) {

    }

    public void workgroupDeleted(Workgroup workgroup) {
        String workgroupNode = workgroup.getJID().getNode();

        // Remove settings from cache
        cachedSettings.remove(workgroupNode);

        // Delete from DB.
        Connection con;
        PreparedStatement pstmt = null;
        try {
            con = DbConnectionManager.getConnection();
            try {
                pstmt = con.prepareStatement(DELETE_CHAT_SETTINGS);
                pstmt.setString(1, workgroupNode);
                pstmt.executeUpdate();
            }
            catch (Exception ex) {
                Log.error(ex);
            }
            finally {
                DbConnectionManager.closeConnection(pstmt, con);
            }
        }
        catch (Exception ex) {
            Log.error(ex);
        }
    }

    public void workgroupOpened(Workgroup workgroup) {

    }

    public void workgroupClosed(Workgroup workgroup) {

    }

    public void agentJoined(Workgroup workgroup, AgentSession agentSession) {

    }

    public void agentDeparted(Workgroup workgroup, AgentSession agentSession) {

    }

    public void chatSupportStarted(Workgroup workgroup, String sessionID) {

    }

    public void chatSupportFinished(Workgroup workgroup, String sessionID) {

    }

    public void agentJoinedChatSupport(Workgroup workgroup, String sessionID, AgentSession agentSession) {

    }

    public void agentLeftChatSupport(Workgroup workgroup, String sessionID, AgentSession agentSession) {

    }
}
