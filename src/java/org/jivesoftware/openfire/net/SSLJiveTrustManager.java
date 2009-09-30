/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
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

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import com.sun.net.ssl.X509TrustManager;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

/**
 * Trust manager which accepts certificates without any validation
 * except date validation.
 * <p/>
 * A skeleton placeholder for developers wishing to implement their own custom
 * trust manager. In future revisions we may expand the skeleton code if customers
 * request assistance in creating custom trust managers.
 * <p/>
 * You only need a trust manager if your server will require clients
 * to authenticated with the server (typically only the server authenticates
 * with the client).
 *
 * @author Iain Shigeoka
 */
public class SSLJiveTrustManager implements X509TrustManager {

    public void checkClientTrusted(X509Certificate[] chain, String authType) {

    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) {
    }

    public boolean isClientTrusted(X509Certificate[] x509Certificates) {
        return true;
    }

    public boolean isServerTrusted(X509Certificate[] x509Certificates) {
        boolean trusted = true;
        try {
            x509Certificates[0].checkValidity();
        }
        catch (CertificateExpiredException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            trusted = false;
        }
        catch (CertificateNotYetValidException e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            trusted = false;
        }
        return trusted;
    }

    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
