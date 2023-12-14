/*
 * Copyright (C) 2017-2018 Ignite Realtime Foundation. All rights reserved.
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
import java.util.List;

/**
 * This is the interface used to map identity credentials from certificates.
 * Users may implement this class to map authentication credentials (i.e. usernames)
 * from certificate data (e.g. CommonName or SubjectAlternativeName) 
 * 
 * @author Victor Hong
 *
 */
public interface CertificateIdentityMapping {
    /**
     * Maps identities from X509Certificates
     * 
     * @param certificate The certificate from which to map identities
     * @return A list of identities mapped from the certificate 
     */
    List<String> mapIdentity(X509Certificate certificate);
    
    /**
     * Returns the short name of the mapping
     * 
     * @return The short name of the mapping
     */
    String name();
}
