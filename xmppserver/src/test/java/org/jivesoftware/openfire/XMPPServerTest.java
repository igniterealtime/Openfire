/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire;

import org.jivesoftware.openfire.keystore.CertificateStoreManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link XMPPServer}.
 *
 * @author Matthew Vivian
 */
public class XMPPServerTest
{
    /**
     * Verifies that setup leaves the read-only certificate stores setting in openfire.xml. Setup copies every
     * openfire.xml property that is not in this list into the database, where the setting has no effect and is reported
     * at each startup as an ignored System Property.
     */
    @Test
    public void testReadOnlyCertificateStoresSettingIsNotCopiedToDatabase()
    {
        assertTrue( XMPPServer.XML_ONLY_PROPERTIES.contains( CertificateStoreManager.READ_ONLY_PROPERTY ),
            "setup must not copy cert.stores.readonly from openfire.xml into the database" );
    }
}
