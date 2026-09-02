/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.handler;

import org.dom4j.Element;
import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.net.Bind2Request;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bind2StreamManagementAvailabilityTest
{
    private Bind2StreamManagementHandler handler;

    @BeforeAll
    static void configureOpenfire() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    static void clearProperties()
    {
        Fixtures.clearExistingProperties();
    }

    @BeforeEach
    void registerHandler()
    {
        handler = new Bind2StreamManagementHandler();
        Bind2Request.registerElementHandler(handler);
    }

    @AfterEach
    void restoreState()
    {
        StreamManager.ACTIVE.setValue(StreamManager.ACTIVE.getDefaultValue());
        Bind2Request.unregisterElementHandler(handler);
        handler = null;
    }

    @Test
    void followsDynamicStreamManagementSetting()
    {
        StreamManager.ACTIVE.setValue(false);
        assertFalse(advertisesStreamManagement());

        StreamManager.ACTIVE.setValue(true);
        assertTrue(advertisesStreamManagement());

        StreamManager.ACTIVE.setValue(false);
        assertFalse(advertisesStreamManagement());
    }

    private static boolean advertisesStreamManagement()
    {
        final Element inline = Bind2Request.featureElement().element("inline");
        return inline.elements("feature").stream()
            .anyMatch(feature -> StreamManager.NAMESPACE_V3.equals(feature.attributeValue("var")));
    }
}
