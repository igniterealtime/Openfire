/*
 * Copyright (C) 2024-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link Bind2StreamManagementHandler}.
 */
public class Bind2StreamManagementHandlerTest {

    private Bind2StreamManagementHandler handler;
    private LocalClientSession mockSession;
    private StreamManager mockStreamManager;
    private Element boundElement;

    @BeforeEach
    public void setUp() {
        handler = new Bind2StreamManagementHandler();
        mockSession = mock(LocalClientSession.class);
        mockStreamManager = mock(StreamManager.class);
        when(mockSession.getStreamManager()).thenReturn(mockStreamManager);
        boundElement = DocumentHelper.createElement(new QName("bound", new Namespace("", "urn:xmpp:bind:0")));
    }

    @Test
    public void testGetNamespace() {
        assertEquals(StreamManager.NAMESPACE_V3, handler.getNamespace());
    }

    @Test
    public void testHandleEnableElementWithoutResume() {
        // Setup
        final Element enableElement = DocumentHelper.createElement(
            new QName("enable", new Namespace("", StreamManager.NAMESPACE_V3)));
        final Element enabledElement = DocumentHelper.createElement(
            new QName("enabled", new Namespace("", StreamManager.NAMESPACE_V3)));
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, false))
            .thenReturn(enabledElement);

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, enableElement);

        // Verify
        assertTrue(result);
        verify(mockStreamManager).enableAndBuildElement(StreamManager.NAMESPACE_V3, false);
        assertEquals(1, boundElement.elements().size());
        assertEquals("enabled", boundElement.elements().get(0).getName());
    }

    @Test
    public void testHandleEnableElementWithResume() {
        // Setup
        final Element enableElement = DocumentHelper.createElement(
            new QName("enable", new Namespace("", StreamManager.NAMESPACE_V3)));
        enableElement.addAttribute("resume", "true");
        final Element enabledElement = DocumentHelper.createElement(
            new QName("enabled", new Namespace("", StreamManager.NAMESPACE_V3)));
        enabledElement.addAttribute("resume", "true");
        enabledElement.addAttribute("id", "someSmId");
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, true))
            .thenReturn(enabledElement);

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, enableElement);

        // Verify
        assertTrue(result);
        verify(mockStreamManager).enableAndBuildElement(StreamManager.NAMESPACE_V3, true);
        assertEquals(1, boundElement.elements().size());
        final Element addedEnabled = (Element) boundElement.elements().get(0);
        assertEquals("enabled", addedEnabled.getName());
        assertEquals("true", addedEnabled.attributeValue("resume"));
    }

    @Test
    public void testHandleEnableElementWithResumeYes() {
        // Setup
        final Element enableElement = DocumentHelper.createElement(
            new QName("enable", new Namespace("", StreamManager.NAMESPACE_V3)));
        enableElement.addAttribute("resume", "yes");
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, true))
            .thenReturn(DocumentHelper.createElement("enabled"));

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, enableElement);

        // Verify
        assertTrue(result);
        verify(mockStreamManager).enableAndBuildElement(StreamManager.NAMESPACE_V3, true);
    }

    @Test
    public void testHandleEnableElementWithResume1() {
        // Setup
        final Element enableElement = DocumentHelper.createElement(
            new QName("enable", new Namespace("", StreamManager.NAMESPACE_V3)));
        enableElement.addAttribute("resume", "1");
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, true))
            .thenReturn(DocumentHelper.createElement("enabled"));

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, enableElement);

        // Verify
        assertTrue(result);
        verify(mockStreamManager).enableAndBuildElement(StreamManager.NAMESPACE_V3, true);
    }

    @Test
    public void testHandleEnableElementWhenEnableFails() {
        // Setup: enableAndBuildElement returns null (e.g. SM already enabled)
        final Element enableElement = DocumentHelper.createElement(
            new QName("enable", new Namespace("", StreamManager.NAMESPACE_V3)));
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, false))
            .thenReturn(null);

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, enableElement);

        // Verify
        assertFalse(result);
        assertTrue(boundElement.elements().isEmpty(), "No element should be added to <bound/> on failure");
    }

    @Test
    public void testHandleNonEnableElementIsIgnored() {
        // Setup: send an unexpected element name
        final Element wrongElement = DocumentHelper.createElement(
            new QName("disable", new Namespace("", StreamManager.NAMESPACE_V3)));

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, wrongElement);

        // Verify
        assertFalse(result);
        verifyNoInteractions(mockStreamManager);
        assertTrue(boundElement.elements().isEmpty());
    }
}
