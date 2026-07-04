/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Represents user agent information provided by XMPP clients during authentication.
 * This information includes an optional UUID v4 identifier, software description,
 * and device description. This information is not exposed to other entities.
 */
public class UserAgentInfo implements Cacheable {
    private static final Logger Log = LoggerFactory.getLogger(UserAgentInfo.class);

    private final String id;          // UUID v4
    private final String software;    // Software description
    private final String device;      // Device description

    public UserAgentInfo(final String id, final String software, final String device) {
        this.id = id;
        this.software = software;
        this.device = device;
    }

    /**
     * Extracts and validates user agent information from the authentication element.
     *
     * @param userAgentElement the authentication element containing potential user agent data
     * @return UserAgentInfo containing the parsed data, or null if no user agent data present
     */
    public static UserAgentInfo extract(Element userAgentElement) {
        if (userAgentElement == null) {
            return null;
        }

        String id = null;

        // Extract and validate UUID v4 id if present
        String rawId = userAgentElement.attributeValue("id");
        if (rawId != null) {
            try {
                UUID uuid = UUID.fromString(rawId);
                // Validate it's a v4 UUID
                if (uuid.version() == 4) {
                    id = rawId;
                } else {
                    Log.warn("Invalid UUID version in user-agent id (must be v4): " + rawId);
                }
            } catch (IllegalArgumentException e) {
                Log.warn("Invalid UUID format in user-agent id: " + rawId);
            }
        }

        // Extract software info if present
        String software = null;
        Element softwareElement = userAgentElement.element("software");
        if (softwareElement != null) {
            software = softwareElement.getTextTrim();
        }

        // Extract device info if present
        String device = null;
        Element deviceElement = userAgentElement.element("device");
        if (deviceElement != null) {
            device = deviceElement.getTextTrim();
        }

        return new UserAgentInfo(id, software, device);
    }

    public String getId() {
        return id;
    }

    public String getSoftware() {
        return software;
    }

    public String getDevice() {
        return device;
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException {
        int size = CacheSizes.sizeOfObject();  // object overhead
        size += CacheSizes.sizeOfString(id);
        size += CacheSizes.sizeOfString(software);
        size += CacheSizes.sizeOfString(device);
        return size;
    }
}
