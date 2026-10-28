/*
 * Copyright (C) 2023-2026 Ignite Realtime Foundation. All rights reserved.
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

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.session.LocalIncomingServerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link ExternalServerSaslServer}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@ExtendWith(MockitoExtension.class)
public class ExternalServerSaslServerTest
{
    @Mock
    private LocalIncomingServerSession session;

    @Mock
    private Connection connection;

    private MockedStatic<SASLAuthentication> saslAuthentication;

    @BeforeEach
    public void setupStaticMock() {
        saslAuthentication = Mockito.mockStatic(SASLAuthentication.class);
    }

    @AfterEach
    public void teardownStaticMock() {
        if (saslAuthentication != null) {
            saslAuthentication.close();
        }
    }

    /**
     * Verify that when <em>no</em> initial response is given, a challenge (for authzid) is returned.
     *
     * The absence of an initial response is represented by a null value being provided as the response to be evaluated,
     * while the provided session <em>does not</em> have this session attribute: {@link SASLAuthentication#SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY}
     * and @{SASLAuthentication#PROPERTY_SASL_EXTERNAL_SERVER_REQUIRE_AUTHZID} is <code>true</code>.
     */
    //@Test @Disabled // TODO This test is disabled until we can figure out a way to set SASLAuthentication#PROPERTY_SASL_EXTERNAL_SERVER_REQUIRE_AUTHZID
    public void testNoInitialResponseWhileRequired() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";

        when(session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY)).thenReturn(null);

        // The following stubs are only used when the implementation under test progresses beyond the check for the
        // emptiness of the initial response. It should not progress beyond that point. I'm leaving the stubs in, for
        // the test to fail gracefully (rather than throw an exception) when the system under test misbehaves.
        when(session.getDefaultIdentity()).thenReturn(streamID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(streamID), anyBoolean())).thenReturn(true);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = new byte[]{};

        // Execute system under test.
        final byte[] response = server.evaluateResponse(input);

        // Verify results.
        assertNotNull(response);
        assertArrayEquals(new byte[0], response);
    }

    /**
     * Verify that when <em>no</em> initial response is given, no challenge (for authzid) is returned.
     *
     * The absence of an initial response is represented by a null value being provided as the response to be evaluated,
     * while the provided session <em>does not</em> have this session attribute: {@link SASLAuthentication#SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY}
     * and @{SASLAuthentication#PROPERTY_SASL_EXTERNAL_SERVER_REQUIRE_AUTHZID} is <code>false</code>.
     */
    @Test
    public void testNoInitialResponseWhileNotRequired() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";

        when(session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY)).thenReturn(null);
        // TODO explicitly set SASLAuthentication#PROPERTY_SASL_EXTERNAL_SERVER_REQUIRE_AUTHZID instead of depending on the default value.

        // The following stubs are only used when the implementation under test progresses beyond the check for the
        // emptiness of the initial response. It should not progress beyond that point. I'm leaving the stubs in, for
        // the test to fail gracefully (rather than throw an exception) when the system under test misbehaves.
        when(session.getDefaultIdentity()).thenReturn(streamID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(streamID), anyBoolean())).thenReturn(true);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = new byte[]{};

        // Execute system under test.
        final byte[] response = server.evaluateResponse(input);

        // Verify results.
        assertNull(response);
    }

    /**
     * Verify that when an empty initial response is given, authentication continues (and succeeds, provided that the
     * provided TLS certificates are valid), without a challenge (for authzid) being returned.
     *
     * The presence of an <em>empty</em> initial response is represented by a null value being provided as the response
     * to be evaluated, while the provided session defines this session attribute: {@link SASLAuthentication#SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY}
     */
    @Test
    public void testEmptyInitialResponse() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";

        when(session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY)).thenReturn(true);
        when(session.getDefaultIdentity()).thenReturn(streamID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(streamID), anyBoolean())).thenReturn(true);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = new byte[]{};

        // Execute system under test.
        final byte[] response = server.evaluateResponse(input);

        // Verify results.
        assertNull(response); // This asserts for successful authentication, rather than 'not a challenge'.
    }

    /**
     * Verify that when an initial response is given that matches the streamID as transmitted over the connection,
     * authentication continues (and succeeds, provided that the provided TLS certificates are valid).
     *
     * The presence of an <em>empty</em> initial response is represented by a null value being provided as the response
     * to be evaluated, while the provided session defines this session attribute: {@link SASLAuthentication#SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY}
     */
    @Test
    public void testInitialResponseMatchingStreamID() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";

        when(session.getDefaultIdentity()).thenReturn(streamID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(streamID), anyBoolean())).thenReturn(true);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = streamID.getBytes(StandardCharsets.UTF_8);

        // Execute system under test.
        final byte[] response = server.evaluateResponse(input);

        // Verify results.
        assertNull(response); // This asserts for successful authentication
    }

    /**
     * Verify that when an initial response is given that matches the streamID as transmitted over the connection, but
     * <em>can not</em> be used to validate the provided TLS certificates, authentication fails with 'not-authorized'.
     */
    @Test
    public void testInitialResponseMatchingStreamIDButNotCertificate() throws Exception
    {
        // Setup test fixture.
        final String authzID = "foo.example.org";

        when(session.getDefaultIdentity()).thenReturn(authzID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(authzID), anyBoolean())).thenReturn(false);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = authzID.getBytes(StandardCharsets.UTF_8);

        // Execute system under test.
        final SaslFailureException result = assertThrows(SaslFailureException.class, () -> server.evaluateResponse(input), "Authentication should fail when the certificate is not valid for the stream 'from' value");

        // Verify results.
        assertEquals(Failure.NOT_AUTHORIZED, result.getFailure(), "A certificate that is not valid for the stream 'from' value should be rejected with 'not-authorized'");
        assertNull(server.getAuthorizationID(), "No identity should be authorised after a failed certificate check");
        saslAuthentication.verify(() -> SASLAuthentication.verifyCertificates(any(), eq(authzID), eq(true)), description("The certificate should have been checked against the stream 'from' value"));
    }

    /**
     * Verify that when an initial response is given that differs from the streamID as transmitted over the connection,
     * authentication fails with 'invalid-authzid', even when the provided TLS certificates are valid for that initial
     * response.
     */
    @Test
    public void testInitialResponseDifferentFromStreamID() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";
        final String authzID = "other.example.com";

        when(session.getDefaultIdentity()).thenReturn(streamID);
        lenient().when(session.getConnection()).thenReturn(connection); // Reached only if the authzid check is bypassed.
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(authzID), anyBoolean())).thenReturn(true);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = authzID.getBytes(StandardCharsets.UTF_8);

        // Execute system under test.
        final SaslFailureException result = assertThrows(SaslFailureException.class, () -> server.evaluateResponse(input), "Authentication should fail when the authzid differs from the stream 'from' value");

        // Verify results.
        assertEquals(Failure.INVALID_AUTHZID, result.getFailure(), "An authzid that differs from the stream 'from' value should be rejected with 'invalid-authzid'");
    }

    /**
     * Verify that when an <em>empty</em> initial response is given, and the provided TLS certificates can not be used
     * to validate the streamID as transmitted over the connection, authentication fails with 'not-authorized'.
     */
    @Test
    public void testEmptyInitialResponseNotMatchingCertificate() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";

        when(session.getSessionData(SASLAuthentication.SASL_LAST_RESPONSE_WAS_PROVIDED_BUT_EMPTY)).thenReturn(true);
        when(session.getDefaultIdentity()).thenReturn(streamID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(streamID), anyBoolean())).thenReturn(false);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = new byte[]{};

        // Execute system under test.
        final SaslFailureException result = assertThrows(SaslFailureException.class, () -> server.evaluateResponse(input), "Authentication with an empty initial response should fail when the certificate is not valid for the stream 'from' value");

        // Verify results.
        assertEquals(Failure.NOT_AUTHORIZED, result.getFailure(), "A certificate that is not valid for the stream 'from' value should be rejected with 'not-authorized'");
    }

    /**
     * Verify that when <em>no</em> initial response is given (and none is required), and the provided TLS certificates
     * can not be used to validate the streamID as transmitted over the connection, authentication fails with
     * 'not-authorized'.
     */
    @Test
    public void testNoInitialResponseNotMatchingCertificate() throws Exception
    {
        // Setup test fixture.
        final String streamID = "example.org";

        when(session.getDefaultIdentity()).thenReturn(streamID);
        when(session.getConnection()).thenReturn(connection);
        saslAuthentication.when(() -> SASLAuthentication.verifyCertificates(any(), eq(streamID), anyBoolean())).thenReturn(false);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = new byte[]{};

        // Execute system under test.
        final SaslFailureException result = assertThrows(SaslFailureException.class, () -> server.evaluateResponse(input), "Authentication without an initial response should fail when the certificate is not valid for the stream 'from' value");

        // Verify results.
        assertEquals(Failure.NOT_AUTHORIZED, result.getFailure(), "A certificate that is not valid for the stream 'from' value should be rejected with 'not-authorized'");
    }

    /**
     * Verify that when the stream as transmitted over the connection has no (or an empty) 'from' attribute value,
     * authentication fails with 'not-authorized'.
     */
    @ParameterizedTest
    @NullAndEmptySource
    public void testMissingStreamFrom(final String streamFrom) throws Exception
    {
        // Setup test fixture.
        when(session.getDefaultIdentity()).thenReturn(streamFrom);

        final ExternalServerSaslServer server = new ExternalServerSaslServer(session);
        final byte[] input = "example.org".getBytes(StandardCharsets.UTF_8);

        // Execute system under test.
        final SaslFailureException result = assertThrows(SaslFailureException.class, () -> server.evaluateResponse(input), "Authentication should fail when the stream has no 'from' value");

        // Verify results.
        assertEquals(Failure.NOT_AUTHORIZED, result.getFailure(), "A stream without a 'from' value should be rejected with 'not-authorized'");
    }
}
