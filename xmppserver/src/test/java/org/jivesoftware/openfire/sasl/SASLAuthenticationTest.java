package org.jivesoftware.openfire.sasl;

import org.dom4j.Element;
import org.dom4j.DocumentHelper;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SASLAuthenticationTest {

    @Mock
    private LocalClientSession clientSession;
    
    @Mock
    private LocalIncomingServerSession serverSession;

    private Element features;

    @BeforeEach
    public void setUp() {
        features = DocumentHelper.createElement("features");
        // Reset any static state between tests and ensure we have test mechanisms
        SASLAuthentication.setEnabledMechanisms(List.of("PLAIN", "DIGEST-MD5"));
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
        // Test initial state
        assertNotNull(SASLAuthentication.getSupportedMechanisms());

        // Add multiple mechanisms and verify they're all present
        SASLAuthentication.addSupportedMechanism("PLAIN");
        SASLAuthentication.addSupportedMechanism("DIGEST-MD5");
        
        Set<String> mechanisms = SASLAuthentication.getSupportedMechanisms();
        assertTrue(mechanisms.contains("PLAIN"));
        assertTrue(mechanisms.contains("DIGEST-MD5"));
    }

    @Test
    public void testGetEnabledMechanisms() {
        // Test default enabled mechanisms
        List<String> enabled = SASLAuthentication.getEnabledMechanisms();
        assertNotNull(enabled);
        assertFalse(enabled.isEmpty());
        
        // Verify expected default mechanisms are present
        assertTrue(enabled.contains("PLAIN"));
        assertTrue(enabled.contains("DIGEST-MD5"));
    }

    @Test
    public void testSetEnabledMechanisms() {
        // Test setting new list of mechanisms
        List<String> newMechs = Arrays.asList("PLAIN", "EXTERNAL");
        SASLAuthentication.setEnabledMechanisms(newMechs);
        
        List<String> enabled = SASLAuthentication.getEnabledMechanisms();
        // assertEquals(newMechs.size(), enabled.size());
        assertTrue(enabled.containsAll(newMechs));

        // Test setting null (should reset to defaults)
        SASLAuthentication.setEnabledMechanisms(null);
        assertNotNull(SASLAuthentication.getEnabledMechanisms());
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
        assertEquals(1, mechanisms.size(), 
            "Server sessions should only get one mechanisms element");
        
        Element mechsElement = mechanisms.get(0);
        assertEquals("urn:ietf:params:xml:ns:xmpp-sasl",
            mechsElement.getNamespaceURI(),
            "Server mechanisms should use SASL namespace");
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
}
