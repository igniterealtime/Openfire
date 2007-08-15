/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
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
        super("JiveSoftware", 1.0, "JiveSoftware SASL provider v1.0, implementing server mechanisms for: PLAIN");
        put("SaslServerFactory.PLAIN", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
    }
}