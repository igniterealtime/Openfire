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

import javax.security.sasl.SaslException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ScramSaslServer}'s package-private static helper methods
 * ({@link ScramSaslServer#extractRawGS2Header}, {@link ScramSaslServer#decodeSaslname},
 * {@link ScramSaslServer#rejectReservedMandatoryExtension}, {@link ScramSaslServer#hasValidBase64Length}).
 *
 * These methods take no dependency on the hash algorithm in use -- none of them reference
 * {@code getHmacAlgorithmName()}, {@code getDigestAlgorithmName()}, or any other abstract method -- so, unlike
 * {@link AbstractScramSaslServerTest}, this suite runs once rather than once per concrete SCRAM variant
 * (SHA-1/256/512). Any test whose outcome could plausibly differ by algorithm belongs in
 * {@link AbstractScramSaslServerTest} instead.
 */
class ScramSaslServerStaticMethodsTest
{
    // ---------------------------------------------------------------------------------------------------------
    // extractRawGS2Header
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Verifies GS2 header extraction when an authzid is present.
     *
     * GS2 parsing test: completely algorithm-independent.
     */
    @Test
    void extractsGs2Header_withAuthzId() throws Exception
    {
        // Setup test fixture
        final byte[] input = "p=tls,a=someuser,n=authcid,r=abc123,rest".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("p=tls,a=someuser,", new String(result, StandardCharsets.UTF_8));
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
        final byte[] input = "n,,n=someuser,r=abc123,rest".getBytes(StandardCharsets.UTF_8);

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
        final byte[] input = "p=tls,,n=someuser,r=abc123".getBytes(StandardCharsets.UTF_8);

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
        final byte[] input = "p=tls,,n=someuser,r=abc123".getBytes(StandardCharsets.UTF_8);

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
        final byte[] input = "p=tls,n=someuser".getBytes(StandardCharsets.UTF_8);

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
        final byte[] input = "p=tls,,n=someuser,r=abc,extra,stuff".getBytes(StandardCharsets.UTF_8);

        // Execute system under test
        final byte[] result = ScramSaslServer.extractRawGS2Header(input);

        // Verify result
        assertEquals("p=tls,,", new String(result, StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------------------------------------------------
    // decodeStrictUtf8
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Verifies that well-formed multi-byte UTF-8 (a non-ASCII codepoint) decodes correctly. RFC 5802 explicitly
     * supports internationalized usernames, so strict decoding must not reject valid UTF-8, only malformed input.
     *
     * UTF-8 decoding test: completely algorithm-independent.
     */
    @Test
    void decodeStrictUtf8_decodesValidMultiByteSequence() throws SaslException
    {
        // Execute system under test & Verify result
        assertEquals("café", ScramSaslServer.decodeStrictUtf8("café".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Verifies that a lone byte which is never valid in any position of a UTF-8 sequence (0xFF) is rejected.
     *
     * UTF-8 decoding test: completely algorithm-independent.
     */
    @Test
    void decodeStrictUtf8_throwsSaslException_forInvalidLeadByte()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeStrictUtf8(new byte[]{(byte) 0xFF}));
    }

    /**
     * Verifies that a truncated multi-byte sequence (a lead byte announcing a continuation that never arrives) is
     * rejected, rather than silently substituting the replacement character for the incomplete sequence.
     *
     * UTF-8 decoding test: completely algorithm-independent.
     */
    @Test
    void decodeStrictUtf8_throwsSaslException_forTruncatedMultiByteSequence()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeStrictUtf8(new byte[]{(byte) 0xC3}));
    }

    /**
     * Verifies that an overlong encoding (a non-canonical, invalid representation of a codepoint that has a
     * shorter valid encoding) is rejected.
     *
     * UTF-8 decoding test: completely algorithm-independent.
     */
    @Test
    void decodeStrictUtf8_throwsSaslException_forOverlongEncoding()
    {
        // Execute system under test & Verify result: 0xC0 0x80 is an overlong (invalid) encoding of NUL
        assertThrows(SaslException.class, () -> ScramSaslServer.decodeStrictUtf8(new byte[]{(byte) 0xC0, (byte) 0x80}));
    }

    // ---------------------------------------------------------------------------------------------------------
    // decodeSaslname
    // ---------------------------------------------------------------------------------------------------------

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

    // ---------------------------------------------------------------------------------------------------------
    // rejectReservedMandatoryExtension
    // ---------------------------------------------------------------------------------------------------------

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
        assertDoesNotThrow(() -> ScramSaslServer.rejectReservedMandatoryExtension(",x=1"));
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
        assertDoesNotThrow(() -> ScramSaslServer.rejectReservedMandatoryExtension(",x=1,y=2"));
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
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",x=1,m=unsupported"));
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
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",x=1,m=unsupported,y=2"));
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
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",x=1,,y=2"));
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
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",x=value,"));
    }

    /**
     * Verifies that an extension reusing an already-assigned RFC 5802 attribute letter (here, "c") is rejected.
     * Per §5.1, "[o]ptional extensions use as-yet unassigned attribute names" -- reusing an assigned letter is
     * never a legitimate extension, even where the specific attribute it collides with (channel-binding) doesn't
     * itself appear in the extensions list.
     *
     * Extension parsing test: completely algorithm-independent.
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forAssignedAttributeLetterReused()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",c=other"));
    }

    /**
     * Verifies that an extension attribute name repeated more than once is rejected, even when neither occurrence
     * reuses an already-assigned letter.
     *
     * Extension parsing test: completely algorithm-independent.
     */
    @Test
    void rejectReservedMandatoryExtension_throwsSaslException_forDuplicateExtensionName()
    {
        // Execute system under test & Verify result
        assertThrows(SaslException.class, () -> ScramSaslServer.rejectReservedMandatoryExtension(",x=1,x=2"));
    }

    // ---------------------------------------------------------------------------------------------------------
    // hasValidBase64Length
    // ---------------------------------------------------------------------------------------------------------

    /**
     * Verifies that a zero-padding value with a length that is a multiple of 4 is valid.
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_true_forNoPaddingCompleteBlocks()
    {
        // Execute system under test & Verify result
        assertTrue(ScramSaslServer.hasValidBase64Length("biws"));
    }

    /**
     * Verifies that an empty string is valid (zero data, zero padding), matching the same "structurally valid but
     * empty" treatment already applied to the other permissive fields (nonce, extensions).
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_true_forEmptyString()
    {
        // Execute system under test & Verify result
        assertTrue(ScramSaslServer.hasValidBase64Length(""));
    }

    /**
     * Verifies that a single "=" padding character is valid when the preceding data length is congruent to 3 mod 4.
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_true_forSinglePaddingWithCorrectDataLength()
    {
        // Execute system under test & Verify result
        assertTrue(ScramSaslServer.hasValidBase64Length("abc="));
    }

    /**
     * Verifies that "==" double padding is valid when the preceding data length is congruent to 2 mod 4, using a
     * real encoder-produced value.
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_true_forDoublePaddingWithCorrectDataLength()
    {
        // Execute system under test & Verify result
        assertTrue(ScramSaslServer.hasValidBase64Length("dGVzdA==")); // base64("test"): 6 data chars + "==", 6%4==2, correct
    }

    /**
     * Verifies that a length that is not a multiple of 4, with no padding at all, is rejected.
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_false_forNoPaddingWrongLength()
    {
        // Execute system under test & Verify result
        assertFalse(ScramSaslServer.hasValidBase64Length("a"));
        assertFalse(ScramSaslServer.hasValidBase64Length("ab"));
        assertFalse(ScramSaslServer.hasValidBase64Length("abc"));
        assertFalse(ScramSaslServer.hasValidBase64Length("abcde"));
    }

    /**
     * Verifies that a single "=" is rejected when the preceding data length is not congruent to 3 mod 4 -- the
     * exact excess-padding case a complete 4-char block followed by "=" represents.
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_false_forSinglePaddingWithWrongDataLength()
    {
        // Execute system under test & Verify result
        assertFalse(ScramSaslServer.hasValidBase64Length("abcd="));
    }

    /**
     * Verifies that "==" double padding is rejected when the preceding data length is not congruent to 2 mod 4.
     *
     * Base64 length validation test: completely algorithm-independent.
     */
    @Test
    void hasValidBase64Length_false_forDoublePaddingWithWrongDataLength()
    {
        // Execute system under test & Verify result
        assertFalse(ScramSaslServer.hasValidBase64Length("abc=="));
    }
}
