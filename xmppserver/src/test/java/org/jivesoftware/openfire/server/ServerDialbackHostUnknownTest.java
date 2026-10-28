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
package org.jivesoftware.openfire.server;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.RoutingTable;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.Trunking;
import org.jivesoftware.openfire.session.DomainPair;
import org.jivesoftware.util.JiveGlobals;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Verifies how Server Dialback, acting as the Receiving Server, decides whether the domain that a remote server asks
 * to be authenticated for is serviced by the local XMPP service (OF-2643).
 *
 * This decision is shared with the validation of stream headers, so that both paths accept the same domains.
 */
public class ServerDialbackHostUnknownTest
{
    private static final String COMPONENT_DOMAIN = "conference." + Fixtures.XMPP_DOMAIN;

    private static final String TRUNKED_DOMAIN = "trunked.example.net";

    private RoutingTable routingTable;

    private ServerDialback dialback;

    @BeforeAll
    public static void setUpClass() throws Exception
    {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @AfterAll
    public static void tearDownClass()
    {
        Fixtures.clearExistingProperties();
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

        dialback = new ServerDialback(mock(Connection.class), new DomainPair(Fixtures.XMPP_DOMAIN, "remote.example.org"));
    }

    /**
     * Asserts that the XMPP domain of the local XMPP service is not unknown.
     */
    @Test
    public void testXmppDomainIsKnown()
    {
        assertFalse(dialback.isHostUnknown(Fixtures.XMPP_DOMAIN), "The XMPP domain must not be unknown.");
    }

    /**
     * Asserts that a subdomain of the XMPP domain that has a component route is not unknown.
     */
    @Test
    public void testComponentSubdomainIsKnown()
    {
        assertFalse(dialback.isHostUnknown(COMPONENT_DOMAIN), "A subdomain that has a component route must not be unknown.");
    }

    /**
     * Asserts that a domain for which trunking is enabled is not unknown.
     */
    @Test
    public void testTrunkedDomainIsKnown()
    {
        // Setup test fixture.
        Trunking.ENABLED.setValue(true);
        Trunking.ALLOWABLE_DOMAINS.setValue(List.of(TRUNKED_DOMAIN));

        // Execute system under test & verify result.
        assertFalse(dialback.isHostUnknown(TRUNKED_DOMAIN), "A domain for which trunking is enabled must not be unknown.");
    }

    /**
     * Asserts that a domain that is unrelated to the local XMPP service is unknown.
     */
    @Test
    public void testUnrelatedDomainIsUnknown()
    {
        assertTrue(dialback.isHostUnknown("unserviced.example.com"), "An unrelated domain must be unknown.");
    }

    /**
     * Asserts that a domain that merely ends with the text of the XMPP domain, without being a subdomain of it, is
     * unknown, even when a component route exists for it.
     */
    @Test
    public void testDomainThatContainsXmppDomainIsUnknown()
    {
        // Setup test fixture.
        final String lookalike = "not" + Fixtures.XMPP_DOMAIN;
        doReturn(true).when(routingTable).hasComponentRoute(new JID(lookalike));

        // Execute system under test & verify result.
        assertTrue(dialback.isHostUnknown(lookalike), "A domain that is not a subdomain of the XMPP domain must not be treated as one.");
    }

    /**
     * Asserts that a dialback request that does not name the domain it is for is treated as being for an unknown
     * domain, rather than causing an exception. XEP-0220 requires the 'to' attribute on these requests.
     */
    @Test
    public void testMissingDomainIsUnknown()
    {
        assertTrue(dialback.isHostUnknown(null), "A dialback request without a 'to' attribute must be treated as being for an unknown host.");
    }
}
