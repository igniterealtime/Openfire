/*
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.util;

import org.jivesoftware.openfire.keystore.CertificateStore;

/**
 * Interface to listen for certificate events. Use
 * the {@link CertificateManager#addListener(CertificateEventListener)} method to register for events.
 *
 * @author Gaston Dombiak
 */
public interface CertificateEventListener {

    /**
     * Event triggered when the content of a certificate store was changed.
     *
     * @param store The store for which the content was changed.
     */
    void storeContentChanged( CertificateStore store );
}
