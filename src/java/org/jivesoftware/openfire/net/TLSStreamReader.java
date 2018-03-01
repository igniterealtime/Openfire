/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

/**
 * A <code>TLSStreamReader</code> that returns a special InputStream that hides the ByteBuffers
 * used by the underlying Channels.
 * 
 * @author Hao Chen
 */
public class TLSStreamReader {

    /**
     * <code>TLSWrapper</code> is a TLS wrapper for connections requiring TLS protocol.
     */
    private TLSWrapper wrapper;

    private ReadableByteChannel rbc;

    /**
     * <code>inNetBB</code> buffer keeps data read from socket.
     */
    private ByteBuffer inNetBB;

    /**
     * <code>inAppBB</code> buffer keeps decypted data.
     */
    private ByteBuffer inAppBB;

    private TLSStatus lastStatus;

    public TLSStreamReader(TLSWrapper tlsWrapper, Socket socket) throws IOException {
        wrapper = tlsWrapper;
        // DANIELE: Add code to use directly the socket channel
        if (socket.getChannel() != null) {
            rbc = ServerTrafficCounter.wrapReadableChannel(socket.getChannel());
        }
        else {
            rbc = Channels.newChannel(
                    ServerTrafficCounter.wrapInputStream(socket.getInputStream()));
        }
        inNetBB = ByteBuffer.allocate(wrapper.getNetBuffSize());
        inAppBB = ByteBuffer.allocate(wrapper.getAppBuffSize());
    }

    /*
     * Read TLS encrpyted data from SocketChannel, and use <code>decrypt</code> method to decypt.
     */
    private void doRead() throws IOException {
        //System.out.println("doRead inNet position: " + inNetBB.position() + " capacity: " + inNetBB.capacity() + " (before read)");

        // Read from the channel and fill inNetBB with the encrypted data
        final int cnt = rbc.read(inNetBB);
        if (cnt > 0) {
            //System.out.println("doRead inNet position: " + inNetBB.position() + " capacity: " + inNetBB.capacity() + " (after read)");
            //System.out.println("doRead inAppBB (before decrypt) position: " + inAppBB.position() + " limit: " + inAppBB.limit() + " capacity: " + inAppBB.capacity());

            // Decode encrypted data
            inAppBB = decrypt(inNetBB, inAppBB);

            ///System.out.println("doRead inAppBB (after decrypt) position: " + inAppBB.position() + " limit: " + inAppBB.limit() + " capacity: " + inAppBB.capacity() + " lastStatus: " + lastStatus);

            if (lastStatus == TLSStatus.OK) {
                // All the data contained in inNetBB was read and decrypted so we can safely
                // set the position of inAppBB to 0 to process it.
                inAppBB.flip();
            }
            else {
                // Some data in inNetBB was not decrypted since it is not complete. A
                // bufferunderflow was detected since the TLS packet is not complete to be
                // decrypted. We need to read more data from the channel to decrypt the whole
                // TLS packet. The inNetBB byte buffer has been compacted so the read and
                // decrypted is discarded and only the unread and encrypted data is left in the
                // buffer. The inAppBB has been completed with the decrypted data and we must
                // leave the position at the end of the written so that in the next doRead the
                // decrypted data is appended to the end of the buffer.
                //System.out.println("Reading more data from the channel (UNDERFLOW state)");
                doRead();
            }
        } else {
            if (cnt == -1) {
                inAppBB.flip();
                rbc.close();
            }
        }
    }

    /*
     * This method uses <code>TLSWrapper</code> to decrypt TLS encrypted data.
     */
    private ByteBuffer decrypt(ByteBuffer input, ByteBuffer output) throws IOException {
        ByteBuffer out = output;
        input.flip();
        do {
            // Decode SSL/TLS network data and place it in the app buffer
            out = wrapper.unwrap(input, out);

            lastStatus = wrapper.getStatus();
        }
        while ((lastStatus == TLSStatus.NEED_READ || lastStatus == TLSStatus.OK) &&
                input.hasRemaining());

        if (input.hasRemaining()) {
            // Complete TLS packets have been read, decrypted and written to the output buffer.
            // However, the input buffer contains incomplete TLS packets that cannot be decrpted.
            // Discard the read data and keep the unread data in the input buffer. The channel will
            // be read again to obtain the missing data to complete the TLS packet. So in the next
            // round the TLS packet will be decrypted and written to the output buffer
            input.compact();
        } else {
            // All the encrypted data in the inpu buffer was decrypted so we can clear
            // the input buffer.
            input.clear();
        }

        return out;
    }

    public InputStream getInputStream() {
        return createInputStream();
    }

    /*
     * Returns an input stream for a ByteBuffer. The read() methods use the relative ByteBuffer
     * get() methods.
     */
    private InputStream createInputStream() {
        return new InputStream() {
            @Override
            public synchronized int read() throws IOException {
                doRead();
                if (!inAppBB.hasRemaining()) {
                    return -1;
                }
                return inAppBB.get();
            }

            @Override
            public synchronized int read(byte[] bytes, int off, int len) throws IOException {
                // Check if in the previous read the inAppBB ByteBuffer remained with unread data.
                // If all the data was consumed then read from the socket channel. Otherwise,
                // consume the data contained in the buffer.
                if (inAppBB.position() == 0) {
                    // Read from the channel the encrypted data, decrypt it and load it
                    // into inAppBB
                    doRead();
                }
                else {
                    //System.out.println("#createInputStream. Detected previously unread data. position: " + inAppBB.position());

                    // The inAppBB contains data from a previous read so set the position to 0
                    // to consume it
                    inAppBB.flip();
                }
                len = Math.min(len, inAppBB.remaining());
                if (len == 0) {
                    // Nothing was read so the end of stream should have been reached.
                    return -1;
                }
                inAppBB.get(bytes, off, len);
                // If the requested length is less than the limit of inAppBB then all the data
                // inside inAppBB was not read. In that case we need to discard the read data and
                // keep only the unread data to be consume the next time this method is called
                if (inAppBB.hasRemaining()) {
                    // Discard read data and move unread data to the begining of the buffer. Leave
                    // the position at the end of the buffer as a way to indicate that there is
                    // unread data
                    inAppBB.compact();

                    //System.out.println("#createInputStream. Data left unread. inAppBB compacted. position: " + inAppBB.position() + " limit: " + inAppBB.limit());
                }
                else {
                    // Everything was read so reset the buffer
                    inAppBB.clear();
                }
                return len;
            }
        };
    }
}
