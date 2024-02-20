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
 * identity credentials and provides possibility to add optional prefix and suffix
 *
 * @author Victor Hong
 */
public abstract class AbstractCNCertificateIdentityMapping implements CertificateIdentityMapping {

    private Pattern cnPattern;
    private String cnPrefix;
    private String cnSuffix;

    protected void setCnPattern(String cnPattern) {
        if (cnPattern == null || cnPattern.isEmpty()) {
            throw new IllegalArgumentException("CN Pattern can't be null or empty");
        }
        this.cnPattern = Pattern.compile(cnPattern);
    }

    protected void setCnPrefix(String cnPrefix) {
        if (cnPrefix == null) {
            throw new IllegalArgumentException("CN Prefix can't be null");
        }
        this.cnPrefix = cnPrefix;
    }

    protected void setCnSuffix(String cnSuffix) {
        if (cnSuffix == null) {
            throw new IllegalArgumentException("CN Suffix can't be null");
        }
        this.cnSuffix = cnSuffix;
    }

    /**
     * Maps certificate CommonName as identity credentials with optional prefix and/or suffix.
     * Prefix and suffix are specified by properties. Default value is an empty string.
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
            names.add(cnPrefix + matcher.group(2) + cnSuffix);
        }

        return names;
    }
}
