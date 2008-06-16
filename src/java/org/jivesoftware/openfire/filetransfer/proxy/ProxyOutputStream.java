/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
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
