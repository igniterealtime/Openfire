/**
 * $RCSfile$
 * $Revision: 19037 $
 * $Date: 2005-06-13 17:01:53 -0700 (Mon, 13 Jun 2005) $
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

/**
 * Represents a setting entry in the ChatSettings table.
 */
public class ChatSetting {

    private String label;
    private String workgroupNode;
    private ChatSettings.SettingType type;
    private KeyEnum key;
    private String value;
    private String defaultValue;
    private String description;

    public ChatSetting(KeyEnum key) {
        this.key = key;
    }

    public ChatSetting(String key) {
        for (KeyEnum keys : KeyEnum.values()) {
            if (keys.toString().equals(key)) {
                this.key = keys;
                break;
            }
        }
    }

    public String getWorkgroupNode() {
        return workgroupNode;
    }

    public void setWorkgroupNode(String workgroupNode) {
        this.workgroupNode = workgroupNode;
    }

    public ChatSettings.SettingType getType() {
        return type;
    }

    public void setType(ChatSettings.SettingType type) {
        this.type = type;
    }

    public void setType(int type){
        for(ChatSettings.SettingType key : ChatSettings.SettingType.values()){
            if(key.getType() == type){
                setType(key);
            }
        }
    }

    public KeyEnum getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
