/*
 * Copyright (C) 2023 Guus der Kinderen. All rights reserved.
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
package org.jivesoftware.openfire.session;

import java.util.HashSet;
import java.util.Set;

public class ExpectedOutcome
{
    /**
     * Enumeration of possible connection states.
     */
    public enum ConnectionState
    {
        /**
         * Connection cannot be established. In the current implementation, this includes scenarios in which connections could not be authenticated.
         */
        NO_CONNECTION("(no conn)"),

        /**
         * Connection without encryption, Initiating Entity is authenticated by the Receiving Entity using the Dialback protocol.
         */
        NON_ENCRYPTED_WITH_DIALBACK_AUTH("PLAIN DIALB"),

        /**
         * Connection that is encrypted, Initiating Entity is authenticated by the Receiving Entity using the Dialback protocol.
         */
        ENCRYPTED_WITH_DIALBACK_AUTH("TLS DIALB"),

        /**
         * Connection that is encrypted, Initiating Entity is authenticated by the Receiving Entity using the SASL EXTERNAL mechanism.
         */
        ENCRYPTED_WITH_SASLEXTERNAL_AUTH("SASL_EXT");

        final String shortCode;
        ConnectionState(String shortCode) {
            this.shortCode = shortCode;
        }

        public String getShortCode() {
            return shortCode;
        }
    }

    private final Set<String> rationales = new HashSet<>();
    private ConnectionState connectionState;

    public void set(final ConnectionState state, final String rationale)
    {
        if (connectionState != null && connectionState != state) {
            throw new IllegalStateException("Cannot set state " + state + ". State already is " + connectionState);
        }
        connectionState = state;
        rationales.add(rationale);
    }

    public ConnectionState getConnectionState()
    {
        return connectionState;
    }

    public Set<String> getRationales()
    {
        return rationales;
    }

    public boolean isInconclusive()
    {
        return connectionState == null;
    }
}
