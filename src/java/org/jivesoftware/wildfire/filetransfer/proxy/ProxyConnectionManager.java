/**
 * $RCSfile$
 * $Revision: 3762 $
 * $Date: 2006-04-12 18:07:15 -0500 (Mon, 12 Apr 2005) $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.filetransfer.proxy;

import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.filetransfer.FileTransferManager;
import org.jivesoftware.wildfire.filetransfer.FileTransferRejectedException;
import org.jivesoftware.wildfire.stats.Statistic;
import org.jivesoftware.wildfire.stats.StatisticsManager;
import org.jivesoftware.wildfire.stats.i18nStatistic;
import org.xmpp.packet.JID;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;

/**
 * Manages the connections to the proxy server. The connections go through two stages before
 * file transfer begins. The first stage is when the file transfer target initiates a connection
 * to this manager. Stage two is when the initiator connects, the manager will then match the two
 * connections using the unique SHA-1 hash defined in the SOCKS5 protocol.
 *
 * @author Alexander Wenckus
 */
public class ProxyConnectionManager {

    private static final String proxyTransferRate = "proxyTransferRate";

    private Map<String, ProxyTransfer> connectionMap;

    private final Object connectionLock = new Object();

    private ExecutorService executor = Executors.newCachedThreadPool();

    private Future<?> socketProcess;

    private int proxyPort;

    private FileTransferManager transferManager;

    private String className;

    private ServerSocketChannel serverChannel;

    public ProxyConnectionManager(FileTransferManager manager) {
        String cacheName = "File Transfer";
        connectionMap = new Cache<String, ProxyTransfer>(cacheName, -1, 1000 * 60 * 10);

        className = JiveGlobals.getProperty("provider.transfer.proxy",
                "org.jivesoftware.wildfire.filetransfer.proxy.DefaultProxyTransfer");

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
            public void run() {
                ServerSocket serverSocket;
                try {
                    serverChannel = ServerSocketChannel.open();
                    serverSocket = serverChannel.socket();
                    serverSocket.bind(new InetSocketAddress(bindInterface, port));
                }
                catch (IOException e) {
                    Log.error("Error binding server socket", e);
                    return;
                }
                while (serverSocket.isBound()) {
                    final SocketChannel channel;
                    try {
                        channel = serverChannel.accept();
                    }
                    catch (IOException e) {
                        if (serverChannel.isOpen()) {
                            Log.error("Error accepting proxy connection", e);
                            continue;
                        }
                        else {
                            break;
                        }
                    }
                    executor.submit(new Runnable() {
                        public void run() {
                            try {
                                processConnection(channel);
                            }
                            catch (IOException ie) {
                                Log.error("Error processing file transfer proxy connection",
                                        ie);
                                try {
                                    channel.close();
                                }
                                catch (IOException e) {
                                    /* Do Nothing */
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

    private void processConnection(SocketChannel connection) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(512);
        connection.read(buffer);
        buffer.flip();
        // first byte is version should be 5
        int b = buffer.get();
        if (b != 5) {
            throw new IOException("Only SOCKS5 supported");
        }

        // second byte number of authentication methods supported
        b = buffer.get();
        int[] auth = new int[b];
        for (int i = 0; i < b; i++) {
            auth[i] = buffer.get();
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
            throw new IOException("Authentication method not supported");
        }

        // No auth method so respond with success
        byte[] cmd = new byte[2];
        cmd[0] = (byte) 0x05;
        cmd[1] = (byte) 0x00;
        buffer.clear();
        buffer.put(cmd).flip();
        connection.write(buffer);

        String responseDigest = processIncomingSocks5Message(connection, buffer);
        try {
            synchronized (connectionLock) {
                ProxyTransfer transfer = connectionMap.get(responseDigest);
                if (transfer == null) {
                    transfer = createProxyTransfer(responseDigest, connection);
                    transferManager.registerProxyTransfer(responseDigest, transfer);
                    connectionMap.put(responseDigest, transfer);
                }
                else {
                    transfer.setInputChannel(connection);
                }
            }
            cmd = createOutgoingSocks5Message(0, responseDigest);
            buffer.clear();
            buffer.put(cmd).flip();
            connection.write(buffer);
        }
        catch (UnauthorizedException eu) {
            cmd = createOutgoingSocks5Message(2, responseDigest);
            buffer.clear();
            buffer.put(cmd).flip();
            connection.write(buffer);
            throw new IOException("Illegal proxy transfer");
        }
    }

    private ProxyTransfer createProxyTransfer(String transferDigest,
                                              WritableByteChannel targetSocket)
    {
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
        provider.setOutputChannel(targetSocket);
        return provider;
    }

    private static String processIncomingSocks5Message(SocketChannel in, ByteBuffer buffer)
            throws IOException {
        buffer.clear();
        // read the version and command
        int read = in.read(buffer);

        if (read < 5) {
            throw new IOException("Error reading Socks5 version and command");
        }
        buffer.position(5);

        // read the digest
        byte[] addr = new byte[buffer.get(4)];
        buffer.get(addr, 0, addr.length);

        return new String(addr);
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
        executor.shutdown();
        StatisticsManager.getInstance().removeStatistic(proxyTransferRate);
    }

    /**
     * Activates the stream, this method should be called when the initiator sends the activate
     * packet after both parties have connected to the proxy.
     *
     * @param initiator The initiator or sender of the file transfer.
     * @param target The target or reciever of the file transfer.
     * @param sid The sessionid the uniquely identifies the transfer between
     * the two participants.
     * @throws IllegalArgumentException This exception is thrown when the activated transfer does
     *                                  not exist or is missing one or both of the realted sockets.
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
            public void run() {
                try {
                    transferManager.fireFileTransferIntercept(transfer);
                }
                catch (FileTransferRejectedException e) {
                    notifyFailure(transfer, e);
                    return;
                }
                try {
                    transfer.doTransfer();
                }
                catch (IOException e) {
                    Log.error("Error during file transfer", e);
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
     * @param initiator The inititator of the stream negotiation
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
        if (serverChannel != null) {
            try {
                serverChannel.close();
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

        public double sample() {
            return (ProxyOutputChannel.amountTransfered.getAndSet(0) / 1000);
        }
    }
}