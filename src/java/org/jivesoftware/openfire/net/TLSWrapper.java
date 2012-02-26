/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.util.JiveGlobals;
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

    /*
     * Enables the JSSE system debugging system property:
     *
     * -Djavax.net.debug=all
     *
     * This gives a lot of low-level information about operations underway, including specific
     * handshake messages, and might be best examined after gaining some familiarity with this
     * application.
     */
    private static boolean debug = false;

    private SSLEngine tlsEngine;
    private SSLEngineResult tlsEngineResult;

    private int netBuffSize;
    private int appBuffSize;

    public TLSWrapper(Connection connection, boolean clientMode, boolean needClientAuth, String remoteServer) {

        boolean c2sConnection = (remoteServer == null);
        if (debug) {
            System.setProperty("javax.net.debug", "all");
        }

        String algorithm = JiveGlobals.getProperty("xmpp.socket.ssl.algorithm", "TLS");

        // Create/initialize the SSLContext with key material
        try {
            // First initialize the key and trust material.
            KeyStore ksKeys = SSLConfig.getKeyStore();
            String keypass = SSLConfig.getKeyPassword();

            KeyStore ksTrust = (c2sConnection ? SSLConfig.getc2sTrustStore() : SSLConfig.gets2sTrustStore());
            String trustpass = (c2sConnection ? SSLConfig.getc2sTrustPassword() : SSLConfig.gets2sTrustPassword());

            // KeyManager's decide which key material to use.
            KeyManager[] km = SSLJiveKeyManagerFactory.getKeyManagers(ksKeys, keypass);

            // TrustManager's decide whether to allow connections.
            TrustManager[] tm = SSLJiveTrustManagerFactory.getTrustManagers(ksTrust, trustpass);
            if (clientMode || needClientAuth) {
                if (c2sConnection) {
                    // Check if we can trust certificates presented by the client
                    tm = new TrustManager[]{new ClientTrustManager(ksTrust)};
                }
                else {
                    // Check if we can trust certificates presented by the server
                    tm = new TrustManager[]{new ServerTrustManager(remoteServer, ksTrust, connection)};
                }
            }

            SSLContext tlsContext = SSLContext.getInstance(algorithm);

            tlsContext.init(km, tm, null);

            /*
                * Configure the tlsEngine to act as a server in the SSL/TLS handshake. We're a server,
                * so no need to use host/port variant.
                *
                * The first call for a server is a NEED_UNWRAP.
                */
            tlsEngine = tlsContext.createSSLEngine();
            tlsEngine.setUseClientMode(clientMode);
            SSLSession sslSession = tlsEngine.getSession();

            netBuffSize = sslSession.getPacketBufferSize();
            appBuffSize = sslSession.getApplicationBufferSize();

        } catch (KeyManagementException e) {
            Log.error("TLSHandler startup problem.\n" + "  SSLContext initialisation failed.", e);
        } catch (NoSuchAlgorithmException e) {
            Log.error("TLSHandler startup problem.\n" + "  The " + algorithm + " does not exist", e);
        } catch (IOException e) {
            Log.error("TLSHandler startup problem.\n"
                    + "  the KeyStore or TrustStore does not exist", e);
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
        tlsEngineResult = tlsEngine.unwrap(net, out);
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
     * @throws SSLException
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
        TLSStatus status = null;
        if (tlsEngineResult != null && tlsEngineResult.getStatus() == Status.BUFFER_UNDERFLOW) {
            status = TLSStatus.UNDERFLOW;
        } else {
            if (tlsEngineResult != null && tlsEngineResult.getStatus() == Status.CLOSED) {
                status = TLSStatus.CLOSED;
            } else {
                switch (tlsEngine.getHandshakeStatus()) {
                case NEED_WRAP:
                    status = TLSStatus.NEED_WRITE;
                    break;
                case NEED_UNWRAP:
                    status = TLSStatus.NEED_READ;
                    break;
                default:
                    status = TLSStatus.OK;
                    break;
                }
            }
        }
        return status;
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
