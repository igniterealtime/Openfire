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
import org.jivesoftware.openfire.streammanagement.StreamManagementException;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.PacketError;

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

    /**
     * Verifies that a value which is not a lexical representation of xs:boolean does not request resumption.
     *
     * XEP-0198 § 3 note 5 admits only "true"/"1" and "false"/"0". Treating anything else as an affirmative would let a
     * client believe it had a resumable stream when a conforming server would not have given it one.
     */
    @Test
    public void testResumeIsNotRequestedByANonBooleanValue() {
        final Element enable = DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3));
        enable.addAttribute("resume", "foobar");
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, false))
            .thenReturn(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));

        handler.handleElement(mockSession, boundElement, enable);

        verify(mockStreamManager).enableAndBuildElement(StreamManager.NAMESPACE_V3, false);
    }

    @Test
    public void testHandleEnableElementWithResume1() {
        // Setup
        final Element enableElement = DocumentHelper.createElement(
            new QName("enable", new Namespace("", StreamManager.NAMESPACE_V3)));
        enableElement.addAttribute("resume", "1");
        when(mockStreamManager.enableAndBuildElement(StreamManager.NAMESPACE_V3, true))
            .thenReturn(DocumentHelper.createElement(QName.get("enabled", StreamManager.NAMESPACE_V3)));

        // Execute
        final boolean result = handler.handleElement(mockSession, boundElement, enableElement);

        // Verify
        assertTrue(result);
        verify(mockStreamManager).enableAndBuildElement(StreamManager.NAMESPACE_V3, true);
    }

    /**
     * Verifies that a failure to enable stream management propagates with the condition that XEP-0198 requires, and is
     * not delivered to the peer as a standalone stanza.
     *
     * The enclosing Bind2 request catches the failure and asks the handler to embed it in {@code <bound/>}, as
     * XEP-0198 § 9.1.1 requires. Sending it separately would put a {@code <failed/>} on the stream before the
     * {@code <success/>} that should contain it.
     */
    @Test
    public void testEnableFailurePropagatesWithoutStandaloneDelivery() {
        // Setup test fixture: a session that has not authenticated.
        final StreamManager streamManager = new StreamManager(mockSession);
        when(mockSession.getStreamManager()).thenReturn(streamManager);
        when(mockSession.isAuthenticated()).thenReturn(false);
        final Element enable = DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3));

        // Execute system under test.
        final StreamManagementException e = assertThrows(StreamManagementException.class,
            () -> handler.handleElement(mockSession, boundElement, enable));

        // Verify result.
        assertEquals(PacketError.Condition.unexpected_request, e.getCondition(),
            "An enable request before authentication must report unexpected-request.");
        assertTrue(boundElement.elements().isEmpty(), "Nothing may be added to <bound/> when enabling failed.");
        verify(mockSession, never()).deliverRawText(anyString());
    }

    /**
     * Verifies that the condition carried by a failure reaches the {@code <failed/>} element, rather than being
     * replaced by a generic one.
     */
    @Test
    public void testFailureConditionIsPreservedInTheBoundElement() {
        // Setup test fixture.
        final StreamManagementException cause = new StreamManagementException(
            PacketError.Condition.unexpected_request, "already enabled");

        // Execute system under test.
        handler.handleFailure(mockSession, boundElement, DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3)), cause);

        // Verify result.
        final Element failed = boundElement.element(QName.get("failed", StreamManager.NAMESPACE_V3));
        assertNotNull(failed);
        assertNotNull(failed.element(QName.get("unexpected-request", "urn:ietf:params:xml:ns:xmpp-stanzas")),
            "The condition that caused the failure must be reported, not a generic one.");
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

    @Test
    public void testMalformedRequestProducesBadRequestFailure() {
        final Element wrongElement = DocumentHelper.createElement(QName.get("disable", StreamManager.NAMESPACE_V3));

        handler.handleFailure(mockSession, boundElement, wrongElement, null);

        final Element failed = boundElement.element(QName.get("failed", StreamManager.NAMESPACE_V3));
        assertNotNull(failed);
        assertNotNull(failed.element(QName.get("bad-request", "urn:ietf:params:xml:ns:xmpp-stanzas")));
    }

    @Test
    public void testProcessingExceptionProducesInternalServerErrorFailure() {
        final Element enable = DocumentHelper.createElement(QName.get("enable", StreamManager.NAMESPACE_V3));

        handler.handleFailure(mockSession, boundElement, enable, new IllegalStateException("test failure"));

        final Element failed = boundElement.element(QName.get("failed", StreamManager.NAMESPACE_V3));
        assertNotNull(failed);
        assertNotNull(failed.element(QName.get("internal-server-error", "urn:ietf:params:xml:ns:xmpp-stanzas")));
    }
}
