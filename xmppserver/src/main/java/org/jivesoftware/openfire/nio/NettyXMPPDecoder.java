/*
 * Copyright (C) 2017-2025 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.nio;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.ssl.NotSslRecordException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.StreamError;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;

/**
 * Decoder that parses ByteBuffers and generates XML stanzas. Generated
 * stanzas are then passed to the next filters.
 */
public class NettyXMPPDecoder extends ByteToMessageDecoder {
    private static final Logger Log = LoggerFactory.getLogger(NettyXMPPDecoder.class);

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Get the XML parser from the channel
        XMLLightweightParser parser = ctx.channel().attr(NettyConnectionHandler.XML_PARSER).get();

        // Check that the stanza constructed by the parser is not bigger than 1 Megabyte. For security reasons
        // we will abort parsing when 1 Mega of queued chars was found.
        if (parser.isMaxBufferSizeExceeded()) {
            // Clear out the buffer to prevent endless exceptions being thrown while the connection is closed.
            // De-allocation of the buffer from this channel will occur following the channel closure, so there is
            // no need to call in.release() as this will cause an IllegalReferenceCountException.
            in.clear();
            NettyConnection connection = ctx.channel().attr(CONNECTION).get();
            Log.warn("Maximum buffer size was exceeded, closing connection: " + connection);
            connection.close(new StreamError(StreamError.Condition.policy_violation, "Maximum stanza length exceeded"));
            return;
        }

        // Parse as many stanzas as possible from the received data
        parser.read(in);

        // Add any decoded messages to our outbound list to be processed by subsequent channelRead() events
        if (parser.areThereMsgs()) {
            out.addAll(Arrays.asList(parser.getMsgs()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        final NettyConnection connection = ctx.channel().attr(CONNECTION).get();

        if (isSslHandshakeError(cause)) {
            connection.close();
            return;
        }

        if (isNotSslRecord(cause)) {
            Log.warn("Closing connection with {}, as unencrypted data was received, while encrypted data was expected (A full stack trace will be logged on the ‘debug’ level).", connection.getPeer() == null ? "(unknown)" : connection.getPeer(), cause);
            Log.debug("Error occurred while decoding XMPP stanza: {}", connection, cause);
            connection.close(new StreamError(StreamError.Condition.internal_server_error, "Received unencrypted data while encrypted data was expected."));
            return;
        }

        if (isConnectionReset(cause)) {
            if (connection.getConfiguration().getType().isClientOriented()) {
                // For clients, a connection reset is very common (eg: network connectivity issue, mobile app killed in the background). Don't log as a warning (OF-3038)
                Log.debug("Closing client connection, as the socket connection was reset: {}", connection, cause);
            } else {
                // For non-clients, a connection reset is less common. They're expected to be connected to more reliable network configuration. Log at a higher level.
                Log.warn("Closing (non-client) connection, as the socket connection was reset: {}", connection, cause);
            }
        } else {
            Log.warn("Error occurred while decoding XMPP stanza, closing connection: {}", connection, cause);
        }
        connection.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred in XMPP Decoder"));
    }

    private boolean isNotSslRecord(Throwable t)
    {
        // Unwrap DecoderException to check for potential SSLHandshakeException
        if (t instanceof DecoderException) {
            t = t.getCause();
        }

        return (t instanceof NotSslRecordException);
    }

    private boolean isSslHandshakeError(Throwable t) {
        // Unwrap DecoderException to check for potential SSLHandshakeException
        if (t instanceof DecoderException) {
            t = t.getCause();
        }

        return (t instanceof SSLHandshakeException);
    }

    private boolean isConnectionReset(Throwable t) {
        return (t instanceof SocketException)
            && "Connection reset".equalsIgnoreCase(t.getMessage());
    }
}
