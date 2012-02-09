/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.util.JiveGlobals;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

/**
 * TLSStreamHandler is responsible for securing plain connections by negotiating TLS. By creating
 * a new instance of this class the plain connection will be secured.
 *
 * @author Hao Chen
 */
public class TLSStreamHandler {

    private TLSStreamWriter writer;

    private TLSStreamReader reader;

    private TLSWrapper wrapper;

    private ReadableByteChannel rbc;
    private WritableByteChannel wbc;

    private SSLEngine tlsEngine;

    /*
      * During the initial handshake, keep track of the next SSLEngine operation that needs to occur:
      *
      * NEED_WRAP/NEED_UNWRAP
      *
      * Once the initial handshake has completed, we can short circuit handshake checks with
      * initialHSComplete.
      */
    private HandshakeStatus initialHSStatus;
    private boolean initialHSComplete;

    private int appBBSize;
    private int netBBSize;

    /*
      * All I/O goes through these buffers. It might be nice to use a cache of ByteBuffers so we're
      * not alloc/dealloc'ing ByteBuffer's for each new SSLEngine. Outbound application data is
      * supplied to us by our callers.
      */
    private ByteBuffer incomingNetBB;
    private ByteBuffer outgoingNetBB;

    private ByteBuffer appBB;

    /*
      * An empty ByteBuffer for use when one isn't available, say as a source buffer during initial
      * handshake wraps or for close operations.
      */
    private static ByteBuffer hsBB = ByteBuffer.allocate(0);

    /**
     * Creates a new TLSStreamHandler and secures the plain socket connection. When connecting
     * to a remote server then <tt>clientMode</tt> will be <code>true</code> and
     * <tt>remoteServer</tt> is the server name of the remote server. Otherwise <tt>clientMode</tt>
     * will be <code>false</code> and  <tt>remoteServer</tt> null.
     *
     * @param connection the connection to secure
     * @param socket the plain socket connection to secure
     * @param clientMode boolean indicating if this entity is a client or a server.
     * @param remoteServer server name of the remote server we are connecting to or <tt>null</tt>
     *        when not in client mode.
     * @param needClientAuth boolean that indicates if client should authenticate during the TLS
     *        negotiation. This option is only required when the client is a server since
     *        EXTERNAL SASL is going to be used.
     * @throws java.io.IOException
     */
    public TLSStreamHandler(Connection connection, Socket socket, boolean clientMode, String remoteServer,
                            boolean needClientAuth) throws IOException {
        wrapper = new TLSWrapper(connection, clientMode, needClientAuth, remoteServer);
        tlsEngine = wrapper.getTlsEngine();
        reader = new TLSStreamReader(wrapper, socket);
        writer = new TLSStreamWriter(wrapper, socket);

        // DANIELE: Add code to use directly the socket-channel.
        if (socket.getChannel() != null) {
            rbc = socket.getChannel();
            wbc = socket.getChannel();
        }
        else {
            rbc = Channels.newChannel(socket.getInputStream());
            wbc = Channels.newChannel(socket.getOutputStream());
        }
        initialHSStatus = HandshakeStatus.NEED_UNWRAP;
        initialHSComplete = false;

        netBBSize = tlsEngine.getSession().getPacketBufferSize();
        appBBSize = tlsEngine.getSession().getApplicationBufferSize();

        incomingNetBB = ByteBuffer.allocate(netBBSize);
        outgoingNetBB = ByteBuffer.allocate(netBBSize);
        outgoingNetBB.position(0);
        outgoingNetBB.limit(0);

        appBB = ByteBuffer.allocate(appBBSize);

        if (clientMode) {
            socket.setSoTimeout(0);
            socket.setKeepAlive(true);
            initialHSStatus = HandshakeStatus.NEED_WRAP;
            tlsEngine.beginHandshake();
        }
        else if (needClientAuth) {
            // Only REQUIRE client authentication if we are fully verifying certificates
            if (JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify", true) &&
                    JiveGlobals.getBooleanProperty("xmpp.server.certificate.verify.chain", true) &&
                    !JiveGlobals
                            .getBooleanProperty("xmpp.server.certificate.accept-selfsigned", false))
            {
                tlsEngine.setNeedClientAuth(true);
            }
            else {
                // Just indicate that we would like to authenticate the client but if client
                // certificates are self-signed or have no certificate chain then we are still
                // good
                tlsEngine.setWantClientAuth(true);
            }
        }
    }

    public InputStream getInputStream(){
        return reader.getInputStream();
    }

    public OutputStream getOutputStream(){
        return writer.getOutputStream();
    }

    void start() throws IOException {
        while (!initialHSComplete) {
            initialHSComplete = doHandshake(null);
        }
    }

    private boolean doHandshake(SelectionKey sk) throws IOException {

        SSLEngineResult result;

        if (initialHSComplete) {
            return initialHSComplete;
        }

        /*
           * Flush out the outgoing buffer, if there's anything left in it.
           */
        if (outgoingNetBB.hasRemaining()) {

            if (!flush(outgoingNetBB)) {
                return false;
            }

            // See if we need to switch from write to read mode.

            switch (initialHSStatus) {

            /*
                * Is this the last buffer?
                */
            case FINISHED:
                initialHSComplete = true;

            case NEED_UNWRAP:
                if (sk != null) {
                    sk.interestOps(SelectionKey.OP_READ);
                }
                break;
            }

            return initialHSComplete;
        }

        switch (initialHSStatus) {

        case NEED_UNWRAP:
            if (rbc.read(incomingNetBB) == -1) {
                tlsEngine.closeInbound();
                return initialHSComplete;
            }

            needIO: while (initialHSStatus == HandshakeStatus.NEED_UNWRAP) {
                /*
                     * Don't need to resize requestBB, since no app data should be generated here.
                     */
                incomingNetBB.flip();
                result = tlsEngine.unwrap(incomingNetBB, appBB);
                incomingNetBB.compact();

                initialHSStatus = result.getHandshakeStatus();

                switch (result.getStatus()) {

                case OK:
                    switch (initialHSStatus) {
                    case NOT_HANDSHAKING:
                        throw new IOException("Not handshaking during initial handshake");

                    case NEED_TASK:
                        initialHSStatus = doTasks();
                        break;

                    case FINISHED:
                        initialHSComplete = true;
                        break needIO;
                    }

                    break;

                case BUFFER_UNDERFLOW:
                    /*
                          * Need to go reread the Channel for more data.
                          */
                    if (sk != null) {
                        sk.interestOps(SelectionKey.OP_READ);
                    }
                    break needIO;

                default: // BUFFER_OVERFLOW/CLOSED:
                    throw new IOException("Received" + result.getStatus()
                            + "during initial handshaking");
                }
            }

            /*
                * Just transitioned from read to write.
                */
            if (initialHSStatus != HandshakeStatus.NEED_WRAP) {
                break;
            }

        // Fall through and fill the write buffers.

        case NEED_WRAP:
            /*
                * The flush above guarantees the out buffer to be empty
                */
            outgoingNetBB.clear();
            result = tlsEngine.wrap(hsBB, outgoingNetBB);
            outgoingNetBB.flip();

            initialHSStatus = result.getHandshakeStatus();

            switch (result.getStatus()) {
            case OK:

                if (initialHSStatus == HandshakeStatus.NEED_TASK) {
                    initialHSStatus = doTasks();
                }

                if (sk != null) {
                    sk.interestOps(SelectionKey.OP_WRITE);
                }

                break;

            default: // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
                throw new IOException("Received" + result.getStatus()
                        + "during initial handshaking");
            }
            break;

        default: // NOT_HANDSHAKING/NEED_TASK/FINISHED
            throw new RuntimeException("Invalid Handshaking State" + initialHSStatus);
        } // switch

        return initialHSComplete;
    }

    /*
      * Writes ByteBuffer to the SocketChannel. Returns true when the ByteBuffer has no remaining
      * data.
      */
    private boolean flush(ByteBuffer bb) throws IOException {
        wbc.write(bb);
        return !bb.hasRemaining();
    }

    /*
      * Do all the outstanding handshake tasks in the current Thread.
      */
    private SSLEngineResult.HandshakeStatus doTasks() {

        Runnable runnable;

        /*
           * We could run this in a separate thread, but do in the current for now.
           */
        while ((runnable = tlsEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
        return tlsEngine.getHandshakeStatus();
    }

    /**
     * Closes the channels that will end up closing the input and output streams of the connection.
     * The channels implement the InterruptibleChannel interface so any other thread that was
     * blocked in an I/O operation will be interrupted and will get an exception.
     *
     * @throws IOException if an I/O error occurs.
     */
    public void close() throws IOException {
        wbc.close();
        rbc.close();
    }

    /**
     * Returns the SSLSession in use. The session specifies a particular cipher suite which
     * is being actively used by all connections in that session, as well as the identities
     * of the session's client and server.
     *
     * @return the SSLSession in use.
     */
    public SSLSession getSSLSession() {
        return tlsEngine.getSession();
    }
}
