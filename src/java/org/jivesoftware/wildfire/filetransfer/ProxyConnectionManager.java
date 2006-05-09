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
package org.jivesoftware.wildfire.filetransfer;

import org.jivesoftware.util.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.filetransfer.spi.DefaultProxyTransfer;
import org.xmpp.packet.JID;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Manages the connections to the proxy server. The connections go through two stages before
 * file transfer begins. The first stage is when the file transfer target initiates a connection
 * to this manager. Stage two is when the initiator connects, the manager will then match the two
 * connections using the unique SHA-1 hash defined in the SOCKS5 protocol.
 *
 * @author Alexander Wenckus
 */
public class ProxyConnectionManager {

    private Map<String, ProxyTransfer> connectionMap;

    private final Object connectionLock = new Object();

    private ExecutorService executor = Executors.newCachedThreadPool();

    private Future<?> socketProcess;

    private int proxyPort;

    private FileTransferManager transferManager;

    private String className;

    static long amountTransfered = 0;

    public ProxyConnectionManager(FileTransferManager manager) {
        String cacheName = "File Transfer";
        CacheManager.initializeCache(cacheName, "fileproxytransfer", -1, 1000 * 60 * 10);
        connectionMap = CacheManager.getCache(cacheName);

        className = JiveGlobals.getProperty("provider.transfer.proxy",
                "org.jivesoftware.wildfire.filetransfer.spi.DefaultProxyTransfer");

        transferManager = manager;
    }

    /*
    * Processes the clients connecting to the proxy matching the initiator and target together.
    * This is the main loop of the manager which will run until the process is canceled.
    */
    synchronized void processConnections(final int port) {
        if (socketProcess != null) {
            if (port != proxyPort) {
                socketProcess.cancel(true);
                socketProcess = null;
            }
            else {
                return;
            }
        }

        socketProcess = executor.submit(new Runnable() {
            public void run() {
                ServerSocket serverSocket;
                try {
                    serverSocket = new ServerSocket(port);
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
                        Log.error("Error accepting procy connection", e);
                        continue;
                    }
                    executor.submit(new Runnable() {
                        public void run() {
                            try {
                                processConnection(socket);
                            }
                            catch (IOException ie) {
                                Log.error("Error processing file transfer proxy connection",
                                        ie);
                                try {
                                    socket.close();
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

    private void processConnection(Socket connection) throws IOException {
        OutputStream out = new DataOutputStream(connection.getOutputStream());
        InputStream in = new DataInputStream(connection.getInputStream());

        // first byte is version should be 5
        int b = in.read();
        if (b != 5) {
            throw new IOException("Only SOCKS5 supported");
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
            throw new IOException("Authentication method not supported");
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
                    transfer.setInitiatorSocket(connection);
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

    private ProxyTransfer createProxyTransfer(String transferDigest, Socket initiatorSocket) {
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
        provider.setTargetSocket(initiatorSocket);
        return provider;
    }

    private static String processIncomingSocks5Message(InputStream in)
            throws IOException
    {
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

        read = in.read();
        read = in.read();

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
        executor.shutdown();
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
                               final JID target)
    {
        return StringUtils.hash(sessionID + initiator.getNode()
                + "@" + initiator.getDomain() + "/"
                + initiator.getResource()
                + target.getNode() + "@"
                + target.getDomain() + "/"
                + target.getResource(), "SHA-1");
    }

    public boolean isRunning() {
        return socketProcess != null;
    }

    public void disable() {
        if (socketProcess != null) {
            socketProcess.cancel(true);
            socketProcess = null;
        }
    }
}
