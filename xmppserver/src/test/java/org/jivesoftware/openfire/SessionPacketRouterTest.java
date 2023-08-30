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
package org.jivesoftware.openfire;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Packet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests that verify the implementation of {@link SessionPacketRouter}
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
@ExtendWith(MockitoExtension.class)
public class SessionPacketRouterTest
{
    @BeforeAll
    public static void setUpClass() throws Exception {
        Fixtures.reconfigureOpenfireHome();
        Fixtures.disableDatabasePersistence();
    }

    @BeforeEach
    public void setUp() {
        //noinspection deprecation
        XMPPServer.setInstance(Fixtures.mockXMPPServer());
    }

    @AfterEach
    public void tearDown() {
        Fixtures.clearExistingProperties();
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza without a 'to' address (instructing the server to handle it directly on behalf of the entity
     * that sent it) on a session that has completed resource binding.
     *
     * As the session has a resource bound, any stanza should be accepted by the method under test.
     */
    @Test
    public void testIsInvalid_noToAddress_authenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.AUTHENTICATED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza that is addressed to the domain of the server on a session that has completed resource
     * binding.
     *
     * As the session has a resource bound, any stanza should be accepted by the method under test.
     */
    @Test
    public void testIsInvalid_addressedAtDomain_authenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        stanza.setTo(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.AUTHENTICATED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza that is addressed to a third party user on a session that has completed resource binding.
     *
     * As the session has a resource bound, any stanza should be accepted by the method under test.
     */
    @Test
    public void testIsInvalid_addressedAtThirdPartyUser_authenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        stanza.setTo(new JID("foobar123", XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "test123"));
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.AUTHENTICATED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza that is addressed to a third party server on a session that has completed resource binding.
     *
     * As the session has a resource bound, any stanza should be accepted by the method under test.
     */
    @Test
    public void testIsInvalid_addressedAtThirdPartyServer_authenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        stanza.setTo(new JID(null, "makedifferent" + XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null));
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.AUTHENTICATED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza without a 'to' address (instructing the server to handle it directly on behalf of the entity
     * that sent it) on a session that has not completed resource binding.
     *
     * The server must handle a stanza without a 'to' address on behalf of the entity that sent it (per RFC 6120,
     * section 10.3), therefor the method under test should accept the stanza.
     */
    @Test
    public void testIsInvalid_noToAddress_unauthenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.CONNECTED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza that is addressed to the domain of the server on a session that has not completed resource
     * binding.
     *
     * Stanzas addressed to the server itself are acceptable (per RFC 6120, section 7.1). The method under test should
     * accept the stanza.
     */
    @Test
    public void testIsInvalid_addressedAtDomain_unauthenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        stanza.setTo(XMPPServer.getInstance().getServerInfo().getXMPPDomain());
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.CONNECTED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertFalse(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza that is addressed to a third party user on a session that has not completed resource binding.
     *
     * Stanzas addressed to other clients or servers are invalid (per RFC 6120, section 7.1). The method under test
     * should not accept the stanza.
     */
    @Test
    public void testIsInvalid_addressedAtThirdPartyUser_unauthenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        stanza.setTo(new JID("foobar123", XMPPServer.getInstance().getServerInfo().getXMPPDomain(), "test123"));
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.CONNECTED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertTrue(result);
    }

    /**
     * Tests the implementation of {@link SessionPacketRouter#isInvalidStanzaSentPriorToResourceBinding(Packet, ClientSession)}
     * by sending a stanza that is addressed to a third party server on a session that has not completed resource
     * binding.
     *
     * Stanzas addressed to other clients or servers are invalid (per RFC 6120, section 7.1). The method under test
     * should not accept the stanza.
     */
    @Test
    public void testIsInvalid_addressedAtThirdPartyServer_unauthenticated() throws Exception
    {
        // Setup test fixture.
        final Packet stanza = new Message();
        stanza.setTo(new JID(null, "makedifferent" + XMPPServer.getInstance().getServerInfo().getXMPPDomain(), null));
        final LocalClientSession session = mock(LocalClientSession.class, withSettings().strictness(Strictness.LENIENT));
        when(session.getStatus()).thenReturn(Session.Status.CONNECTED); // Openfire sets 'AUTHENTICATED' only after resource binding has been done.

        // Execute system under test.
        final boolean result = SessionPacketRouter.isInvalidStanzaSentPriorToResourceBinding(stanza, session);

        // Verify results.
        assertTrue(result);
    }
}
