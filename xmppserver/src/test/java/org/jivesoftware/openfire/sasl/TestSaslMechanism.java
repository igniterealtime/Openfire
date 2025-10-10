package org.jivesoftware.openfire.sasl;

import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalSession;

import javax.security.auth.callback.CallbackHandler;
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
        private String authorizationID = null;
        private boolean throwError = false;
        private long steps = 1;
        private LocalSession clientSession;

        public TestSaslServer(LocalSession clientSession) {
            this.clientSession = clientSession;
        }

        public void reset() {
            authorizationID = null;
            throwError = false;
            steps = 1;
        }

        @Override
        public String getMechanismName() {
            return "TEST-MECHANISM";
        }

        @Override
        public byte[] evaluateResponse(byte[] response) throws SaslException {
            if ( response.length == 0 )
            {
                if (clientSession.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY) == null) {
                    response = null;
                }
            }
            if (throwError) {
                throw new SaslException("Authentication failed");
            }
            if (this.steps <= 0) {
                throw new SaslException("Authentication steps exceeded: " + this.steps);
            }
            this.steps--;
            authorizationID = "test-user";
            return response;
        }

        @Override
        public boolean isComplete() {
            return this.steps == 0;
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

        public void setSteps(long steps) {
            this.steps = steps;
        }
    }

    /**
     * A test SASL Server factory that creates our test mechanism
     */
    public static class TestSaslServerFactory implements SaslServerFactory {
        private static ThreadLocal<TestSaslServer> saslServer = new ThreadLocal<>();

        public TestSaslServerFactory() {
            // Default constructor required for factory instantiation
        }

        private static void setSaslServer(TestSaslServer server) {
            saslServer.set(server);
        }

        private static void clearSaslServer() {
            saslServer.remove();
        }

        @Override
        public SaslServer createSaslServer(String mechanism, String protocol,
                                           String serverName, Map<String, ?> props,
                                           CallbackHandler cbh) throws SaslException {
            if ("TEST-MECHANISM".equals(mechanism)) {
                return saslServer.get();
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
    public static TestSaslServer registerTestMechanism(LocalSession clientSession) {
        TestSaslServer testSaslServer = new TestSaslServer(clientSession);

        // Set the server instance before registering the provider
        TestSaslServerFactory.setSaslServer(testSaslServer);

        if (Security.getProvider("Test Provider") == null) {
            // Register the provider if not already registered
            Security.addProvider(new Provider("Test Provider", "1.0", "Test Provider") {{
                put("SaslServerFactory.TEST-MECHANISM", TestSaslServerFactory.class.getName());
            }});
        }

        return testSaslServer;
    }

    public static void unregisterTestMechanism() {
        TestSaslServerFactory.clearSaslServer();
    }
}
