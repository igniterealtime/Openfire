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
package org.jivesoftware.admin;

import org.jivesoftware.openfire.keystore.CertificateStoreManager;

/**
 * Saves the read-only setting of the certificate stores ({@link CertificateStoreManager#READ_ONLY}) from the
 * read-only section of the admin console's Certificate Stores page.
 *
 * @author Matthew Vivian
 */
public final class CertificateStoresReadOnlySetting
{
    /**
     * The name of the submit button of the read-only section, which distinguishes its form from the store forms on the
     * same page.
     */
    public static final String SAVE_PARAMETER = "saveReadOnly";

    /**
     * The name of the read-only checkbox.
     */
    public static final String CHECKBOX_PARAMETER = "readOnly";

    private CertificateStoresReadOnlySetting() {}

    /**
     * Sets the read-only setting to what the checkbox shows. The setting is only written when its value changes.
     *
     * @param readOnly true when the checkbox is ticked.
     * @return true if the value of the setting changed, false if it already had the value.
     */
    public static boolean save( final boolean readOnly )
    {
        if ( CertificateStoreManager.READ_ONLY.getValue() == readOnly )
        {
            return false;
        }
        CertificateStoreManager.READ_ONLY.setValue( readOnly );
        return true;
    }
}
