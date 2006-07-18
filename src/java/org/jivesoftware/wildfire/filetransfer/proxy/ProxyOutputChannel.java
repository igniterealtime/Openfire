/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.wildfire.filetransfer.proxy;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.channels.WritableByteChannel;
import java.nio.ByteBuffer;

/**
 *  An output stream which tracks the amount of bytes transfered by proxy sockets.
 */
public class ProxyOutputChannel implements WritableByteChannel {
    static AtomicLong amountTransfered = new AtomicLong(0);
    private WritableByteChannel channel;

    public ProxyOutputChannel(WritableByteChannel channel) {
        this.channel = channel;
    }

    public int write(ByteBuffer src) throws IOException {
        int bytesWritten = channel.write(src);
        amountTransfered.addAndGet(bytesWritten);
        return bytesWritten;
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void close() throws IOException {
        channel.close();
    }
}
