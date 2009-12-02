/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.sip.tester.security;

import java.util.Hashtable;

/**
 * The class is used to cache all realms that a certain call has been authorized
 * against and all credentials that have been used for each realm. Note that
 * rfc3261 suggests keeping callId->credentials mapping where as we map
 * realm->credentials. This is done to avoid asking the user for a password
 * before each call.
 *
 * @author Emil Ivov <emcho@dev.java.net>
 * @version 1.0
 */

class CredentialsCache {
    // Contains call->realms mappings
    private Hashtable<String, CredentialsCacheEntry> authenticatedRealms = new Hashtable<String, CredentialsCacheEntry>();

    /**
     * Cache credentials for the specified call and realm
     *
     * @param realm      the realm that the specify credentials apply to
     * @param cacheEntry the credentials
     */
    void cacheEntry(String realm, CredentialsCacheEntry cacheEntry) {
        authenticatedRealms.put(realm, cacheEntry);
    }

    /**
     * Returns the credentials corresponding to the specified realm or null if
     * none could be found.
     *
     * @param realm the realm that the credentials apply to
     * @return the credentials corresponding to the specified realm or null if
     *         none could be found.
     */
    CredentialsCacheEntry get(String realm) {
        return (CredentialsCacheEntry)this.authenticatedRealms.get(realm);
    }

    /**
     * Returns the credentials corresponding to the specified realm or null if
     * none could be found and removes the entry from the cache.
     *
     * @param realm
     *            the realm that the credentials apply to
     * @return the credentials corresponding to the specified realm or null if
     *         none could be found.
     */
    CredentialsCacheEntry remove(String realm) {
        return (CredentialsCacheEntry)this.authenticatedRealms.remove(realm);
	}

}
