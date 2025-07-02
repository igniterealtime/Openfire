/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.net;

import org.dom4j.Element;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Represents user agent information provided by XMPP clients during authentication.
 * This information includes an optional UUID v4 identifier, software description,
 * and device description. This information is not exposed to other entities.
 */
public class UserAgentInfo {
    private static final Logger Log = LoggerFactory.getLogger(UserAgentInfo.class);

    private String id;          // UUID v4
    private String software;    // Software description
    private String device;      // Device description

    /**
     * Extracts and validates user agent information as from authentication element.
     *
     * @param userAgentElement the authentication element containing potential user agent data
     * @return UserAgentInfo containing the parsed data, or null if no user agent data present
     */
    public static UserAgentInfo extract(Element userAgentElement) {
        if (userAgentElement == null) {
            return null;
        }

        UserAgentInfo userAgentInfo = new UserAgentInfo();

        // Extract and validate UUID v4 id if present
        String id = userAgentElement.attributeValue("id");
        if (id != null) {
            try {
                UUID uuid = UUID.fromString(id);
                // Validate it's a v4 UUID
                if (uuid.version() == 4) {
                    userAgentInfo.setId(id);
                } else {
                    Log.warn("Invalid UUID version in user-agent id (must be v4): " + id);
                }
            } catch (IllegalArgumentException e) {
                Log.warn("Invalid UUID format in user-agent id: " + id);
            }
        }

        // Extract software info if present
        Element softwareElement = userAgentElement.element("software");
        if (softwareElement != null) {
            userAgentInfo.setSoftware(softwareElement.getTextTrim());
        }

        // Extract device info if present
        Element deviceElement = userAgentElement.element("device");
        if (deviceElement != null) {
            userAgentInfo.setDevice(deviceElement.getTextTrim());
        }

        return userAgentInfo;
    }

    public String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    public String getSoftware() {
        return software;
    }

    void setSoftware(String software) {
        this.software = software;
    }

    public String getDevice() {
        return device;
    }

    void setDevice(String device) {
        this.device = device;
    }
}
