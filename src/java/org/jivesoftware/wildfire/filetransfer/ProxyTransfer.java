/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.filetransfer;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.Cacheable;

import java.net.Socket;
import java.util.concurrent.Future;

/**
 * Tracks the different connections related to a file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public class ProxyTransfer implements Cacheable {

    private String initiatorJID;

    private Socket initiatorSocket;

    private Socket targetSocket;

    private String targetJID;

    private String transferDigest;

    private String transferSession;
    
    private Future<?> future;

    public ProxyTransfer(String transferDigest, Socket targetSocket) {
        this.transferDigest = transferDigest;
        this.targetSocket = targetSocket;
    }

    public String getInitiatorJID() {
        return initiatorJID;
    }

    public void setInitiatorJID(String initiatorJID) {
        this.initiatorJID = initiatorJID;
    }

    public Socket getInitiatorSocket() {
        return initiatorSocket;
    }

    public void setInitiatorSocket(Socket initiatorSocket) {
        this.initiatorSocket = initiatorSocket;
    }

    public Socket getTargetSocket() {
        return targetSocket;
    }

    public void setTargetSocket(Socket targetSocket) {
        this.targetSocket = targetSocket;
    }

    public String getTargetJID() {
        return targetJID;
    }

    public void setTargetJID(String targetJID) {
        this.targetJID = targetJID;
    }

    public String getTransferDigest() {
        return transferDigest;
    }

    public void setTransferDigest(String transferDigest) {
        this.transferDigest = transferDigest;
    }

    public String getTransferSession() {
        return transferSession;
    }

    public void setTransferSession(String transferSession) {
        this.transferSession = transferSession;
    }

    /**
     * Returns true if the Bytestream is ready to be activated and the transfer can begin.
     *
     * @return Returns true if the Bytestream is ready to be activated.
     */
    public boolean isActivatable() {
        return ((initiatorSocket != null) && (targetSocket != null));
    }

    public void setTransferFuture(Future<?> future) {
        this.future = future;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(initiatorJID);
        size += CacheSizes.sizeOfString(targetJID);
        size += CacheSizes.sizeOfString(transferDigest);
        size += CacheSizes.sizeOfString(transferSession);
        return size;
    }
}
