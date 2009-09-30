/**
 * $RCSfile$
 * $Revision: 2774 $
 * $Date: 2005-09-05 01:53:16 -0300 (Mon, 05 Sep 2005) $
 *
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

package org.jivesoftware.openfire.net;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.jivesoftware.util.Log;

/**
 * A custom KeyManagerFactory that creates a key manager list using the
 * default key manager or a standard keystore as specified in openfire.xml.
 * The default keystore provided with the Jive distribution uses the Sun Java
 * Keystore (JKS) and that takes a single password which must apply to both the
 * keystore and the key itself. Users may specify another keystore type and keystore
 * location. Alternatively, don't set a keystore type to use the JVM defaults and
 * configure your JVMs security files (see your JVM documentation) to plug in
 * any KeyManagerFactory provider.
 *
 * @author Iain Shigeoka
 */
public class SSLJiveKeyManagerFactory {

    /**
     * Creates a KeyManager list which is null if the storeType is null, or
     * is a standard KeyManager that uses a KeyStore of type storeType,
     * located at 'keystore' location under home, and uses 'keypass' as
     * the password for the keystore password and key password. The default
     * Jive keystore contains a self-signed X509 certificate pair under the
     * alias '127.0.0.1' in a Java KeyStore (JKS) with initial password 'changeit'.
     * This is sufficient for local host testing but should be using standard
     * key management tools for any significant testing or deployment. See
     * the Jive XMPP server security documentation for more information.
     *
     * @param storeType The type of keystore (e.g. "JKS") to use or null to indicate no keystore should be used
     * @param keystore  The relative location of the keystore under home
     * @param keypass   The password for the keystore and key
     * @return An array of relevant KeyManagers (may be null indicating a default KeyManager should be created)
     * @throws NoSuchAlgorithmException  If the keystore type doesn't exist (not provided or configured with your JVM)
     * @throws KeyStoreException         If the keystore is corrupt
     * @throws IOException               If the keystore could not be located or loaded
     * @throws CertificateException      If there were no certificates to be loaded or they are invalid
     * @throws UnrecoverableKeyException If they keystore coud not be opened (typically the password is bad)
     */
    public static KeyManager[] getKeyManagers(String storeType, String keystore, String keypass) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException, UnrecoverableKeyException {
        KeyManager[] keyManagers;
        if (keystore == null) {
            keyManagers = null;
        }
        else {
            if (keypass == null) {
                keypass = "";
            }
            KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(keystore), keypass.toCharArray());

            KeyManagerFactory keyFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyFactory.init(keyStore, keypass.toCharArray());
            keyManagers = keyFactory.getKeyManagers();
        }
        return keyManagers;
    }
    public static KeyManager[] getKeyManagers(KeyStore keystore, String keypass) {
		KeyManager[] keyManagers;
		try {
			if (keystore == null) {
				keyManagers = null;
			} else {
				KeyManagerFactory keyFactory = KeyManagerFactory
						.getInstance(KeyManagerFactory.getDefaultAlgorithm());
				if (keypass == null) {
					keypass = SSLConfig.getKeyPassword();
				}

				keyFactory.init(keystore, keypass.toCharArray());
				keyManagers = keyFactory.getKeyManagers();
			}
		} catch (KeyStoreException e) {
			keyManagers = null;
			Log.error("SSLJiveKeyManagerFactory startup problem.\n" +
                    "  the keystore is corrupt", e);
		} catch (NoSuchAlgorithmException e) {
			keyManagers = null;
			Log.error("SSLJiveKeyManagerFactory startup problem.\n" +
                    "  the keystore type doesn't exist (not provided or configured with your JVM)", e);
		} catch (UnrecoverableKeyException e) {
			keyManagers = null;
			Log.error("SSLJiveKeyManagerFactory startup problem.\n" +
                    "  the keystore could not be opened (typically the password is bad)", e);
		} 
		return keyManagers;
	}
}
