/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.jivesoftware.openfire.nio;

import java.io.IOException;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilter;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoFilterChain;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.CompressionFilter;
import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;

/**
 * An {@link IoFilter} which compresses all data using
 * <a href="http://www.jcraft.com/jzlib/">JZlib</a>.
 * Support for the LZW (DLCZ) algorithm is also planned.
 * <p>
 * This filter only supports compression using the <tt>PARTIAL FLUSH</tt> method,
 * since that is the only method useful when doing stream level compression.
 * <p>
 * This filter supports compression/decompression of the input and output
 * channels selectively.  It can also be enabled/disabled on the fly.
 * <p>
 * This filter does not discard the zlib objects, keeping them around for the
 * entire life of the filter.  This is because the zlib dictionary needs to
 * be built up over time, which is used during compression and decompression.
 * Over time, as repetitive data is sent over the wire, the compression efficiency
 * steadily increases.
 * <p>
 * Note that the zlib header is written only once. It is not necessary that
 * the data received after processing by this filter may not be complete due
 * to packet fragmentation.
 * <p>
 * It goes without saying that the other end of this stream should also have a
 * compatible compressor/decompressor using the same algorithm.
 *
 * @author The Apache Directory Project (mina-dev@directory.apache.org)
 * @version $Rev: 629330 $, $Date: 2008-02-20 12:18:28 +0900 (Wed, 20 Feb 2008) $
 */
public class AdvancedCompressionFilter extends IoFilterAdapter {
    /**
     * Max compression level.  Will give the highest compression ratio, but
     * will also take more cpu time and is the slowest.
     */
    public static final int COMPRESSION_MAX = org.apache.mina.filter.support.Zlib.COMPRESSION_MAX;

    /**
     * Provides the best speed at the price of a low compression ratio.
     */
    public static final int COMPRESSION_MIN = org.apache.mina.filter.support.Zlib.COMPRESSION_MIN;

    /**
     * No compression done on the data.
     */
    public static final int COMPRESSION_NONE = org.apache.mina.filter.support.Zlib.COMPRESSION_NONE;

    /**
     * The default compression level used. Provides the best balance
     * between speed and compression
     */
    public static final int COMPRESSION_DEFAULT = org.apache.mina.filter.support.Zlib.COMPRESSION_DEFAULT;

    /**
     * A session attribute that stores the {@link org.apache.mina.filter.support.Zlib} object used for compression.
     */
    private static final String DEFLATER = CompressionFilter.class.getName()
            + ".Deflater";

    /**
     * A session attribute that stores the {@link org.apache.mina.filter.support.Zlib} object used for decompression.
     */
    private static final String INFLATER = CompressionFilter.class.getName()
            + ".Inflater";

    /**
     * A flag that allows you to disable compression once.
     */
    public static final String DISABLE_COMPRESSION_ONCE = CompressionFilter.class
            .getName()
            + ".DisableCompressionOnce";

    private boolean compressInbound = true;

    private boolean compressOutbound = true;

    private int compressionLevel;

    private int deflateWindowBits = 15;
    private int inflateWindowBits = 15;
    private int memLevel = 8;

    /**
     * Creates a new instance which compresses outboud data and decompresses
     * inbound data with default compression level.
     */
    public AdvancedCompressionFilter() {
        this(true, true, COMPRESSION_DEFAULT);
    }

    /**
     * Creates a new instance which compresses outboud data and decompresses
     * inbound data with the specified <tt>compressionLevel</tt>.
     *
     * @param compressionLevel the level of compression to be used. Must
     *                         be one of {@link #COMPRESSION_DEFAULT},
     *                         {@link #COMPRESSION_MAX},
     *                         {@link #COMPRESSION_MIN}, and
     *                         {@link #COMPRESSION_NONE}.
     */
    public AdvancedCompressionFilter(final int compressionLevel) {
        this(true, true, compressionLevel);
    }

    /**
     * Creates a new instance.
     *
     * @param compressInbound <tt>true</tt> if data read is to be decompressed
     * @param compressOutbound <tt>true</tt> if data written is to be compressed
     * @param compressionLevel the level of compression to be used. Must
     *                         be one of {@link #COMPRESSION_DEFAULT},
     *                         {@link #COMPRESSION_MAX},
     *                         {@link #COMPRESSION_MIN}, and
     *                         {@link #COMPRESSION_NONE}.
     */
    public AdvancedCompressionFilter(final boolean compressInbound,
            final boolean compressOutbound, final int compressionLevel) {
        this(compressInbound,compressOutbound,compressionLevel,15,15,8);
    }

    /**
     * Creates a new instance.
     *
     * @param compressInbound <tt>true</tt> if data read is to be decompressed
     * @param compressOutbound <tt>true</tt> if data written is to be compressed
     * @param compressionLevel the level of compression to be used. Must
     *                         be one of {@link #COMPRESSION_DEFAULT},
     *                         {@link #COMPRESSION_MAX},
     *                         {@link #COMPRESSION_MIN}, and
     *                         {@link #COMPRESSION_NONE}.
     * @param inflateWindowBits the windowBits parameter for the inflater
     * @param deflateWindowBits the windowBits parameter for the deflater
     * @param memLevel the memLevel parameter for the deflater
     */
    public AdvancedCompressionFilter(final boolean compressInbound,
            final boolean compressOutbound, final int compressionLevel,
            final int inflateWindowBits, final int deflateWindowBits,
            final int memLevel) {
        this.compressionLevel = compressionLevel;
        this.compressInbound = compressInbound;
        this.compressOutbound = compressOutbound;
        this.inflateWindowBits = inflateWindowBits;
        this.deflateWindowBits = deflateWindowBits;
        this.memLevel = memLevel;
    }

    public void messageReceived(NextFilter nextFilter, IoSession session,
            Object message) throws Exception {
        if (!compressInbound || !(message instanceof ByteBuffer)) {
            nextFilter.messageReceived(session, message);
            return;
        }

        Zlib inflater = (Zlib) session.getAttribute(INFLATER);
        if (inflater == null) {
            throw new IllegalStateException();
        }

        ByteBuffer inBuffer = (ByteBuffer) message;
        ByteBuffer outBuffer = inflater.inflate(inBuffer);
        inBuffer.release();
        nextFilter.messageReceived(session, outBuffer);
    }

    /*
     * @see org.apache.mina.common.IoFilter#filterWrite(org.apache.mina.common.IoFilter.NextFilter, org.apache.mina.common.IoSession, org.apache.mina.common.IoFilter.WriteRequest)
     */
    public void filterWrite(NextFilter nextFilter, IoSession session,
            WriteRequest writeRequest) throws IOException {
        if (!compressOutbound) {
            nextFilter.filterWrite(session, writeRequest);
            return;
        }

        if (session.containsAttribute(DISABLE_COMPRESSION_ONCE)) {
            // Remove the marker attribute because it is temporary.
            session.removeAttribute(DISABLE_COMPRESSION_ONCE);
            nextFilter.filterWrite(session, writeRequest);
            return;
        }

        Zlib deflater = (Zlib) session.getAttribute(DEFLATER);
        if (deflater == null) {
            throw new IllegalStateException();
        }

        ByteBuffer inBuffer = (ByteBuffer) writeRequest.getMessage();
        if (!inBuffer.hasRemaining()) {
            // Ignore empty buffers
            nextFilter.filterWrite(session, writeRequest);
        } else {
            ByteBuffer outBuf;
            synchronized(deflater){
                outBuf = deflater.deflate(inBuffer);
            }
            inBuffer.release();
            nextFilter.filterWrite(session, new WriteRequest(outBuf,
                    writeRequest.getFuture()));
        }
    }

    public void onPreAdd(IoFilterChain parent, String name,
            NextFilter nextFilter) throws Exception {
        if (parent.contains(CompressionFilter.class)) {
            throw new IllegalStateException(
                    "A filter chain cannot contain more than"
                            + " one Stream Compression filter.");
        }

        Zlib deflater = new Zlib(compressionLevel, inflateWindowBits, deflateWindowBits, memLevel, org.apache.mina.filter.support.Zlib.MODE_DEFLATER);
        Zlib inflater = new Zlib(compressionLevel, inflateWindowBits, deflateWindowBits, memLevel, org.apache.mina.filter.support.Zlib.MODE_INFLATER);

        IoSession session = parent.getSession();

        session.setAttribute(DEFLATER, deflater);
        session.setAttribute(INFLATER, inflater);
    }

    /**
     * Returns <tt>true</tt> if incoming data is being compressed.
     */
    public boolean isCompressInbound() {
        return compressInbound;
    }

    /**
     * Sets if incoming data has to be compressed.
     */
    public void setCompressInbound(boolean compressInbound) {
        this.compressInbound = compressInbound;
    }

    /**
     * Returns <tt>true</tt> if the filter is compressing data being written.
     */
    public boolean isCompressOutbound() {
        return compressOutbound;
    }

    /**
     * Set if outgoing data has to be compressed.
     */
    public void setCompressOutbound(boolean compressOutbound) {
        this.compressOutbound = compressOutbound;
    }

    public void onPostRemove(IoFilterChain parent, String name,
            NextFilter nextFilter) throws Exception {
        super.onPostRemove(parent, name, nextFilter);
        IoSession session = parent.getSession();
        if (session == null) {
            return;
        }

        Zlib inflater = (Zlib) session.getAttribute(INFLATER);
        Zlib deflater = (Zlib) session.getAttribute(DEFLATER);
        if (deflater != null) {
            deflater.cleanUp();
        }

        if (inflater != null) {
            inflater.cleanUp();
        }
    }

        /**
     * A helper class for interfacing with the JZlib library. This class acts both
     * as a compressor and decompressor, but only as one at a time.  The only
     * flush method supported is <tt>Z_SYNC_FLUSH</tt> also known as <tt>Z_PARTIAL_FLUSH</tt>
     *
     * @author The Apache Directory Project (mina-dev@directory.apache.org)
     * @version $Rev: 629330 $, $Date: 2008-02-20 12:18:28 +0900 (Wed, 20 Feb 2008) $
     */
    public static class Zlib {
        public static final int COMPRESSION_MAX = JZlib.Z_BEST_COMPRESSION;

        public static final int COMPRESSION_MIN = JZlib.Z_BEST_SPEED;

        public static final int COMPRESSION_NONE = JZlib.Z_NO_COMPRESSION;

        public static final int COMPRESSION_DEFAULT = JZlib.Z_DEFAULT_COMPRESSION;

        public static final int MODE_DEFLATER = 1;

        public static final int MODE_INFLATER = 2;

        private ZStream zStream = null;

        private int mode = -1;

        /**
         * @param compressionLevel the level of compression that should be used
         * @param inflateWindowBits the windowBits parameter for the inflater
         * @param deflateWindowBits the windowBits parameter for the deflater
         * @param memLevel the memLevel parameter for the deflater
         * @param mode             the mode in which the instance will operate. Can be either
         *                         of <tt>MODE_DEFLATER</tt> or <tt>MODE_INFLATER</tt>
         */
        public Zlib(int compressionLevel, int inflateWindowBits, int deflateWindowBits,
                                int memLevel, int mode) {
            if(compressionLevel < JZlib.Z_NO_COMPRESSION ||
                    compressionLevel > JZlib.Z_BEST_COMPRESSION) {
                throw new IllegalArgumentException("invalid compression level specified");
            }

            // create a new instance of ZStream. This will be done only once.
            zStream = new ZStream();

            switch (mode) {
                case MODE_DEFLATER:
                    zStream.deflateInit2(compressionLevel,deflateWindowBits,memLevel,0);
                    break;
                case MODE_INFLATER:
                    zStream.inflateInit(inflateWindowBits);
                    break;
                default:
                    throw new IllegalArgumentException("invalid mode specified");
            }
            this.mode = mode;
        }

            /**
         * @param inBuffer the {@link ByteBuffer} to be decompressed. The contents
         *                 of the buffer are transferred into a local byte array and the buffer is
         *                 flipped and returned intact.
         * @return the decompressed data. If not passed to the MINA methods that
         *         release the buffer automatically, the buffer has to be manually released
         * @throws IOException if the decompression of the data failed for some reason.
         */
        public ByteBuffer inflate(ByteBuffer inBuffer) throws IOException {
            if (mode == MODE_DEFLATER) {
                throw new IllegalStateException("not initialized as INFLATER");
            }

            byte[] inBytes = new byte[inBuffer.remaining()];
            inBuffer.get(inBytes).flip();

            // We could probably do this better, if we're willing to return multiple buffers
            //  (e.g. with a callback function)
            byte[] outBytes = new byte[inBytes.length * 2];
            ByteBuffer outBuffer = ByteBuffer.allocate(outBytes.length);
            outBuffer.setAutoExpand(true);

            zStream.next_in = inBytes;
            zStream.next_in_index = 0;
            zStream.avail_in = inBytes.length;
            zStream.next_out = outBytes;
            zStream.next_out_index = 0;
            zStream.avail_out = outBytes.length;
            int retval = 0;

            do {
                retval = zStream.inflate(JZlib.Z_SYNC_FLUSH);
                switch (retval) {
                    case JZlib.Z_OK:
                        // completed decompression, lets copy data and get out
                    case JZlib.Z_BUF_ERROR:
                        // need more space for output. store current output and get more
                        outBuffer.put(outBytes, 0, zStream.next_out_index);
                        zStream.next_out_index = 0;
                        zStream.avail_out = outBytes.length;
                        break;
                    default:
                        // unknown error
                        outBuffer.release();
                        outBuffer = null;
                        if (zStream.msg == null)
                            throw new IOException("Unknown error. Error code : "
                                    + retval);
                        else
                            throw new IOException("Unknown error. Error code : "
                                    + retval + " and message : " + zStream.msg);
                }
            } while (zStream.avail_in > 0);

            return outBuffer.flip();
        }

        /**
         * @param inBuffer the buffer to be compressed. The contents are transferred
         *                 into a local byte array and the buffer is flipped and returned intact.
         * @return the buffer with the compressed data. If not passed to any of the
         *         MINA methods that automatically release the buffer, the buffer has to be
         *         released manually.
         * @throws IOException if the compression of teh buffer failed for some reason
         */
        public ByteBuffer deflate(ByteBuffer inBuffer) throws IOException {
            if (mode == MODE_INFLATER) {
                throw new IllegalStateException("not initialized as DEFLATER");
            }

            byte[] inBytes = new byte[inBuffer.remaining()];
            inBuffer.get(inBytes).flip();

            // If we're playing with windowBits/memLevel, we need
            // 13.5% + 11 bytes of extra space
            int outLen = (int) Math.round(inBytes.length * 1.135) + 12;
            byte[] outBytes = new byte[outLen];

            zStream.next_in = inBytes;
            zStream.next_in_index = 0;
            zStream.avail_in = inBytes.length;
            zStream.next_out = outBytes;
            zStream.next_out_index = 0;
            zStream.avail_out = outBytes.length;

            int retval = zStream.deflate(JZlib.Z_SYNC_FLUSH);
            if (retval != JZlib.Z_OK) {
                outBytes = null;
                inBytes = null;
                throw new IOException("Compression failed with return value : "
                        + retval);
            }

            ByteBuffer outBuf = ByteBuffer
                    .wrap(outBytes, 0, zStream.next_out_index);

            return outBuf;
        }

        /**
         * Cleans up the resources used by the compression library.
         */
        public void cleanUp() {
            if (zStream != null)
                zStream.free();
        }
    }
}