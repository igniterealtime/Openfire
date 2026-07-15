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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserAgentInfoTest {

    @Test
    public void testNullElement() {
        // Setup test fixture.
        // (no setup needed - null is passed directly)

        // Execute system under test.
        // Verify result.
        assertNull(UserAgentInfo.extract(null));
    }

    @Test
    public void testEmptyUserAgent() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertNull(info.getId());
        assertNull(info.getSoftware());
        assertNull(info.getDevice());
    }

    @Test
    public void testCompleteValidUserAgent() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addAttribute("id", "123e4567-e89b-42d3-a456-556642440000"); // Valid UUID v4

        Element software = userAgent.addElement("software");
        software.setText("My XMPP Client v1.0");

        Element device = userAgent.addElement("device");
        device.setText("Android Phone Model X");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertEquals("123e4567-e89b-42d3-a456-556642440000", info.getId());
        assertEquals("My XMPP Client v1.0", info.getSoftware());
        assertEquals("Android Phone Model X", info.getDevice());
    }

    @Test
    public void testInvalidUUIDFormat() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addAttribute("id", "not-a-uuid");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertNull(info.getId()); // Invalid UUID should be ignored
    }

    @Test
    public void testNonV4UUID() {
        // Setup test fixture.
        // This is a valid UUID v1
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addAttribute("id", "123e4567-e89b-12d3-a456-556642440000");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertNull(info.getId()); // Non-v4 UUID should be ignored
    }

    @Test
    public void testOnlySoftware() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");
        Element software = userAgent.addElement("software");
        software.setText("My XMPP Client v1.0");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertNull(info.getId());
        assertEquals("My XMPP Client v1.0", info.getSoftware());
        assertNull(info.getDevice());
    }

    @Test
    public void testOnlyDevice() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");
        Element device = userAgent.addElement("device");
        device.setText("Android Phone Model X");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertNull(info.getId());
        assertNull(info.getSoftware());
        assertEquals("Android Phone Model X", info.getDevice());
    }

    @Test
    public void testEmptyElements() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");
        userAgent.addElement("software");
        userAgent.addElement("device");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertNull(info.getId());
        assertEquals("", info.getSoftware());
        assertEquals("", info.getDevice());
    }

    @Test
    public void testWhitespaceHandling() {
        // Setup test fixture.
        Element userAgent = DocumentHelper.createElement("user-agent");

        Element software = userAgent.addElement("software");
        software.setText("  My XMPP Client v1.0  ");

        Element device = userAgent.addElement("device");
        device.setText("\tAndroid Phone Model X\n");

        // Execute system under test.
        UserAgentInfo info = UserAgentInfo.extract(userAgent);

        // Verify result.
        assertNotNull(info);
        assertEquals("My XMPP Client v1.0", info.getSoftware());
        assertEquals("Android Phone Model X", info.getDevice());
    }
}
