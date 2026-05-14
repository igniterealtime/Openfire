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

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.PacketRouter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Server stanza handler variant for QUIC-based S2S connections.
 *
 * <p>QUIC provides TLS 1.3 as an integral part of its transport layer. There is no STARTTLS
 * negotiation phase: the connection is encrypted from the very first packet. This handler
 * therefore treats every incoming QUIC S2S connection as if it were a Direct-TLS connection
 * ({@code directTLS=true}) and suppresses the STARTTLS upgrade path entirely.</p>
 *
 * <p>SASL EXTERNAL (XEP-0178 / RFC 6120 §6.3.4) is the expected authentication mechanism.
 * {@link org.jivesoftware.openfire.net.SASLAuthentication} offers SASL EXTERNAL when
 * {@code session.isEncrypted()} is {@code true} and the peer has presented a trusted X.509
 * certificate. Both conditions are satisfied here: the connection is marked encrypted by
 * {@link org.jivesoftware.openfire.nio.QuicServerConnectionHandler#createNettyConnection}, and
 * the peer certificate chain is retrieved from the parent {@code QuicChannel}'s
 * {@code SSLEngine} via the updated
 * {@link org.jivesoftware.openfire.nio.NettyConnection#getPeerCertificates()} fallback.</p>
 */
public class QuicServerStanzaHandler extends ServerStanzaHandler
{
    public QuicServerStanzaHandler(final PacketRouter router, final Connection connection)
    {
        // directTLS=true: skip STARTTLS feature advertisement and treat the connection as
        // already encrypted, matching the QUIC transport's built-in TLS 1.3.
        super(router, connection, true);
    }

    /**
     * No-op: QUIC provides TLS at the transport layer; there is no application-level TLS
     * upgrade to perform.
     */
    @Override
    void startTLS() throws Exception
    {
        // Intentionally empty — QUIC's TLS 1.3 handshake has already completed before any
        // XMPP bytes are exchanged. Calling connection.startTLS() here would be incorrect.
    }

    /**
     * No-op: because {@code directTLS=true} is passed to the superclass constructor,
     * {@code LocalIncomingServerSession.createSession} will not advertise STARTTLS and will
     * not call {@code tlsNegotiated}. This override is a safety net in case the call path
     * changes in the future.
     */
    @Override
    protected void tlsNegotiated(final XmlPullParser xpp) throws XmlPullParserException, IOException
    {
        // Intentionally empty — TLS is already established at the QUIC transport layer.
    }
}
