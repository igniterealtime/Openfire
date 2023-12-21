/*
 * Copyright (C) 2017-2023 Ignite Realtime Foundation. All rights reserved.
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
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.StreamError;

import java.io.IOException;
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
        char[] readChars = in.readCharSequence(in.readableBytes(), CharsetUtil.UTF_8).toString().toCharArray();
        parser.read(readChars);

        // Add any decoded messages to our outbound list to be processed by subsequent channelRead() events
        if (parser.areThereMsgs()) {
            out.addAll(Arrays.asList(parser.getMsgs()));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        final NettyConnection connection = ctx.channel().attr(CONNECTION).get();
        Log.warn("Error occurred while decoding XMPP stanza, closing connection: {}", connection, cause);
        connection.close(new StreamError(StreamError.Condition.internal_server_error, "An error occurred in XMPP Decoder"), cause instanceof IOException);
    }
}
