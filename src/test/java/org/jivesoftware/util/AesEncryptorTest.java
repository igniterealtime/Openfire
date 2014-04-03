package org.jivesoftware.util;

import java.util.UUID;
import junit.framework.TestCase;
import org.junit.Test;

public class AesEncryptorTest extends TestCase {

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
		
		String test = null;
		
		Encryptor encryptor = new AesEncryptor();
		
		String b64Encrypted = encryptor.encrypt(test);
		
		assertNull(b64Encrypted);
		
	}

}
