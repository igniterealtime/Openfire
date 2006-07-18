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

import org.jivesoftware.util.CacheSizes;

import java.util.concurrent.Future;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;

/**
 * Tracks the different connections related to a file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public class DefaultProxyTransfer implements ProxyTransfer {

    private String initiator;

    private ReadableByteChannel inputStream;

    private WritableByteChannel outputStream;

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

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    public ReadableByteChannel getInputChannel() {
        return inputStream;
    }

    public void setInputChannel(ReadableByteChannel inputChannel) {
        this.inputStream = inputChannel;
    }

    public WritableByteChannel getOutputChannel() {
        return outputStream;
    }

    public void setOutputChannel(WritableByteChannel outputChannel) {
        this.outputStream = outputChannel;
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
        return ((inputStream != null) && (outputStream != null));
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
        ReadableByteChannel in = getInputChannel();
        WritableByteChannel out = new ProxyOutputChannel(getOutputChannel());

        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int count = 0;
        amountWritten = 0;

        do {
            // write to the output channel
            out.write(buffer);

            amountWritten += count;

            // read more bytes from the input channel
            buffer.clear();
            count = in.read(buffer);
            buffer.flip();
        } while (count >= 0);

        in.close();
        out.close();
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

}
