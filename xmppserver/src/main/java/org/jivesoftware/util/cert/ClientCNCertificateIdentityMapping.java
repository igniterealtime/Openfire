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

import org.jivesoftware.util.SystemProperty;

/**
 * Client Certificate identity mapping that uses the CommonName as the
 * identity credentials and provides possibility to add optional prefix and suffix
 *
 */
public class ClientCNCertificateIdentityMapping extends AbstractCNCertificateIdentityMapping {

    public ClientCNCertificateIdentityMapping() {
        SystemProperty<String> cnPattern = SystemProperty.Builder.ofType(String.class)
                .setKey("provider.clientCnIdentity.pattern")
                .setDefaultValue("(?i)(cn=)([^,]*)")
                .setDynamic(true)
                .addListener(this::setCnPattern)
                .build();

        setCnPattern(cnPattern.getValue());

        SystemProperty<String> cnPrefix = SystemProperty.Builder.ofType(String.class)
                .setKey("provider.clientCnIdentity.prefix")
                .setDefaultValue("")
                .setDynamic(true)
                .addListener(this::setCnSuffix)
                .build();
        setCnPrefix(cnPrefix.getValue());

        SystemProperty<String> cnSuffix = SystemProperty.Builder.ofType(String.class)
                .setKey("provider.clientCnIdentity.suffix")
                .setDefaultValue("")
                .setDynamic(true)
                .addListener(this::setCnSuffix)
                .build();
        setCnSuffix(cnSuffix.getValue());
    }


    /**
     * Returns the short name of mapping
     *
     * @return The short name of the mapping
     */
    @Override
    public String name() {
        return "Client Common Name Mapping";
    }

}
