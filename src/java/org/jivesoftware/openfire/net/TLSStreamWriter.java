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
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;

/**
 * A <code>TLSStreamWriter</code> that returns a special OutputStream that hides the ByteBuffers
 * used by the underlying Channels.
 *
 * @author Hao Chen
 *
 */
public class TLSStreamWriter {

    /**
     * <code>TLSWrapper</code> is a TLS wrapper for connections requiring TLS protocol.
     */
    private TLSWrapper wrapper;

    private WritableByteChannel wbc;

    private ByteBuffer outAppData;

    public TLSStreamWriter(TLSWrapper tlsWrapper, Socket socket) throws IOException {
        wrapper = tlsWrapper;
        // DANIELE: Add code to use directly the socket channel
        if (socket.getChannel() != null) {
            wbc = ServerTrafficCounter.wrapWritableChannel(socket.getChannel());
        }
        else {
            wbc = Channels.newChannel(
                    ServerTrafficCounter.wrapOutputStream(socket.getOutputStream()));
        }
        outAppData = ByteBuffer.allocate(tlsWrapper.getAppBuffSize());
    }

    private void doWrite(ByteBuffer buff) throws IOException {

        if (buff == null) {
            // Possibly handshaking process
            buff = ByteBuffer.allocate(0);
        }

        if (wrapper == null) {
            writeToSocket(buff);
        } else {
            tlsWrite(buff);
        }
    }

    private void tlsWrite(ByteBuffer buf) throws IOException {
        ByteBuffer tlsBuffer;
        ByteBuffer tlsOutput;
        do {
            // TODO Consider optimizing by not creating new instances each time
            tlsBuffer = ByteBuffer.allocate(Math.min(buf.remaining(), wrapper.getAppBuffSize()));
            tlsOutput = ByteBuffer.allocate(wrapper.getNetBuffSize());

            while (tlsBuffer.hasRemaining() && buf.hasRemaining()) {
                tlsBuffer.put(buf.get());
            }

            tlsBuffer.flip();
            wrapper.wrap(tlsBuffer, tlsOutput);

            tlsOutput.flip();
            writeToSocket(tlsOutput);

            tlsOutput.clear();
        } while (buf.hasRemaining());
    }

    /*
     * Writes outNetData to the SocketChannel. <P> Returns true when the ByteBuffer has no remaining
     * data.
     */
    private boolean writeToSocket(ByteBuffer outNetData) throws IOException {
        wbc.write(outNetData);
        return !outNetData.hasRemaining();
    }

    public OutputStream getOutputStream() {
        return createOutputStream();
    }

    /*
     * Returns an output stream for a ByteBuffer. The write() methods use the relative ByteBuffer
     * put() methods.
     */
    private OutputStream createOutputStream() {
        return new OutputStream() {
            @Override
            public synchronized void write(int b) throws IOException {
                outAppData.put((byte) b);
                outAppData.flip();
                doWrite(outAppData);
                outAppData.clear();
            }

            @Override
            public synchronized void write(byte[] bytes, int off, int len) throws IOException {
                outAppData = resizeApplicationBuffer(bytes.length);
                outAppData.put(bytes, off, len);
                outAppData.flip();
                doWrite(outAppData);
                outAppData.clear();
            }
        };
    }

    private ByteBuffer resizeApplicationBuffer(int increment) {
        // TODO Creating new buffers and copying over old one may not scale. Consider using views. Thanks to Noah for the tip.
        if (outAppData.remaining() < increment) {
            ByteBuffer bb = ByteBuffer.allocate(outAppData.capacity() + wrapper.getAppBuffSize());
            outAppData.flip();
            bb.put(outAppData);
            return bb;
        } else {
            return outAppData;
        }
    }

}
