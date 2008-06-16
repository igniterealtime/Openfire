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
package org.jivesoftware.openfire.filetransfer;

/**
 * Interface used to capture a file transfer before it begins.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferInterceptor {
    /**
     * Invokes the interceptor on the specified file transfer. The interceptor can either modify
     * the file transfer or throw a FileTransferRejectedException. The file transfer went sent to
     * the interceptor can be in two states, ready and not ready. The not ready state indicates
     * that this event was fired when the file transfer request was sent by the initatior. The ready
     * state indicates that the file transfer is ready to begin, and the channels can be
     * manipulated by the interceptor.
     * <p>
     * It is recommended for the the sake of user experience that
     * when in the not ready state, any processing done on the file transfer should be quick.
     *
     * @param transfer the transfer being intercepted
     * @param isReady true if the transfer is ready to commence or false if this is related to the
     * initial file transfer request. An exception at this point will cause the transfer to
     * not go through.
     *
     */
    void interceptFileTransfer(FileTransfer transfer, boolean isReady)
            throws FileTransferRejectedException;
}
