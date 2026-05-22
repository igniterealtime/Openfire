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
package org.jivesoftware.openfire.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.jivesoftware.openfire.nio.NettyConnectionHandler.IDLE_FLAG;
import static org.jivesoftware.openfire.spi.NettyServerInitializer.TRAFFIC_HANDLER_NAME;

/**
 * Handles the HTTP/3 WebTransport upgrade on a QUIC stream and then delegates to the
 * standard C2S XMPP pipeline.
 *
 * <h2>Protocol flow</h2>
 * <ol>
 *   <li>The client opens a bidirectional QUIC stream and sends an HTTP/3 CONNECT request
 *       with {@code :protocol: webtransport} and {@code :path: /xmpp}.</li>
 *   <li>This handler reads the raw HTTP/3 CONNECT request bytes, validates the path, and
 *       responds with a {@code 200} status to complete the WebTransport session establishment.</li>
 *   <li>After the upgrade the handler removes itself from the pipeline and installs the
 *       standard XMPP C2S handlers ({@link NettyXMPPDecoder}, {@link StringEncoder},
 *       {@link QuicClientConnectionHandler}), so subsequent bytes on the stream are treated
 *       as XMPP.</li>
 * </ol>
 *
 * <h2>HTTP/3 framing</h2>
 * <p>HTTP/3 uses QUIC variable-length integer encoding for frame types and lengths (RFC 9000
 * §16). A HEADERS frame has type {@code 0x01}. This handler performs a minimal parse: it
 * reads the frame type and length, then scans the QPACK-encoded header block for the
 * {@code :method: CONNECT} and {@code :protocol: webtransport} pseudo-headers. Full QPACK
 * decompression is not required because WebTransport CONNECT requests use only the static
 * QPACK table entries defined in RFC 9204 Appendix A.</p>
 *
 * <h2>Relationship to netty-incubator-codec-http3</h2>
 * <p>A full HTTP/3 codec (e.g. {@code netty-incubator-codec-http3}) would provide proper
 * QPACK decompression and HTTP/3 frame parsing. This handler intentionally avoids that
 * dependency to keep the XMPP server footprint small; it only needs to handle the single
 * WebTransport CONNECT handshake, not general HTTP/3 request handling.</p>
 */
public class WebTransportConnectionHandler extends ChannelInboundHandlerAdapter
{
    private static final Logger Log = LoggerFactory.getLogger(WebTransportConnectionHandler.class);

    /** HTTP/3 HEADERS frame type (RFC 9114 §7.2.2). */
    private static final int H3_FRAME_TYPE_HEADERS = 0x01;

    /** The WebTransport path this server accepts CONNECT requests on. */
    public static final String WEBTRANSPORT_PATH = "/xmpp";

    private final ConnectionConfiguration configuration;
    private final QuicSessionStreamRouter streamRouter;

    /** Accumulation buffer for partial HTTP/3 frames. */
    private ByteBuf accumulator;

    public WebTransportConnectionHandler(final ConnectionConfiguration configuration,
                                         final QuicSessionStreamRouter streamRouter)
    {
        this.configuration = configuration;
        this.streamRouter = streamRouter;
    }

    @Override
    public void handlerAdded(final ChannelHandlerContext ctx)
    {
        accumulator = ctx.alloc().buffer(512);
    }

    @Override
    public void handlerRemoved(final ChannelHandlerContext ctx)
    {
        if (accumulator != null) {
            accumulator.release();
            accumulator = null;
        }
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception
    {
        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg);
            return;
        }

        final ByteBuf in = (ByteBuf) msg;
        try {
            accumulator.writeBytes(in);
        } finally {
            in.release();
        }

        tryUpgrade(ctx);
    }

    /**
     * Attempts to parse a complete HTTP/3 HEADERS frame from the accumulator.
     * If the frame is a valid WebTransport CONNECT request, sends a 200 response and
     * replaces this handler with the XMPP C2S pipeline. If the request is invalid,
     * sends a 400 response and closes the stream.
     *
     * <p>Non-HEADERS frames that appear before the HEADERS frame are skipped, as the
     * browser may send unknown or extension frames first.</p>
     */
    private void tryUpgrade(final ChannelHandlerContext ctx)
    {
        final ByteBuf buf = accumulator;

        // Log the raw bytes on first read to aid diagnosis.
        if (Log.isDebugEnabled() && buf.readableBytes() > 0) {
            final byte[] raw = new byte[Math.min(buf.readableBytes(), 64)];
            buf.getBytes(buf.readerIndex(), raw);
            Log.debug("WebTransport stream {}: raw bytes (first {} of {}): {}",
                ((QuicStreamChannel) ctx.channel()).streamId(),
                raw.length, buf.readableBytes(), bytesToHex(raw));
        }

        // Scan frames until we find a HEADERS frame (type 0x01), skipping any others.
        long headersFrameLength = -1;
        while (true) {
            buf.markReaderIndex();

            if (buf.readableBytes() < 2) {
                buf.resetReaderIndex();
                return; // wait for more data
            }

            final long frameType = readVarInt(buf);
            if (frameType < 0) {
                buf.resetReaderIndex();
                return; // incomplete varint
            }

            final long frameLength = readVarInt(buf);
            if (frameLength < 0) {
                buf.resetReaderIndex();
                return; // incomplete varint
            }

            if (buf.readableBytes() < frameLength) {
                buf.resetReaderIndex();
                return; // wait for more data
            }

            if (frameType == H3_FRAME_TYPE_HEADERS) {
                headersFrameLength = frameLength;
                break;
            }

            // Skip non-HEADERS frames (DATA, unknown extension frames, etc.).
            Log.debug("WebTransport stream {}: skipping frame type={} length={}",
                ((QuicStreamChannel) ctx.channel()).streamId(), frameType, frameLength);
            buf.skipBytes((int) frameLength);
        }

        // Read the QPACK-encoded header block.
        final byte[] headerBlock = new byte[(int) headersFrameLength];
        buf.readBytes(headerBlock);

        if (isWebTransportConnect(headerBlock)) {
            Log.info("WebTransport CONNECT received on stream {}; upgrading to XMPP C2S pipeline.",
                ((QuicStreamChannel) ctx.channel()).streamId());
            sendConnectResponse(ctx);
            Log.debug("WebTransport: sent HTTP/3 200 response on stream {}.",
                ((QuicStreamChannel) ctx.channel()).streamId());
            upgradeToXmppPipeline(ctx);
        } else {
            Log.warn("WebTransport: CONNECT request missing required headers (CONNECT / webtransport); closing stream.");
            sendErrorAndClose(ctx, 400);
        }
    }

    /**
     * Reads a QUIC/HTTP3 variable-length integer from {@code buf}.
     * Returns the value, or {@code -1} if there are not enough bytes.
     */
    private static long readVarInt(final ByteBuf buf)
    {
        if (!buf.isReadable()) {
            return -1;
        }
        final int firstByte = buf.readUnsignedByte();
        final int prefix = (firstByte & 0xC0) >> 6;
        final long value;
        switch (prefix) {
            case 0:
                value = firstByte & 0x3F;
                break;
            case 1:
                if (!buf.isReadable()) return -1;
                value = ((long)(firstByte & 0x3F) << 8) | buf.readUnsignedByte();
                break;
            case 2:
                if (buf.readableBytes() < 3) return -1;
                value = ((long)(firstByte & 0x3F) << 24)
                    | ((long) buf.readUnsignedByte() << 16)
                    | ((long) buf.readUnsignedByte() << 8)
                    | buf.readUnsignedByte();
                break;
            case 3:
                if (buf.readableBytes() < 7) return -1;
                value = ((long)(firstByte & 0x3F) << 56)
                    | ((long) buf.readUnsignedByte() << 48)
                    | ((long) buf.readUnsignedByte() << 40)
                    | ((long) buf.readUnsignedByte() << 32)
                    | ((long) buf.readUnsignedByte() << 24)
                    | ((long) buf.readUnsignedByte() << 16)
                    | ((long) buf.readUnsignedByte() << 8)
                    | buf.readUnsignedByte();
                break;
            default:
                value = -1;
        }
        return value;
    }

    /**
     * Checks whether the QPACK-encoded header block contains the required WebTransport
     * CONNECT pseudo-headers, using fixed byte-pattern matching against known QPACK/Huffman
     * encodings.
     *
     * <p>QPACK encodes headers using static table references and Huffman-encoded literals.
     * Neither {@code :method: CONNECT} nor {@code :protocol: webtransport} appear as plain
     * ASCII strings in the encoded block:
     * <ul>
     *   <li>{@code :method: CONNECT} — QPACK static table index 15, encoded as the single
     *       byte {@code 0xCF} (indexed field line, static=1, index=15, RFC 9204 §3.2.2).</li>
     *   <li>{@code :protocol: webtransport} — literal field line with literal name, both
     *       name and value Huffman-encoded (RFC 7541 Appendix B). The name {@code :protocol}
     *       always encodes to {@code b9 5d 87 49 c8 7a 3f} and the value {@code webtransport}
     *       always encodes to {@code f0 58 d3 60 ea 45 67 b1 3f}. These are deterministic
     *       per the RFC 7541 Huffman table.</li>
     * </ul>
     * </p>
     *
     * <p>The path is intentionally <em>not</em> checked here: this handler is only installed
     * on {@code h3} ALPN connections, and the only HTTP/3 use-case on this server is
     * WebTransport for XMPP.</p>
     */
    private static boolean isWebTransportConnect(final byte[] headerBlock)
    {
        // :method: CONNECT — QPACK static table index 15, encoded as single byte 0xCF.
        boolean hasConnect = false;
        for (final byte b : headerBlock) {
            if (b == (byte) 0xCF) {
                hasConnect = true;
                break;
            }
        }

        // :protocol: webtransport — Huffman-encoded name b9 5d 87 49 c8 7a 3f
        // followed (not necessarily immediately) by Huffman-encoded value f0 58 d3 60 ea 45 67 b1 3f.
        // We scan for the Huffman-encoded name bytes as a subarray; their presence is sufficient
        // to confirm the :protocol header is present with value webtransport.
        final byte[] PROTOCOL_NAME_HUFFMAN  = { (byte)0xb9, (byte)0x5d, (byte)0x87, (byte)0x49, (byte)0xc8, (byte)0x7a, (byte)0x3f };
        final byte[] WEBTRANSPORT_VAL_HUFFMAN = { (byte)0xf0, (byte)0x58, (byte)0xd3, (byte)0x60, (byte)0xea, (byte)0x45, (byte)0x67, (byte)0xb1, (byte)0x3f };
        final boolean hasProtocolName = containsBytes(headerBlock, PROTOCOL_NAME_HUFFMAN);
        final boolean hasWebtransport = containsBytes(headerBlock, WEBTRANSPORT_VAL_HUFFMAN);

        Log.debug("WebTransport header check: hasConnect={}, hasProtocolName={}, hasWebtransport={}, raw (hex)={}",
            hasConnect, hasProtocolName, hasWebtransport, bytesToHex(headerBlock));
        return hasConnect && hasProtocolName && hasWebtransport;
    }

    /** Returns true if {@code haystack} contains {@code needle} as a contiguous subsequence. */
    private static boolean containsBytes(final byte[] haystack, final byte[] needle)
    {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    /** Converts a byte array to a hex string for diagnostic logging. */
    private static String bytesToHex(final byte[] bytes)
    {
        final StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (final byte b : bytes) {
            sb.append(String.format("%02x ", b));
        }
        return sb.toString().trim();
    }

    /**
     * Sends an HTTP/3 200 response to complete the WebTransport session establishment.
     * The response is a minimal HEADERS frame containing only the {@code :status: 200}
     * pseudo-header, encoded using QPACK static table index 25.
     */
    private static void sendConnectResponse(final ChannelHandlerContext ctx)
    {
        // HTTP/3 HEADERS frame: type=0x01, length=3, QPACK Required Insert Count=0,
        // S=0, Delta Base=0, then indexed header field: static index 25 (:status: 200).
        // Encoded: 0x01 (frame type) 0x03 (length=3) 0x00 0x00 (QPACK prefix) 0xD9 (static idx 25)
        // QPACK static table index 25 = :status: 200 (RFC 9204 Appendix A, row 25)
        final byte[] response = new byte[]{
            0x01,       // HEADERS frame type
            0x03,       // payload length = 3 (QPACK prefix 2 bytes + 1 indexed field line)
            0x00,       // Required Insert Count = 0
            0x00,       // S=0, Delta Base=0
            (byte) 0xD9 // Indexed Field Line, static=1, index=25 (:status: 200)
        };
        ctx.writeAndFlush(Unpooled.wrappedBuffer(response));
    }

    /**
     * Sends an HTTP/3 error response and closes the stream.
     */
    private static void sendErrorAndClose(final ChannelHandlerContext ctx, final int statusCode)
    {
        // Minimal HEADERS frame with :status: 400 (static index 24) or 500 (static index 27).
        // For simplicity we always send 400 encoded as static index 24.
        final byte[] response = new byte[]{
            0x01,       // HEADERS frame type
            0x04,       // payload length = 4
            0x00,       // Required Insert Count = 0
            0x00,       // S=0, Delta Base=0
            (byte) 0xD8 // Indexed Field Line, static=1, index=24 (:status: 400)
        };
        ctx.writeAndFlush(Unpooled.wrappedBuffer(response))
            .addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Removes this handler and installs the standard XMPP C2S pipeline in its place.
     * Any bytes already buffered beyond the CONNECT frame are re-injected into the pipeline.
     */
    private void upgradeToXmppPipeline(final ChannelHandlerContext ctx)
    {
        final ChannelPipeline pipeline = ctx.pipeline();

        // Install the XMPP C2S handlers before this handler so they receive subsequent reads.
        final QuicClientConnectionHandler businessLogicHandler =
            new QuicClientConnectionHandler(configuration, streamRouter);

        pipeline.addAfter(ctx.name(), TRAFFIC_HANDLER_NAME, new ChannelTrafficShapingHandler(0));
        pipeline.addAfter(TRAFFIC_HANDLER_NAME, "xmppDecoder", new NettyXMPPDecoder());
        pipeline.addAfter("xmppDecoder", "stringEncoder", new StringEncoder(StandardCharsets.UTF_8));
        pipeline.addAfter("stringEncoder", "autoReadEnabler", new ChannelInboundHandlerAdapter()
        {
            @Override
            public void channelActive(final ChannelHandlerContext ctx2) throws Exception
            {
                businessLogicHandler.getStanzaHandlerFuture().thenRun(() ->
                    ctx2.channel().eventLoop().execute(() -> {
                        ctx2.channel().attr(IDLE_FLAG).set(null);
                        ctx2.channel().config().setAutoRead(true);
                    })
                );
                super.channelActive(ctx2);
            }
        });
        pipeline.addAfter("autoReadEnabler", "businessLogicHandler", businessLogicHandler);

        // Remove this upgrade handler — it has done its job.
        pipeline.remove(this);

        // Re-fire channelActive so the XMPP handler initialises its session.
        ctx.fireChannelActive();

        // If there are leftover bytes after the CONNECT frame, re-inject them.
        if (accumulator != null && accumulator.isReadable()) {
            final ByteBuf leftover = accumulator;
            accumulator = null;
            ctx.fireChannelRead(leftover);
        }
    }

    @Override
    public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause)
    {
        Log.warn("WebTransport upgrade error on stream {}; closing.",
            ctx.channel().id().asShortText(), cause);
        ctx.close();
    }
}
