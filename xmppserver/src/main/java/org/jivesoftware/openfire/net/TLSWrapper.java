/*
 * Copyright (C) 2005-2008 Jive Software and Artur Hefczyc. All rights reserved.
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

import java.nio.ByteBuffer;
import java.security.*;
import java.util.Arrays;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.EncryptionArtifactFactory;
import org.jivesoftware.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates and initializes the SSLContext instance to use to secure the plain connection. This
 * class is also responsible for encoding and decoding the encrypted data and place it into
 * the corresponding the {@link ByteBuffer}.
 *
 * @author Artur Hefczyc
 * @author Hao Chen
 */
public class TLSWrapper {

    private static final Logger Log = LoggerFactory.getLogger(TLSWrapper.class);

    /*
     * Enables logging of the SSLEngine operations.
     */
    private boolean logging = false;

    private SSLEngine tlsEngine;
    private SSLEngineResult tlsEngineResult;

    private int netBuffSize;
    private int appBuffSize;

    /**
     * @deprecated Use the other constructor.
     * @param connection the connection to wrap
     * @param clientMode {@code true} to use client mode, {@code false} to use server mode
     * @param needClientAuth unused parameter
     * @param remoteServer unused parameter
     */
    @Deprecated
    public TLSWrapper(Connection connection, boolean clientMode, boolean needClientAuth, String remoteServer)
    {
        this(
            connection.getConfiguration(),
            clientMode
        );
    }

    public TLSWrapper(ConnectionConfiguration configuration, boolean clientMode ) {

        try
        {
            final EncryptionArtifactFactory factory = new EncryptionArtifactFactory( configuration );
            if ( clientMode )
            {
                tlsEngine = factory.createClientModeSSLEngine();
            }
            else
            {
                tlsEngine = factory .createServerModeSSLEngine();
            }

            final SSLSession sslSession = tlsEngine.getSession();

            netBuffSize = sslSession.getPacketBufferSize();
            appBuffSize = sslSession.getApplicationBufferSize();
        }
        catch ( NoSuchAlgorithmException | KeyManagementException | KeyStoreException | UnrecoverableKeyException ex )
        {
            Log.error("TLSHandler startup problem. SSLContext initialisation failed.", ex );
        }
    }

    public int getNetBuffSize() {
        return netBuffSize;
    }

    public int getAppBuffSize() {
        return appBuffSize;
    }

    /**
     * Returns whether unwrap(ByteBuffer, ByteBuffer) will accept any more inbound data messages and
     * whether wrap(ByteBuffer, ByteBuffer) will produce any more outbound data messages.
     *
     * @return true if the TLSHandler will not consume anymore network data and will not produce any
     *         anymore network data.
     */
    public boolean isEngineClosed() {
        return (tlsEngine.isOutboundDone() && tlsEngine.isInboundDone());
    }

    public void enableLogging(boolean logging) {
        this.logging = logging;
    }

    /**
     * Attempts to decode SSL/TLS network data into a subsequence of plaintext application data
     * buffers. Depending on the state of the TLSWrapper, this method may consume network data
     * without producing any application data (for example, it may consume handshake data.)
     *
     * If this TLSWrapper has not yet started its initial handshake, this method will automatically
     * start the handshake.
     *
     * @param net a ByteBuffer containing inbound network data
     * @param app a ByteBuffer to hold inbound application data
     * @return a ByteBuffer containing inbound application data
     * @throws SSLException A problem was encountered while processing the data that caused the
     *             TLSHandler to abort.
     */
    public ByteBuffer unwrap(ByteBuffer net, ByteBuffer app) throws SSLException {
        ByteBuffer out = app;
        out = resizeApplicationBuffer(out);// guarantees enough room for unwrap

        // Record a hex dump of the buffer, but only when logging on level 'debug'.
        // Create the dump before the buffer is being passed to tlsEngine, to ensure
        // that the original content of the buffer is logged.
        String hexDump = null;
        if ( Log.isDebugEnabled() )
        {
            final ByteBuffer bb = net.duplicate();
            final byte[] data = Arrays.copyOf( bb.array(), bb.limit() );
            hexDump = StringUtils.encodeHex( data );
        }

        try {
            tlsEngineResult = tlsEngine.unwrap( net, out );
        } catch ( SSLException e ) {
            if ( e.getMessage().startsWith( "Unsupported record version Unknown-" ) ) {
                Log.debug( "Buffer that wasn't TLS: {}", hexDump );
                throw new SSLException( "We appear to have received plain text data where we expected encrypted data. A common cause for this is a peer sending us a plain-text error message when it shouldn't send a message, but close the socket instead).", e );
            }
            else {
                throw e;
            }
        }
        log("server unwrap: ", tlsEngineResult);
        if (tlsEngineResult.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            // If the result indicates that we have outstanding tasks to do, go
            // ahead and run them in this thread.
            doTasks();
        }
        return out;
    }

    /**
     * Attempts to encode a buffer of plaintext application data into TLS network data. Depending on
     * the state of the TLSWrapper, this method may produce network data without consuming any
     * application data (for example, it may generate handshake data).
     *
     * If this TLSWrapper has not yet started its initial handshake, this method will automatically
     * start the handshake.
     *
     * @param app a ByteBuffer containing outbound application data
     * @param net a ByteBuffer to hold outbound network data
     * @throws SSLException A problem was encountered while processing the data that caused the
     *             TLSWrapper to abort.
     */
    public void wrap(ByteBuffer app, ByteBuffer net) throws SSLException {
        tlsEngineResult = tlsEngine.wrap(app, net);
        log("server wrap: ", tlsEngineResult);
        if (tlsEngineResult.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
            // If the result indicates that we have outstanding tasks to do, go
            // ahead and run them in this thread.
            doTasks();
        }
    }

    /**
     * Signals that no more outbound application data will be sent on this TLSHandler.
     *
     * @throws SSLException never
     */
    public void close() throws SSLException {
        // Indicate that application is done with engine
        tlsEngine.closeOutbound();
    }

    /**
     * Returns the current status for this TLSHandler.
     *
     * @return the current TLSStatus
     */
    public TLSStatus getStatus() {
        if (tlsEngineResult != null && tlsEngineResult.getStatus() == Status.BUFFER_UNDERFLOW) {
            return TLSStatus.UNDERFLOW;
        } else {
            if (tlsEngineResult != null && tlsEngineResult.getStatus() == Status.CLOSED) {
                return TLSStatus.CLOSED;
            } else {
                switch (tlsEngine.getHandshakeStatus()) {
                case NEED_WRAP:
                    return TLSStatus.NEED_WRITE;
                case NEED_UNWRAP:
                    return TLSStatus.NEED_READ;
                default:
                    return TLSStatus.OK;
                }
            }
        }
    }

    private ByteBuffer resizeApplicationBuffer(ByteBuffer app) {
        // TODO Creating new buffers and copying over old one may not scale and may even be a
        // security risk. Consider using views. Thanks to Noah for the tip.
        if (app.remaining() < appBuffSize) {
            ByteBuffer bb = ByteBuffer.allocate(app.capacity() + appBuffSize);
            app.flip();
            bb.put(app);
            return bb;
        } else {
            return app;
        }
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

    /*
      * Logging code
      */
    private boolean resultOnce = true;

    private void log(String str, SSLEngineResult result) {
        if (!logging) {
            return;
        }
        if (resultOnce) {
            resultOnce = false;
            Log.info("The format of the SSLEngineResult is: \n"
                    + "\t\"getStatus() / getHandshakeStatus()\" +\n"
                    + "\t\"bytesConsumed() / bytesProduced()\"\n");
        }
        HandshakeStatus hsStatus = result.getHandshakeStatus();
        Log.info(str + result.getStatus() + "/" + hsStatus + ", " + result.bytesConsumed() + "/"
                + result.bytesProduced() + " bytes");
        if (hsStatus == HandshakeStatus.FINISHED) {
            Log.info("\t...ready for application data");
        }
    }

    protected SSLEngine getTlsEngine() {
        return tlsEngine;
    }
}
