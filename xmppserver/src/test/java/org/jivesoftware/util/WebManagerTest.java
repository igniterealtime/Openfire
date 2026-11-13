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
package org.jivesoftware.util;

import org.jivesoftware.openfire.user.User;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests that verify the per-page user properties ("pageName1=value,pageName2=value") of {@link WebManager}.
 */
public class WebManagerTest {

    private static final String PROPERTY = "console.rows_per_page";

    private final Map<String, String> userProperties = new HashMap<>();

    private final WebManager webManager = new WebManager() {
        @Override
        public User getUser() {
            return new User() {
                @Override
                public Map<String, String> getProperties() {
                    return userProperties;
                }
            };
        }
    };

    @Test
    public void storedValuesCanBeRetrieved() {
        webManager.setRowsPerPage("user-summary", 25);
        webManager.setRowsPerPage("/group-summary.jsp", 50);
        webManager.setRowsPerPage("user-summary", 100);

        assertEquals("user-summary=100,/group-summary.jsp=50", userProperties.get(PROPERTY));
        assertEquals(100, webManager.getRowsPerPage("user-summary", 15));
        assertEquals(50, webManager.getRowsPerPage("/group-summary.jsp", 15));
        assertEquals(15, webManager.getRowsPerPage("session-summary", 15));
    }

    @Test
    public void pageNamesAreMatchedAsAWhole() {
        webManager.setRowsPerPage("server-session-summary", 25);
        webManager.setRowsPerPage("session-summary", 50);

        assertEquals("server-session-summary=25,session-summary=50", userProperties.get(PROPERTY));
        assertEquals(25, webManager.getRowsPerPage("server-session-summary", 15));
        assertEquals(50, webManager.getRowsPerPage("session-summary", 15));
    }

    @Test
    public void pageNamesThatContainDelimitersAreNotStored() {
        webManager.setRowsPerPage("/server-properties.jsp;x=y", 25);
        assertNull(userProperties.get(PROPERTY));

        webManager.setRowsPerPage("user-summary", 25);
        webManager.setRowsPerPage("/server-properties.jsp;x=y", 25);
        webManager.setRowsPerPage("/server-properties.jsp;x,y", 25);

        assertEquals("user-summary=25", userProperties.get(PROPERTY));
    }

    @Test
    public void malformedStoredValuesAreIgnored() {
        for (final String stored : new String[] { "/server-properties.jsp;x=y=25", "user-summary", "user-summary=", "=", ",", "" }) {
            userProperties.put(PROPERTY, stored);

            assertEquals(15, webManager.getRowsPerPage("user-summary", 15), "Stored value: " + stored);
            assertEquals(15, webManager.getRowsPerPage("/server-properties.jsp", 15), "Stored value: " + stored);
        }
    }

    @Test
    public void valuesCanBeStoredNextToMalformedStoredValues() {
        userProperties.put(PROPERTY, "/server-properties.jsp=25,/server-properties.jsp;x=y=25");

        webManager.setRowsPerPage("/server-properties.jsp", 50);
        webManager.setRowsPerPage("user-summary", 100);

        assertEquals("/server-properties.jsp=50,/server-properties.jsp;x=y=25,user-summary=100", userProperties.get(PROPERTY));
        assertEquals(50, webManager.getRowsPerPage("/server-properties.jsp", 15));
        assertEquals(100, webManager.getRowsPerPage("user-summary", 15));
    }
}
