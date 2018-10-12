package org.jivesoftware.util;

public interface Encryptor {

    /**
     * Encrypt a clear text String.
     *
     * @param value The clear text attribute
     * @return The encrypted attribute, or null
     */
    String encrypt( String value );

    /**
     * Encrypt a clear text String.
     *
     * @param value The clear text attribute
     * @param iv The IV to use, or null for the default IV
     * @return The encrypted attribute, or null
     */
    String encrypt( String value, byte[] iv );

    /**
     * Decrypt an encrypted String. 
     *
     * @param value The encrypted attribute in Base64 encoding
     * @return The clear text attribute, or null
     */
    String decrypt( String value );

    /**
     * Decrypt an encrypted String.
     *
     * @param value The encrypted attribute in Base64 encoding
     * @param iv The IV to use, or null for the default IV
     * @return The clear text attribute, or null
     */
    String decrypt( String value, byte[] iv );

    /**
     * Set the encryption key. This will apply the user-defined key,
     * truncated or filled (via the default key) as needed  to meet
     * the key length specifications.
     *
     * @param key The encryption key
     */
    void setKey( String key );

}
