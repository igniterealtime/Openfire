package org.jivesoftware.util;

import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class BlowfishEncryptorTest {

    @Test
    public void testEncryptionUsingDefaultKey() {
        String test = UUID.randomUUID().toString();
        
        Encryptor encryptor = new Blowfish();
        
        String b64Encrypted = encryptor.encrypt(test);
        assertFalse(test.equals(b64Encrypted));
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }

    @Test
    public void testEncryptionUsingCustomKey() {
        
        String test = UUID.randomUUID().toString();
        
        Encryptor encryptor = new Blowfish(UUID.randomUUID().toString());
        
        String b64Encrypted = encryptor.encrypt(test);
        assertFalse(test.equals(b64Encrypted));
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }

    @Test
    public void testEncryptionForEmptyString() {
        
        String test = "";
        
        Encryptor encryptor = new Blowfish();
        
        String b64Encrypted = encryptor.encrypt(test);
        assertFalse(test.equals(b64Encrypted));
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }


    @Test
    public void testEncryptionForNullString() {
        Encryptor encryptor = new Blowfish();

        String b64Encrypted = encryptor.encrypt(null);

        assertNull(b64Encrypted);
    }
}
