package org.jivesoftware.openfire.sasl;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.dom4j.QName;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.lockout.LockOutFlag;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.lockout.LockOutProvider;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.UserAgentInfo;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SASLAuthenticationTest {

    @Mock(lenient = true)
    private LocalClientSession clientSession;
    
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

    @BeforeAll
    public static void setUpClass() throws Exception {
        CacheFactory.initialize();
        // Set this or I can't set anythign else.
        JiveGlobals.setXMLProperty("setup", "true");
        SASLAuthentication.setEnabledMechanisms(Arrays.asList("BLURDYBLOOP", "TEST-MECHANISM"));
        // Enable SASL2
        SASLAuthentication.ENABLE_SASL2.setValue(true);
    }
//
//    @AfterAll
//    public static void tearDownClass() {
//        CacheFactory.shutdown();
//    }

    @BeforeEach
    public void setUp() throws Exception {
        long start = System.currentTimeMillis();
        System.out.println("Starting setUp");

        // Setup XMPPServer mock
        XMPPServer.setInstance(xmppServer);
        when(xmppServer.getServerInfo()).thenReturn(serverInfo);
        when(xmppServer.createJID(anyString(), anyString())).thenReturn(new JID("foo@bar"));
        when(xmppServer.createJID(anyString(), isNull())).thenReturn(new JID("foo@bar"));
        when(xmppServer.createJID(anyString(), anyString(), anyBoolean())).thenReturn(new JID("foo@bar"));

        // Setup ServerInfo mock
        when(serverInfo.getXMPPDomain()).thenReturn("example.com");
        when(serverInfo.getHostname()).thenReturn("openfire.example.com");

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

        long end = System.currentTimeMillis();
        System.out.println("Finished setUp in " + (end-start) + "ms");
    }

    @AfterEach
    public void tearDown() {
        // Clear any SASL state
        // SASLAuthentication.setEnabledMechanisms(null);
        // Clear caches
        CacheFactory.clearCaches();
        testSaslServer.reset();
    }

    @Test
    public void testRegisteredSaslProvider() {
        Set<String> implemented = SASLAuthentication.getImplementedMechanisms();
        assertNotNull(implemented);
        assertFalse(implemented.isEmpty());
        assertTrue(implemented.contains("TEST-MECHANISM"));
        Set<String> enabled = SASLAuthentication.getSupportedMechanisms();
        assertNotNull(enabled);
        assertFalse(enabled.isEmpty());
        assertTrue(enabled.contains("TEST-MECHANISM"));
    }

    // Existing tests
    @Test
    public void testAddSupportedMechanism() {
        // Test adding valid mechanism
        SASLAuthentication.addSupportedMechanism("PLAIN");
        assertTrue(SASLAuthentication.getSupportedMechanisms().contains("PLAIN"));

        // Test adding lowercase mechanism (should be converted to uppercase)
        SASLAuthentication.addSupportedMechanism("digest-md5");
        assertTrue(SASLAuthentication.getSupportedMechanisms().contains("DIGEST-MD5"));

        // Test null mechanism
        assertThrows(IllegalArgumentException.class, () -> {
            SASLAuthentication.addSupportedMechanism(null);
        });

        // Test empty mechanism
        assertThrows(IllegalArgumentException.class, () -> {
            SASLAuthentication.addSupportedMechanism("");
        });
    }

    @Test
    public void testRemoveSupportedMechanism() {
        // Add and then remove a mechanism
        SASLAuthentication.addSupportedMechanism("PLAIN");
        SASLAuthentication.removeSupportedMechanism("PLAIN");
        assertFalse(SASLAuthentication.getSupportedMechanisms().contains("PLAIN"));

        // Test case insensitive removal
        SASLAuthentication.addSupportedMechanism("DIGEST-MD5");
        SASLAuthentication.removeSupportedMechanism("digest-md5");
        assertFalse(SASLAuthentication.getSupportedMechanisms().contains("DIGEST-MD5"));

        // Test removing non-existent mechanism
        SASLAuthentication.removeSupportedMechanism("NONEXISTENT");
        // Should not throw exception

        // Test null mechanism
        assertThrows(IllegalArgumentException.class, () -> {
            SASLAuthentication.removeSupportedMechanism(null);
        });
    }

    @Test
    public void testGetSupportedMechanisms() {
        long t0 = System.currentTimeMillis();
        System.out.println("Test starting: " + System.currentTimeMillis());

        Set<String> implemented = SASLAuthentication.getImplementedMechanisms();
        System.out.println("After getImplementedMechanisms: " + (System.currentTimeMillis() - t0));
        Set<String> mechanisms = SASLAuthentication.getSupportedMechanisms();
        System.out.println("After getSupportedMechanisms: " + (System.currentTimeMillis() - t0));
        assertNotNull(mechanisms);
        System.out.println("After assertNotNull: " + (System.currentTimeMillis() - t0));

        // Add multiple mechanisms and verify they're all present
        SASLAuthentication.addSupportedMechanism("PLAIN");
        System.out.println("After add PLAIN: " + (System.currentTimeMillis() - t0));
        SASLAuthentication.addSupportedMechanism("DIGEST-MD5");
        System.out.println("After add DIGEST-MD5: " + (System.currentTimeMillis() - t0));

        mechanisms = SASLAuthentication.getSupportedMechanisms();
        System.out.println("After second getSupportedMechanisms: " + (System.currentTimeMillis() - t0));
        
        assertTrue(mechanisms.contains("PLAIN"));
        assertTrue(mechanisms.contains("DIGEST-MD5"));
        System.out.println("Test ending: " + (System.currentTimeMillis() - t0));
    }

    @Test
    public void testGetEnabledMechanisms() {
        // Test default enabled mechanisms
        List<String> enabled = SASLAuthentication.getEnabledMechanisms();
        assertNotNull(enabled);
        assertFalse(enabled.isEmpty());
        
        // Verify expected default mechanisms are present
        assertTrue(enabled.contains("BLURDYBLOOP"));
        assertTrue(enabled.contains("TEST-MECHANISM"));
    }

    @Test
    public void testGetImplementedMechanisms() {
        // Verify we get some implemented mechanisms
        Set<String> implemented = SASLAuthentication.getImplementedMechanisms();
        assertNotNull(implemented);
        assertFalse(implemented.isEmpty());
        
        // Verify common mechanisms are present
        assertTrue(implemented.contains("PLAIN"));
        assertTrue(implemented.contains("DIGEST-MD5"));
        assertFalse(implemented.contains("BLURDYBLOOP"));
        assertTrue(implemented.contains("TEST-MECHANISM"));
    }

    // New tests for addSASLMechanisms functionality
    @Test
    public void testAddSASLMechanismsToAuthenticatedSession() {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(true);
        
        // Execute
        SASLAuthentication.addSASLMechanisms(features, clientSession);
        
        // Verify
        assertTrue(features.elements().isEmpty(), 
            "No SASL mechanisms should be added for authenticated sessions");
    }

    @Test
    public void testAddSASLMechanismsToClientSession() {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);

        // Disable SASL2
        SASLAuthentication.ENABLE_SASL2.setValue(false);

        try {

            // Execute
            SASLAuthentication.addSASLMechanisms(features, clientSession);

            // Verify
            List<Element> mechanisms = features.elements();
            assertFalse(mechanisms.isEmpty(), "SASL mechanisms should be added");

            // Should have both SASL and SASL2 mechanisms elements
            assertEquals(1, mechanisms.size(), "Should have both SASL and SASL2 mechanisms");

            // Verify both namespaces are present without assuming order
            Set<String> namespaces = mechanisms.stream()
                .map(Element::getNamespaceURI)
                .collect(Collectors.toSet());
            assertTrue(namespaces.contains("urn:ietf:params:xml:ns:xmpp-sasl"),
                "SASL namespace should be present");
            assertFalse(namespaces.contains("urn:xmpp:sasl:2"), "SASL2 namespace should be present");
        } finally {
            // Enable SASL2
            SASLAuthentication.ENABLE_SASL2.setValue(true);

        }
    }

    @Test
    public void testAddSASLMechanismsToClientSessionWithSASL2() {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);

        // Execute
        SASLAuthentication.addSASLMechanisms(features, clientSession);

        // Verify
        List<Element> mechanisms = features.elements();
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
    public void testAddSASLMechanismsToServerSession() {
        // Setup
        when(serverSession.isAuthenticated()).thenReturn(false);
        
        // Execute
        SASLAuthentication.addSASLMechanisms(features, serverSession);

        // Verify
        List<Element> mechanisms = features.elements();
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
    public void testAddSASLMechanismsToList() {
        // Setup
        List<Element> featuresList = new ArrayList<>();
        when(clientSession.isAuthenticated()).thenReturn(false);
        
        // Execute
        SASLAuthentication.addSASLMechanisms(featuresList, clientSession);
        
        // Verify
        assertEquals(2, featuresList.size(),
            "Should add both SASL and SASL2 mechanisms to list");
        
        // Verify both namespaces are present without assuming order
        Set<String> namespaces = featuresList.stream()
            .map(Element::getNamespaceURI)
            .collect(Collectors.toSet());
        assertTrue(namespaces.contains("urn:ietf:params:xml:ns:xmpp-sasl"),
            "SASL namespace should be present");
        assertTrue(namespaces.contains("urn:xmpp:sasl:2"),
            "SASL2 namespace should be present");
    }

    @Test 
    public void testAddSASLMechanismsToUnknownSessionType() {
        // Setup
        LocalSession unknownSession = mock(LocalSession.class);
        when(unknownSession.isAuthenticated()).thenReturn(false);
        
        // Execute
        SASLAuthentication.addSASLMechanisms(features, unknownSession);
        
        // Verify
        assertTrue(features.elements().isEmpty(),
            "Unknown session types should not get any mechanisms");
    }

    @Test
    public void testAuthenticationWithoutInitialResponse() throws Exception {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Execute - First step: Client sends auth without initial response
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify server sends success
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
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        auth.setText(Base64.getEncoder().encodeToString("initial-response".getBytes())); // Empty initial response
        
        // Execute - Client sends auth with initial response
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify server sends success
        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        verify(clientSession).deliverRawText(responseCaptor.capture());

        Element response = DocumentHelper.parseText(responseCaptor.getValue()).getRootElement();
        assertEquals("success", response.getName());
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl", response.getNamespaceURI());
        String additionalData = new String(Base64.getDecoder().decode(response.getText()), StandardCharsets.UTF_8);
        assertEquals("initial-response", additionalData);
        
        // Verify session state
        verify(clientSession).setAuthToken(any(AuthToken.class));
        assertFalse(clientSession.isAnonymousUser());
    }

    @Test
    public void testAuthenticationWithSASL2() throws Exception {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Execute - Client sends auth request
        SASLAuthentication.handle(clientSession, auth, true);
        
        // Verify server sends success with SASL2 format
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
        assertFalse(clientSession.isAnonymousUser());
    }

    @Test
    public void testAuthenticationWithSASL2andIR() throws Exception {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM")
            .addElement("initial-response")
            .addCDATA(Base64.getEncoder().encodeToString("initial-response".getBytes()))
            .getParent();

        // Execute - Client sends auth request
        SASLAuthentication.handle(clientSession, auth, true);

        // Verify server sends success with SASL2 format
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
        assertFalse(clientSession.isAnonymousUser());
    }

    @Test
    public void testAuthenticationWithSASL2andIRMultistep() throws Exception {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        testSaslServer.setSteps(2);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM")
            .addElement("initial-response")
            .addCDATA(Base64.getEncoder().encodeToString("initial-response".getBytes()))
            .getParent();

        // Execute - Client sends auth request
        SASLAuthentication.handle(clientSession, auth, true);

        Element response = DocumentHelper.createElement(QName.get("response", "urn:xmpp:sasl:2"))
            .addCDATA(Base64.getEncoder().encodeToString("subsequent-response".getBytes()));
        SASLAuthentication.handle(clientSession, response, true);

        // Verify server sends challenge with SASL2 format
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
        // Setup
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "INVALID-MECHANISM");
        
        // Execute
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify server sends failure
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
    public void testAuthenticationReplayAttack() throws Exception {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // First authentication
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Reset mock to verify second attempt
        reset(clientSession);

        // Try to authenticate again with same session
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify server sends failure
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
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Execute - First step: Client sends auth without initial response
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify server sends success
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
        // Setup
        Element auth = DocumentHelper.createElement(QName.get("auth", "urn:ietf:params:xml:ns:xmpp-sasl"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        testSaslServer.setThrowError(true);

        // Execute
        SASLAuthentication.handle(clientSession, auth, false);
        
        // Verify server sends failure
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
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Add simple user-agent element
        Element userAgent = auth.addElement("user-agent");
        userAgent.addElement("software").setText("Test Client");

        // Execute authentication
        SASLAuthentication.handle(clientSession, auth, true);  // true for SASL2
        
        // Verify user agent info was stored in session
        assertNotNull(clientSession.getSessionData("user-agent-info"));
}

    @Test
    public void testNoUserAgentWhenElementMissing() throws Exception {
        // Setup
        when(clientSession.isAuthenticated()).thenReturn(false);
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");

        // Execute authentication
        SASLAuthentication.handle(clientSession, auth, true);  // true for SASL2
        
        // Verify no user agent info was stored
        assertNull(clientSession.getSessionData("user-agent-info"));
    }

    @Test
    public void testNoUserAgentForServerSession() throws Exception {
        // Setup
        Element auth = DocumentHelper.createElement(QName.get("authenticate", "urn:xmpp:sasl:2"))
            .addAttribute("mechanism", "TEST-MECHANISM");
        
        // Add user-agent element
        Element userAgent = auth.addElement("user-agent");
        userAgent.addElement("software").setText("Test Server");

        // Execute authentication
        SASLAuthentication.handle(serverSession, auth, true);  // true for SASL2
        
        // Verify no user agent info was stored
        assertNull(serverSession.getSessionData("user-agent-info"));  // Fixed to check serverSession instead of clientSession
    }
}
