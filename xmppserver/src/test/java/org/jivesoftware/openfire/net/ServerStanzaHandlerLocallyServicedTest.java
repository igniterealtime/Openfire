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
package org.jivesoftware.openfire.net;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;

/**
 * Verifies {@link ServerStanzaHandler#isLocallyServiced(String)}, which decides whether a domain that a remote server
 * addresses is serviced by this server (OF-2643). Both the validation of stream headers and Server Dialback use it.
 */
public class ServerStanzaHandlerLocallyServicedTest
{
    private static final String COMPONENT_DOMAIN = "conference." + Fixtures.XMPP_DOMAIN;

    private static final String TRUNKED_DOMAIN = "trunked.example.net";

    private static final String UNSERVICED_DOMAIN = "unserviced.example.com";

    private RoutingTable routingTable;

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
        JiveGlobals.setProperty("xmpp.domain", Fixtures.XMPP_DOMAIN);

        final XMPPServer xmppServer = Fixtures.mockXMPPServer();
        routingTable = Fixtures.mockRoutingTable();
        doReturn(true).when(routingTable).hasComponentRoute(new JID(COMPONENT_DOMAIN));
        doReturn(routingTable).when(xmppServer).getRoutingTable();
        XMPPServer.setInstance(xmppServer);
    }

    /**
     * Asserts that the XMPP domain of this server is serviced.
     */
    @Test
    public void testXmppDomainIsServiced()
    {
        assertTrue(ServerStanzaHandler.isLocallyServiced(Fixtures.XMPP_DOMAIN));
    }

    /**
     * Asserts that the XMPP domain of this server is serviced when a peer names it in a different case. Domain names
     * are compared after normalisation, as RFC 6120 requires.
     */
    @Test
    public void testXmppDomainInDifferentCaseIsServiced()
    {
        assertTrue(ServerStanzaHandler.isLocallyServiced(Fixtures.XMPP_DOMAIN.toUpperCase(Locale.ROOT)));
    }

    /**
     * Asserts that a subdomain of the XMPP domain that has a component route (such as a multi-user chat service) is
     * serviced.
     */
    @Test
    public void testComponentSubdomainIsServiced()
    {
        assertTrue(ServerStanzaHandler.isLocallyServiced(COMPONENT_DOMAIN));
    }

    /**
     * Asserts that a subdomain of the XMPP domain that has no component route is not serviced.
     */
    @Test
    public void testSubdomainWithoutComponentIsNotServiced()
    {
        assertFalse(ServerStanzaHandler.isLocallyServiced("unknown." + Fixtures.XMPP_DOMAIN));
    }

    /**
     * Asserts that a domain that merely ends with the text of the XMPP domain, without being a subdomain of it, is not
     * serviced, even when a component route exists for it.
     */
    @Test
    public void testDomainThatContainsXmppDomainIsNotServiced()
    {
        // Setup test fixture.
        final String lookalike = "not" + Fixtures.XMPP_DOMAIN;
        doReturn(true).when(routingTable).hasComponentRoute(new JID(lookalike));

        // Execute system under test & verify result.
        assertFalse(ServerStanzaHandler.isLocallyServiced(lookalike), "A domain that is not a subdomain of the XMPP domain must not be treated as one.");
    }

    /**
     * Asserts that a domain for which trunking is enabled is serviced.
     */
    @Test
    public void testTrunkedDomainIsServiced()
    {
        // Setup test fixture.
        Trunking.ENABLED.setValue(true);
        Trunking.ALLOWABLE_DOMAINS.setValue(List.of(TRUNKED_DOMAIN));

        // Execute system under test & verify result.
        assertTrue(ServerStanzaHandler.isLocallyServiced(TRUNKED_DOMAIN));
    }

    /**
     * Asserts that a domain that is configured for trunking is not serviced when trunking itself is disabled.
     */
    @Test
    public void testTrunkedDomainIsNotServicedWhenTrunkingIsDisabled()
    {
        // Setup test fixture.
        Trunking.ENABLED.setValue(false);
        Trunking.ALLOWABLE_DOMAINS.setValue(List.of(TRUNKED_DOMAIN));

        // Execute system under test & verify result.
        assertFalse(ServerStanzaHandler.isLocallyServiced(TRUNKED_DOMAIN));
    }

    /**
     * Asserts that a subdomain of a domain for which trunking is enabled is not serviced: trunking applies to the
     * domains that are configured, and nothing else.
     */
    @Test
    public void testSubdomainOfTrunkedDomainIsNotServiced()
    {
        // Setup test fixture.
        Trunking.ENABLED.setValue(true);
        Trunking.ALLOWABLE_DOMAINS.setValue(List.of(TRUNKED_DOMAIN));

        // Execute system under test & verify result.
        assertFalse(ServerStanzaHandler.isLocallyServiced("conference." + TRUNKED_DOMAIN));
    }

    /**
     * Asserts that a domain that is unrelated to this server is not serviced.
     */
    @Test
    public void testUnrelatedDomainIsNotServiced()
    {
        assertFalse(ServerStanzaHandler.isLocallyServiced(UNSERVICED_DOMAIN));
    }

    /**
     * Asserts that a value that cannot be a domain name (here, because a label exceeds 63 characters) is not serviced,
     * rather than causing an exception.
     */
    @Test
    public void testInvalidDomainIsNotServiced()
    {
        assertFalse(ServerStanzaHandler.isLocallyServiced("a".repeat(64) + ".example.org"));
    }
}
