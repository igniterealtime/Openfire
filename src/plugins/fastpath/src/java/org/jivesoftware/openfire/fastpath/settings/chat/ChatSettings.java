/**
 * $RCSfile$
 * $Revision: 19225 $
 * $Date: 2005-07-06 12:56:54 -0700 (Wed, 06 Jul 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.fastpath.settings.chat;

import org.jivesoftware.xmpp.workgroup.Workgroup;

import java.util.*;

/**
 * Facade for caching of workgroup chat settings. This class will handle adding, removing and
 * retrieval of ChatSetting objects from the cache.
 */
public class ChatSettings {

    private transient Workgroup workgroup;

    private Map<KeyEnum, ChatSetting> settingsList = new HashMap<KeyEnum, ChatSetting>();

    /**
     * Creates a new ChatSettings instance.
     *
     * @param workgroup the workgroup.
     */
    public ChatSettings(Workgroup workgroup) {
        this.workgroup = workgroup;
    }

    /**
     * Adds a ChatSetting to settings list.
     *
     * @param setting the <code>ChatSetting</code> to add.
     */
    protected void addChatSetting(ChatSetting setting) {
        settingsList.put(setting.getKey(), setting);
    }

    /**
     * Adds a ChatSetting to settings list.
     *
     * @param setting the <code>ChatSetting</code> to add.
     */
    protected void removeChatSetting(ChatSetting setting) {
        settingsList.remove(setting.getKey());
    }


    /**
     * Returns all settings for a workgroup.
     *
     * @return the list of settings.
     */
    public Collection<ChatSetting> getChatSettings() {
        return settingsList.values();
    }

    /**
     * Returns all settings of a workgroup of a certain type.
     *
     * @param type the type of setting to return.
     * @return the list of settings in a workgroup with a certain type.
     */
    public List<ChatSetting> getChatSettingsByType(ChatSettings.SettingType type) {
        final List<ChatSetting> returnList = new ArrayList<ChatSetting>();
        for (ChatSetting setting : settingsList.values()) {
            if (setting.getType() == type) {
                returnList.add(setting);
            }
        }
        Collections.sort(returnList, chatSettingComparator);
        return returnList;
    }

    /**
     * Returns a ChatSetting based on it's key value.
     *
     * @param key the key to search for.
     * @return the ChatSetting with the given key, if not found, this method returns null.
     */
    public ChatSetting getChatSetting(KeyEnum key) {
        ChatSetting setting = settingsList.get(key);
        if (setting == null) {
            synchronized (key) {
                setting = settingsList.get(key);
                if (setting == null) {
                    // Try to create the default setting for the given key
                    ChatSettingsCreator.getInstance().createDefaultSetting(workgroup.getJID(), key);
                    setting = settingsList.get(key);
                }
            }
        }
        return setting;
    }

    public ChatSetting getChatSetting(String key) {
        for (KeyEnum k : KeyEnum.values()) {
            if (k.toString().equals(key)) {
                return getChatSetting(k);
            }
        }
        return null;
    }

    /**
     * Sorts all SearchResults by Relevance.
     */
    static final Comparator<ChatSetting> chatSettingComparator = new Comparator<ChatSetting>() {
        public int compare(ChatSetting item1, ChatSetting item2) {
            String str1 = item1.getLabel();
            String str2 = item2.getLabel();

            // Check if identical
            if (str1.equals(str2)) {
                return 0;
            }

            int i = str1.compareToIgnoreCase(str2);
            if (i < 0) {
                return -1;
            }
            else if (i > 0) {
                return 1;
            }
            else {
                return 0;
            }
        }
    };

    public enum SettingType {
        image_settings(0),
        text_settings(1),
        bot_settings(2),
        search_settings(3),
        offline_settings(4);

        private int type;


        SettingType(int t) {
            type = t;
        }

        public int getType() {
            return type;
        }
    }
}