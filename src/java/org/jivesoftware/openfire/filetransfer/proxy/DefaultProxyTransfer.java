/*
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.openfire.filetransfer.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;

import org.jivesoftware.util.cache.CacheSizes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks the different connections related to a file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public class DefaultProxyTransfer implements ProxyTransfer {

    private static final Logger Log = LoggerFactory.getLogger(DefaultProxyTransfer.class);

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


    @Override
    public String getInitiator() {
        return initiator;
    }

    @Override
    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public void setInputStream(InputStream initiatorInputStream) {
        this.inputStream = initiatorInputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void setOutputStream(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public String getTarget() {
        return target;
    }

    @Override
    public void setTarget(String target) {
        this.target = target;
    }

    @Override
    public String getTransferDigest() {
        return transferDigest;
    }

    @Override
    public void setTransferDigest(String transferDigest) {
        this.transferDigest = transferDigest;
    }

    @Override
    public String getSessionID() {
        return streamID;
    }

    @Override
    public void setSessionID(String streamID) {
        this.streamID = streamID;
    }


    @Override
    public boolean isActivatable() {
        return ((inputStream != null) && (outputStream != null));
    }

    @Override
    public synchronized void setTransferFuture(Future<?> future) {
        if(this.future != null) {
            throw new IllegalStateException("Transfer is already in progress, or has completed.");
        }
        this.future = future;
    }

    @Override
    public long getAmountTransferred() {
        return amountWritten;
    }

    @Override
    public void doTransfer() throws IOException {
        if (!isActivatable()) {
            throw new IOException("Transfer missing party");
        }

        try (InputStream in = getInputStream()) {
            try (OutputStream out = new ProxyOutputStream(getOutputStream())) {

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
        }
    }

    @Override
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
