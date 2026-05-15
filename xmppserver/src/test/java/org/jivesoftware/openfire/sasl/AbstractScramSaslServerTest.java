/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
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

import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abstract base class providing a reusable suite of SCRAM SASL server tests.
 *
 * Subclasses supply algorithm-specific fixtures (credentials, nonce, expected proof length) and a factory method for
 * the concrete server under test. All protocol-level and SASL contract tests are defined here so that every SCRAM
 * variant (SHA-1, SHA-256, etc.) can share the same coverage without duplication.
 */
public abstract class AbstractScramSaslServerTest
{
    /**
     * Creates a new SCRAM SASL server instance for the algorithm under test.
     *
     * @param isPlusMechanism true to create the channel-binding (-PLUS) variant, false otherwise
     */
    protected abstract ScramSaslServer newServer(boolean isPlusMechanism);

    /**
     * Configures all authentication-data mocks or stubs with the canonical test fixture values
     * (salt, iterations, password, stored key, server key) required by the algorithm under test.
     */
    protected abstract void setupCanonicalAuthData();

    /**
     * Computes a valid client proof for the given SCRAM exchange state using the algorithm under test.
     *
     * @param initialMessage      the raw bytes of the initial client message
     * @param firstServerResponse the server's first response string
     * @param firstExchangeResult the parsed result of the first server response
     * @return the Base64-encoded client proof
     * @throws Exception if key derivation or HMAC computation fails
     */
    protected abstract String createValidProof(
        byte[] initialMessage,
        String firstServerResponse,
        FirstExchangeResult firstExchangeResult
    ) throws Exception;

    /**
     * Returns the username used in test fixtures for the algorithm under test.
     *
     * @return the test username
     */
    protected abstract String username();

    /**
     * Returns the client nonce used in test fixtures for the algorithm under test.
     *
     * @return the test client nonce
     */
    protected abstract String clientNonce();

    /**
     * Returns the expected byte length of the client proof produced by the algorithm under test.
     * For example, 20 for SHA-1 and 32 for SHA-256.
     *
     * @return expected proof length in bytes
     */
    protected abstract int expectedProofLengthBytes();

    /**
     * Verifies GS2 header extraction when an authzid is present.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void extractsGs2Header_withAuthzId() throws Exception
    {
        // Setup test fixture
        final byte[] input = ("p=tls,,n=" + username() + ",r=abc123,rest").getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("p=tls,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Verifies GS2 header extraction when no authzid is present.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void extractsGs2Header_withoutAuthzId() throws Exception
    {
        // Setup test fixture
        final byte[] input = ("n,,n=" + username() + ",r=abc123,rest").getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("n,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Ensures the GS2 header includes a trailing comma as specified.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void includesTrailingComma_exactlyAsSpecified() throws Exception
    {
        // Setup test fixture
        final byte[] input = ("p=tls,,n=" + username() + ",r=abc123").getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals(',', result[result.length - 1], "GS2 header must end with a comma");
    }

    /**
     * Ensures GS2 header extraction preserves the exact bytes, with no re-encoding.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void preservesExactBytes_noReEncoding() throws Exception
    {
        // Setup test fixture
        final byte[] input = ("p=tls,,n=" + username() + ",r=abc123").getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        byte[] expected = Arrays.copyOfRange(input, 0, result.length);
        assertArrayEquals(expected, result, "Must be exact prefix of original bytes");
    }

    /**
     * Verifies that an exception is thrown when the GS2 header does not contain a second comma.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void throwsException_whenNoSecondComma()
    {
        // Setup test fixture
        final byte[] input = ("p=tls,n=" + username()).getBytes(StandardCharsets.UTF_8);

        // Execute System under test & Verify result
        assertThrows(SaslException.class, () ->
            ScramSaslServer.extractRawGS2Header(input));
    }

    /**
     * Verifies that the minimal valid GS2 header is handled correctly.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void handlesMinimalValidGs2Header() throws Exception
    {
        // Setup test fixture
        final byte[] input = "n,,rest".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("n,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Ensures GS2 header extraction stops at the second comma only.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void stopsAtSecondComma_only() throws Exception
    {
        // Setup test fixture
        final byte[] input = ("p=tls,,n=" + username() + ",r=abc,extra,stuff").getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("p=tls,,", new String(result, StandardCharsets.UTF_8));
    }

    /**
     * Verifies that isComplete() returns false before any exchange has taken place.
     *
     * Mechanism state test.
     */
    @Test
    void isComplete_returnsFalse_initially()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);

        // Execute system under test
        final boolean complete = server.isComplete();

        // Verify result
        assertFalse(complete, "isComplete() should return false before any exchange has taken place");
    }

    /**
     * Verifies that isComplete() returns false after only the first exchange round.
     *
     * Mechanism state test.
     */
    @Test
    void isComplete_returnsFalse_afterFirstExchangeOnly() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        doFirstExchange(server);

        // Execute system under test
        final boolean complete = server.isComplete();

        // Verify result
        assertFalse(complete, "isComplete() should return false after only the first exchange");
    }

    /**
     * Verifies that a non-empty response submitted after a completed exchange is rejected.
     *
     * Mechanism state test.
     */
    @Test
    void rejectsNonEmptyResponse_afterExchangeComplete() throws Exception
    {
        // Setup test fixture
        final ScramSaslServer server = completeSuccessfulExchange();

        // Execute system under test
        assertTrue(server.isComplete(), "Server should be complete after successful exchange");

        // Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse("unexpected".getBytes(StandardCharsets.UTF_8)),
            "Non-empty response after exchange complete should be rejected");
    }

    /**
     * Verifies that an empty response submitted after a completed exchange is tolerated
     * (some SASL frameworks send an empty final acknowledgement).
     *
     * Mechanism state test.
     */
    @Test
    void acceptsEmptyResponse_afterExchangeComplete() throws Exception
    {
        // Setup test fixture
        final ScramSaslServer server = completeSuccessfulExchange();

        // Execute system under test & Verify result
        assertDoesNotThrow(() -> server.evaluateResponse(new byte[0]),
            "Empty response after exchange complete should be tolerated");
    }

    /**
     * Verifies that dispose() resets the server to its initial state, making isComplete() return false
     * and preventing getAuthorizationID() from returning stale data.
     *
     * Mechanism state test.
     */
    @Test
    void dispose_resetsStateAndClearsSensitiveFields() throws Exception
    {
        // Setup test fixture
        final ScramSaslServer server = completeSuccessfulExchange();
        assertTrue(server.isComplete(), "Server should be complete after successful exchange");

        // Execute system under test
        server.dispose();

        // Verify result
        assertFalse(server.isComplete(), "Server should not be complete after dispose()");
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "getAuthorizationID() should throw after dispose()");
    }

    /**
     * Verifies that getAuthorizationID() throws before the exchange completes.
     *
     * SASL contract test.
     */
    @Test
    void getAuthorizationID_throwsIllegalStateException_beforeCompletion()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, server::getAuthorizationID,
            "getAuthorizationID() before completion should throw IllegalStateException");
    }

    /**
     * Verifies that getAuthorizationID() returns the authenticated username after a successful exchange.
     *
     * SASL contract test.
     */
    @Test
    void getAuthorizationID_returnsUsername_afterCompletion() throws Exception
    {
        // Setup test fixture
        final ScramSaslServer server = completeSuccessfulExchange();

        // Execute system under test
        final String authzId = server.getAuthorizationID();

        // Verify result
        assertEquals(username(), authzId, "getAuthorizationID() should return the authenticated username after completion");
    }

    /**
     * Verifies that getNegotiatedProperty() throws before the exchange completes.
     *
     * SASL contract test.
     */
    @Test
    void getNegotiatedProperty_throwsIllegalStateException_beforeCompletion()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, () -> server.getNegotiatedProperty(Sasl.QOP),
            "getNegotiatedProperty() before completion should throw IllegalStateException");
    }

    /**
     * Verifies that getNegotiatedProperty() reports "auth" for QOP after a successful exchange,
     * as SCRAM provides authentication only (no integrity or confidentiality layer).
     *
     * SASL contract test.
     */
    @Test
    void getNegotiatedProperty_returnsAuth_forQOP_afterCompletion() throws Exception
    {
        // Setup test fixture
        final ScramSaslServer server = completeSuccessfulExchange();

        // Execute system under test
        final Object qop = server.getNegotiatedProperty(Sasl.QOP);

        // Verify result
        assertEquals("auth", qop, "getNegotiatedProperty(Sasl.QOP) should return 'auth' after completion");
    }

    /**
     * Verifies that getNegotiatedProperty() returns null for unknown properties after completion.
     *
     * SASL contract test.
     */
    @Test
    void getNegotiatedProperty_returnsNull_forUnknownProperty_afterCompletion() throws Exception
    {
        // Setup test fixture
        final ScramSaslServer server = completeSuccessfulExchange();

        // Execute system under test
        final Object unknown = server.getNegotiatedProperty("unknown.property");

        // Verify result
        assertNull(unknown, "getNegotiatedProperty() should return null for unknown properties after completion");
    }

    /**
     * Verifies that unwrap() always throws, as SCRAM has no security layer.
     *
     * SASL contract test.
     */
    @Test
    void unwrap_throwsIllegalStateException_always()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, () -> server.unwrap(new byte[]{1, 2, 3}, 0, 3),
            "unwrap() should always throw IllegalStateException as SCRAM has no security layer");
    }

    /**
     * Verifies that wrap() always throws, as SCRAM has no security layer.
     *
     * SASL contract test.
     */
    @Test
    void wrap_throwsIllegalStateException_always()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);

        // Execute system under test & Verify result
        assertThrows(IllegalStateException.class, () -> server.wrap(new byte[]{1, 2, 3}, 0, 3),
            "wrap() should always throw IllegalStateException as SCRAM has no security layer");
    }

    /**
     * Verifies that a completely malformed first client message is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_invalidFormat()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = "not-a-valid-scram-message".getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "Malformed first client message should be rejected with SaslException");
    }

    /**
     * Verifies that a first client message containing an empty username is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_emptyUsername()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", "", clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "First client message with empty username should be rejected");
    }

    /**
     * Verifies that a first client message containing an empty client nonce is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_emptyClientNonce()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", username(), "");

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "First client message with empty client nonce should be rejected");
    }

    /**
     * Verifies that a 'p' GS2 channel-binding flag is rejected when using the non-PLUS mechanism.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_channelBindingRequestedOnNonPlusMechanism()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("p=tls-unique,,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "Channel binding requested on non-PLUS mechanism should be rejected");
    }

    /**
     * Verifies RFC 5802 §6: a 'y' GS2 flag MUST be rejected when the server advertises a -PLUS mechanism,
     * because this is a signal that a downgrade attack may be in progress.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_downgradeAttackDetected()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("y,,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "Downgrade attack (y-flag) should be rejected when -PLUS is advertised");
    }

    /**
     * Verifies that a completely malformed final client message is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_invalidFormat() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        doFirstExchange(server);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse("not-a-valid-final-message".getBytes(StandardCharsets.UTF_8)),
            "Malformed final client message should be rejected with SaslException");
    }

    /**
     * Verifies that a final client message with an empty proof attribute is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_emptyProof() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final byte[] clientFinalMessage = createClientFinalMessage("biws", firstExchangeResult.serverNonce, "");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "Final client message with empty proof should be rejected");
    }

    /**
     * Verifies that a final client message with an empty channel binding attribute is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_emptyChannelBinding() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final byte[] clientFinalMessage = createClientFinalMessage("", firstExchangeResult.serverNonce, "dGVzdA==");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "Final client message with empty channel binding should be rejected");
    }

    /**
     * Verifies that a final client message containing an incorrect nonce is rejected.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_incorrectNonce() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        doFirstExchange(server); // returned nonce is not used in this test, but the first exchange needs to happen to get the engine in the correct state.
        final byte[] clientFinalMessage = createClientFinalMessage("biws", "completely-wrong-nonce", "dGVzdA==");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "Final client message with incorrect nonce should be rejected");
    }

    /**
     * Verifies that a final client message carrying an incorrect channel binding value is rejected
     * for a non-PLUS exchange. For non-PLUS, c= must decode to exactly the GS2 header ("n,,"),
     * whose base64 encoding is "biws".
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_incorrectChannelBindingValue_nonPlusMechanism() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String wrongBinding = Base64.getEncoder().encodeToString("p=tls-unique,,".getBytes(StandardCharsets.UTF_8));
        final byte[] clientFinalMessage = createClientFinalMessage(wrongBinding, firstExchangeResult.serverNonce, "dGVzdA==");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "Final client message with incorrect channel binding value should be rejected");
    }

    /**
     * Verifies that a proof whose decoded length differs from the expected HMAC output length
     * is rejected with a clean SaslException rather than an ArrayIndexOutOfBoundsException.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_proofWithWrongLength() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String shortProof = Base64.getEncoder().encodeToString(new byte[expectedProofLengthBytes() / 2]); // half the expected bytes
        final byte[] clientFinalMessage = createClientFinalMessage("biws", firstExchangeResult.serverNonce, shortProof);

        // Execute system under test
        final SaslException ex = assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "Final client message with proof of wrong length should be rejected");

        // Verify result
        assertTrue(ex.getMessage().contains("proof"), "Exception should mention the proof");
    }

    /**
     * Verifies that a correctly structured final message carrying a wrong (but correctly sized) proof
     * results in an authentication failure rather than a successful login.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_incorrectProof() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String wrongProof = Base64.getEncoder().encodeToString(new byte[expectedProofLengthBytes()]); // all zero bytes
        final byte[] clientFinalMessage = createClientFinalMessage("biws", firstExchangeResult.serverNonce, wrongProof);

        // Execute system under test & Verify result
        assertThrows(SaslException.class,() -> server.evaluateResponse(clientFinalMessage),
            "Final client message with incorrect proof should be rejected");
    }

    /**
     * Drives a complete successful SCRAM exchange and returns the completed server instance.
     */
    protected ScramSaslServer completeSuccessfulExchange() throws Exception
    {
        setupCanonicalAuthData();

        final ScramSaslServer server = newServer(false);
        final byte[] initialMessage = createClientInitialMessage("n,,", username(), clientNonce());
        final String firstServerResponse = new String(server.evaluateResponse(initialMessage), StandardCharsets.UTF_8);

        final FirstExchangeResult firstExchangeResult = FirstExchangeResult.fromFirstServerResponse(firstServerResponse);

        final String proof = createValidProof(initialMessage, firstServerResponse, firstExchangeResult);

        final String clientFinalBare = "c=biws,r=" + firstExchangeResult.serverNonce;
        final String clientFinalMessage = clientFinalBare + ",p=" + proof;
        server.evaluateResponse(clientFinalMessage.getBytes(StandardCharsets.UTF_8));
        return server;
    }

    /**
     * Performs the first exchange round and returns the composite server nonce.
     */
    protected final FirstExchangeResult doFirstExchange(final ScramSaslServer server) throws SaslException
    {
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", username(), clientNonce());
        final String firstServerResponse = new String(server.evaluateResponse(clientInitialMessage), StandardCharsets.UTF_8);
        return FirstExchangeResult.fromFirstServerResponse(firstServerResponse);
    }

    /**
     * Constructs a SCRAM client-initial-message as raw UTF-8 bytes.
     *
     * @param gs2Header the GS2 header prefix (e.g. "n,," or "p=tls-unique,,")
     * @param username  the authentication identity to include in the message
     * @param nonce     the client-generated nonce
     * @return the encoded client initial message
     */
    @Nonnull
    protected final byte[] createClientInitialMessage(@Nonnull final String gs2Header, @Nonnull final String username, @Nonnull final String nonce)
    {
        return (gs2Header + "n=" + username + ",r=" + nonce).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a SCRAM client-final-message as raw UTF-8 bytes.
     *
     * @param channelBinding the Base64-encoded channel-binding data for the "c=" attribute
     * @param serverNonce    the combined client-and-server nonce for the "r=" attribute
     * @param proof          the Base64-encoded client proof for the "p=" attribute
     * @return the encoded client final message
     */
    @Nonnull
    protected final byte[] createClientFinalMessage(@Nonnull final String channelBinding, @Nonnull final String serverNonce, @Nonnull final String proof)
    {
        return ("c=" + channelBinding + ",r=" + serverNonce + ",p=" + proof).getBytes(StandardCharsets.UTF_8);
    }
}
