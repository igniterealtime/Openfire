/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
        super("JiveSoftware", 1.0, "JiveSoftware SASL provider v1.0, implementing server mechanisms for: PLAIN, CLEARSPACE");
        // Add SaslServer supporting the PLAIN SASL mechanism
        put("SaslServerFactory.PLAIN", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
        // Add SaslServer supporting the Clearspace SASL mechanism
        put("SaslServerFactory.CLEARSPACE", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
    }
}