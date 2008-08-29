/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.filetransfer.proxy;

import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.openfire.filetransfer.FileTransferProgress;

import java.io.IOException;

/**
 * Tracks the different connections related to a proxy file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public interface ProxyTransfer extends Cacheable, FileTransferProgress {

    /**
     * Sets the transfer digest for a file transfer. The transfer digest uniquely identifies a file
     * transfer in the system.
     *
     * @param digest the digest which uniquely identifies this transfer.
     */
    public void setTransferDigest(String digest);

    /**
     * Returns the transfer digest uniquely identifies a file transfer in the system.
     *  
     * @return the transfer digest uniquely identifies a file transfer in the system.
     */
    public String getTransferDigest();

    /**
     * Returns true if the Bytestream is ready to be activated and the proxy transfer can begin.
     *
     * @return true if the Bytestream is ready to be activated.
     */
    public boolean isActivatable();

    /**
     * Transfers the file from the initiator to the target.
     *
     * @throws java.io.IOException when an error occurs either reading from the input stream or
     * writing to the output stream.
     */
    public void doTransfer() throws IOException;
}
