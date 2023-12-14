/*
 * Copyright (C) 2017-2019 Ignite Realtime Foundation. All rights reserved.
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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Certificate identity mapping that uses the CommonName as the
 * identity credentials
 * 
 * @author Victor Hong
 *
 */
public class CNCertificateIdentityMapping implements CertificateIdentityMapping {

    private static Pattern cnPattern = Pattern.compile("(?i)(cn=)([^,]*)");
    
    /**
     * Maps certificate CommonName as identity credentials
     * 
     * @param certificate the certificates to map
     * @return A List of names.
     */
    @Override
    public List<String> mapIdentity(X509Certificate certificate) {
        String name = certificate.getSubjectDN().getName();
        Matcher matcher = cnPattern.matcher(name);
        // Create an array with the detected identities
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(2));
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
