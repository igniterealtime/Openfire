package org.jivesoftware.util;

import java.util.UUID;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

public class AesEncryptorTest {

    @Test
    public void testEncryptionUsingDefaultKey() {
        String test = UUID.randomUUID().toString();
        
        Encryptor encryptor = new AesEncryptor();
        
        String b64Encrypted = encryptor.encrypt(test);
        assertFalse(test.equals(b64Encrypted));
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }

    @Test
    public void testEncryptionUsingCustomKey() {
        
        String test = UUID.randomUUID().toString();
        
        Encryptor encryptor = new AesEncryptor(UUID.randomUUID().toString());
        
        String b64Encrypted = encryptor.encrypt(test);
        assertFalse(test.equals(b64Encrypted));
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }

    @Test
    public void testEncryptionForEmptyString() {
        
        String test = "";
        
        Encryptor encryptor = new AesEncryptor();
        
        String b64Encrypted = encryptor.encrypt(test);
        assertFalse(test.equals(b64Encrypted));
        
        assertEquals(test, encryptor.decrypt(b64Encrypted));
    }


    @Test
    public void testEncryptionForNullString() {
        Encryptor encryptor = new AesEncryptor();
        
        String b64Encrypted = encryptor.encrypt(null);
        
        assertNull(b64Encrypted);
    }

    @Test
    public void testEncryptionWithKeyAndIV() {

        final String plainText = UUID.randomUUID().toString();
        final byte[] iv = "0123456789abcdef".getBytes();
        final Encryptor encryptor = new AesEncryptor(UUID.randomUUID().toString());
        final String encryptedText = encryptor.encrypt(plainText, iv);

        final String decryptedText = encryptor.decrypt(encryptedText, iv);

        assertThat(decryptedText, is(plainText));
    }

    @Test
    public void testEncryptionWithKeyAndBadIV() {

        final String plainText = UUID.randomUUID().toString();
        final byte[] iv = "0123456789abcdef".getBytes();
        final Encryptor encryptor = new AesEncryptor(UUID.randomUUID().toString());
        final String encryptedText = encryptor.encrypt(plainText, iv);

        final String decryptedText = encryptor.decrypt(encryptedText);

        assertThat(decryptedText, is(not(plainText)));

    }

}
