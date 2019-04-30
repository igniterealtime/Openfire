/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.auth;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Blowfish;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.SystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pluggable authentication service. Users of Openfire that wish to change the AuthProvider
 * implementation used to authenticate users can set the <code>AuthProvider.className</code>
 * system property. For example, if you have configured Openfire to use LDAP for user information,
 * you'd want to send a custom implementation of AuthFactory to make LDAP auth queries.
 * After changing the <code>AuthProvider.className</code> system property, you must restart your
 * application server.
 *
 * @author Matt Tucker
 */
public class AuthFactory {

    private static final Logger Log = LoggerFactory.getLogger(AuthFactory.class);
    private static final SystemProperty<String> PASSWORD_KEY = SystemProperty.Builder.ofType(String.class)
        .setKey("passwordKey")
        .setDynamic(true)
        .build();
    public static final SystemProperty<Class> AUTH_PROVIDER = SystemProperty.Builder.ofType(Class.class)
        .setKey("provider.auth.className")
        .setBaseClass(AuthProvider.class)
        .setDefaultValue(DefaultAuthProvider.class)
        .setDynamic(true)
        .addListener(AuthFactory::initProvider)
        .build();

    private static AuthProvider authProvider = null;
    private static MessageDigest digest;
    private static final Object DIGEST_LOCK = new Object();
    private static Blowfish cipher = null;

    static {
        // Create a message digest instance.
        try {
            digest = MessageDigest.getInstance("SHA");
        }
        catch (NoSuchAlgorithmException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
        // Load an auth provider.
        initProvider(AUTH_PROVIDER.getValue());
    }

    private static void initProvider(final Class clazz) {

        // Check if we need to reset the auth provider class
        if (authProvider == null || !clazz.equals(authProvider.getClass())) {
            try {
                authProvider = (AuthProvider)clazz.newInstance();
            }
            catch (Exception e) {
                Log.error("Error loading auth provider: " + clazz.getName(), e);
                authProvider = new DefaultAuthProvider();
            }
        }
    }

    /**
     * Returns the currently-installed AuthProvider. <b>Warning:</b> in virtually all
     * cases the auth provider should not be used directly. Instead, the appropriate
     * methods in AuthFactory should be called. Direct access to the auth provider is
     * only provided for special-case logic.
     *
     * @return the current UserProvider.
     * @deprecated Prefer using the corresponding factory method, rather than 
     * 					invoking methods on the provider directly
     */
    public static AuthProvider getAuthProvider() {
        return authProvider;
    }

    /**
     * Returns whether the currently-installed AuthProvider is instance of a specific class.
     * @param c the class to compare with
     * @return true - if the currently-installed AuthProvider is instance of c, false otherwise.
     */
    public static boolean isProviderInstanceOf(Class<?> c) {
        return c.isInstance(authProvider);
    }

    /**
     * Indicates if the currently-installed AuthProvider is the HybridAuthProvider supporting a specific class.
     *
     * @param clazz the class to check
     * @return {@code true}  if the currently-installed AuthProvider is a HybridAuthProvider that supports an instance of clazz, otherwise {@code false}.
     */
    public static boolean isProviderHybridInstanceOf(Class<? extends AuthProvider> clazz) {
        return authProvider instanceof HybridAuthProvider &&
            ((HybridAuthProvider) authProvider).isProvider(clazz);
    }

    /**
     * Returns true if the currently installed {@link AuthProvider} supports password
     * retrieval. Certain implementation utilize password hashes and other authentication
     * mechanisms that do not require the original password.
     *
     * @return true if plain password retrieval is supported.
     */
    public static boolean supportsPasswordRetrieval() {
        return authProvider.supportsPasswordRetrieval();
    }

    /**
     * Returns the user's password. This method will throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @return the user's password.
     * @throws UserNotFoundException if the given user could not be found.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     */
    public static String getPassword(String username) throws UserNotFoundException,
            UnsupportedOperationException {
        return authProvider.getPassword(username.toLowerCase());
    }

    /**
     * Sets the users's password. This method should throw an UnsupportedOperationException
     * if this operation is not supported by the backend user store.
     *
     * @param username the username of the user.
     * @param password the new plaintext password for the user.
     * @throws UserNotFoundException if the given user could not be loaded.
     * @throws UnsupportedOperationException if the provider does not
     *      support the operation (this is an optional operation).
     * @throws ConnectionException if there is a problem connecting to user and group system
     * @throws InternalUnauthenticatedException if there is a problem authentication Openfire itself into the user and group system
     */
    public static void setPassword(String username, String password) throws UserNotFoundException, 
            UnsupportedOperationException, ConnectionException, InternalUnauthenticatedException {
            authProvider.setPassword(username, password);
        }

    /**
     * Authenticates a user with a username and plain text password and returns and
     * AuthToken. If the username and password do not match the record of
     * any user in the system, this method throws an UnauthorizedException.
     *
     * @param username the username.
     * @param password the password.
     * @return an AuthToken token if the username and password are correct.
     * @throws UnauthorizedException if the username and password do not match any existing user
     *      or the account is locked out.
     * @throws ConnectionException if there is a problem connecting to user and group system
     * @throws InternalUnauthenticatedException if there is a problem authentication Openfire itself into the user and group system
     */
    public static AuthToken authenticate(String username, String password)
            throws UnauthorizedException, ConnectionException, InternalUnauthenticatedException {
        if (LockOutManager.getInstance().isAccountDisabled(username)) {
            LockOutManager.getInstance().recordFailedLogin(username);
            throw new UnauthorizedException();
        }
        authProvider.authenticate(username, password);
        return AuthToken.generateUserToken( username );
    }

    /**
     * Returns a digest given a token and password, according to JEP-0078.
     *
     * @param token the token used in the digest.
     * @param password the plain-text password to be digested.
     * @return the digested result as a hex string.
     */
    public static String createDigest(String token, String password) {
        synchronized (DIGEST_LOCK) {
            digest.update(token.getBytes());
            return StringUtils.encodeHex(digest.digest(password.getBytes()));
        }
    }

    /**
     * Returns an encrypted version of the plain-text password. Encryption is performed
     * using the Blowfish algorithm. The encryption key is stored as the Jive property
     * "passwordKey". If the key is not present, it will be automatically generated.
     *
     * @param password the plain-text password.
     * @return the encrypted password.
     * @throws UnsupportedOperationException if encryption/decryption is not possible;
     *      for example, during setup mode.
     */
    public static String encryptPassword(String password) {
        if (password == null) {
            return null;
        }
        Blowfish cipher = getCipher();
        if (cipher == null) {
            throw new UnsupportedOperationException();
        }
        return cipher.encryptString(password);
    }

    /**
     * Returns a decrypted version of the encrypted password. Encryption is performed
     * using the Blowfish algorithm. The encryption key is stored as the Jive property
     * "passwordKey". If the key is not present, it will be automatically generated.
     *
     * @param encryptedPassword the encrypted password.
     * @return the encrypted password.
     * @throws UnsupportedOperationException if encryption/decryption is not possible;
     *      for example, during setup mode.
     */
    public static String decryptPassword(String encryptedPassword) {
        if (encryptedPassword == null) {
            return null;
        }
        Blowfish cipher = getCipher();
        if (cipher == null) {
            throw new UnsupportedOperationException();
        }
        return cipher.decryptString(encryptedPassword);
    }

    /**
     * Returns a Blowfish cipher that can be used for encrypting and decrypting passwords.
     * The encryption key is stored as the Jive property "passwordKey". If it's not present,
     * it will be automatically generated.
     *
     * @return the Blowfish cipher, or {@code null} if Openfire is not able to create a Cipher;
     *      for example, during setup mode.
     */
    private static synchronized Blowfish getCipher() {
        if (cipher != null) {
            return cipher;
        }
        // Get the password key, stored as a database property. Obviously,
        // protecting your database is critical for making the
        // encryption fully secure.
        String keyString;
        try {
            keyString = PASSWORD_KEY.getValue();
            if (keyString == null) {
                keyString = StringUtils.randomString(15);
                PASSWORD_KEY.setValue(keyString);
                // Check to make sure that setting the property worked. It won't work,
                // for example, when in setup mode.
                if (!keyString.equals(PASSWORD_KEY.getValue())) {
                    return null;
                }
            }
            cipher = new Blowfish(keyString);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        return cipher;
    }

    public static boolean supportsScram() {
        // TODO Auto-generated method stub
        return authProvider.isScramSupported();
    }

    public static String getSalt(String username) throws UnsupportedOperationException, UserNotFoundException {
        return authProvider.getSalt(username);
    }
    public static int getIterations(String username) throws UnsupportedOperationException, UserNotFoundException {
        return authProvider.getIterations(username);
    }
    public static String getServerKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        return authProvider.getServerKey(username);
    }
    public static String getStoredKey(String username) throws UnsupportedOperationException, UserNotFoundException {
        return authProvider.getStoredKey(username);
    }

    public static final String ONE_TIME_PROPERTY = "oneTimeAccessToken";

    public static boolean isOneTimeAccessTokenEnabled() {
        return org.apache.commons.lang3.StringUtils.isNotBlank(JiveGlobals.getXMLProperty(ONE_TIME_PROPERTY));
    }

    public static AuthToken checkOneTimeAccessToken(String userToken) throws UnauthorizedException {
        String accessToken = JiveGlobals.getXMLProperty(ONE_TIME_PROPERTY);
        if (isOneTimeAccessTokenEnabled() && accessToken.equals(userToken)) {
            // Remove the one time token.
            // This invocation will overwrite the openfire.xml with removing the OneTimeAccessToken tag.
            JiveGlobals.deleteXMLProperty(ONE_TIME_PROPERTY);
            Log.info("Login with the one time access token.");
            return AuthToken.generateOneTimeToken(accessToken);
        } else {
            throw new UnauthorizedException();
        }
    }
}
