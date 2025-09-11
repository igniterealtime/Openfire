/*
 * Copyright (C) 2017-2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.util.cert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * Certificate identity mapping that uses the CommonName as the
 * identity credentials
 * 
 * @author Nikita Markevich
 * @author Guus der Kinderen
 * @author Victor Hong
 */
public class CNCertificateIdentityMapping implements CertificateIdentityMapping {

    private static final Logger Log = LoggerFactory.getLogger(CNCertificateIdentityMapping.class);

    /**
     * Maps certificate CommonName as identity credentials
     * 
     * @param certificate the certificates to map
     * @return A List of names.
     */
    @Override
    public List<String> mapIdentity(X509Certificate certificate)
    {
        // Create an array with the detected identities
        final List<String> names = new ArrayList<>();
        final X500Principal p = certificate.getSubjectX500Principal();
        final LdapName ln;
        try {
            ln = new LdapName(p.getName(X500Principal.RFC2253));
            for (final Rdn rdn : ln.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    names.add(rdn.getValue().toString());
                }
            }
        } catch (InvalidNameException e) {
            Log.warn("Unable to extract identify from certificate: {}", certificate, e);
        }

        return names;
    }

    /**
     * Returns the short name of mapping
     * 
     * @return The short name of the mapping
     */
    @Override
    public String name() {
        return "Common Name Mapping";
    }
}
