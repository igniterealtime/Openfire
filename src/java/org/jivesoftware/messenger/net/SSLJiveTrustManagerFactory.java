/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.net;

import com.sun.net.ssl.TrustManager;
import com.sun.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * A custom TrustManagerFactory that creates a trust manager list using the
 * default trust manager or a standard keystore as specified in jive_config.xml.
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
     * located at 'keystore' location under messengerHome, and uses 'keypass' as
     * the password for the keystore password and key password (note that
     * trust managers typically don't need a key password as public keys
     * are stored in the clear and can be obtained without a key password).
     * The default Jive distribution doesn't ship with a trust keystore
     * as it is not needed (the server does not require client authentication).
     *
     * @param storeType  The type of keystore (e.g. "JKS") to use or null to indicate no keystore should be used
     * @param truststore The relative location of the keystore under messengerHome
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
}
