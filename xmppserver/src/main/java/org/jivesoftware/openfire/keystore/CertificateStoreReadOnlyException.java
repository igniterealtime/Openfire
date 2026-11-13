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
package org.jivesoftware.openfire.keystore;

/**
 * Indicates that a change to a certificate store was refused, because Openfire is configured not to modify the
 * certificate stores it uses.
 *
 * As this is a subclass of {@link CertificateStoreConfigException}, code that handles a failure to change a store
 * handles this refusal too.
 *
 * @author Matthew Vivian
 * @see CertificateStoreManager#READ_ONLY
 */
public class CertificateStoreReadOnlyException extends CertificateStoreConfigException
{
    /**
     * Creates the refusal of a change to a certificate store.
     *
     * @param message a description of the refused change, naming the store.
     */
    public CertificateStoreReadOnlyException( String message )
    {
        super( message );
    }
}
