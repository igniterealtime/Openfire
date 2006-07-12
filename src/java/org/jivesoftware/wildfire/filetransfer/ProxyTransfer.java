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

import java.io.IOException;

/**
 * Tracks the different connections related to a proxy file transfer. There are two connections, the
 * initiator and the target and when both connections are completed the transfer can begin.
 */
public interface ProxyTransfer extends Cacheable, FileTransferProgress {

    public void setTransferDigest(String digest);

    public String getTransferDigest();

    /**
     * Returns true if the Bytestream is ready to be activated and the  proxy transfer can begin.
     *
     * @return Returns true if the Bytestream is ready to be activated.
     */
    public boolean isActivatable();

    /**
     * Transfers the file from the initiator to the target.
     */
    public void doTransfer() throws IOException;
}
