
package org.jivesoftware.openfire.sasl;

import java.security.Provider;


public class SaslProvider extends Provider {

    public SaslProvider() {
        super("JiveSoftware", 1.0, "JiveSoftware SASL provider v1.0, implementing server mechanisms for: PLAIN");
        put("SaslServerFactory.PLAIN", "org.jivesoftware.openfire.sasl.SaslServerFactoryImpl");
    }
}