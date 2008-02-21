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

import java.util.Map;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslServerFactory;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import com.sun.security.sasl.util.PolicyUtils;
import org.jivesoftware.openfire.clearspace.ClearspaceSaslServer;

/**
 * Server Factory for supported mechanisms.
 *
 * @author Jay Kline
 */

public class SaslServerFactoryImpl implements SaslServerFactory {

    private static final String myMechs[] = { "PLAIN", "CLEARSPACE" };
    private static final int mechPolicies[] = { PolicyUtils.NOANONYMOUS, PolicyUtils.NOANONYMOUS };
    private static final int PLAIN = 0;
    private static final int CLEARSPACE = 1;

    public SaslServerFactoryImpl() {
    }

    /**
     * Creates a <code>SaslServer</code> implementing a supported mechanism using the parameters supplied.
     *
     * @param mechanism The non-null IANA-registered named of a SASL mechanism.
     * @param protocol The non-null string name of the protocol for which the authentication is being performed (e.g., "ldap").
     * @param serverName The non-null fully qualified host name of the server to authenticate to.
     * @param props The possibly null set of properties used to select the SASL mechanism and to configure the authentication exchange of the selected mechanism. 
     * @param cbh The possibly null callback handler to used by the SASL mechanisms to get further information from the application/library to complete the authentication. 
     * @return A possibly null SaslServer created using the parameters supplied. If null, this factory cannot produce a SaslServer  using the parameters supplied.
     * @throws SaslException If cannot create a SaslServer because of an error.
     */

    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
        if (mechanism.equals(myMechs[PLAIN]) && PolicyUtils.checkPolicy(mechPolicies[PLAIN], props)) {
            if (cbh == null) {
                throw new SaslException("CallbackHandler with support for Password, Name, and AuthorizeCallback required");
            }
            return new SaslServerPlainImpl(protocol, serverName, props, cbh);
        }
        else if (mechanism.equals(myMechs[CLEARSPACE]) && PolicyUtils.checkPolicy(mechPolicies[CLEARSPACE], props)) {
            if (cbh == null) {
                throw new SaslException("CallbackHandler with support for AuthorizeCallback required");
            }
            return new ClearspaceSaslServer();
        }
        return null;
    }

    /**
     * Returns an array of names of mechanisms that match the specified mechanism selection policies.
     * @param props The possibly null set of properties used to specify the security policy of the SASL mechanisms.
     * @return A non-null array containing a IANA-registered SASL mechanism names.
     */

    public String[] getMechanismNames(Map<String, ?> props) {
        return PolicyUtils.filterMechs(myMechs, mechPolicies, props);
    }
}