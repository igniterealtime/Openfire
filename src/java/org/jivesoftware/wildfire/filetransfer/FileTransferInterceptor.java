/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.wildfire.filetransfer;

/**
 * Interface used to capture a file transfer before it begins.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferInterceptor {
    /**
     * Invokes the interceptor on the specified file transfer. The interceptor can either modify
     * the file transfer or throw a FileTransferRejectedException.
     *
     * @param transfer
     */
    void interceptFileTransfer(FileTransfer transfer) throws FileTransferRejectedException;
}
