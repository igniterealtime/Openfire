package org.jivesoftware.openfire.sasl;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.security.Provider;
import java.security.Security;
import java.util.Map;

public class TestSaslMechanism {
    /**
     * A test SASL mechanism that we can control for testing purposes
     */
    public static class TestSaslServer implements SaslServer {
        private boolean complete = false;
        private String authorizationID = null;
        private boolean throwError = false;

        public void reset() {
            complete = false;
            authorizationID = null;
            throwError = false;
        }

        @Override
        public String getMechanismName() {
            return "TEST-MECHANISM";
        }

        @Override
        public byte[] evaluateResponse(byte[] response) throws SaslException {
            if (throwError) {
                throw new SaslException("Authentication failed");
            }
            complete = true;
            authorizationID = "test-user";
            return null;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public String getAuthorizationID() {
            return authorizationID;
        }

        @Override
        public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
            return new byte[0];
        }

        @Override
        public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
            return new byte[0];
        }

        @Override
        public Object getNegotiatedProperty(String propName) {
            return null;
        }

        @Override
        public void dispose() throws SaslException {
        }

        public void setThrowError(boolean throwError) {
            this.throwError = throwError;
        }
    }

    /**
     * A test SASL Server factory that creates our test mechanism
     */
    public static class TestSaslServerFactory implements SaslServerFactory {
        private static TestSaslServer saslServer;

        public TestSaslServerFactory() {
            // Default constructor required for factory instantiation
        }

        private static void setSaslServer(TestSaslServer server) {
            saslServer = server;
        }

        @Override
        public SaslServer createSaslServer(String mechanism, String protocol,
                                           String serverName, Map<String, ?> props,
                                           CallbackHandler cbh) throws SaslException {
            if ("TEST-MECHANISM".equals(mechanism)) {
                return saslServer;
            }
            return null;
        }

        @Override
        public String[] getMechanismNames(Map<String, ?> props) {
            return new String[]{"TEST-MECHANISM"};
        }
    }

    /**
     * Helper method to register the test mechanism
     */
    public static TestSaslServer registerTestMechanism() {
        if (Security.getProvider("Test Provider") == null) {
            TestSaslServer testSaslServer = new TestSaslServer();

            // Set the server instance before registering the provider
            TestSaslServerFactory.setSaslServer(testSaslServer);

            // Register the provider if not already registered
            Security.addProvider(new Provider("Test Provider", "1.0", "Test Provider") {{
                put("SaslServerFactory.TEST-MECHANISM", TestSaslServerFactory.class.getName());
            }});
        }

        return TestSaslServerFactory.saslServer;
    }
}
