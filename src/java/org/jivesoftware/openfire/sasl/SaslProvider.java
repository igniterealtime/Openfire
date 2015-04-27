/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.sasl;

import java.security.Provider;

/**
 * This is the Provider object providing the SaslServerFactory written by Jive Software. 
 *
 * @see SaslServerFactoryImpl
 */

public class SaslProvider extends Provider {

    /**
     * Constructs a the JiveSoftware SASL provider.
     */
    public SaslProvider() {
        super("JiveSoftware", 1.0, "JiveSoftware SASL provider v1.0, implementing server mechanisms for: PLAIN, CLEARSPACE, SCRAM-SHA-1");
        // Add SaslServer supporting the PLAIN SASL mechanism
        put("SaslServerFactory.PLAIN", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
        // Add SaslServer supporting the Clearspace SASL mechanism
        put("SaslServerFactory.CLEARSPACE", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
        // Add SaslServer supporting the SCRAM-SHA-1 SASL mechanism
        put("SaslServerFactory.SCRAM-SHA-1", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
    }
}