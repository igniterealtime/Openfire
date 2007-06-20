package org.jivesoftware.openfire.sasl;

import java.util.Map;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslServerFactory;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import com.sun.security.sasl.util.PolicyUtils;

public class SaslServerFactoryImpl implements SaslServerFactory {

    private static final String myMechs[] = { "PLAIN" };
    private static final int mechPolicies[] = { PolicyUtils.NOANONYMOUS };
    private static final int PLAIN = 0;

    public SaslServerFactoryImpl() {
    }


    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh) throws SaslException {
        if (mechanism.equals(myMechs[PLAIN]) && PolicyUtils.checkPolicy(mechPolicies[PLAIN], props)) {
            if (cbh == null) {
                throw new SaslException("CallbackHandler with support for Password, Name, and AuthorizeCallback required");
            }
            return new SaslServerPlainImpl(protocol, serverName, props, cbh);
        }
        return null;
    }

    public String[] getMechanismNames(Map<String, ?> props) {
        return PolicyUtils.filterMechs(myMechs, mechPolicies, props);
    }
}