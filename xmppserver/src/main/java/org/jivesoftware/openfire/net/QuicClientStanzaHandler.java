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

import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.jivesoftware.openfire.nio.NettyConnection;
import org.jivesoftware.openfire.nio.QuicSessionStreamRouter;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.StringUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Client stanza handler variant that allows multiple QUIC streams to share one client session.
 */
public class QuicClientStanzaHandler extends ClientStanzaHandler
{
    private final QuicSessionStreamRouter streamRouter;

    public QuicClientStanzaHandler(final PacketRouter router, final Connection connection, final QuicSessionStreamRouter streamRouter)
    {
        super(router, connection);
        this.streamRouter = streamRouter;
    }

    @Override
    protected void createSession(final String serverName, final XmlPullParser xpp, final Connection connection) throws XmlPullParserException
    {
        final LocalClientSession existingSession = streamRouter.getSession();
        if (existingSession == null) {
            super.createSession(serverName, xpp, connection);
            if (session instanceof LocalClientSession localClientSession && connection instanceof NettyConnection nettyConnection) {
                streamRouter.bindPrimarySession(localClientSession, nettyConnection);
            }
            return;
        }

        session = existingSession;
        if (connection instanceof NettyConnection nettyConnection) {
            nettyConnection.reinit(existingSession);
        }
        connection.setAdditionalNamespaces(XMPPPacketReader.getPrefixedNamespacesOnCurrentElement(xpp));
        connection.setXMPPVersion(Session.MAJOR_VERSION, Session.MINOR_VERSION);
        connection.deliverRawText(StringUtils.asUnclosedStream(getStreamHeader()));
    }
}
