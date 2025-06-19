package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.net.SASLAuthentication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SASLAuthenticationTest {

    @BeforeEach
    public void setUp() {
        // Reset any static state between tests
        // Initialize test environment
    }

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
        assertEquals(newMechs.size(), enabled.size());
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
}
