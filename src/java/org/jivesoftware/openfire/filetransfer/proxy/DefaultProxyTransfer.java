/**
 * $RCSfile$
 * $Revision: 3762 $
 * $Date: 2006-04-12 18:07:15 -0500 (Mon, 12 Apr 2005) $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.filetransfer.proxy;

import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.Log;

import java.util.concurrent.Future;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Tracks the different connections related to a file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public class DefaultProxyTransfer implements ProxyTransfer {

    private String initiator;

    private InputStream inputStream;

    private OutputStream outputStream;

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

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream initiatorInputStream) {
        this.inputStream = initiatorInputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
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
        if (!isActivatable()) {
            throw new IOException("Transfer missing party");
        }
        InputStream in = null;
        OutputStream out = null;

        try {
            in = getInputStream();
            out = new ProxyOutputStream(getOutputStream());

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
        }
        finally {
            if (in != null) {
                try {
                    in.close();
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
            if (out != null) {
                try {
                    out.close();
                }
                catch (Exception e) {
                    Log.error(e);
                }
            }
        }
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
