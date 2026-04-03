/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class XMLPropertiesTest {

    @Test
    public void testAttributes() throws Exception {
        String xml = "<root><foo></foo></root>";
        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes()));
        assertNull(props.getAttribute("foo","bar"));
        xml = "<root><foo bar=\"test123\"></foo></root>";
        props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes()));
        assertEquals(props.getAttribute("foo","bar"), "test123");
    }

    @Test
    public void testGetProperty() throws Exception {
        XMLProperties props = XMLProperties.getNonPersistedInstance(Objects.requireNonNull(getClass().getResourceAsStream("XMLProperties.test01.xml")));
        assertEquals("123", props.getProperty("foo.bar"));
        assertEquals("456", props.getProperty("foo.bar.baz"));
        assertNull(props.getProperty("foo"));
        assertNull(props.getProperty("nothing.something"));
    }

    @Test
    public void testGetChildPropertiesIterator() throws Exception {
        XMLProperties props = XMLProperties.getNonPersistedInstance(Objects.requireNonNull(getClass().getResourceAsStream("XMLProperties.test02.xml")));
        String[] names = {"a","b","c","d"};
        String[] values = {"1","2","3","4"};
        String[] children = props.getChildrenProperties("foo.bar");
        for (int i=0; i<children.length; i++) {
            String prop = children[i];
            assertEquals(names[i], prop);
            String value = props.getProperty("foo.bar." + prop);
            assertEquals(values[i], value);
            i++;
        }
    }

    @Test
    public void testGetPropertyWithXMLEntity() throws Exception {
        String xml = "<root><foo>foo&amp;bar</foo></root>";
        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes()));
        assertEquals("foo&bar", props.getProperty("foo"));
    }

    /**
     * Tests that encrypted properties with IV attribute can be loaded and have correct attributes.
     * Verifies the new format: &lt;property encrypted="true" iv="base64IV"&gt;ciphertext&lt;/property&gt;
     */
    @Test
    public void testEncryptedPropertyWithIVAttributeStructure() throws Exception {
        // Create XML with encrypted attribute and valid IV attribute (new format)
        String xml = "<root>"
                + "<encrypted>"
                + "<secret encrypted=\"true\" iv=\"MTIzNDU2Nzg5MDEyMzQ1Ng==\">encryptedValue</secret>"
                + "</encrypted>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Verify IV attribute is present
        String ivAttribute = props.getAttribute("encrypted.secret", "iv");
        assertNotNull(ivAttribute, "IV attribute should be present in new format");
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Ng==", ivAttribute);

        // Verify encrypted attribute is present
        String encryptedAttribute = props.getAttribute("encrypted.secret", "encrypted");
        assertEquals("true", encryptedAttribute);

        // Verify the IV can be decoded from Base64 (proving it's valid Base64)
        byte[] iv = Base64.getDecoder().decode(ivAttribute);
        assertEquals(16, iv.length, "Decoded IV should be 16 bytes");
    }

    /**
     * Tests that legacy encrypted properties without IV attribute are still readable.
     * Ensures backward compatibility: &lt;property encrypted="true"&gt;ciphertext&lt;/property&gt;
     */
    @Test
    public void testLegacyEncryptedPropertyWithoutIV() throws Exception {
        // Create XML with encrypted attribute but NO IV attribute (legacy format)
        String xml = "<root>"
                + "<encrypted>"
                + "<secret encrypted=\"true\">legacyEncryptedValue</secret>"
                + "</encrypted>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Verify IV attribute is NOT present in legacy format
        String ivAttribute = props.getAttribute("encrypted.secret", "iv");
        assertNull(ivAttribute, "IV attribute should NOT be present in legacy format");

        // Verify encrypted attribute is still present
        String encryptedAttribute = props.getAttribute("encrypted.secret", "encrypted");
        assertEquals("true", encryptedAttribute);

        // Verify the value can be read (without decryption, since we don't have JiveGlobals set up)
        String value = props.getProperty("encrypted.secret");
        assertEquals("legacyEncryptedValue", value);
    }

    /**
     * Tests that legacy format (no IV) and new format (with IV) can coexist in same XML file.
     * This ensures smooth migration path where old and new properties work side-by-side.
     */
    @Test
    public void testMixedLegacyAndNewFormatCompatibility() throws Exception {
        // Mix of legacy (no IV) and new (with IV) encrypted properties
        String xml = "<root>"
                + "<config>"
                + "<oldSecret encrypted=\"true\">legacyValue</oldSecret>"
                + "<newSecret encrypted=\"true\" iv=\"MTIzNDU2Nzg5MDEyMzQ1Ng==\">newValue</newSecret>"
                + "</config>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Verify legacy property has no IV
        assertNull(props.getAttribute("config.oldSecret", "iv"), "Legacy property should not have IV attribute");
        assertEquals("true", props.getAttribute("config.oldSecret", "encrypted"));

        // Verify new property has IV
        assertNotNull(props.getAttribute("config.newSecret", "iv"), "New property should have IV attribute");
        assertEquals("true", props.getAttribute("config.newSecret", "encrypted"));

        // Both should be readable
        assertEquals("legacyValue", props.getProperty("config.oldSecret"));
        assertEquals("newValue", props.getProperty("config.newSecret"));
    }

    /**
     * Tests that valid Base64-encoded IVs of correct length (16 bytes) are accepted.
     */
    @Test
    public void testValidIVFormats() throws Exception {
        // Test multiple valid 16-byte IVs encoded as Base64
        String[] validIVs = {
            "AAAAAAAAAAAAAAAAAAAAAA==",  // All zeros (16 bytes)
            "/////////////////////w==",  // All 0xFF (16 bytes)
            "MTIzNDU2Nzg5MDEyMzQ1Ng==",  // ASCII "1234567890123456" (16 bytes)
            "YWJjZGVmZ2hpamtsbW5vcA=="  // ASCII "abcdefghijklmnop" (16 bytes)
        };

        for (String validIV : validIVs) {
            String xml = "<root><config><secret encrypted=\"true\" iv=\"" + validIV + "\">value</secret></config></root>";
            XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            String ivAttribute = props.getAttribute("config.secret", "iv");
            assertNotNull(ivAttribute);

            // Should decode without exception
            byte[] decodedIV = Base64.getDecoder().decode(ivAttribute);
            assertEquals(16, decodedIV.length, "All valid IVs should decode to 16 bytes");
        }
    }

    /**
     * Tests that IV attribute is stored correctly when present.
     * This verifies getAttribute() correctly retrieves the iv attribute value.
     */
    @Test
    public void testIVAttributeRetrieval() throws Exception {
        String expectedIV = "YWJjZGVmZ2hpamtsbW5vcA=="; // Base64 for "abcdefghijklmnop"
        String xml = "<root>"
                + "<secure>"
                + "<apiKey encrypted=\"true\" iv=\"" + expectedIV + "\">encryptedApiKey</apiKey>"
                + "</secure>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Retrieve IV attribute
        String actualIV = props.getAttribute("secure.apiKey", "iv");
        assertEquals(expectedIV, actualIV, "Retrieved IV should match stored IV");

        // Verify it's valid Base64 and decodes to 16 bytes
        byte[] decodedIV = Base64.getDecoder().decode(actualIV);
        assertEquals(16, decodedIV.length);
        assertArrayEquals("abcdefghijklmnop".getBytes(StandardCharsets.UTF_8), decodedIV);
    }

    /**
     * Tests that encrypting the same plaintext with different IVs produces different ciphertexts.
     * This verifies that random IVs are properly used in encryption.
     */
    @Test
    public void testEncryptionWithDifferentIVsProducesDifferentCiphertext() throws Exception {
        String plaintext = "my-secret-password";
        Encryptor encryptor = new AesEncryptor();

        // Generate two different random IVs
        byte[] iv1 = new byte[16];
        byte[] iv2 = new byte[16];
        new java.security.SecureRandom().nextBytes(iv1);
        new java.security.SecureRandom().nextBytes(iv2);

        // Encrypt same plaintext with different IVs
        String ciphertext1 = encryptor.encrypt(plaintext, iv1);
        String ciphertext2 = encryptor.encrypt(plaintext, iv2);

        // Ciphertexts should be different
        assertNotEquals(ciphertext1, ciphertext2,
                "Same plaintext with different IVs should produce different ciphertext");

        // Both should decrypt to same plaintext with their respective IVs
        assertEquals(plaintext, encryptor.decrypt(ciphertext1, iv1));
        assertEquals(plaintext, encryptor.decrypt(ciphertext2, iv2));
    }

    /**
     * Tests full encryption/decryption round-trip with random IV stored in XML.
     * This simulates the actual XMLProperties encryption workflow.
     */
    @Test
    public void testEncryptionDecryptionRoundTripWithStoredIV() throws Exception {
        String plaintext = "sensitive-data-123";
        Encryptor encryptor = new AesEncryptor();

        // Generate random IV
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);

        // Encrypt plaintext
        String ciphertext = encryptor.encrypt(plaintext, iv);

        // Encode IV as Base64 (as would be stored in XML)
        String ivBase64 = Base64.getEncoder().encodeToString(iv);

        // Create XML with encrypted value and IV attribute
        String xml = "<root>"
                + "<config>"
                + "<secret encrypted=\"true\" iv=\"" + ivBase64 + "\">" + ciphertext + "</secret>"
                + "</config>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Retrieve stored values
        String storedCiphertext = props.getProperty("config.secret");
        String storedIV = props.getAttribute("config.secret", "iv");

        // Verify values match what we stored
        assertEquals(ciphertext, storedCiphertext);
        assertEquals(ivBase64, storedIV);

        // Decrypt using stored IV
        byte[] retrievedIV = Base64.getDecoder().decode(storedIV);
        String decrypted = encryptor.decrypt(storedCiphertext, retrievedIV);

        // Verify decryption works
        assertEquals(plaintext, decrypted);
    }

    /**
     * Tests that legacy encrypted properties (no IV) can still be decrypted.
     * This ensures backward compatibility with properties encrypted before random IVs were implemented.
     */
    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyEncryptedPropertyDecryption() throws Exception {
        String plaintext = "legacy-password";
        Encryptor encryptor = new AesEncryptor();

        // Encrypt using deprecated method (hardcoded IV)
        String legacyCiphertext = encryptor.encrypt(plaintext);

        // Create XML with legacy format (encrypted="true" but NO iv attribute)
        String xml = "<root>"
                + "<config>"
                + "<oldSecret encrypted=\"true\">" + legacyCiphertext + "</oldSecret>"
                + "</config>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        // Retrieve stored ciphertext
        String storedCiphertext = props.getProperty("config.oldSecret");
        assertEquals(legacyCiphertext, storedCiphertext);

        // Verify no IV attribute exists
        String ivAttribute = props.getAttribute("config.oldSecret", "iv");
        assertNull(ivAttribute, "Legacy format should not have IV attribute");

        // Decrypt using deprecated method (hardcoded IV)
        String decrypted = encryptor.decrypt(storedCiphertext);
        assertEquals(plaintext, decrypted);
    }

    /**
     * Tests that decryptPropertyValue handles corrupted IV with invalid Base64.
     * Should log error and return null for corrupted properties.
     */
    @Test
    public void testDecryptPropertyValue_CorruptedBase64IV() throws Exception {
        // Create XML with corrupted IV (invalid Base64)
        String xml = "<root>"
                + "<config>"
                + "<secret encrypted=\"true\" iv=\"!!!NOT-BASE64!!!\">ciphertext</secret>"
                + "</config>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Document doc = DocumentHelper.parseText(xml);
        Element element = doc.getRootElement().element("config").element("secret");

        // Test decryptPropertyValue helper method directly
        String result = props.decryptPropertyValue("config.secret", element);

        // Should return null for corrupted IV
        assertNull(result, "Should return null when IV has invalid Base64");
    }

    /**
     * Tests that decryptPropertyValue handles IV with wrong length.
     * IV must be exactly 16 bytes for AES.
     */
    @Test
    public void testDecryptPropertyValue_WrongLengthIV() throws Exception {
        // Create IV that's only 8 bytes (wrong length, should be 16)
        byte[] shortIV = new byte[8];
        String shortIVBase64 = Base64.getEncoder().encodeToString(shortIV);

        String xml = "<root>"
                + "<config>"
                + "<secret encrypted=\"true\" iv=\"" + shortIVBase64 + "\">ciphertext</secret>"
                + "</config>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Document doc = DocumentHelper.parseText(xml);
        Element element = doc.getRootElement().element("config").element("secret");

        // Test decryptPropertyValue helper method directly
        String result = props.decryptPropertyValue("config.secret", element);

        // Should return null for wrong-length IV
        assertNull(result, "Should return null when IV has wrong length");
    }

    /**
     * Tests that decryptPropertyValue handles decryption failures gracefully.
     * Even with valid IV, corrupted ciphertext should not crash.
     */
    @Test
    public void testDecryptPropertyValue_DecryptionFailure() throws Exception {
        // Create valid IV but corrupted ciphertext
        byte[] validIV = new byte[16];
        new SecureRandom().nextBytes(validIV);
        String validIVBase64 = Base64.getEncoder().encodeToString(validIV);

        String xml = "<root>"
                + "<config>"
                + "<secret encrypted=\"true\" iv=\"" + validIVBase64 + "\">CORRUPTED_CIPHERTEXT</secret>"
                + "</config>"
                + "</root>";

        XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Document doc = DocumentHelper.parseText(xml);
        Element element = doc.getRootElement().element("config").element("secret");

        // Test decryptPropertyValue helper method directly
        String result = props.decryptPropertyValue("config.secret", element);

        // Should return null when decryption fails
        assertNull(result, "Should return null when decryption fails");
    }

    /**
     * Tests that encryptPropertyWithNewEncryptor generates random IVs.
     * Multiple encryptions of the same value should produce different IVs.
     */
    @Test
    public void testEncryptPropertyWithNewEncryptor_ProducesRandomIVs() throws Exception {
        XMLProperties props = XMLProperties.getNonPersistedInstance();
        String plaintext = "test-value";

        // Create two elements for encryption
        Document doc1 = DocumentHelper.parseText("<root><element1/></root>");
        Element element1 = doc1.getRootElement().element("element1");

        Document doc2 = DocumentHelper.parseText("<root><element2/></root>");
        Element element2 = doc2.getRootElement().element("element2");

        // Encrypt the same value twice using new encryptor (target key during rotation)
        String encrypted1 = props.encryptPropertyWithNewEncryptor(plaintext, element1);
        String encrypted2 = props.encryptPropertyWithNewEncryptor(plaintext, element2);

        // Get the generated IVs
        String iv1 = element1.attributeValue("iv");
        String iv2 = element2.attributeValue("iv");

        // Verify IVs are different (random)
        assertNotNull(iv1, "First IV should be generated");
        assertNotNull(iv2, "Second IV should be generated");
        assertNotEquals(iv1, iv2, "IVs should be different for different encryptions");

        // Verify ciphertexts are different (because IVs are different)
        assertNotEquals(encrypted1, encrypted2, "Ciphertexts should be different when IVs are different");

        // Verify encrypted attribute is set
        assertEquals("true", element1.attributeValue("encrypted"));
        assertEquals("true", element2.attributeValue("encrypted"));
    }

    /**
     * Tests that encryptPropertyWithCurrentEncryptor generates valid 16-byte IVs.
     */
    @Test
    public void testEncryptPropertyWithCurrentEncryptor_GeneratesValid16ByteIV() throws Exception {
        XMLProperties props = XMLProperties.getNonPersistedInstance();
        String plaintext = "test-value";

        Document doc = DocumentHelper.parseText("<root><element/></root>");
        Element element = doc.getRootElement().element("element");

        // Encrypt using current encryptor (for list-based properties)
        props.encryptPropertyWithCurrentEncryptor(plaintext, element);

        // Get and validate IV
        String ivBase64 = element.attributeValue("iv");
        assertNotNull(ivBase64, "IV should be generated");

        // Decode and check length
        byte[] iv = Base64.getDecoder().decode(ivBase64);
        assertEquals(16, iv.length, "IV should be exactly 16 bytes");
    }

    /**
     * Tests that auto-upgrade is enabled by default when system property is not set.
     * This verifies the secure-by-default behaviour.
     */
    @Test
    public void testAutoUpgrade_EnabledByDefault() throws Exception {
        // Clear system property to test default behaviour
        String originalValue = System.getProperty("openfire.xmlproperties.encryption.autoupgrade");
        try {
            System.clearProperty("openfire.xmlproperties.encryption.autoupgrade");

            // Create XML with legacy encrypted property (no IV attribute)
            String xml = "<root>"
                    + "<config>"
                    + "<legacy encrypted=\"true\">legacyValue</legacy>"
                    + "</config>"
                    + "</root>";

            XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Verify legacy property has no IV initially
            assertNull(props.getAttribute("config.legacy", "iv"),
                    "Legacy property should not have IV attribute initially");

            // shouldAutoUpgradeProperty should return true by default (secure default)
            Document doc = DocumentHelper.parseText(xml);
            Element element = doc.getRootElement().element("config").element("legacy");
            boolean shouldUpgrade = props.shouldAutoUpgradeProperty("config.legacy", element);

            assertTrue(shouldUpgrade, "Auto-upgrade should be enabled by default");
        } finally {
            // Restore original system property
            if (originalValue != null) {
                System.setProperty("openfire.xmlproperties.encryption.autoupgrade", originalValue);
            } else {
                System.clearProperty("openfire.xmlproperties.encryption.autoupgrade");
            }
        }
    }

    /**
     * Tests that auto-upgrade can be disabled via system property.
     * This allows administrators to prevent automatic migration if needed.
     */
    @Test
    public void testAutoUpgrade_Disabled() throws Exception {
        String originalValue = System.getProperty("openfire.xmlproperties.encryption.autoupgrade");
        try {
            // Disable auto-upgrade
            System.setProperty("openfire.xmlproperties.encryption.autoupgrade", "false");

            // Create XML with legacy encrypted property (no IV attribute)
            String xml = "<root>"
                    + "<config>"
                    + "<legacy encrypted=\"true\">legacyValue</legacy>"
                    + "</config>"
                    + "</root>";

            XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Verify shouldAutoUpgradeProperty returns false
            Document doc = DocumentHelper.parseText(xml);
            Element element = doc.getRootElement().element("config").element("legacy");
            boolean shouldUpgrade = props.shouldAutoUpgradeProperty("config.legacy", element);

            assertFalse(shouldUpgrade, "Auto-upgrade should be disabled when property is 'false'");
        } finally {
            if (originalValue != null) {
                System.setProperty("openfire.xmlproperties.encryption.autoupgrade", originalValue);
            } else {
                System.clearProperty("openfire.xmlproperties.encryption.autoupgrade");
            }
        }
    }

    /**
     * Tests that properties with IV attribute are not flagged for auto-upgrade.
     * Only legacy properties (encrypted="true" with no iv attribute) should be upgraded.
     */
    @Test
    public void testAutoUpgrade_SkipsPropertiesWithIV() throws Exception {
        String originalValue = System.getProperty("openfire.xmlproperties.encryption.autoupgrade");
        try {
            // Enable auto-upgrade
            System.setProperty("openfire.xmlproperties.encryption.autoupgrade", "true");

            // Create XML with modern encrypted property (has IV attribute)
            String xml = "<root>"
                    + "<config>"
                    + "<modern encrypted=\"true\" iv=\"MTIzNDU2Nzg5MDEyMzQ1Ng==\">modernValue</modern>"
                    + "</config>"
                    + "</root>";

            XMLProperties props = XMLProperties.getNonPersistedInstance(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            // Verify property has IV
            assertNotNull(props.getAttribute("config.modern", "iv"),
                    "Modern property should have IV attribute");

            // Verify shouldAutoUpgradeProperty returns false (no upgrade needed)
            Document doc = DocumentHelper.parseText(xml);
            Element element = doc.getRootElement().element("config").element("modern");
            boolean shouldUpgrade = props.shouldAutoUpgradeProperty("config.modern", element);

            assertFalse(shouldUpgrade,
                    "Properties with IV should not be flagged for auto-upgrade");
        } finally {
            if (originalValue != null) {
                System.setProperty("openfire.xmlproperties.encryption.autoupgrade", originalValue);
            } else {
                System.clearProperty("openfire.xmlproperties.encryption.autoupgrade");
            }
        }
    }
}
