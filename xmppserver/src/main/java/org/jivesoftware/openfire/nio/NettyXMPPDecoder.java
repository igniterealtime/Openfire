/*
 * Copyright (C) 2017-2026 Ignite Realtime Foundation. All rights reserved.
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
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.CONNECTION;

/**
 * A Netty pipeline decoder that parses raw bytes from an XMPP connection into discrete XML stanzas.
 *
 * This decoder sits in the Netty channel pipeline and delegates to a per-channel {@link XMLLightweightParser} to
 * incrementally parse incoming data. Fully parsed stanzas are passed to the next handler in the pipeline via the
 * {@code out} list.
 *
 * As a security measure, connections that attempt to send a single stanza exceeding the configured maximum buffer size
 * are closed with a {@code policy-violation} stream error.
 */
public class NettyXMPPDecoder extends ByteToMessageDecoder {
    private static final Logger Log = LoggerFactory.getLogger(NettyXMPPDecoder.class);

    /**
     * Decodes incoming bytes from the channel into one or more XMPP stanzas.
     *
     * Retrieves the {@link XMLLightweightParser} associated with this channel and feeds it the available bytes. Any
     * fully parsed stanzas are added to {@code out} for processing by subsequent handlers in the pipeline.
     *
     * If the parser's internal buffer has exceeded the maximum allowed size, the connection is closed immediately with
     * a {@code policy-violation} stream error and no further parsing is attempted.
     *
     * @param ctx the channel handler context, used to retrieve channel attributes
     * @param in  the incoming byte buffer containing raw XMPP data
     * @param out the list to which fully decoded stanzas are added
     * @throws Exception if an error occurs during parsing
     */
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
            Log.warn("Maximum buffer size was exceeded (peer possibly sent a stanza that was too large), closing connection: {}", connection);
            connection.close(new StreamError(StreamError.Condition.policy_violation, "Maximum stanza length exceeded"));
            return;
        }

        // Parse as many stanzas as possible from the received data
        parser.read(in);

        // Add any decoded messages to our outbound list to be processed by subsequent channelRead() events
        if (parser.areThereMsgs()) {
            Collections.addAll(out, parser.getMsgs());
        }
    }

    /**
     * Handles exceptions thrown during decoding or elsewhere in the pipeline.
     *
     * Categorises the cause and closes the connection with an appropriate stream error.
     * Full stack traces are intentionally logged at DEBUG level only, to avoid excessive log
     * noise during production operation where port scanners and misconfigured clients can
     * produce a high volume of these exceptions.
     *
     * If no {@link NettyConnection} has been associated with the channel yet (e.g. an
     * exception occurs during early channel setup before registration), the exception is
     * logged but no close attempt is made.
     *
     * @param ctx   the channel handler context
     * @param cause the exception that was caught
     * @throws Exception if an error occurs while handling the exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        final NettyConnection connection = ctx.channel().attr(CONNECTION).get();

        if (isSslHandshakeError(cause)) {
            Log.debug("SSL Handshake error caught, closing connection: {}", connection);
            if (connection != null) {
                connection.close();
            } else {
                ctx.channel().close();
            }
            return;
        }

        if (isNotSslRecord(cause)) {
            Log.warn("Closing connection with {}, as unencrypted data was received, while encrypted data was expected (A full stack trace will be logged on the 'debug' level).", (connection == null || connection.getPeer() == null) ? "(unknown)" : connection.getPeer());
            Log.debug("Error occurred while decoding XMPP stanza: {}", connection, cause);
            if (connection != null) {
                connection.close(new StreamError(StreamError.Condition.internal_server_error, "Received unencrypted data while encrypted data was expected."));
            } else {
                ctx.channel().close();
            }
            return;
        }

        if (isConnectionReset(cause)) {
            Log.debug("Closing connection, as the socket connection was reset: {}", connection, cause);
        } else {
            Log.warn("Closing connection, as an error occurred while decoding XMPP stanza (A full stack trace will be logged on the 'debug' level): {} on connection {}", (cause != null ? cause.getMessage() : "Unknown error"), connection);
            Log.debug("Error occurred while decoding XMPP stanza: {}", connection, cause);
        }
        if (connection != null) {
            connection.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred while attempting to decode XMPP data."));
        } else {
            ctx.channel().close();
        }
    }

    /**
     * Determines whether the given throwable represents a non-SSL record error, which occurs when unencrypted data is
     * received on a TLS-expecting connection.
     *
     * @param t the throwable to inspect
     * @return {@code true} if the cause is a {@link NotSslRecordException}, {@code false} otherwise
     */
    private boolean isNotSslRecord(Throwable t)
    {
        // Unwrap DecoderException to check for potential SSLHandshakeException
        if (t instanceof DecoderException) {
            t = t.getCause();
        }

        return (t instanceof NotSslRecordException);
    }

    /**
     * Determines whether the given throwable represents a TLS handshake failure.
     *
     * @param t the throwable to inspect
     * @return {@code true} if the cause is an {@link SSLHandshakeException}, {@code false} otherwise
     */
    private boolean isSslHandshakeError(Throwable t) {
        // Unwrap DecoderException to check for potential SSLHandshakeException
        if (t instanceof DecoderException) {
            t = t.getCause();
        }

        return (t instanceof SSLHandshakeException);
    }

    /**
     * Determines whether the given throwable represents a TCP connection reset by the remote peer.
     *
     * @param t the throwable to inspect
     * @return {@code true} if the cause is a {@link SocketException} with the message
     *         {@code "Connection reset"}, {@code false} otherwise
     */
    private boolean isConnectionReset(Throwable t) {
        if (t instanceof DecoderException) {
            t = t.getCause();
        }
        return (t instanceof SocketException)
            && "Connection reset".equalsIgnoreCase(t.getMessage());
    }
}
