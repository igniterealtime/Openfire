package org.jivesoftware.util;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class providing symmetric AES encryption/decryption. To strengthen
 * the encrypted result, use the {@link #setKey} method to provide a custom
 * key prior to invoking the {@link #encrypt} or {@link #decrypt} methods.
 *
 * @author Tom Evans
 */
public class AesEncryptor implements Encryptor {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptor.class);
    private static final String ALGORITHM = "AES/CBC/PKCS7Padding";

    private static final byte[] INIT_PARM =
    {
        (byte)0xcd, (byte)0x91, (byte)0xa7, (byte)0xc5,
        (byte)0x27, (byte)0x8b, (byte)0x39, (byte)0xe0,
        (byte)0xfa, (byte)0x72, (byte)0xd0, (byte)0x29,
        (byte)0x83, (byte)0x65, (byte)0x9d, (byte)0x74
    };

    private static final byte[] DEFAULT_KEY =
    {
        (byte)0xf2, (byte)0x46, (byte)0x5d, (byte)0x2a,
        (byte)0xd1, (byte)0x73, (byte)0x0b, (byte)0x18,
        (byte)0xcb, (byte)0x86, (byte)0x95, (byte)0xa3,
        (byte)0xb1, (byte)0xe5, (byte)0x89, (byte)0x27
    };

    private static boolean isInitialized = false;

    private byte[] cipherKey = null;

    /** Default constructor */
    public AesEncryptor() { initialize(); }

    /**
     * Custom key constructor
     *
     * @param key the custom key
     */
    public AesEncryptor(String key) { 
        initialize();
        setKey(key);
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.util.Encryptor#encrypt(java.lang.String)
     */
    @Override
    public String encrypt(String value) {
        return encrypt(value, null);
    }

    @Override
    public String encrypt(String value, byte[] iv) {
        if (value == null) { return null; }
        byte [] bytes = value.getBytes(StandardCharsets.UTF_8);
        return Base64.encodeBytes(cipher(bytes, getKey(), iv == null ? INIT_PARM : iv, Cipher.ENCRYPT_MODE));
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.util.Encryptor#decrypt(java.lang.String)
     */
    @Override
    public String decrypt(String value) {
        return decrypt(value, null);
    }

    @Override
    public String decrypt(String value, byte[] iv) {
        if (value == null) { return null; }
        byte [] bytes = cipher(Base64.decode(value), getKey(), iv == null ? INIT_PARM : iv, Cipher.DECRYPT_MODE);
        if (bytes == null) { return null; }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Symmetric encrypt/decrypt routine.
     *
     * @param attribute The value to be converted
     * @param key The encryption key
     * @param mode The cipher mode (encrypt or decrypt)
     * @return The converted attribute, or null if conversion fails
     */
    private byte [] cipher(byte [] attribute, byte [] key, byte[] iv, int mode)
    {
        byte [] result = null;
        try
        {
            // Create AES encryption key
            Key aesKey = new SecretKeySpec(key, "AES");

            // Create AES Cipher
            Cipher aesCipher = Cipher.getInstance(ALGORITHM);

            // Initialize AES Cipher and convert
            aesCipher.init(mode, aesKey, new IvParameterSpec(iv));
            result = aesCipher.doFinal(attribute);
        }
        catch (Exception e)
        {
            log.error("AES cipher failed", e);
        }
        return result;
    }

    /**
     * Return the encryption key. This will return the user-defined
     * key (if available) or a default encryption key.
     *
     * @return The encryption key
     */
    private byte [] getKey()
    {
        return cipherKey == null ? DEFAULT_KEY : cipherKey;
    }

    /**
     * Set the encryption key. This will apply the user-defined key,
     * truncated or filled (via the default key) as needed  to meet
     * the key length specifications.
     *
     * @param key The encryption key
     */
    private void setKey(byte [] key)
    {
        cipherKey = editKey(key);
    }

    /* (non-Javadoc)
     * @see org.jivesoftware.util.Encryptor#setKey(java.lang.String)
     */
    @Override
    public void setKey(String key)
    {
        if (key == null) { 
            cipherKey = null; 
            return;
        }
        byte [] bytes = key.getBytes(StandardCharsets.UTF_8);
        setKey(editKey(bytes));
    }

    /**
     * Validates an optional user-defined encryption key. Only the
     * first sixteen bytes of the input array will be used for the key.
     * It will be filled (if necessary) to a minimum length of sixteen.
     *
     * @param key The user-defined encryption key
     * @return A valid encryption key, or null
     */
    private byte [] editKey(byte [] key)
    {
        if (key == null) { return null; }
        byte [] result = new byte [DEFAULT_KEY.length];
        for (int x=0; x<DEFAULT_KEY.length; x++)
        {
            result[x] = x < key.length ? key[x] : DEFAULT_KEY[x];
        }
        return result;
    }

    /** Installs the required security provider(s) */
    private synchronized void initialize()
    {
        if (!isInitialized)
        {
            try
            {
                Security.addProvider(new BouncyCastleProvider());
                isInitialized = true;
            }
            catch (Throwable t)
            {
                log.warn("JCE provider failure; unable to load BC", t);
            }
        }
    }

/* */
    
}
