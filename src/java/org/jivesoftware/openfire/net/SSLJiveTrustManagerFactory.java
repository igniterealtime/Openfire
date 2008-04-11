/**
 * $RCSfile$
 * $Revision: 2774 $
 * $Date: 2005-09-05 01:53:16 -0300 (Mon, 05 Sep 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.net;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.jivesoftware.util.Log;

/**
 * A custom TrustManagerFactory that creates a trust manager list using the
 * default trust manager or a standard keystore as specified in openfire.xml.
 * There is no default trust keystore provided with the Jive distribution as most
 * clients will not need to be authenticated with the server.
 * <p/>
 * The Java Keystore (JKS) takes a single password which must apply to both the
 * keystore and the key itself. Users may specify another keystore type and keystore
 * location. Alternatively, don't set a keystore type to use the JVM defaults and
 * configure your JVMs security files (see your JVM documentation) to plug in
 * any TrustManagerFactory provider.
 *
 * @author Iain Shigeoka
 */
public class SSLJiveTrustManagerFactory {

    /**
     * Creates a TrustManager list which is null if the storeType is null, or
     * is a standard TrustManager that uses a KeyStore of type storeType,
     * located at 'keystore' location under home, and uses 'keypass' as
     * the password for the keystore password and key password (note that
     * trust managers typically don't need a key password as public keys
     * are stored in the clear and can be obtained without a key password).
     * The default Jive distribution doesn't ship with a trust keystore
     * as it is not needed (the server does not require client authentication).
     *
     * @param storeType  The type of keystore (e.g. "JKS") to use or null to indicate no keystore should be used
     * @param truststore The relative location of the keystore under home
     * @param trustpass  The password for the keystore and key
     * @return An array of relevant KeyManagers (may be null indicating a default KeyManager should be created)
     * @throws NoSuchAlgorithmException If the keystore type doesn't exist (not provided or configured with your JVM)
     * @throws KeyStoreException        If the keystore is corrupt
     * @throws IOException              If the keystore could not be located or loaded
     * @throws CertificateException     If there were no certificates to be loaded or they are invalid
     */
    public static TrustManager[] getTrustManagers(String storeType, String truststore, String trustpass) throws NoSuchAlgorithmException, KeyStoreException, IOException, CertificateException {
        TrustManager[] trustManagers;
        if (truststore == null) {
            trustManagers = null;
        }
        else {
            TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            if (trustpass == null) {
                trustpass = "";
            }
            KeyStore keyStore = KeyStore.getInstance(storeType);
            keyStore.load(new FileInputStream(truststore), trustpass.toCharArray());
            trustFactory.init(keyStore);
            trustManagers = trustFactory.getTrustManagers();
        }
        return trustManagers;
    }
    

    //TODO: Is this for c2s or s2s connections? Or both?
    public static TrustManager[] getTrustManagers(KeyStore truststore,
			String trustpass) {
		TrustManager[] trustManagers;
		try {
			if (truststore == null) {
				trustManagers = null;
			} else {
				TrustManagerFactory trustFactory = TrustManagerFactory
						.getInstance(TrustManagerFactory.getDefaultAlgorithm());
				if (trustpass == null) {
					trustpass = SSLConfig.gets2sTrustPassword();
				}

				trustFactory.init(truststore);

				trustManagers = trustFactory.getTrustManagers();
			}
		} catch (KeyStoreException e) {
			trustManagers = null;
			Log.error("SSLJiveTrustManagerFactory startup problem.\n" +
                    "  the keystore is corrupt", e);
		} catch (NoSuchAlgorithmException e) {
			trustManagers = null;
			Log.error("SSLJiveTrustManagerFactory startup problem.\n" +
                    "  the keystore type doesn't exist (not provided or configured with your JVM)", e);
		}
		return trustManagers;
	}
}
