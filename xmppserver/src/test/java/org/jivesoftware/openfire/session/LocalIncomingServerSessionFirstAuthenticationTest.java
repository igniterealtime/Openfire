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
package org.jivesoftware.openfire.session;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.session.ServerSession.AuthenticationMethod;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Verifies that a {@link LocalIncomingServerSession} records the first domain that is validated for it, and the method
 * with which that was authenticated, which decides who may resume the stream of the session.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
public class LocalIncomingServerSessionFirstAuthenticationTest
{
    private static final String REMOTE_DOMAIN = "remote.example.org";
    private static final String OTHER_DOMAIN = "other.example.org";

    private LocalIncomingServerSession session;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp()
    {
        Fixtures.clearExistingProperties();

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        //noinspection deprecation
        XMPPServer.setInstance(xmppServer);

        final Connection connection = mock(Connection.class, withSettings().lenient());
        session = new LocalIncomingServerSession(Fixtures.XMPP_DOMAIN, connection, new BasicStreamIDFactory().createStreamID(), "claimed.example.org");
    }

    @AfterEach
    public void tearDown()
    {
        Fixtures.clearExistingProperties();
        //noinspection deprecation
        XMPPServer.setInstance(null);
    }

    /**
     * Verifies that nothing is recorded before a domain is validated.
     */
    @Test
    public void nothingIsRecordedBeforeDomainIsValidated()
    {
        // Setup test fixture.

        // Execute system under test.
        final LocalIncomingServerSession.DomainAuthentication result = session.getFirstAuthentication();

        // Verify result.
        assertNull(result, "No authentication must be recorded before a domain is validated.");
    }

    /**
     * Verifies that the first validated domain is recorded with the method that it was authenticated with, rather than
     * the domain that was claimed in the stream header.
     */
    @Test
    public void firstValidatedDomainIsRecordedWithMethod()
    {
        // Setup test fixture.

        // Execute system under test.
        session.addValidatedDomain(REMOTE_DOMAIN, AuthenticationMethod.SASL_EXTERNAL);

        // Verify result.
        assertEquals(new LocalIncomingServerSession.DomainAuthentication(REMOTE_DOMAIN, AuthenticationMethod.SASL_EXTERNAL), session.getFirstAuthentication(), "The first validated domain must be recorded, with the method that it was authenticated with.");
    }

    /**
     * Verifies that a domain that is validated later, with another method, does not change what is recorded, also not
     * when the authentication method of the session changes.
     */
    @Test
    public void laterDomainDoesNotChangeRecord()
    {
        // Setup test fixture.
        session.addValidatedDomain(REMOTE_DOMAIN, AuthenticationMethod.SASL_EXTERNAL);
        session.setAuthenticationMethod(AuthenticationMethod.SASL_EXTERNAL);

        // Execute system under test.
        session.setAuthenticationMethod(AuthenticationMethod.DIALBACK);
        session.addValidatedDomain(OTHER_DOMAIN, AuthenticationMethod.DIALBACK);

        // Verify result.
        assertEquals(new LocalIncomingServerSession.DomainAuthentication(REMOTE_DOMAIN, AuthenticationMethod.SASL_EXTERNAL), session.getFirstAuthentication(), "A domain that is validated later must not change the recorded first authentication.");
    }

    /**
     * Verifies that the record is kept when the first domain is removed from the validated domains, and that a domain
     * that is validated after that does not replace it.
     */
    @Test
    public void recordIsKeptWhenFirstDomainIsRemoved()
    {
        // Setup test fixture.
        session.addValidatedDomain(REMOTE_DOMAIN, AuthenticationMethod.DIALBACK);

        // Execute system under test.
        session.removeValidatedDomain(REMOTE_DOMAIN);
        session.addValidatedDomain(OTHER_DOMAIN, AuthenticationMethod.SASL_EXTERNAL);

        // Verify result.
        assertEquals(new LocalIncomingServerSession.DomainAuthentication(REMOTE_DOMAIN, AuthenticationMethod.DIALBACK), session.getFirstAuthentication(), "The first authentication must stay recorded when its domain is removed.");
    }

    /**
     * Verifies that a domain that is validated without naming a method is recorded with the authentication method of
     * the session at that time.
     */
    @Test
    public void domainWithoutMethodIsRecordedWithMethodOfSession()
    {
        // Setup test fixture.
        session.setAuthenticationMethod(AuthenticationMethod.DIALBACK);

        // Execute system under test.
        session.addValidatedDomain(REMOTE_DOMAIN);

        // Verify result.
        assertEquals(new LocalIncomingServerSession.DomainAuthentication(REMOTE_DOMAIN, AuthenticationMethod.DIALBACK), session.getFirstAuthentication(), "A domain that is validated without a method must be recorded with the authentication method of the session.");
    }

    /**
     * Verifies that a domain that is validated without naming a method, before the session has an authentication
     * method, is recorded without one.
     */
    @Test
    public void domainWithoutMethodIsRecordedWithoutMethodWhenSessionHasNone()
    {
        // Setup test fixture.

        // Execute system under test.
        session.addValidatedDomain(REMOTE_DOMAIN);

        // Verify result.
        assertEquals(new LocalIncomingServerSession.DomainAuthentication(REMOTE_DOMAIN, null), session.getFirstAuthentication(), "A domain that is validated before the session has an authentication method must be recorded without one.");
    }
}
