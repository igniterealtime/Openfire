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

package org.jivesoftware.openfire.sasl;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.dom4j.QName;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.XMPPServer;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.jivesoftware.util.cache.CacheFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SASLIntegrationTest {

    @Mock(lenient = true)
    private LocalClientSession clientSession;

    @Mock(lenient = true)
    private Connection connection;

    @Mock
    private LocalIncomingServerSession serverSession;

    @Mock(lenient = true)
    private XMPPServer xmppServer;

    @Mock(lenient = true) 
    private XMPPServerInfo serverInfo;

    private Element features;

    private TestSaslMechanism.TestSaslServer testSaslServer;

    // Create a real map to store session data
    private Map<String, Object> sessionDataMap;

    // Store/restore variables.
    private static List<String> originalEnabledMechanisms;
    private static boolean originalSasl2Enabled;
    private static boolean originalSasl2TLSRequired;
    private static LockOutProvider originalLockOutProvider;

    @BeforeAll
    public static void setUpClass() throws Exception {
        CacheFactory.initialize();
        // Set this or I can't set anything else.
        JiveGlobals.setXMLProperty("setup", "true");
        originalEnabledMechanisms = new ArrayList<>(SASLAuthentication.getEnabledMechanisms());
        originalSasl2Enabled = SASLAuthentication.ENABLE_SASL2.getValue();
        originalSasl2TLSRequired = SASLAuthentication.SASL2_REQUIRE_TLS.getValue();
        SASLAuthentication.setEnabledMechanisms(Arrays.asList("BLURDYBLOOP", "TEST-MECHANISM"));
        // Enable SASL2
        SASLAuthentication.ENABLE_SASL2.setValue(true);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(false);
    }

    @AfterAll
    public static void tearDownClass() {
        SASLAuthentication.setEnabledMechanisms(originalEnabledMechanisms);
        SASLAuthentication.ENABLE_SASL2.setValue(originalSasl2Enabled);
        SASLAuthentication.SASL2_REQUIRE_TLS.setValue(originalSasl2TLSRequired);
    }

    @BeforeEach
    public void setUp() throws Exception {
        // Setup XMPPServer mock
        XMPPServer.setInstance(xmppServer);
        when(xmppServer.getServerInfo()).thenReturn(serverInfo);
        when(xmppServer.createJID(anyString(), anyString())).thenReturn(new JID("foo@bar"));
        when(xmppServer.createJID(anyString(), isNull())).thenReturn(new JID("foo@bar"));
        when(xmppServer.createJID(anyString(), anyString(), anyBoolean())).thenReturn(new JID("foo@bar"));

        // Setup ServerInfo mock
        when(serverInfo.getXMPPDomain()).thenReturn("example.com");
        when(serverInfo.getHostname()).thenReturn("openfire.example.com");

        // Setup Connection mock
        when(clientSession.getConnection()).thenReturn(connection);
        when(connection.getSupportedChannelBindingTypes()).thenReturn(Collections.emptySet());

        features = DocumentHelper.createElement("features");
        
        // Create our test SASL server
        testSaslServer = TestSaslMechanism.registerTestMechanism(clientSession);

        // Enable our test mechanism
        SASLAuthentication.addSupportedMechanism("TEST-MECHANISM");


        sessionDataMap = new HashMap<>();
        // Mock both get and set to use the real map
        when(clientSession.getSessionData(anyString())).thenAnswer(inv ->
            sessionDataMap.get(inv.getArgument(0)));

        doAnswer(inv -> {
            sessionDataMap.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(clientSession).setSessionData(anyString(), any());


        // Instead of setting property, directly set the provider through reflection
        try {
            Field providerField = LockOutManager.class.getDeclaredField("provider");
            providerField.setAccessible(true);
            originalLockOutProvider = (LockOutProvider) providerField.get(null);

            // Create anonymous implementation
            LockOutProvider mockProvider = new LockOutProvider() {
                @Override
                public LockOutFlag getDisabledStatus(String username) {
                    return null;
                }
                @Override
                public void setDisabledStatus(LockOutFlag flag) {}
                @Override
                public void unsetDisabledStatus(String username) {}
                @Override
                public boolean isReadOnly() { return false; }
                @Override
                public boolean isDelayedStartSupported() { return false; }
                @Override
                public boolean isTimeoutSupported() { return false; }
                @Override
                public boolean shouldNotBeCached() { return true; }
            };

            providerField.set(null, mockProvider);
        } catch (Exception e) {
            fail("Could not set mock provider: " + e.getMessage());
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Clear any SASL state
        // SASLAuthentication.setEnabledMechanisms(null);
        // Clear caches
        CacheFactory.clearCaches();
        testSaslServer = null;
        TestSaslMechanism.unregisterTestMechanism();
        XMPPServer.setInstance(null);
        Field providerField = LockOutManager.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(null, originalLockOutProvider);
    }

    @Test
    public void testRegisteredSaslProvider() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        Set<String> implemented = SASLAuthentication.getImplementedMechanisms();
        Set<String> enabled = SASLAuthentication.getSupportedMechanisms();

        // Verify result.
        assertNotNull(implemented);
        assertFalse(implemented.isEmpty());
        assertTrue(implemented.contains("TEST-MECHANISM"));
        assertNotNull(enabled);
        assertFalse(enabled.isEmpty());
        assertTrue(enabled.contains("TEST-MECHANISM"));
    }

    // Existing tests
    @Test
    public void testAddSupportedMechanism() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        SASLAuthentication.addSupportedMechanism("PLAIN");
        SASLAuthentication.addSupportedMechanism("digest-md5");

        // Verify result.
        assertTrue(SASLAuthentication.getSupportedMechanisms().contains("PLAIN"));
        assertTrue(SASLAuthentication.getSupportedMechanisms().contains("DIGEST-MD5"));
        assertThrows(IllegalArgumentException.class, () -> {
            SASLAuthentication.addSupportedMechanism(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            SASLAuthentication.addSupportedMechanism("");
        });
    }

    @Test
    public void testRemoveSupportedMechanism() {
        // Setup test fixture.
        SASLAuthentication.addSupportedMechanism("PLAIN");
        SASLAuthentication.addSupportedMechanism("DIGEST-MD5");

        // Execute system under test.
        SASLAuthentication.removeSupportedMechanism("PLAIN");
        SASLAuthentication.removeSupportedMechanism("digest-md5");

        // Verify result.
        assertFalse(SASLAuthentication.getSupportedMechanisms().contains("PLAIN"), "Unsupported PLAIN mechanism should be removed");
        assertFalse(SASLAuthentication.getSupportedMechanisms().contains("DIGEST-MD5"), "Unsupported DIGEST-MD5 mechanism should be removed");
        SASLAuthentication.removeSupportedMechanism("NONEXISTENT"); // Should not throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            SASLAuthentication.removeSupportedMechanism(null);
        }, "Null mechanism should not be allowed");
    }

    @Test
    public void testGetSupportedMechanisms() {
        // Setup test fixture.
        SASLAuthentication.addSupportedMechanism("PLAIN");
        SASLAuthentication.addSupportedMechanism("DIGEST-MD5");

        // Execute system under test.
        Set<String> mechanisms = SASLAuthentication.getSupportedMechanisms();

        // Verify result.
        assertNotNull(mechanisms, "Supported mechanisms should not be null");
        assertTrue(mechanisms.contains("PLAIN"), "PLAIN mechanism should be supported");
        assertTrue(mechanisms.contains("DIGEST-MD5"), "DIGEST-MD5 mechanism should be supported");
    }

    @Test
    public void testGetEnabledMechanisms() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        List<String> enabled = SASLAuthentication.getEnabledMechanisms();

        // Verify result.
        assertNotNull(enabled, "Enabled mechanisms should not be null");
        assertFalse(enabled.isEmpty(), "Enabled mechanisms should not be empty");
        assertTrue(enabled.contains("BLURDYBLOOP"), "BLURDYBLOOP mechanism should be enabled");
        assertTrue(enabled.contains("TEST-MECHANISM"), "TEST-MECHANISM mechanism should be enabled");
    }

    @Test
    public void testGetImplementedMechanisms() {
        // Setup test fixture.
        // (no additional setup required)

        // Execute system under test.
        Set<String> implemented = SASLAuthentication.getImplementedMechanisms();

        // Verify result.
        assertNotNull(implemented, "Implemented mechanisms should not be null");
        assertFalse(implemented.isEmpty(), "Implemented mechanisms should not be empty");
        assertTrue(implemented.contains("PLAIN"), "PLAIN mechanism should be implemented");
        assertTrue(implemented.contains("DIGEST-MD5"), "DIGEST-MD5 mechanism should be implemented");
        assertFalse(implemented.contains("BLURDYBLOOP"), "BLURDYBLOOP mechanism should not be implemented");
        assertTrue(implemented.contains("TEST-MECHANISM"), "TEST-MECHANISM mechanism should be implemented");
    }

    // New tests for addSASLMechanisms functionality
    @Test
    public void testGetSASLMechanismsToAuthenticatedSession() {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(true);
        
        // Execute system under test.
        final List<Element> mechanisms = SASLAuthentication.getSASLMechanisms(clientSession);

        // Verify result.
        assertTrue(mechanisms.isEmpty(),
            "No SASL mechanisms should be added for authenticated sessions");
    }

    @Test
    public void testGetSASLMechanismsToClientSession() {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);

        // Disable SASL2
        SASLAuthentication.ENABLE_SASL2.setValue(false);

        try {

            // Execute system under test.
            final List<Element> mechanisms = SASLAuthentication.getSASLMechanisms(clientSession);

            // Verify result.
            assertFalse(mechanisms.isEmpty(), "SASL mechanisms should be added");

            // Should have exactly one mechanism element (SASL1 only, no SASL2)
            assertEquals(1, mechanisms.size(), "Should have exactly one SASL1 mechanism element when SASL2 is disabled");

            // Verify SASL namespace is present and SASL2 is absent
            Set<String> namespaces = mechanisms.stream()
                .map(Element::getNamespaceURI)
                .collect(Collectors.toSet());
            assertTrue(namespaces.contains("urn:ietf:params:xml:ns:xmpp-sasl"),
                "SASL namespace should be present");
            assertFalse(namespaces.contains("urn:xmpp:sasl:2"), "SASL2 namespace should not be present when SASL2 is disabled");
        } finally {
            // Enable SASL2
            SASLAuthentication.ENABLE_SASL2.setValue(true);

        }
    }

    @Test
    public void testGetSASLMechanismsToClientSessionWithSASL2() {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);

        // Execute system under test.
        final List<Element> mechanisms = SASLAuthentication.getSASLMechanisms(clientSession);

        // Verify result.
        assertFalse(mechanisms.isEmpty(), "SASL mechanisms should be added");

        // Should have both SASL and SASL2 mechanisms elements
        assertEquals(2, mechanisms.size(), "Should have both SASL and SASL2 mechanisms");

        // Verify both namespaces are present without assuming order
        Set<String> namespaces = mechanisms.stream()
            .map(Element::getNamespaceURI)
            .collect(Collectors.toSet());
        assertTrue(namespaces.contains("urn:ietf:params:xml:ns:xmpp-sasl"),
            "SASL namespace should be present");
        assertTrue(namespaces.contains("urn:xmpp:sasl:2"),
            "SASL2 namespace should be present");
    }

    @Test 
    public void testGetSASLMechanismsToUnknownSessionType() {
        // Setup test fixture.
        LocalSession unknownSession = mock(LocalSession.class);
        when(unknownSession.isAuthenticated()).thenReturn(false);
        
        // Execute system under test.
        final List<Element> mechanisms = SASLAuthentication.getSASLMechanisms(unknownSession);
        
        // Verify result.
        assertTrue(mechanisms.isEmpty(),
            "Unknown session types should not get any mechanisms");
    }

    @Test
    public void testAuthenticationWithoutInitialResponse() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        String xml = responseCaptor.getValue();
        Element response = DocumentHelper.parseText(xml).getRootElement();
        assertNull(clientSession.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY));
        assertEquals("success", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        assertEquals("", response.getText()); // We gave no IR, so no success-data reflected.

        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
        assertFalse(clientSession.isAnonymousUser());
    }

    @Test
    public void testAuthenticationWithInitialResponse() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        auth.setText(Base64.getEncoder().encodeToString("initial-response".getBytes())); // Non-empty initial response
        
        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("success", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        String additionalData = new String(Base64.getDecoder().decode(response.getText()), StandardCharsets.UTF_8);
        assertEquals("initial-response", additionalData);
        
        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
        assertFalse(clientSession.isAnonymousUser(), "Session should not be anonymous");
    }

    @Test
    public void testAuthenticationWithSASL2() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("success", response.getName());
        assertEquals("urn:xmpp:sasl:2", response.getNamespaceURI());

        Element additionalData = response.element("additional-data");
        assertNull(additionalData, "SASL2 success must not include additional-data");

        // Verify authorization-identifier is present
        Element authId = response.element("authorization-identifier");
        assertNotNull(authId, "SASL2 success must include authorization-identifier");
        assertTrue(authId.getText().contains("@"), "Authorization ID should be a full JID");
        
        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
        assertFalse(clientSession.isAnonymousUser(), "Session should not be anonymous");
    }

    @Test
    public void testAuthenticationWithSASL2andIR() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM")
            .addElement("initial-response")
            .addCDATA(Base64.getEncoder().encodeToString("initial-response".getBytes()))
            .getParent();

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);

        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("success", response.getName());
        assertEquals("urn:xmpp:sasl:2", response.getNamespaceURI());

        Element additionalData = response.element("additional-data");
        assertNotNull(additionalData, "SASL2 success must include additional-data");
        String responseEnc = additionalData.getText();
        byte[] resp = Base64.getDecoder().decode(responseEnc.getBytes(StandardCharsets.UTF_8));
        String additionalDataString = new String(resp, StandardCharsets.UTF_8);
        assertEquals("initial-response", additionalDataString);

        // Verify authorization-identifier is present
        Element authId = response.element("authorization-identifier");
        assertNotNull(authId, "SASL2 success must include authorization-identifier");
        assertTrue(authId.getText().contains("@"), "Authorization ID should be a full JID");

        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
        assertFalse(clientSession.isAnonymousUser(), "Session should not be anonymous");
    }

    @Test
    public void testAuthenticationWithSASL2andIRMultistep() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setSteps(2);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM")
            .addElement("initial-response")
            .addCDATA(Base64.getEncoder().encodeToString("initial-response".getBytes()))
            .getParent();

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);

        Element response = DocumentHelper.createElement(QName.get("response", "urn:xmpp:sasl:2"))
            .addCDATA(Base64.getEncoder().encodeToString("subsequent-response".getBytes()));
        SASLAuthentication.handle(clientSession, response, true);

        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession, times(2)).deliverRawText(responseCaptor.capture());

        List<String> responses = responseCaptor.getAllValues();

        {
            Element challenge = DocumentHelper.parseText(responses.get(0)).getRootElement();
            assertEquals("challenge", challenge.getName());
            assertEquals("urn:xmpp:sasl:2", challenge.getNamespaceURI());
            String responseEnc = challenge.getText();
            byte[] resp = Base64.getDecoder().decode(responseEnc.getBytes(StandardCharsets.UTF_8));
            String additionalDataString = new String(resp, StandardCharsets.UTF_8);
            assertEquals("initial-response", additionalDataString);
        }

        {
            Element success = DocumentHelper.parseText(responses.get(1)).getRootElement();
            assertEquals("success", success.getName());
            assertEquals("urn:xmpp:sasl:2", success.getNamespaceURI());

            Element additionalData = success.element("additional-data");
            assertNotNull(additionalData, "SASL2 success must include additional-data");
            String responseEnc2 = additionalData.getText();
            byte[] resp2 = Base64.getDecoder().decode(responseEnc2.getBytes(StandardCharsets.UTF_8));
            String additionalDataString2 = new String(resp2, StandardCharsets.UTF_8);
            assertEquals("subsequent-response", additionalDataString2);

            // Verify authorization-identifier is present
            Element authId = success.element("authorization-identifier");
            assertNotNull(authId, "SASL2 success must include authorization-identifier");
            assertTrue(authId.getText().contains("@"), "Authorization ID should be a full JID");
        }
        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
        assertFalse(clientSession.isAnonymousUser());
    }

    @Test
    public void testAuthenticationFailureInvalidMechanism() throws Exception {
        // Setup test fixture.
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "INVALID-MECHANISM");
        
        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        assertNotNull(response.element("invalid-mechanism"), 
            "Should indicate invalid-mechanism as failure reason");
        
        // Verify session state
        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
        verify(clientSession, never()).setAuthToken(null);
    }

    @Test
    public void testSecondAuthAttemptFails() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        SASLAuthentication.handle(clientSession, auth, false);
        clearInvocations(clientSession);

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        String xml = responseCaptor.getValue();
        Element response = DocumentHelper.parseText(xml).getRootElement();
        assertEquals("failure", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        assertNotNull(response.element("not-authorized"), 
            "Should indicate not-authorized as failure reason");
    }

    @Test
    public void testSuccessfulAuthentication() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        
        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("success", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        
        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
    }

    @Test
    public void testFailedAuthentication() throws Exception {
        // Setup test fixture.
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        testSaslServer.setThrowError(true);

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        
        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        
        // Verify session state
        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    @Test
    public void testUserAgentCapturedForClientSession() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        Element userAgent = auth.addElement("user-agent");
        userAgent.addElement("software").setText("Test Client");

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);  // true for SASL2
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"success".equals(response.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not succeed, this test assertion does not prove anything."); };
        assertNotNull(clientSession.getSessionData("user-agent-info"));
}

    @Test
    public void testNoUserAgentWhenElementMissing() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);  // true for SASL2
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"success".equals(response.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not succeed, this test assertion does not prove anything."); };
        assertNull(clientSession.getSessionData("user-agent-info"));
    }

    //@Test // Disabled test, as, without a successful authentication, doesn't assert anything meaningful.
    public void testNoUserAgentForServerSession() throws Exception {
        // Setup test fixture.
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        Element userAgent = auth.addElement("user-agent");
        userAgent.addElement("software").setText("Test Server");

        // Execute system under test.
        SASLAuthentication.handle(serverSession, auth, true);  // true for SASL2
        
        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(serverSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"success".equals(response.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not succeed, this test assertion does not prove anything."); };
        assertNull(serverSession.getSessionData("user-agent-info"));
    }

    @Test
    public void testSasl2DomainQualifiedAuthzidIsNormalized() throws Exception {
        // Setup test fixture: SASL yields an authzid that already carries a domain-part.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setAuthorizationID("test-user@example.com");

        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);

        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"success".equals(response.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not succeed, this test assertion does not prove anything."); }

        final String value = response.element("authorization-identifier").getText();
        assertEquals(1, value.chars().filter(c -> c == '@').count(),
            "A domain-qualified authzid must not yield a double-'@' authorization-identifier.");
        assertEquals("test-user@example.com", value);
    }

    @Test
    public void testSasl2AnonymousAuthorizationIdentifierIsBareJid() throws Exception {
        // Setup test fixture.
        when(clientSession.isAuthenticated()).thenReturn(false);
        when(clientSession.getAnonymousUsername()).thenReturn("randomresource");
        testSaslServer.setAnonymous(true);

        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);

        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"success".equals(response.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not succeed, this test assertion does not prove anything."); }

        Element authId = response.element("authorization-identifier");
        assertNotNull(authId, "SASL2 success must include an authorization-identifier.");

        final String value = authId.getText();
        assertFalse(value.startsWith("null@"), "Anonymous authorization-identifier must not be derived from the (null) SASL authzid.");
        assertEquals("randomresource@example.com", value, "Anonymous authorization-identifier must be a bare JID built from the session's anonymous username.");
    }
}
