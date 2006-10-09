/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.net;

import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.ConnectionManager;
import org.jivesoftware.wildfire.ServerPort;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

/**
 * Accepts new socket connections using a non-blocking model. A single selector is
 * used for all connected clients and also for accepting new connections.
 *
 * @author Daniele Piras
 */
class NonBlockingAcceptingMode extends SocketAcceptingMode {

    // Time (in ms) to sleep from a reading-cycle to another
    private static final long CYCLE_TIME = 10;

    // Selector to collect messages from client connections.
    private Selector selector;

    protected NonBlockingAcceptingMode(ConnectionManager connManager, ServerPort serverPort,
            InetAddress bindInterface) throws IOException {
        super(connManager, serverPort);

        // Chaning server to use NIO
        // Open selector...
        selector = Selector.open();
        // Create a new ServerSocketChannel
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        // Retrieve socket and bind socket with specified address
        this.serverSocket = serverSocketChannel.socket();
        this.serverSocket.bind(new InetSocketAddress(bindInterface, serverPort.getPort()));
        // Configure Blocking to unblocking
        serverSocketChannel.configureBlocking(false);
        // Registering connection with selector.
        SelectionKey sk = serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        AcceptConnection acceptConnection = new AcceptConnection();
        sk.attach(acceptConnection);
    }

    /**
     * DANIELE:
     * This thread use the selector NIO features to retrieve client connections
     * and messages.
     */
    public void run() {
        while (notTerminated && !Thread.interrupted()) {
            try {
                selector.select();
                Set selected = selector.selectedKeys();
                Iterator it = selected.iterator();
                while (it.hasNext()) {
                    SelectionKey key = (SelectionKey) it.next();
                    it.remove();
                    SelectorAction action = (SelectorAction) key.attachment();
                    if (action == null) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        action.connect(key);
                    }
                    else if (key.isReadable()) {
                        action.read(key);
                    }
                }
                Thread.sleep(CYCLE_TIME);
            }
            catch (IOException ie) {
                if (notTerminated) {
                    Log.error(LocaleUtils.getLocalizedString("admin.error.accept"),
                            ie);
                }
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error.accept"), e);
            }
        }
    }

    /*
    * InnerClass that is use when a new client arrive.
    * It's use the reactor pattern to register an abstract action
    * to the selector.
    */
    class AcceptConnection implements SelectorAction {


        public void read(SelectionKey key) throws IOException {
        }

        /*
        * A client arrive...
        */
        public void connect(SelectionKey key) throws IOException {
            // Retrieve the server socket channel...
            ServerSocketChannel sChannel = (ServerSocketChannel) key.channel();
            // Accept the connection
            SocketChannel socketChannel = sChannel.accept();
            // Retrieve socket for incoming connection
            Socket sock = socketChannel.socket();
            socketChannel.configureBlocking(false);
            // Registering READING operation into the selector
            SelectionKey sockKey = socketChannel.register(selector, SelectionKey.OP_READ);
            if (sock != null) {
                System.out.println("Connect " + sock.toString());
                Log.debug("Connect " + sock.toString());
                try {
                    SocketReader reader =
                            connManager.createSocketReader(sock, false, serverPort, false);
                    SelectorAction action = new ReadAction(reader);
                    sockKey.attach(action);
                }
                catch (Exception e) {
                    // There is an exception...
                    Log.error(LocaleUtils.getLocalizedString("admin.error.accept"), e);
                }
            }
        }
    }

    class ReadAction implements SelectorAction {

        SocketReader reader;

        public ReadAction(SocketReader reader) {
            this.reader = reader;
        }

        public void read(SelectionKey key) throws IOException {
            // Socket reader (using non-blocking mode) will read the stream and process, in
            // another thread, any number of stanzas found in the stream.
            reader.run();
        }

        public void connect(SelectionKey key) throws IOException {
        }
    }
}
