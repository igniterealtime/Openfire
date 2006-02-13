/**
 * $Revision$
 * $Date$
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.filetransfer;

import org.jivesoftware.util.Cache;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages the connections to the proxy server. The connections go through two stages before
 * file transfer begins. The first stage is when the file transfer target initiates a connection
 * to this manager. Stage two is when the initiator connects, the manager will then match the two
 * connections using the unique SHA-1 hash defined in the SOCKS5 protocol.
 *
 * @author Alexander Wenckus
 */
public class ProxyConnectionManager {

    private int port;

    private Map<String, ProxyTransfer> connectionMap =
            new Cache("File Transfer Cache", -1, 1000 * 60 * 10);

    private final Object connectionLock = new Object();

    private ExecutorService executor = Executors.newCachedThreadPool();

    public ProxyConnectionManager(int port) {
        this.port = port;
    }

    /*
    * Processes the clients connecting to the proxy matching the initiator and target together.
    * This is the main loop of the manager which will run until the process is canceled.
    */
    public void processConnections() {
        executor.submit(new Runnable() {
            public void run() {
                ServerSocket serverSocket = null;
                try {
                    serverSocket = new ServerSocket(port);
                }
                catch (IOException e) {
                    Log.error("Error creating server socket", e);
                    return;
                }
                while (serverSocket != null) {
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
                            }
                        }
                    });
                }
            }
        });

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
        cmd = createOutgoingSocks5Message(0, responseDigest);

        synchronized (connectionLock) {
            ProxyTransfer transfer = connectionMap.get(responseDigest);
            if (transfer == null) {
                connectionMap.put(responseDigest, new ProxyTransfer(responseDigest, connection));
            }
            else {
                transfer.setInitiatorSocket(connection);
            }
        }

        if (!connection.isConnected()) {
            throw new IOException("Socket closed by remote user");
        }
        out.write(cmd);
    }

    private String processIncomingSocks5Message(InputStream in)
            throws IOException {
        // read the version and command
        byte[] cmd = new byte[5];
        in.read(cmd, 0, 5);

        // read the digest
        byte[] addr = new byte[cmd[4]];
        in.read(addr, 0, addr.length);
        String digest = new String(addr);

        in.read();
        in.read();

        return digest;
    }

    private byte[] createOutgoingSocks5Message(int cmd, String digest) {
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

    /**
     * Activates the stream, this method should be called when the initiator sends the activate
     * packet after both parties have connected to the proxy.
     *
     * @param initiator The initiator or sender of the file transfer.
     * @param target    The target or reciever of the file transfer.
     * @param sid       The sessionid the uniquely identifies the transfer between
     *                  the two participants.
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

        transfer.setInitiatorJID(initiator.toString());
        transfer.setTargetJID(target.toString());
        transfer.setTransferSession(sid);
        transfer.setTransferFuture(executor.submit(new Runnable() {
            public void run() {
                try {
                    transfer(transfer);
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

    private void transfer(ProxyTransfer transfer) throws IOException {
        InputStream in = transfer.getInitiatorSocket().getInputStream();
        OutputStream out = transfer.getTargetSocket().getOutputStream();
        final byte[] b = new byte[1000];
        int count = 0;
        int amountWritten = 0;

        count = in.read(b);
        while (count != -1) {

            // write to the output stream
            out.write(b, 0, count);

            amountWritten += count;

            // read more bytes from the input stream
            count = in.read(b);
        }
    }

    /**
     * Creates the digest needed for a byte stream. It is the SHA1(sessionID +
     * initiator + target).
     *
     * @param sessionID The sessionID of the stream negotiation
     * @param initiator The inititator of the stream negotiation
     * @param target    The target of the stream negotiation
     * @return SHA-1 hash of the three parameters
     */
    private String createDigest(final String sessionID, final JID initiator,
            final JID target) {
        return hash(sessionID + initiator.getNode()
                + "@" + initiator.getDomain() + "/"
                + initiator.getResource()
                + target.getNode() + "@"
                + target.getDomain() + "/"
                + target.getResource());
    }

    private static MessageDigest digest = null;

    private synchronized static String hash(String data) {
        if (digest == null) {
            try {
                digest = MessageDigest.getInstance("SHA-1");
            }
            catch (NoSuchAlgorithmException nsae) {
                Log.error("Failed to load the SHA-1 MessageDigest. " +
                        "Jive will be unable to function normally.", nsae);
            }
        }
        // Now, compute hash.
        try {
            digest.update(data.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            Log.error(e);
        }
        return encodeHex(digest.digest());
    }

    private static String encodeHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);

        for (int i = 0; i < bytes.length; i++) {
            if (((int) bytes[i] & 0xff) < 0x10) {
                hex.append("0");
            }
            hex.append(Integer.toString((int) bytes[i] & 0xff, 16));
        }

        return hex.toString();
    }
}
