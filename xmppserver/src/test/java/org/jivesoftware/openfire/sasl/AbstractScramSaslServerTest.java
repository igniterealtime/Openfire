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

import org.jivesoftware.openfire.auth.AuthFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.annotation.Nonnull;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

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
     * Static mock for {@link AuthFactory}, shared by all SCRAM algorithm variants. Individual test methods configure
     * it via {@link #setupCanonicalAuthData()}, which subclasses implement with their own algorithm-specific fixtures.
     */
    protected MockedStatic<AuthFactory> authFactory;

    @BeforeEach
    void setupStaticMock()
    {
        authFactory = Mockito.mockStatic(AuthFactory.class);
    }

    @AfterEach
    void teardownStaticMock()
    {
        if (authFactory != null) {
            authFactory.close();
        }
    }

    /**
     * Creates a new SCRAM SASL server instance for the algorithm under test.
     *
     * @param isPlusMechanism true to create the channel-binding (-PLUS) variant, false otherwise
     */
    protected abstract ScramSaslServer newServer(boolean isPlusMechanism);

    /**
     * Creates a new SCRAM SASL server instance for the algorithm under test.
     *
     * @param isPlusMechanism true to create the channel-binding (-PLUS) variant, false otherwise
     * @param advertisedMechanismNames The names of SASL mechanisms that are advertised to the peer.
     */
    protected abstract ScramSaslServer newServer(boolean isPlusMechanism, Set<String> advertisedMechanismNames);

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
     * Verifies that a GS2 header carrying a value on the "n" flag ("n=...") is rejected. Per RFC 5802, only the
     * "p" flag may carry a channel-binding name; "n" and "y" are bare tokens.
     *
     * GS2 parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFirstMessage_valueOnNonPlusFlag_n()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n=unexpected-value,,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage), "A GS2 header with a value on the 'n' flag must be rejected");
    }

    /**
     * Verifies that a GS2 header carrying a value on the "y" flag ("y=...") is rejected, for the same reason as
     * the "n" flag above.
     *
     * GS2 parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFirstMessage_valueOnNonPlusFlag_y()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("y=unexpected-value,,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage), "A GS2 header with a value on the 'y' flag must be rejected");
    }

    /**
     * Verifies that a GS2 header with an empty authzid ("a=" followed immediately by a comma) is rejected. Per
     * RFC 5802, authzid is either entirely absent or a non-empty saslname; "a=" with nothing following is neither.
     *
     * GS2 parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFirstMessage_emptyAuthzidAttribute()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,a=,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage), "A GS2 header with an empty authzid attribute must be rejected");
    }

    /**
     * Verifies that a "p" flag with an empty channel-binding name ("p=" followed immediately by a comma) is
     * rejected, for the same reason as the empty-authzid case above: per RFC 5802, cb-name is a non-empty value.
     *
     * GS2 parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFirstMessage_emptyChannelBindingName()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("p=,,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "A GS2 header with an empty channel-binding name must be rejected");
    }

    /**
     * Verifies that a channel-binding name containing a character outside the cb-name grammar ("@" is not
     * ALPHA / DIGIT / "." / "-") is rejected before it can reach channel-binding provider lookup.
     *
     * GS2 parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFirstMessage_channelBindingNameWithInvalidCharacter()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("p=tls@unique,,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "A channel-binding name containing characters outside the cb-name grammar must be rejected");
    }

    /**
     * Verifies that a client nonce containing a control character is rejected. RFC 5802's "printable" grammar
     * (%x21-2B / %x2D-7E) excludes control characters, space, and DEL from the nonce.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFirstMessage_nonceContainsControlCharacter()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", username(), "abc\u0000def");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "A client nonce containing a control character must be rejected");
    }

    /**
     * Verifies that a channel-binding value that is not valid base64 is rejected, rather than reaching the base64
     * decoder as unvalidated input.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFinalMessage_channelBindingNotValidBase64() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final byte[] clientFinalMessage = createClientFinalMessage("not valid base64!!", firstExchangeResult.serverNonce, "dGVzdA==");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "A channel-binding value that is not valid base64 must be rejected");
    }

    /**
     * Verifies that a proof value that is not valid base64 is rejected, for the same reason as the channel-binding
     * case above.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3355">OF-3355: Restrict SCRAM protocol fields to their RFC 5802-defined character sets</a>
     */
    @Test
    void rejectsFinalMessage_proofNotValidBase64() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final byte[] clientFinalMessage = createClientFinalMessage("biws", firstExchangeResult.serverNonce, "not valid base64!!");

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "A proof value that is not valid base64 must be rejected");
    }

    /**
     * Verifies RFC 5802 §5: the reserved "m" attribute must be rejected even when it appears among the extensions
     * following the nonce, not only in the leading reserved-mext position. A client cannot bypass mandatory-
     * extension rejection simply by relocating the attribute.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectsFirstMessage_mandatoryExtensionRequested_postNoncePosition()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = ("n,,n=" + username() + ",r=" + clientNonce() + ",m=unsupported").getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        final SaslException ex = assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "A client-first-message with the reserved 'm' attribute after the nonce must be rejected, the same as in the leading position");
        assertTrue(ex.getMessage().contains("mandatory extension"), "Exception should mention the mandatory extension. Got: " + ex.getMessage());
    }

    /**
     * Verifies that a malformed extension in a client-final-message (one that is not a well-formed, non-empty
     * attr-val pair) is rejected, rather than being silently tolerated by an over-permissive extension match.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_malformedExtension() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String proof = Base64.getEncoder().encodeToString(new byte[expectedProofLengthBytes()]);
        final byte[] clientFinalMessage = ("c=biws,r=" + firstExchangeResult.serverNonce + ",notavalidextension,p=" + proof)
            .getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "A client-final-message with a malformed (non-attr-val) extension must be rejected");
    }

    /**
     * Verifies RFC 5802 §5.1: the reserved "m" attribute must cause authentication failure wherever it appears,
     * not only in client-first-message. A client-final-message containing "m=unsupported" among its optional
     * extensions must be rejected for the mandatory-extension reason, even if it carries an otherwise-valid proof.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectsFinalMessage_mandatoryExtensionRequested() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String proof = Base64.getEncoder().encodeToString(new byte[expectedProofLengthBytes()]);
        final byte[] clientFinalMessage = ("c=biws,r=" + firstExchangeResult.serverNonce + ",m=unsupported,p=" + proof).getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        final SaslException ex = assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "A client-final-message requesting an unsupported mandatory extension must be rejected, regardless of the proof");
        assertTrue(ex.getMessage().contains("mandatory extension"), "Exception should mention the mandatory extension, not merely 'proof failed'. Got: " + ex.getMessage());
    }

    /**
     * Verifies that a saslname containing an escaped comma ("=2C") is decoded correctly.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesEscapedComma() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("smith,doe", ScramSaslServer.decodeSaslname("smith=2Cdoe"));
    }

    /**
     * Verifies that a saslname containing an escaped equals sign ("=3D") is decoded correctly.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesEscapedEqualsSign() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("user=admin", ScramSaslServer.decodeSaslname("user=3Dadmin"));
    }

    /**
     * Verifies that a saslname containing multiple, different escape sequences decodes each one correctly.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesMultipleEscapesInSingleValue() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("a,b=c", ScramSaslServer.decodeSaslname("a=2Cb=3Dc"));
    }

    /**
     * Verifies that a saslname with no escape sequences at all is returned unchanged.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void returnsUnchanged_whenNoEscapeSequencesPresent() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("plainuser", ScramSaslServer.decodeSaslname("plainuser"));
    }

    /**
     * Verifies that an empty saslname decodes to an empty string.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void returnsEmptyString_whenInputIsEmpty() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("", ScramSaslServer.decodeSaslname(""));
    }

    /**
     * Verifies that an escape sequence at the very start of a saslname is decoded correctly.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesEscapeAtStartOfValue() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals(",leading", ScramSaslServer.decodeSaslname("=2Cleading"));
    }

    /**
     * Verifies that an escape sequence at the very end of a saslname is decoded correctly, exercising the
     * boundary condition of the loop's end-of-string check.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesEscapeAtEndOfValue() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("trailing,", ScramSaslServer.decodeSaslname("trailing=2C"));
    }

    /**
     * Verifies that two escape sequences immediately adjacent to each other (no literal characters between them)
     * are both decoded correctly, confirming the scan resumes at the right index after each escape.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesConsecutiveEscapes() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals(",=", ScramSaslServer.decodeSaslname("=2C=3D"));
    }

    /**
     * Verifies that a trailing "=" with no following characters at all is rejected as a malformed escape.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void throwsException_whenEscapeIsIncomplete_atEndOfString()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeSaslname("abc="));
    }

    /**
     * Verifies that an "=" followed by only one further character (not the required two) is rejected as a
     * malformed escape.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void throwsException_whenEscapeIsIncomplete_withOneTrailingCharacter()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeSaslname("abc=2"));
    }

    /**
     * Verifies that an "=" followed by two characters that are neither "2C" nor "3D" is rejected, and that the
     * exception identifies the offending sequence to aid diagnosis.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void throwsException_whenEscapeSequenceIsUnrecognized()
    {
        // Execute system under test & Verify result
        final SaslException ex = assertThrows(SaslException.class, () -> ScramSaslServer.decodeSaslname("abc=XYdef"));
        assertTrue(ex.getMessage().contains("=XY"), "Exception should identify the offending escape sequence. Got: " + ex.getMessage());
    }

    /**
     * Verifies that lowercase hex digits in an escape sequence ("=2c" instead of "=2C") are rejected. RFC 5802
     * defines the escapes as the literal uppercase strings "=2C" and "=3D"; lowercase is not a valid alternative
     * spelling.
     *
     * Saslname decoding test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void throwsException_whenEscapeSequenceUsesLowercaseHexDigits()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeSaslname("abc=2cdef"));
    }

    /**
     * Verifies that decodeSaslname() rejects a NUL character directly, independent of the parsing regex. Since
     * this method is reachable without going through CLIENT_FIRST_MESSAGE_BARE or GS2_HEADER (it is
     * {@code @VisibleForTesting}), it must enforce the NUL exclusion itself rather than relying solely on the
     * caller to have pre-filtered the input.
     *
     * Saslname decoding test: completely algorithm-independent.
     */
    @Test
    void throwsException_whenSaslnameContainsNulCharacter()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeSaslname("user\u0000name"));
    }

    /**
     * Verifies that a saslname consisting only of a NUL character (no other content at all) is still rejected,
     * exercising the boundary case where the NUL check must fire before the "no escape sequences present" fast
     * path would otherwise return the string unchanged.
     *
     * Saslname decoding test: completely algorithm-independent.
     */
    @Test
    void throwsException_whenSaslnameIsOnlyNulCharacter()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeSaslname("\u0000"));
    }

    /**
     * Verifies that an empty extensions string (no extensions present) does not throw.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_doesNotThrow_whenNoExtensionsPresent()
    {
        // Execute system under test & Verify result
        assertDoesNotThrow(() -> ScramSaslServer.rejectReservedMandatoryExtension(""));
    }

    /**
     * Verifies that a single, non-reserved extension does not throw.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_doesNotThrow_forSingleNonReservedExtension()
    {
        // Execute system under test & Verify result
        assertDoesNotThrow(() -> ScramSaslServer.rejectReservedMandatoryExtension(",a=1"));
    }

    /**
     * Verifies that multiple non-reserved extensions, none of which is "m", do not throw.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_doesNotThrow_forMultipleNonReservedExtensions()
    {
        // Execute system under test & Verify result
        assertDoesNotThrow(() -> ScramSaslServer.rejectReservedMandatoryExtension(",a=1,b=2"));
    }

    /**
     * Verifies that a single reserved "m" extension is rejected.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_throws_forSingleReservedExtension()
    {
        // Execute system under test & Verify result
        final SaslException ex = assertThrows(SaslException.class,
            () -> ScramSaslServer.rejectReservedMandatoryExtension(",m=unsupported"));
        assertTrue(ex.getMessage().contains("m=unsupported"), "Exception should identify the offending extension. Got: " + ex.getMessage());
    }

    /**
     * Verifies that a reserved "m" extension appearing first among several extensions is still rejected.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_throws_whenReservedExtensionIsFirst()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",m=unsupported,a=1"));
    }

    /**
     * Verifies that a reserved "m" extension appearing after other, non-reserved extensions is still rejected --
     * confirming the scan doesn't stop after the first segment.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_throws_whenReservedExtensionIsNotFirst()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",a=1,m=unsupported"));
    }

    /**
     * Verifies that a reserved "m" extension sandwiched between two other extensions is still rejected.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_throws_whenReservedExtensionIsInTheMiddle()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",a=1,m=unsupported,b=2"));
    }

    /**
     * Verifies that an uppercase "M" attribute is NOT treated as the reserved extension. RFC 5802 defines
     * reserved-mext using the literal lowercase string "m="; this test documents that an uppercase "M=" is
     * currently treated as an ordinary, ignorable extension rather than the reserved one.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_doesNotThrow_forUppercaseM()
    {
        // Execute system under test & Verify result
        assertDoesNotThrow(() -> ScramSaslServer.rejectReservedMandatoryExtension(",M=uppercase"));
    }

    /**
     * Verifies that an empty extension segment (e.g. two consecutive commas) is rejected as malformed, rather than
     * throwing an unchecked exception when the implementation inspects its first character.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forEmptySegment()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",a=1,,b=2"));
    }

    /**
     * Verifies that a segment which is not a well-formed, single-letter attr-val pair (e.g. a multi-letter
     * attribute name) is rejected as malformed.
     *
     * Extension parsing test: completely algorithm-independent.
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forMultiLetterAttributeName()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",ab=1"));
    }

    /**
     * Verifies that rejectReservedMandatoryExtension() rejects a NUL character in an extension's value portion
     * directly, independent of the parsing regex, for the same direct-callable reachability reason as
     * decodeSaslname() above.
     *
     * Extension parsing test: completely algorithm-independent.
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forNulInValue()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",a=\u0000"));
    }

    /**
     * Verifies that an extension whose attribute name is not an ALPHA character (e.g. a digit) is rejected. The
     * attr-val grammar requires a single letter name; a purely length/position-based shape check could miss this.
     *
     * Extension parsing test: completely algorithm-independent.
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forNonAlphaAttributeName()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",1=value"));
    }

    /**
     * Verifies that a trailing empty segment (a raw extensions string ending in a comma) is rejected, rather than
     * being silently dropped by String.split(",")'s default trailing-empty-string behavior.
     *
     * Extension parsing test: completely algorithm-independent.
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forTrailingEmptySegment()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",a=value,"));
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
     * Verifies that an empty authzid (i.e. no authzid at all) is accepted, and that the exchange proceeds normally.
     * This is the common case: most clients do not supply an authzid, relying on the server to authorize the SASL
     * authentication identity itself.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3352">OF-3352: Reject or authorize a supplied GS2 authorization identity.</a>
     */
    @Test
    void acceptsFirstMessage_emptyAuthzid() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", username(), clientNonce());

        // Execute system under test
        final byte[] firstServerResponse = server.evaluateResponse(clientInitialMessage);

        // Verify result
        assertNotNull(firstServerResponse, "An empty authzid should be accepted and the exchange should proceed");
        assertTrue(new String(firstServerResponse, StandardCharsets.UTF_8).startsWith("r="), "The server should respond with a first server message when no authzid is supplied");
    }

    /**
     * Verifies that an authzid identical to the SASL authentication identity ('username') is accepted.
     *
     * This is not proxy authorization in any meaningful sense: the client is (redundantly) asking to be authorized
     * as itself, which {@link ScramSaslServer#getAuthorizationID()} already guarantees regardless of whether an
     * authzid was supplied. Rejecting this case would needlessly break clients that echo their own identity into
     * the GS2 header without gaining any security benefit.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3352">OF-3352: Reject or authorize a supplied GS2 authorization identity.</a>
     */
    @Test
    void acceptsFirstMessage_authzidEqualToUsername() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,a=" + username() + ",", username(), clientNonce());

        // Execute system under test
        final byte[] firstServerResponse = server.evaluateResponse(clientInitialMessage);

        // Verify result
        assertNotNull(firstServerResponse, "An authzid equal to the authentication identity is not proxy authorization and should be accepted");
        assertTrue(new String(firstServerResponse, StandardCharsets.UTF_8).startsWith("r="), "The server should respond with a first server message when the authzid matches the authentication identity");
    }

    /**
     * Verifies that a non-empty authzid that differs from the SASL authentication identity ('username') is
     * rejected.
     *
     * RFC 5802 requires the server to either authorize a supplied authzid or fail authentication if it cannot do
     * so. Openfire does not support SASL proxy authorization, so silently ignoring the requested identity (and
     * authenticating as 'username' regardless) would be a security-relevant deviation from the spec: it could let a
     * client believe it authenticated as one identity when the server granted a different one.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3352">OF-3352: Reject or authorize a supplied GS2 authorization identity.</a>
     */
    @Test
    void rejectsFirstMessage_authzidDifferentFromUsername()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,a=someotheruser,", username(), clientNonce());

        // Execute system under test & Verify result
        final SaslException ex = assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "A non-empty authzid that differs from the authentication identity must be rejected, since proxy authorization is not supported");
        assertTrue(ex.getMessage().toLowerCase().contains("proxy"), "Exception should mention proxy authorization. Got: " + ex.getMessage());
    }

    /**
     * Verifies RFC 5802 §5: the reserved "m=" attribute signals a mandatory extension. Since none are currently
     * defined/supported, its presence must cause the exchange to fail rather than be silently ignored.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3350">OF-3350: SCRAM server accepts unsupported mandatory extensions</a>
     */
    @Test
    void rejectsFirstMessage_mandatoryExtensionRequested()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = ("n,,m=unsupported-ext,n=" + username() + ",r=" + clientNonce())
            .getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        final SaslException ex = assertThrows(SaslException.class,
            () -> server.evaluateResponse(clientInitialMessage),
            "A client-first-message containing the reserved 'm=' mandatory extension must be rejected");
        assertTrue(ex.getMessage().contains("mandatory extension"), "Exception should mention the mandatory extension. Got: " + ex.getMessage());
    }

    /**
     * Verifies that a username containing a NUL character is rejected. RFC 5802's "value-safe-char" production
     * (which underlies "saslname") explicitly excludes NUL; a username value with an embedded NUL byte must not
     * reach credential lookup.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_usernameContainsNulCharacter()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", "user\u0000name", clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "A username containing a NUL character must be rejected");
    }

    /**
     * Verifies that an authzid containing a NUL character is rejected, for the same reason as the username case
     * above: authzid shares the "saslname" production and its "value-safe-char" character set.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void rejectsFirstMessage_authzidContainsNulCharacter()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,a=user\u0000name,", username(), clientNonce());

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "An authzid containing a NUL character must be rejected");
    }

    /**
     * Verifies that an extension value containing a NUL character is rejected in a client-first-message. Extension
     * values share the "value-char" / "value-safe-char" productions with saslname, which exclude NUL.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFirstMessage_extensionValueContainsNulCharacter()
    {
        // Setup test fixture
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = ("n,,n=" + username() + ",r=" + clientNonce() + ",x=\u0000")
            .getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientInitialMessage),
            "An extension value containing a NUL character must be rejected");
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
     * Verifies RFC 5802 §7: a client-final-message MAY include optional extensions between the nonce and the
     * proof. The parser must not reject such a message merely for containing one; the exchange should proceed to
     * proof verification (and fail there, with an "authentication failed" style error, rather than being rejected
     * at the parsing stage).
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3351">OF-3351: SCRAM server rejects optional extensions in client-final-message</a>
     */
    @Test
    void acceptsFinalMessage_withOptionalExtension() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String wrongProof = Base64.getEncoder().encodeToString(new byte[expectedProofLengthBytes()]);
        final byte[] clientFinalMessage = ("c=biws,r=" + firstExchangeResult.serverNonce + ",x=ignored,p=" + wrongProof)
            .getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final SaslException ex = assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "An all-zero proof does not match the expected proof, so authentication must still fail");

        // Verify result: failure happened at proof verification, not at message parsing.
        assertTrue(ex.getMessage().contains("Authentication failed"),
            "A client-final-message with an optional extension must be structurally accepted; rejection should occur "
                + "at proof verification, not parsing. Got: " + ex.getMessage());
    }

    /**
     * Verifies that an extension value containing a NUL character is rejected in a client-final-message too, since
     * CLIENT_FINAL_MESSAGE's extensions share the same ATTR_VAL character-class definition as the client-first
     * case above.
     *
     * Generic protocol validation test (also algorithm-independent).
     */
    @Test
    void rejectsFinalMessage_extensionValueContainsNulCharacter() throws Exception
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final ScramSaslServer server = newServer(false);
        final FirstExchangeResult firstExchangeResult = doFirstExchange(server);
        final String proof = Base64.getEncoder().encodeToString(new byte[expectedProofLengthBytes()]);
        final byte[] clientFinalMessage = ("c=biws,r=" + firstExchangeResult.serverNonce + ",x=\u0000,p=" + proof)
            .getBytes(StandardCharsets.UTF_8);

        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> server.evaluateResponse(clientFinalMessage),
            "An extension value containing a NUL character must be rejected in a client-final-message");
    }

    /**
     * A client that supports channel binding but was not offered a -PLUS mechanism sends the 'y' GS2 flag. When the
     * server did not in fact advertise the -PLUS variant to this session, that claim is truthful and authentication
     * must proceed.
     *
     * This guards against evaluating downgrade protection against the server's *global* mechanism set: a session on
     * which -PLUS was filtered out (for example, one that is not encrypted, or whose connection cannot supply channel
     * binding data) would otherwise be rejected even though the client behaved correctly.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc5802.html#section-6">RFC 5802, section 6</a>
     */
    @Test
    public void testGs2FlagYAcceptedWhenPlusMechanismWasNotOfferedToSession() throws Exception
    {
        // Setup test fixture.
        setupCanonicalAuthData();

        final Set<String> advertisedMechanisms = new HashSet<>(); // only the non-PLUS mechanism was advertised to this session.
        advertisedMechanisms.add(scramMechanismName(false));
        final ScramSaslServer server = newServer(false, advertisedMechanisms);

        final byte[] initialMessage = createClientInitialMessage("y,,", username(), clientNonce());

        // Execute system under test.
        final byte[] firstServerResponse = server.evaluateResponse(initialMessage);

        // Verify result: the exchange proceeds (a first server message is produced).
        assertNotNull(firstServerResponse, "A client that was not offered a -PLUS mechanism may send the 'y' flag; the exchange should proceed.");
        assertTrue(new String(firstServerResponse, StandardCharsets.UTF_8).startsWith("r="), "The server should respond with a first server message when the 'y' flag is used and no -PLUS mechanism was offered.");
    }

    /**
     * A client that sends the 'y' GS2 flag asserts that the server did not offer a -PLUS mechanism. When the server
     * *did* advertise the -PLUS variant to this session, that assertion is false, which indicates that the advertised
     * mechanism list was tampered with. Authentication must be aborted.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc5802.html#section-6">RFC 5802, section 6</a>
     */
    @Test
    public void testGs2FlagYRejectedWhenPlusMechanismWasOfferedToSession()
    {
        // Setup test fixture.
        setupCanonicalAuthData();

        final Set<String> advertisedMechanisms = new HashSet<>(); // both the -PLUS and the non-PLUS mechanism were advertised to this session.
        advertisedMechanisms.add(scramMechanismName(false));
        advertisedMechanisms.add(scramMechanismName(true));
        final ScramSaslServer server = newServer(false, advertisedMechanisms);

        final byte[] initialMessage = createClientInitialMessage("y,,", username(), clientNonce());

        // Execute system under test & verify result.
        assertThrows(SaslException.class,
            () -> server.evaluateResponse(initialMessage),
            "A client claiming that no -PLUS mechanism was offered, while it was, indicates a downgrade attack and must be rejected.");
    }

    /**
     * A client that supports channel binding but was not offered the -PLUS variant of the SCRAM mechanism that it is
     * using sends the 'y' GS2 flag. The fact that the server advertised a -PLUS variant for a different SCRAM hash
     * algorithm must not trigger downgrade protection.
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc5802.html#section-6">RFC 5802, section 6</a>
     */
    @Test
    public void testGs2FlagYAcceptedWhenOnlyDifferentScramPlusMechanismWasOfferedToSession() throws Exception
    {
        // Setup test fixture.
        setupCanonicalAuthData();

        final Set<String> advertisedMechanisms = new HashSet<>();
        advertisedMechanisms.add(scramMechanismName(false));
        advertisedMechanisms.add(differentScramMechanismName(true)); // Advertise a -PLUS mechanism for a different SCRAM hash algorithm. This must not be considered relevant to the SCRAM mechanism that is being negotiated by the server under test.
        final ScramSaslServer server = newServer(false, advertisedMechanisms);

        final byte[] initialMessage = createClientInitialMessage("y,,", username(), clientNonce());

        // Execute system under test.
        final byte[] firstServerResponse = server.evaluateResponse(initialMessage);

        // Verify result.
        assertNotNull(firstServerResponse, "A -PLUS mechanism for a different SCRAM hash algorithm must not trigger downgrade protection.");
        assertTrue(new String(firstServerResponse, StandardCharsets.UTF_8).startsWith("r="), "The server should respond with a first server message when the 'y' flag is used and the relevant -PLUS mechanism was not offered.");
    }

    /**
     * Verifies RFC 5802 §5.1: a literal comma in a username must be escaped on the wire as "=2C". The server must
     * decode this escaping before using the value to look up the user's credentials; failing to do so means a user
     * whose real username contains a comma can never be found.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesEscapedComma_inUsername_beforeCredentialLookup()
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final String literalUsername = "smith,doe";
        final String escapedUsername = "smith=2Cdoe"; // "," must be sent as "=2C" per RFC 5802 §5.1
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", escapedUsername, clientNonce());

        // Execute system under test
        assertDoesNotThrow(() -> server.evaluateResponse(clientInitialMessage), "A syntactically valid first message should be processed, even before credential lookup is verified");

        // Verify result: credential lookup must use the decoded username, not the raw escaped wire value.
        final ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        authFactory.verify(() -> AuthFactory.getSalt(usernameCaptor.capture(), any()));
        assertEquals(literalUsername, usernameCaptor.getValue(), "Credential lookup must use the decoded username ('" + literalUsername + "'), not the raw escaped wire value ('" + escapedUsername + "')");
    }

    /**
     * Verifies RFC 5802 §5.1: a literal equals sign in a username must be escaped on the wire as "=3D". The server
     * must decode this escaping before using the value to look up the user's credentials.
     *
     * Generic protocol validation test (also algorithm-independent).
     *
     * @see <a href="https://igniterealtime.atlassian.net/browse/OF-3353">OF-3353: SCRAM username and authzid are not un-escaped per RFC 5802 saslname rules</a>
     */
    @Test
    void decodesEscapedEqualsSign_inUsername_beforeCredentialLookup()
    {
        // Setup test fixture
        setupCanonicalAuthData();
        final String literalUsername = "user=admin";
        final String escapedUsername = "user=3Dadmin"; // "=" must be sent as "=3D" per RFC 5802 §5.1
        final ScramSaslServer server = newServer(false);
        final byte[] clientInitialMessage = createClientInitialMessage("n,,", escapedUsername, clientNonce());

        // Execute system under test
        assertDoesNotThrow(() -> server.evaluateResponse(clientInitialMessage), "A syntactically valid first message should be processed, even before credential lookup is verified");

        // Verify result: credential lookup must use the decoded username, not the raw escaped wire value.
        final ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        authFactory.verify(() -> AuthFactory.getSalt(usernameCaptor.capture(), any()));
        assertEquals(literalUsername, usernameCaptor.getValue(), "Credential lookup must use the decoded username ('" + literalUsername + "'), not the raw escaped wire value ('" + escapedUsername + "')");
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

    /**
     * Returns the name of a SCRAM mechanism of the mechanism under test.
     *
     * @param isPlusMechanism true if the returned mechanism is to be a -PLUS mechanism, false otherwise
     * @return a SCRAM mechanism name
     */
    private String scramMechanismName(boolean isPlusMechanism)
    {
        return newServer(isPlusMechanism).getMechanismName();
    }

    /**
     * Returns the name of a SCRAM mechanism using a different hash algorithm than the mechanism under test.
     *
     * @param isPlusMechanism true if the returned mechanism is to be a -PLUS mechanism, false otherwise
     * @return a SCRAM -PLUS mechanism name for a different hash algorithm
     */
    private String differentScramMechanismName(boolean isPlusMechanism)
    {
        final String mechanismName = newServer(false).getMechanismName();

        final String differentMechanismName;
        if (mechanismName.equals(ScramSha1SaslServer.MECHANISM_NAME)) {
            differentMechanismName = ScramSha256SaslServer.MECHANISM_NAME;
        } else {
            differentMechanismName = ScramSha1SaslServer.MECHANISM_NAME;
        }
        return isPlusMechanism ? differentMechanismName + "-PLUS" : differentMechanismName;
    }
}
