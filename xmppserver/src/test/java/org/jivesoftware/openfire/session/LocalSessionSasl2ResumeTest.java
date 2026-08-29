/*
 * Copyright (C) 2026 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.jivesoftware.openfire.session;

import org.jivesoftware.Fixtures;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.spi.BasicStreamIDFactory;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xmpp.packet.JID;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class LocalSessionSasl2ResumeTest
{
    private SessionManager sessionManager;

    @BeforeEach
    void setUp()
    {
        final XMPPServer server = Fixtures.mockXMPPServer();
        XMPPServer.setInstance(server);
        sessionManager = server.getSessionManager();
    }

    @Test
    void transfersConnectionOwnershipAndRemovesTheProviderSession()
    {
        final Connection oldConnection = mock(Connection.class);
        when(oldConnection.isClosed()).thenReturn(true);
        final LocalClientSession resumed = new LocalClientSession(Fixtures.XMPP_DOMAIN, oldConnection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);
        resumed.setAddress(new JID("romeo", Fixtures.XMPP_DOMAIN, "balcony"));
        resumed.setAuthToken(AuthToken.generateUserToken("romeo"));
        resumed.setStatus(Session.Status.AUTHENTICATED);
        resumed.getStreamManager().enableAndBuildElement(StreamManager.NAMESPACE_V3, true);

        final Connection newConnection = mock(Connection.class);
        final LocalClientSession connectionProvider = new LocalClientSession(Fixtures.XMPP_DOMAIN, newConnection,
            new BasicStreamIDFactory().createStreamID(), Locale.ENGLISH);

        resumed.reattachForSasl2(connectionProvider, 0);

        assertEquals(newConnection, resumed.getConnection());
        assertNull(connectionProvider.getConnection());
        assertEquals(Session.Status.AUTHENTICATED, resumed.getStatus());
        verify(newConnection).reinit(resumed);
        verify(sessionManager).removeDetached(resumed);
        verify(sessionManager, atLeastOnce()).removeSession(connectionProvider);
    }
}
