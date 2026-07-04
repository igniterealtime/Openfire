/*
 * Copyright (C) 2025-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalSession;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.security.Provider;
import java.security.Security;
import java.util.Map;

/**
 * A test double for a SASL mechanism, intended for use in unit tests that exercise
 * {@link SASLAuthentication}. Provides a controllable {@link SaslServer} implementation
 * (named {@code TEST-MECHANISM}) together with the factory and JCA provider registration
 * helpers needed to plug it into the standard Java SASL machinery.
 *
 * <p>Typical usage in a JUnit test class:</p>
 * <pre>{@code
 * private TestSaslMechanism.TestSaslServer testSaslServer;
 *
 * @BeforeEach
 * void setUp() {
 *     testSaslServer = TestSaslMechanism.registerTestMechanism(clientSession);
 * }
 *
 * @AfterEach
 * void tearDown() {
 *     TestSaslMechanism.unregisterTestMechanism();
 * }
 * }</pre>
 */
public class TestSaslMechanism {
    /**
     * A controllable {@link SaslServer} implementation for the {@code TEST-MECHANISM} SASL
     * mechanism. Tests can configure the server to succeed after a given number of
     * {@link #evaluateResponse} calls, or to throw a {@link SaslException} on demand.
     */
    public static class TestSaslServer implements SaslServer {
        private String authorizationID = null;
        private boolean throwError = false;
        private long steps = 1;
        private LocalSession clientSession;

        /**
         * Creates a new {@code TestSaslServer} bound to the supplied session.
         *
         * @param clientSession the client session whose session-data map is consulted when
         *                      deciding how to treat an empty response byte array.
         */
        public TestSaslServer(LocalSession clientSession) {
            this.clientSession = clientSession;
        }

        /**
         * Resets this server to its initial state: clears the authorization ID, disables
         * error injection, and restores the step counter to {@code 1}.
         */
        public void reset() {
            authorizationID = null;
            throwError = false;
            steps = 1;
        }

        @Override
        public String getMechanismName() {
            return "TEST-MECHANISM";
        }

        /**
         * Processes one client response.
         *
         * <p>If the response is empty and the session does not carry the
         * {@link SASLAuthentication#SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY} marker, the
         * response is treated as {@code null} (i.e. absent). If {@link #setThrowError} has
         * been called with {@code true}, a {@link SaslException} is thrown immediately.
         * Otherwise the step counter is decremented and the (possibly {@code null}) response
         * bytes are echoed back as the challenge/success data.</p>
         *
         * @param response the bytes sent by the client; must not be {@code null}.
         * @return the bytes to send back to the client, or {@code null} when the original
         *         response was absent.
         * @throws SaslException if error injection is enabled, or if the step counter has
         *                       already reached zero.
         */
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

        /** Returns {@code true} once all configured steps have been consumed. */
        @Override
        public boolean isComplete() {
            return this.steps == 0;
        }

        /** Returns the authorization ID set during the last successful {@link #evaluateResponse} call. */
        @Override
        public String getAuthorizationID() {
            return authorizationID;
        }

        /** Not used by the test mechanism; always returns an empty byte array. */
        @Override
        public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
            return new byte[0];
        }

        /** Not used by the test mechanism; always returns an empty byte array. */
        @Override
        public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
            return new byte[0];
        }

        /** Not used by the test mechanism; always returns {@code null}. */
        @Override
        public Object getNegotiatedProperty(String propName) {
            return null;
        }

        /** No-op; the test server holds no resources that need releasing. */
        @Override
        public void dispose() throws SaslException {
        }

        /**
         * Controls whether {@link #evaluateResponse} should throw a {@link SaslException}.
         *
         * @param throwError {@code true} to simulate an authentication failure.
         */
        public void setThrowError(boolean throwError) {
            this.throwError = throwError;
        }

        /**
         * Sets the number of {@link #evaluateResponse} calls that must succeed before
         * {@link #isComplete()} returns {@code true}.
         *
         * @param steps the desired number of steps; must be {@code >= 1} for at least one
         *              successful round-trip.
         */
        public void setSteps(long steps) {
            this.steps = steps;
        }
    }

    /**
     * A {@link SaslServerFactory} that vends {@link TestSaslServer} instances for the
     * {@code TEST-MECHANISM} mechanism. The active server instance is stored in a
     * {@link ThreadLocal} so that parallel test runs do not interfere with each other.
     */
    public static class TestSaslServerFactory implements SaslServerFactory {
        private static ThreadLocal<TestSaslServer> saslServer = new ThreadLocal<>();

        /** Required no-arg constructor for JCA provider instantiation. */
        public TestSaslServerFactory() {
            // Default constructor required for factory instantiation
        }

        /**
         * Stores {@code server} as the instance to be returned by
         * {@link #createSaslServer} on the current thread.
         *
         * @param server the server instance to register.
         */
        private static void setSaslServer(TestSaslServer server) {
            saslServer.set(server);
        }

        /**
         * Removes the server instance associated with the current thread, allowing it to
         * be garbage-collected.
         */
        private static void clearSaslServer() {
            saslServer.remove();
        }

        /**
         * Returns the {@link TestSaslServer} previously registered via
         * {@link #setSaslServer} for the {@code TEST-MECHANISM} mechanism, or {@code null}
         * for any other mechanism name.
         */
        @Override
        public SaslServer createSaslServer(String mechanism, String protocol,
                                           String serverName, Map<String, ?> props,
                                           CallbackHandler cbh) throws SaslException {
            if ("TEST-MECHANISM".equals(mechanism)) {
                return saslServer.get();
            }
            return null;
        }

        /** Returns the single mechanism name advertised by this factory: {@code TEST-MECHANISM}. */
        @Override
        public String[] getMechanismNames(Map<String, ?> props) {
            return new String[]{"TEST-MECHANISM"};
        }
    }

    /**
     * Creates a {@link TestSaslServer} for the given session, registers it with the JCA
     * security framework under the name {@code "Test Provider"}, and returns it so that
     * tests can configure its behaviour.
     *
     * <p>This method is idempotent with respect to provider registration: if the provider
     * is already present (e.g. from a previous test run in the same JVM), it is not
     * registered a second time.</p>
     *
     * <p>Call {@link #unregisterTestMechanism()} in an {@code @AfterEach} method to clean
     * up after each test.</p>
     *
     * @param clientSession the session to associate with the new server instance.
     * @return the newly created {@link TestSaslServer}.
     */
    public static TestSaslServer registerTestMechanism(LocalSession clientSession) {
        TestSaslServer testSaslServer = new TestSaslServer(clientSession);

        // Set the server instance before registering the provider
        TestSaslServerFactory.setSaslServer(testSaslServer);

        final String providerName = "Openfire-TestSaslMechanism";
        if (Security.getProvider(providerName) == null) {
            // Register the provider if not already registered
            Security.addProvider(new Provider(providerName, "1.0", providerName) {{
                put("SaslServerFactory.TEST-MECHANISM", TestSaslServerFactory.class.getName());
            }});
        }

        return testSaslServer;
    }

    /**
     * Removes the thread-local server instance and unregisters the {@code "Test Provider"}
     * JCA provider. Should be called from an {@code @AfterEach} method to ensure a clean
     * state for subsequent tests.
     */
    public static void unregisterTestMechanism() {
        TestSaslServerFactory.clearSaslServer();
        Security.removeProvider("Openfire-TestSaslMechanism");
    }
}
