/**
 * $RCSfile  $
 * $Revision  $
 * $Date  $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.filetransfer.proxy;

import java.io.OutputStream;
import java.io.IOException;
import java.io.DataOutputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 *  An output stream which tracks the amount of bytes transfered by proxy sockets.
 */
public class ProxyOutputStream extends DataOutputStream {
    static AtomicLong amountTransfered = new AtomicLong(0);

    public ProxyOutputStream(OutputStream out) {
        super(out);
    }

    public synchronized void write(byte b[], int off, int len) throws IOException {
        super.write(b, off, len);
        amountTransfered.addAndGet(len);
    }
}
