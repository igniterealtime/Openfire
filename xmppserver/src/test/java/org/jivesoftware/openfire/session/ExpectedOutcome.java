/*
 * Copyright (C) 2023 Ignite Realtime Foundation. All rights reserved.
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

import static org.jivesoftware.openfire.session.ExpectedOutcome.ConnectionState.*;

/**
 * Given XMPP federation, this class defines the expected result of one domain trying to establish an authenticated
 * connection to another domain.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6120>Extensible Messaging and Presence Protocol (XMPP): Core</a>
 * @see <a href="https://xmpp.org/extensions/xep-0220.html">XEP-0220: Server Dialback</a>
 * @see <a href="https://xmpp.org/extensions/xep-0178.xml">XEP-0178: Best Practices for Use of SASL EXTERNAL with Certificates</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7590">Use of Transport Layer Security (TLS) in the Extensible Messaging and Presence Protocol (XMPP)</a>
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2611">OF-2611: Improve automated tests for S2S functionality</a>
 * @see <a href="https://igniterealtime.atlassian.net/browse/OF-2591">OF-2591: S2S Outbound should allow encryption if Dialback used for authentication</a>
 */
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

    /**
     * Given the configuration of the initiating and receiving server, returns the expected outcome of an outbound
     * server-to-server connection attempt.
     *
     * @param initiatingServer Configuration of the local server
     * @param receivingServer Configuration of the remote server
     * @return the expected outcome
     */
    public static ExpectedOutcome generateExpectedOutcome(final ServerSettings initiatingServer, final ServerSettings receivingServer) {
        final ExpectedOutcome expectedOutcome = new ExpectedOutcome();

        switch (initiatingServer.encryptionPolicy) {
            case disabled: // <-- Initiating server's encryption policy.
                switch (receivingServer.encryptionPolicy) {
                    case disabled: // Intended fall-through: if one peer disables TLS, it won't be used in any circumstances.
                    case optional:
                        // The certificate status of both peers is irrelevant, as TLS won't happen.
                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                            expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK_AUTH, "although TLS is not available (so it cannot be used for encryption or authentication), Dialback is available, which allows the Initiating Entity to be authenticated by the Receiving Entity.");
                        } else {
                            expectedOutcome.set(NO_CONNECTION, "TLS and Dialback aren't available, making it impossible for the Initiating Entity to be authenticated by the Receiving Entity.");
                        }
                        break;
                    case required:
                        expectedOutcome.set(NO_CONNECTION, "one peer requires encryption while the other disables encryption. This cannot work.");
                        break;
                }
                break;

            case optional: // <-- Initiating server's encryption policy.
                switch (receivingServer.encryptionPolicy) {
                    case disabled:
                        // The certificate status of both peers is irrelevant, as TLS won't happen.
                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                            expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK_AUTH, "TLS is not available, so it cannot be used for encryption or authentication. Dialback is available, which allows the Initiating Entity to be authenticated by the Receiving Entity.");
                        } else {
                            expectedOutcome.set(NO_CONNECTION, "TLS and Dialback aren't available, making it impossible for the Initiating Entity to be authenticated by the Receiving Entity.");
                        }
                        break;
                    case optional:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                    // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                    expectedOutcome.set(NON_ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity does not provides a TLS certificate. As ANON cypher suites are expected to be unavailable, Initiating Entity cannot negotiate TLS, so that cannot be used for encryption or authentication. Dialback is available, so authentication can occur.");
                                } else {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity does not provides a TLS certificate, Initiating Entity cannot negotiate TLS. With TLS and Dialback unavailable, authentication cannot occur (even if usage of an ANON cypher suite would make TLS-for-encryption possible)");
                                }
                                break;
                            case INVALID:
                                if (initiatingServer.strictCertificateValidation) {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate, which should cause Initiating Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                    expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Initiating Entity may choose to ignore Receiving Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                } else {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                }
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity, but Dialback is available for that purpose.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case INVALID:
                                        if (receivingServer.strictCertificateValidation) {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                        } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity may choose to ignore Initiating Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        // valid certs all around. Dialback is not needed.
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "TLS is usable for both encryption and authentication.");
                                        break;
                                }
                                break;
                        }
                        break;
                    case required:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity requires encryption, but it does not provide a TLS certificate. As ANON cypher suites are expected to be unavailable, the Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                break;
                            case INVALID:
                                if (initiatingServer.strictCertificateValidation) {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate, which should cause Initiating Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                    expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Initiating Entity may choose to ignore Receiving Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                } else {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                }
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity, but Dialback is available for that purpose.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case INVALID:
                                        if (receivingServer.strictCertificateValidation) {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                        } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity may choose to ignore Initiating Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        // valid certs all around. Dialback is not needed.
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "TLS is usable for both encryption and authentication.");
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;

            case required: // <-- Initiating server's encryption policy.
                switch (receivingServer.encryptionPolicy) {
                    case disabled:
                        expectedOutcome.set(NO_CONNECTION, "one peer requires encryption, the other disables encryption. This cannot work.");
                        break;
                    case optional:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity does not provide a TLS certificate. As ANON cypher suites are expected to be unavailable, the Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                break;
                            case INVALID:
                                if (initiatingServer.strictCertificateValidation) {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate, which should cause Initiating Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                    expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Initiating Entity may choose to ignore Receiving Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                } else {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                }
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity, but Dialback is available for that purpose.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides a valid TLS certificate allowing Initiating Entity to be able to establish TLS for encryption. Initiating Entity but does not provide a certificate itself, so SASL EXTERNAL is not available for Receiving Entity to authenticate Initiating Entity. As Dialback is also not available, authentication cannot occur.");
                                        }
                                        break;
                                    case INVALID:
                                        if (receivingServer.strictCertificateValidation) {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                        } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity may choose to ignore Initiating Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        // valid certs all around. Dialback is not needed.
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "TLS is usable for both encryption and authentication.");
                                        break;
                                }
                                break;
                        }
                        break;
                    case required:
                        switch (receivingServer.certificateState) {
                            case MISSING:
                                expectedOutcome.set(NO_CONNECTION, "Receiving Entity does not provide a TLS certificate. As ANON cypher suites are expected to be unavailable, the Initiating Entity cannot negotiate TLS and therefor the required encrypted connection cannot be established.");
                                // TODO: should we take into account a manual configuration of an ANON cypher suite, so that encryption-without-authentication can occur via TLS, followed by a Dialback-based authentication?
                                break;
                            case INVALID:
                                if (initiatingServer.strictCertificateValidation) {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate, which should cause Initiating Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                    expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Initiating Entity may choose to ignore Receiving Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                } else {
                                    expectedOutcome.set(NO_CONNECTION, "Receiving Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                }
                                break;
                            case VALID:
                                switch (initiatingServer.certificateState) {
                                    case MISSING:
                                        if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Initiating Entity can negotiate encryption, but does not provide a certificate. SASL EXTERNAL cannot be used, but Dialback is available, so authentication can occur.");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity can negotiate encryption, but does not provide a certificate. As Dialback is not available, authentication cannot occur. Connection cannot be established.");
                                        }
                                        break;
                                    case INVALID:
                                        if (receivingServer.strictCertificateValidation) {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate, which should cause Receiving Entity to abort TLS (as per RFC 6120 section 13.7.2).");
                                        } else if (initiatingServer.dialbackSupported && receivingServer.dialbackSupported) {
                                            expectedOutcome.set(ENCRYPTED_WITH_DIALBACK_AUTH, "Receiving Entity may choose to ignore Initiating Entities invalid certificate (for encryption purposes only) and choose to authenticate with Server Dialback (per RFC 7590 Section 3.4)");
                                        } else {
                                            expectedOutcome.set(NO_CONNECTION, "Initiating Entity provides an invalid TLS certificate. As Server Dialback is not available, authentication cannot occur.");
                                        }
                                        break;
                                    case VALID:
                                        expectedOutcome.set(ENCRYPTED_WITH_SASLEXTERNAL_AUTH, "Initiating Entity can establish encryption and authenticate using TLS.");
                                        break;
                                }
                                break;
                        }
                        break;
                }
                break;
        }

        // FIXME: add support for the DirectTLS TLS policy.
        return expectedOutcome;
    }
}
