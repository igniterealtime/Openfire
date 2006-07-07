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

import org.jivesoftware.util.Cacheable;

import java.net.Socket;
import java.io.IOException;
import java.util.concurrent.Future;

/**
 * Tracks the different connections related to a proxy file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public interface ProxyTransfer extends Cacheable, FileTransferProgress {
    /**
     * Returns the fully qualified JID of the initiator of the file transfer.
     *
     * @return Returns the fully qualified JID of the initiator of the file transfer.
     */
    public String getInitiator();

    public void setInitiatorSocket(Socket initiatorSocket);

    public Socket getInitiatorSocket();

    public void setTargetSocket(Socket targetSocket);

    public Socket getTargetSocket();

    public void setTarget(String target);

    public String getTarget();

    public void setTransferDigest(String digest);

    public String getTransferDigest();

    public String getSessionID();

    public void setSessionID(String streamID);

    /**
     * Returns true if the Bytestream is ready to be activated and the transfer can begin.
     *
     * @return Returns true if the Bytestream is ready to be activated.
     */
    public boolean isActivatable();

    public long getAmountTransfered();

    public int getCachedSize();

    public void setTransferFuture(Future<?> future);

    /**
     * Transfers the file from the initiator to the target.
     */
    public void doTransfer() throws IOException;

    void setInitiator(String s);
}
