/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.net.spi;

import org.jivesoftware.net.policies.BasicAcceptPolicy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.*;

import org.jivesoftware.net.*;
import org.jivesoftware.messenger.container.ModuleProperties;
import org.jivesoftware.util.Log;

public class AcceptPortImpl implements AcceptPort {

    private ModuleProperties properties;
    private InetSocketAddress address;
    private boolean secure;
    private AcceptPolicy policy = new BasicAcceptPolicy(true);
    private long startTime = -1;
    private ConnectionMonitor acceptMonitor = new TransientConnectionMonitor();
    private ConnectionMonitor connectMonitor = new TransientConnectionMonitor();
    private ConnectionMonitor disconnectMonitor = new TransientConnectionMonitor();
    private AcceptThread acceptThread = null;
    private ConnectionManager conManager;

    public AcceptPortImpl(ModuleProperties props, ConnectionManager connectionManager) {
        properties = props;
        conManager = connectionManager;
        String hostname = properties.getProperty("port.hostname");
        String port = properties.getProperty("port.portnumber");
        address = new InetSocketAddress(hostname,Integer.parseInt(port));
    }

    public AcceptPortImpl(ModuleProperties props,
                          ConnectionManager connectionManager,
                          InetSocketAddress bindAddress) {
        properties = props;
        conManager = connectionManager;
        address = bindAddress;
        savePort();
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean securePort) {
        this.secure = securePort;
        savePort();
    }

    public InetSocketAddress getInetSocketAddress() {
        return address;
    }

    public void setInetSocketAddress(InetSocketAddress bindAddress) {
        this.address = bindAddress;
        savePort();
    }

    public long getUptime() {
        long uptime = -1;
        if (startTime > 0){
            uptime = System.currentTimeMillis() - startTime;
        }
        return uptime;
    }

    public void open()
            throws IOException, SecurityException, IllegalArgumentException {
        if (acceptThread == null){
            acceptThread = new AcceptThread();
            acceptThread.init();
            acceptThread.start();
            startTime = System.currentTimeMillis();
        }
    }

    public void close() throws IOException {
        if (acceptThread != null){
            acceptThread.close();
            acceptThread = null;
        }
    }

    public void remove() throws IOException {
        if (properties != null){
            properties.deleteProperty("port");
        }
        if (startTime > 0){
            close();
        }
    }

    public AcceptPolicy getAcceptPolicy() {
        return policy;
    }

    public ConnectionMonitor getAcceptMonitor() {
        return acceptMonitor;
    }

    public ConnectionMonitor getConnectMonitor() {
        return connectMonitor;
    }

    public ConnectionMonitor getDisconnectMonitor() {
        return disconnectMonitor;
    }

    private void savePort(){
        if (properties != null && address != null){
            properties.setProperty("port.hostname",address.getHostName());
            properties.setProperty("port.portnumber",Integer.toString(address.getPort()));
        }
    }

    private class AcceptThread extends Thread{
        AcceptThread(){
            super("Accept thread - " + address.toString());
            setDaemon(true);
        }
        private ServerSocketChannel server;
        private boolean running = false;

        void init() throws IOException {
            server = ServerSocketChannel.open();
            server.socket().bind(address);
            server.configureBlocking(true);
        }

        public void run(){
            running = true;
            while (running){
                try {
                    SocketChannel socket = server.accept();
                    Connection conn = new SocketChannelConnection(socket);
                    acceptMonitor.addSample(conn);
                    if (policy.evaluate(conn)){
                        conManager.addConnection(conn);
                        connectMonitor.addSample(conn);
                    } else {
                        disconnectMonitor.addSample(conn);
                    }
                } catch (ClosedChannelException ce){
                    startTime = -1;
                    running = false;
                } catch (NotYetBoundException ce){
                    throw new IllegalStateException("Must init thread before running");
                } catch (IOException e) {
                    Log.error(e);
                }
            }
        }
        void close(){
            if (running){
                running = false;
                startTime = -1;
                try {
                    server.close();
                } catch (IOException e) {
                    //
                }
            }
        }
    }
}
