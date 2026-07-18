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
import org.dom4j.Namespace;
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
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.XMPPServer;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.jivesoftware.util.cache.CacheFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
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

        doAnswer(inv -> {
            sessionDataMap.remove(inv.getArgument(0));
            return null;
        }).when(clientSession).removeSessionData(anyString());

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
    public void testAuthenticationWithSASL2AndBind2IncludesResource() throws Exception {
        // Setup a SessionManager mock that returns BOUND from bindResource.
        SessionManager sessionManager = mock(SessionManager.class);
        when(xmppServer.getSessionManager()).thenReturn(sessionManager);
        when(sessionManager.bindResource(any(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(SessionManager.BindResult.BOUND));

        // Mock connection so getAvailableMechanismsForClientSession assertion passes.
        Connection connection = mock(Connection.class);
        when(clientSession.getConnection()).thenReturn(connection);

        when(clientSession.isAuthenticated()).thenReturn(false);
        when(clientSession.getStatus()).thenReturn(org.jivesoftware.openfire.session.Session.Status.CONNECTED);
        when(clientSession.getAuthToken()).thenReturn(AuthToken.generateUserToken("testuser"));

        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Add bind2 element
        Element bind = auth.addElement(new QName("bind", new Namespace("", "urn:xmpp:bind:0")));
        bind.addElement("tag").setText("MyClient");

        // Mock getAvailableStreamFeatures so the async features delivery doesn't NPE.
        when(clientSession.getAvailableStreamFeatures()).thenReturn(Collections.emptyList());

        // Execute - Client sends auth request
        SASLAuthentication.handle(clientSession, auth, true);

        // The response is delivered asynchronously inside the whenComplete callback.
        // Since we used completedFuture, the callback runs synchronously on the calling thread.
        // Expect 2 deliverRawText calls: first <success/>, then <stream:features/>.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession, times(2)).deliverRawText(responseCaptor.capture());

        List<String> deliveredStrings = responseCaptor.getAllValues();

        // First call must be <success/>
        String responseString = deliveredStrings.get(0);
        Element response = DocumentHelper.parseText(responseString).getRootElement();
        assertEquals("success", response.getName());
        assertEquals("urn:xmpp:sasl:2", response.getNamespaceURI());

        // Verify authorization-identifier is present and includes a resource
        Element authId = response.element("authorization-identifier");
        assertNotNull(authId, "SASL2 success must include authorization-identifier");
        String jid = authId.getText();
        assertTrue(jid.contains("@"), "Authorization ID should be a JID");
        assertTrue(jid.contains("/"), "Authorization ID should include a resource part");
        assertTrue(jid.contains("MyClient"), "Resource should include client tag");

        // Verify <bound/> is present (XEP-0386)
        Element bound = response.element("bound");
        assertNotNull(bound, "SASL2 success must include bound element: " + responseString);

        // Second call must be <stream:features/>
        String featuresString = deliveredStrings.get(1);
        Element features = DocumentHelper.parseText(featuresString).getRootElement();
        assertEquals("features", features.getName());
        assertEquals("http://etherx.jabber.org/streams", features.getNamespaceURI());

        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
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

    /**
     * Verifies the SASL2 failure format: the {@code <failure/>} wrapper element is in the SASL2 namespace, while the
     * condition child element remains in the (RFC 6120) SASL namespace.
     *
     * @see <a href="https://xmpp.org/extensions/xep-0388.html">XEP-0388: Extensible SASL Profile</a>
     */
    @Test
    public void testSasl2FailureUsesSasl2WrapperWithSasl1Condition() throws Exception {
        // Setup test fixture: an authentication attempt that is guaranteed to fail.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setThrowError(true);

        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, true);

        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"failure".equals(failure.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not fail, this test assertion does not prove anything."); }

        // The wrapper element must be in the SASL2 namespace.
        assertEquals("urn:xmpp:sasl:2", failure.getNamespaceURI(),
            "The SASL2 failure wrapper element must be in the SASL2 namespace.");

        // The condition child must be in the original (RFC 6120) SASL namespace, not the SASL2 namespace.
        assertEquals(1, failure.elements().size(), "Expected exactly one condition child element in the failure.");
        Element condition = failure.elements().get(0);
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", condition.getNamespaceURI(),
            "The failure condition element must remain in the RFC 6120 SASL namespace, not the SASL2 namespace.");
    }

    /**
     * Verifies that a SASL1 failure is entirely in the (RFC 6120) SASL namespace: both the wrapper and the condition.
     * This is the counterpart to {@link #testSasl2FailureUsesSasl2WrapperWithSasl1Condition}, guarding against a
     * regression that would apply the SASL2 namespace split to SASL1 as well.
     */
    @Test
    public void testSasl1FailureUsesSasl1NamespaceThroughout() throws Exception {
        // Setup test fixture: an authentication attempt that is guaranteed to fail.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setThrowError(true);

        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        SASLAuthentication.handle(clientSession, auth, false);

        // Verify result.
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        if (!"failure".equals(failure.getName())) { throw new IllegalStateException("Test setup issue: if authentication does not fail, this test assertion does not prove anything."); }

        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", failure.getNamespaceURI(),
            "The SASL1 failure wrapper element must be in the SASL namespace.");

        assertEquals(1, failure.elements().size(), "Expected exactly one condition child element in the failure.");
        Element condition = failure.elements().get(0);
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", condition.getNamespaceURI(),
            "The SASL1 failure condition element must be in the SASL namespace.");
    }

    /**
     * Verifies that a SASL2 <abort/> mid-negotiation yields an 'aborted' failure in the correct namespaces.
     */
    @Test
    public void testSasl2AbortYieldsAbortedFailure() throws Exception {
        // Setup test fixture: start a multi-step negotiation, so that an abort interrupts something.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setSteps(2);

        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM")
            .addElement("initial-response")
            .addCDATA(Base64.getEncoder().encodeToString("initial-response".getBytes()))
            .getParent();
        SASLAuthentication.handle(clientSession, auth, true); // Yields a challenge; negotiation is now in progress.
        clearInvocations(clientSession);

        // Execute system under test.
        Element abort = DocumentHelper.createElement(QName.get("abort", "urn:xmpp:sasl:2"));
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, abort, true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "An abort must fail the SASL negotiation.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());
        assertEquals("urn:xmpp:sasl:2", failure.getNamespaceURI(), "The SASL2 failure wrapper must be in the SASL2 namespace.");

        assertEquals(1, failure.elements().size(), "Expected exactly one condition child element.");
        Element condition = failure.elements().get(0);
        assertEquals("aborted", condition.getName(), "An abort must yield the 'aborted' failure condition.");
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", condition.getNamespaceURI(), "The condition must remain in the RFC 6120 SASL namespace.");

        // The aborted negotiation must not have authenticated the session.
        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that a SASL1 <abort/> mid-negotiation yields an 'aborted' failure, entirely in the SASL namespace.
     */
    @Test
    public void testSasl1AbortYieldsAbortedFailure() throws Exception {
        // Setup test fixture: start a multi-step negotiation, so that an abort interrupts something.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setSteps(2);

        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        auth.setText(Base64.getEncoder().encodeToString("initial-response".getBytes()));
        SASLAuthentication.handle(clientSession, auth, false); // Yields a challenge; negotiation is now in progress.
        clearInvocations(clientSession);

        // Execute system under test.
        Element abort = DocumentHelper.createElement(QName.get("abort", "urn:ietf:params:xml:ns:xmpp-sasl"));
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, abort, false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "An abort must fail the SASL negotiation.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", failure.getNamespaceURI());

        assertEquals(1, failure.elements().size(), "Expected exactly one condition child element.");
        Element condition = failure.elements().get(0);
        assertEquals("aborted", condition.getName(), "An abort must yield the 'aborted' failure condition.");
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", condition.getNamespaceURI());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that the SaslServer instance is removed from the session when a negotiation is aborted, so that a
     * subsequent <response/> cannot continue the aborted negotiation.
     */
    @Test
    public void testAbortRemovesSaslServerFromSession() throws Exception {
        // Setup test fixture: start a multi-step negotiation, then abort it.
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setSteps(2);

        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM")
            .addElement("initial-response")
            .addCDATA(Base64.getEncoder().encodeToString("initial-response".getBytes()))
            .getParent();
        SASLAuthentication.handle(clientSession, auth, true);
        assertNotNull(clientSession.getSessionData("SaslServer"), "Test setup issue: expected an in-progress negotiation.");

        // Execute system under test.
        Element abort = DocumentHelper.createElement(QName.get("abort", "urn:xmpp:sasl:2"));
        SASLAuthentication.handle(clientSession, abort, true);

        // Verify result.
        assertNull(clientSession.getSessionData("SaslServer"), "An aborted negotiation must not leave a SaslServer on the session.");
    }

    /**
     * Verifies that an element in the wrong namespace for the SASL profile in use is rejected: a SASL2-namespaced
     * element must not be processed as SASL1.
     */
    @Test
    public void testSasl1RejectsElementInSasl2Namespace() throws Exception {
        // Setup test fixture: a SASL2-namespaced element, processed on the SASL1 path.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, auth, false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "An element in the wrong namespace must not be processed.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that an element in the wrong namespace for the SASL profile in use is rejected: a SASL1-namespaced
     * element must not be processed as SASL2.
     */
    @Test
    public void testSasl2RejectsElementInSasl1Namespace() throws Exception {
        // Setup test fixture: a SASL1-namespaced element, processed on the SASL2 path.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, auth, true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "An element in the wrong namespace must not be processed.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that the SASL2-only <authenticate/> element is rejected when processed on the SASL1 path, even when it
     * carries the SASL1 namespace (which would otherwise pass the namespace check).
     */
    @Test
    public void testSasl1RejectsAuthenticateElement() throws Exception {
        // Setup test fixture: <authenticate/> (a SASL2 element) in the SASL1 namespace, processed as SASL1.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element authenticate = DocumentHelper.createElement(QName.get("authenticate", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, authenticate, false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "The SASL2 'authenticate' element must not be processed as SASL1.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that the SASL1-only <auth/> element is rejected when processed on the SASL2 path, even when it carries
     * the SASL2 namespace (which would otherwise pass the namespace check).
     */
    @Test
    public void testSasl2RejectsAuthElement() throws Exception {
        // Setup test fixture: <auth/> (a SASL1 element) in the SASL2 namespace, processed as SASL2.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, auth, true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "The SASL1 'auth' element must not be processed as SASL2.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that a <response/> received without a preceding <authenticate/> is rejected: there is no SaslServer on
     * the session to process it against.
     */
    @Test
    public void testSasl2ResponseWithoutPrecedingAuthenticateIsRejected() throws Exception {
        // Setup test fixture: a bare <response/>, with no negotiation in progress.
        when(clientSession.isAuthenticated()).thenReturn(false);
        assertNull(clientSession.getSessionData("SaslServer"), "Test setup issue: expected no negotiation to be in progress.");

        Element response = DocumentHelper.createElement(QName.get("response", "urn:xmpp:sasl:2"))
            .addCDATA(Base64.getEncoder().encodeToString("unsolicited".getBytes()));

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, response, true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "A response without a preceding authenticate must fail the negotiation.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName(), "An unsolicited response must yield a failure, not an escaping exception.");
        assertEquals("urn:xmpp:sasl:2", failure.getNamespaceURI());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that a <response/> received without a preceding <auth/> is rejected on the SASL1 path as well.
     */
    @Test
    public void testSasl1ResponseWithoutPrecedingAuthIsRejected() throws Exception {
        // Setup test fixture: a bare <response/>, with no negotiation in progress.
        when(clientSession.isAuthenticated()).thenReturn(false);
        assertNull(clientSession.getSessionData("SaslServer"), "Test setup issue: expected no negotiation to be in progress.");

        Element response = DocumentHelper.createElement(QName.get("response", "urn:ietf:params:xml:ns:xmpp-sasl"));
        response.setText(Base64.getEncoder().encodeToString("unsolicited".getBytes()));

        // Execute system under test.
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, response, false);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "A response without a preceding auth must fail the negotiation.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName(), "An unsolicited response must yield a failure, not an escaping exception.");
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", failure.getNamespaceURI());

        verify(clientSession, never()).setAuthToken(any(AuthToken.class));
    }

    /**
     * Verifies that a <response/> received after a negotiation has already completed successfully is rejected: the
     * SaslServer is removed from the session on success, so there is nothing left to continue.
     */
    @Test
    public void testSasl2ResponseAfterCompletedNegotiationIsRejected() throws Exception {
        // Setup test fixture: complete a single-step negotiation.
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        SASLAuthentication.handle(clientSession, auth, true);
        assertNull(clientSession.getSessionData("SaslServer"), "Test setup issue: a completed negotiation should not leave a SaslServer behind.");
        clearInvocations(clientSession);

        // Execute system under test.
        Element response = DocumentHelper.createElement(QName.get("response", "urn:xmpp:sasl:2"))
            .addCDATA(Base64.getEncoder().encodeToString("late".getBytes()));
        final SASLAuthentication.Status status = SASLAuthentication.handle(clientSession, response, true);

        // Verify result.
        assertEquals(SASLAuthentication.Status.failed, status, "A response after a completed negotiation must fail.");

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());
        Element failure = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("failure", failure.getName());
    }
}
