/*
 * Copyright (C) 1999-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.filetransfer.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;

import org.jivesoftware.openfire.JMXManager;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.filetransfer.FileTransferManager;
import org.jivesoftware.openfire.filetransfer.FileTransferRejectedException;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegate;
import org.jivesoftware.openfire.mbean.ThreadPoolExecutorDelegateMBean;
import org.jivesoftware.openfire.stats.Statistic;
import org.jivesoftware.openfire.stats.StatisticsManager;
import org.jivesoftware.openfire.stats.i18nStatistic;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

import javax.management.ObjectName;

/**
 * Manages the connections to the proxy server. The connections go through two stages before
 * file transfer begins. The first stage is when the file transfer target initiates a connection
 * to this manager. Stage two is when the initiator connects, the manager will then match the two
 * connections using the unique SHA-1 hash defined in the SOCKS5 protocol.
 *
 * @author Alexander Wenckus
 */
public class ProxyConnectionManager {

    private static final Logger Log = LoggerFactory.getLogger(ProxyConnectionManager.class);

    /**
     * The number of threads to keep in the thread pool that powers proxy (SOCKS5) connections, even if they are idle.
     */
    public static final SystemProperty<Integer> EXECUTOR_CORE_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("provider.transfer.proxy.threadpool.size.core")
        .setMinValue(0)
        .setDefaultValue(0)
        .setDynamic(false)
        .build();

    /**
     * The maximum number of threads to allow in the thread pool that powers proxy (SOCKS5) connections.
     */
    public static final SystemProperty<Integer> EXECUTOR_MAX_POOL_SIZE = SystemProperty.Builder.ofType(Integer.class)
        .setKey("provider.transfer.proxy.threadpool.size.max")
        .setMinValue(1)
        .setDefaultValue(Integer.MAX_VALUE)
        .setDynamic(false)
        .build();

    /**
     * The number of threads in the thread pool that powers proxy (SOCKS5) connections is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating.
     */
    public static final SystemProperty<Duration> EXECUTOR_POOL_KEEP_ALIVE = SystemProperty.Builder.ofType(Duration.class)
        .setKey("provider.transfer.proxy.threadpool.keepalive")
        .setChronoUnit(ChronoUnit.SECONDS)
        .setDefaultValue(Duration.ofSeconds(60))
        .setDynamic(false)
        .build();

    private static final String proxyTransferRate = "proxyTransferRate";

    private Cache<String, ProxyTransfer> connectionMap;

    private final Object connectionLock = new Object();

    private final ThreadPoolExecutor executor;

    /**
     * Object name used to register delegate MBean (JMX) for the thread pool executor.
     */
    private ObjectName objectName;

    private Future<?> socketProcess;

    private ServerSocket serverSocket;

    private int proxyPort;

    private FileTransferManager transferManager;

    private String className;

    public ProxyConnectionManager(FileTransferManager manager) {
        executor = new ThreadPoolExecutor(
            EXECUTOR_CORE_POOL_SIZE.getValue(),
            EXECUTOR_MAX_POOL_SIZE.getValue(),
            EXECUTOR_POOL_KEEP_ALIVE.getValue().toSeconds(),
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new NamedThreadFactory( "proxy-connection-worker-", null, null, null ) );

        if (JMXManager.isEnabled()) {
            final ThreadPoolExecutorDelegateMBean mBean = new ThreadPoolExecutorDelegate(executor);
            objectName = JMXManager.tryRegister(mBean, ThreadPoolExecutorDelegateMBean.BASE_OBJECT_NAME + "proxy-connection");
        }

        String cacheName = "File Transfer";
        connectionMap = CacheFactory.createCache(cacheName);

        className = JiveGlobals.getProperty("provider.transfer.proxy",
                "org.jivesoftware.openfire.filetransfer.proxy.DefaultProxyTransfer");

        transferManager = manager;
        StatisticsManager.getInstance().addStatistic(proxyTransferRate, new ProxyTracker());
    }

    /*
    * Processes the clients connecting to the proxy matching the initiator and target together.
    * This is the main loop of the manager which will run until the process is canceled.
    */
    synchronized void processConnections(final InetAddress bindInterface, final int port) {
        if (socketProcess != null) {
            if (proxyPort == port) {
                return;
            }
        }
        reset();
        socketProcess = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port, -1, bindInterface);
                }
                catch (IOException e) {
                    Log.error("Error creating server socket", e);
                    return;
                }
                while (serverSocket.isBound()) {
                    final Socket socket;
                    try {
                        socket = serverSocket.accept();
                    }
                    catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            Log.error("Error accepting proxy connection", e);
                            continue;
                        }
                        else {
                            break;
                        }
                    }
                    executor.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                processConnection(socket);
                            }
                            catch (IOException ie) {
                                try {
                                    Log.info("Unable to process data send through inbound connection from {} to file transfer proxy: {}", socket.getInetAddress(), ie.getMessage());
                                    socket.close();
                                }
                                catch (IOException e) {
                                    Log.debug("An exception occurred while trying to close a socket after processing data delivered by it caused an exception.", e);
                                }
                            }
                        }
                    });
                }
            }
        });
        proxyPort = port;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    private void processConnection(Socket connection) throws IOException {
        OutputStream out = new DataOutputStream(connection.getOutputStream());
        InputStream in = new DataInputStream(connection.getInputStream());

        // first byte is version should be 5
        int b = in.read();
        if (b != 5) {
            throw new IOException("Only SOCKS5 supported. Peer is sending something that is incompatible.");
        }

        // second byte number of authentication methods supported
        b = in.read();
        int[] auth = new int[b];
        for (int i = 0; i < b; i++) {
            auth[i] = in.read();
        }

        int authMethod = -1;
        for (int anAuth : auth) {
            authMethod = (anAuth == 0 ? 0 : -1); // only auth method
            // 0, no
            // authentication,
            // supported
            if (authMethod == 0) {
                break;
            }
        }
        if (authMethod != 0) {
            throw new IOException("Authentication method requested by peer is not supported.");
        }

        // No auth method so respond with success
        byte[] cmd = new byte[2];
        cmd[0] = (byte) 0x05;
        cmd[1] = (byte) 0x00;
        out.write(cmd);

        String responseDigest = processIncomingSocks5Message(in);
        try {
            synchronized (connectionLock) {
                ProxyTransfer transfer = connectionMap.get(responseDigest);
                if (transfer == null) {
                    transfer = createProxyTransfer(responseDigest, connection);
                    transferManager.registerProxyTransfer(responseDigest, transfer);
                    connectionMap.put(responseDigest, transfer);
                }
                else {
                    transfer.setInputStream(connection.getInputStream());
                }
            }
            cmd = createOutgoingSocks5Message(0, responseDigest);
            out.write(cmd);
        }
        catch (UnauthorizedException eu) {
            cmd = createOutgoingSocks5Message(2, responseDigest);
            out.write(cmd);
            throw new IOException("Illegal proxy transfer");
        }
    }

    private ProxyTransfer createProxyTransfer(String transferDigest, Socket targetSocket)
            throws IOException {
        ProxyTransfer provider;
        try {
            Class c = ClassUtils.forName(className);
            provider = (ProxyTransfer) c.newInstance();
        }
        catch (Exception e) {
            Log.error("Error loading proxy transfer provider: " + className, e);
            provider = new DefaultProxyTransfer();
        }

        provider.setTransferDigest(transferDigest);
        provider.setOutputStream(targetSocket.getOutputStream());
        return provider;
    }

    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    private static String processIncomingSocks5Message(InputStream in)
            throws IOException {
        // read the version and command
        byte[] cmd = new byte[5];
        int read = in.read(cmd, 0, 5);
        if (read != 5) {
            throw new IOException("Error reading Socks5 version and command");
        }

        // read the digest
        byte[] addr = new byte[cmd[4]];
        read = in.read(addr, 0, addr.length);
        if (read != addr.length) {
            throw new IOException("Error reading provided address");
        }
        String digest = new String(addr);

        in.read();
        in.read();

        return digest;
    }

    private static byte[] createOutgoingSocks5Message(int cmd, String digest) {
        byte addr[] = digest.getBytes();

        byte[] data = new byte[7 + addr.length];
        data[0] = (byte) 5;
        data[1] = (byte) cmd;
        data[2] = (byte) 0;
        data[3] = (byte) 0x3;
        data[4] = (byte) addr.length;

        System.arraycopy(addr, 0, data, 5, addr.length);
        data[data.length - 2] = (byte) 0;
        data[data.length - 1] = (byte) 0;

        return data;
    }

    synchronized void shutdown() {
        disable();
        if (objectName != null) {
            JMXManager.tryUnregister(objectName);
            objectName = null;
        }
        executor.shutdown();
        StatisticsManager.getInstance().removeStatistic(proxyTransferRate);
    }

    /**
     * Activates the stream, this method should be called when the initiator sends the activate
     * packet after both parties have connected to the proxy.
     *
     * @param initiator The initiator or sender of the file transfer.
     * @param target The target or receiver of the file transfer.
     * @param sid The session id that uniquely identifies the transfer between the two participants.
     * @throws IllegalArgumentException This exception is thrown when the activated transfer does
     *                                  not exist or is missing one or both of the sockets.
     */
    void activate(JID initiator, JID target, String sid) {
        final String digest = createDigest(sid, initiator, target);

        ProxyTransfer temp;
        synchronized (connectionLock) {
            temp = connectionMap.get(digest);
        }
        final ProxyTransfer transfer = temp;
        // check to make sure we have all the required
        // information to start the transfer
        if (transfer == null || !transfer.isActivatable()) {
            throw new IllegalArgumentException("Transfer doesn't exist or is missing parameters");
        }

        transfer.setInitiator(initiator.toString());
        transfer.setTarget(target.toString());
        transfer.setSessionID(sid);
        transfer.setTransferFuture(executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    transferManager.fireFileTransferStart( transfer.getSessionID(), true );
                }
                catch (FileTransferRejectedException e) {
                    notifyFailure(transfer, e);
                    return;
                }
                try {
                    transfer.doTransfer();
                    transferManager.fireFileTransferCompleted( transfer.getSessionID(), true );
                }
                catch (IOException e) {
                    Log.error("Error during file transfer", e);
                    transferManager.fireFileTransferCompleted( transfer.getSessionID(), false );
                }
                finally {
                    connectionMap.remove(digest);
                }
            }
        }));
    }

    private void notifyFailure(ProxyTransfer transfer, FileTransferRejectedException e) {

    }

    /**
     * Creates the digest needed for a byte stream. It is the SHA1(sessionID +
     * initiator + target).
     *
     * @param sessionID The sessionID of the stream negotiation
     * @param initiator The initiator of the stream negotiation
     * @param target The target of the stream negotiation
     * @return SHA-1 hash of the three parameters
     */
    public static String createDigest(final String sessionID, final JID initiator,
                                      final JID target) {
        return StringUtils.hash(sessionID + initiator.getNode()
                + "@" + initiator.getDomain() + "/"
                + initiator.getResource()
                + target.getNode() + "@"
                + target.getDomain() + "/"
                + target.getResource(), "SHA-1");
    }

    public boolean isRunning() {
        return socketProcess != null && !socketProcess.isDone();
    }

    public void disable() {
        reset();
    }

    private void reset() {
        if (socketProcess != null) {
            socketProcess.cancel(true);
            socketProcess = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            }
            catch (IOException e) {
                Log.warn("Error closing proxy listening socket", e);
            }
        }
    }

    private static class ProxyTracker extends i18nStatistic {
        public ProxyTracker() {
            super("filetransferproxy.transfered", Statistic.Type.rate);
        }

        @Override
        public double sample() {
            return (ProxyOutputStream.amountTransferred.getAndSet(0) / 1000d);
        }

        @Override
        public boolean isPartialSample() {
            return true;
        }
    }
}
