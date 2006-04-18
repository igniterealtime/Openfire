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
package org.jivesoftware.wildfire.filetransfer.spi;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.wildfire.filetransfer.ProxyTransfer;

import java.net.Socket;
import java.util.concurrent.Future;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.DataOutputStream;

/**
 * Tracks the different connections related to a file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public class DefaultProxyTransfer implements ProxyTransfer {
    private static long amountTransfered;

    private String initiator;

    private Socket initiatorSocket;

    private Socket targetSocket;

    private String target;

    private String transferDigest;

    private String streamID;

    private Future<?> future;

    private long amountWritten;

    private static final int BUFFER_SIZE = 8000;

    public DefaultProxyTransfer() { }


    public String getInitiator() {
        return initiator;
    }

    /**
     * Sets the fully qualified JID of the initiator of the file transfer.
     *
     * @param initiator The fully qualified JID of the initiator of the file transfer.
     */
    public void setInitiator(String initiator) {
        this.initiator = initiator;
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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getTransferDigest() {
        return transferDigest;
    }

    public void setTransferDigest(String transferDigest) {
        this.transferDigest = transferDigest;
    }

    public String getSessionID() {
        return streamID;
    }

    public void setSessionID(String streamID) {
        this.streamID = streamID;
    }


    public boolean isActivatable() {
        return ((initiatorSocket != null) && (targetSocket != null));
    }

    public synchronized void setTransferFuture(Future<?> future) {
        if(this.future != null) {
            throw new IllegalStateException("Transfer is already in progress, or has completed.");
        }
        this.future = future;
    }

    public long getAmountTransfered() {
        return amountWritten;
    }

    public void doTransfer() throws IOException {
        if(!isActivatable()) {
            throw new IOException("Transfer missing party");
        }
        InputStream in = getInitiatorSocket().getInputStream();
        OutputStream out = new ProxyOutputStream(getTargetSocket().getOutputStream());

        final byte[] b = new byte[BUFFER_SIZE];
        int count = 0;
        amountWritten = 0;

        do {
            // write to the output stream
            out.write(b, 0, count);

            amountWritten += count;

            // read more bytes from the input stream
            count = in.read(b);
        } while (count >= 0);

        getInitiatorSocket().close();
        getTargetSocket().close();
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(initiator);
        size += CacheSizes.sizeOfString(target);
        size += CacheSizes.sizeOfString(transferDigest);
        size += CacheSizes.sizeOfString(streamID);
        size += CacheSizes.sizeOfLong();  // Amount written
        size += CacheSizes.sizeOfObject(); // Initiatior Socket
        size += CacheSizes.sizeOfObject(); // Target socket
        size += CacheSizes.sizeOfObject(); // Future
        return size;
    }

    static class ProxyOutputStream extends DataOutputStream {
        public ProxyOutputStream(OutputStream out) {
            super(out);
        }

        public synchronized void write(byte b[], int off, int len) throws IOException {
            super.write(b, off, len);
            amountTransfered += len;
        }
    }
}
