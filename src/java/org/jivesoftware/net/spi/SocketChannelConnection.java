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

import java.util.Date;
import java.net.InetAddress;
import java.nio.channels.SocketChannel;

import org.jivesoftware.net.*;
import org.jivesoftware.messenger.auth.UnauthorizedException;

public class SocketChannelConnection implements Connection {

    private SocketChannel socket;
    public SocketChannelConnection(SocketChannel socket){
        this.socket = socket;
    }

    public Date getConnectDate() {
        return null;
    }

    public long getUptime() {
        return 0;
    }

    public InetAddress getInetAddress() {
        return null;
    }

    public InetAddress getLocalInetAddress() {
        return null;
    }

    public DataConsumer getDataConsumer() throws IllegalStateException {
        return null;
    }

    public DataProducer getDataProducer() {
        return null;
    }

    public void close() {

    }

    public boolean isClosed() {
        return false;
    }

    public boolean isAcceptCreated() {
        return false;
    }

    public boolean isSecure() {
        return false;
    }

    public void setSecure(boolean secure) {

    }

    public Object registerCloseListener(ConnectionCloseListener listener, Object handbackMessage) throws UnauthorizedException {
        return null;
    }

    public Object removeCloseListener(ConnectionCloseListener listener) throws UnauthorizedException {
        return null;
    }

    public void setConnectionManager(ConnectionManager manager) {

    }
}
