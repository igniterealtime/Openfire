/**
 * $RCSfile:  $
 * $Revision:  $
 * $Date:  $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.plugin.spark.manager;

import org.jivesoftware.openfire.filetransfer.*;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;

/**
 *
 */
public class FileTransferFilterManager {

    /**
     * The JiveProperty relating to whether or not file transfer is currently enabled. If file
     * transfer is disabled all known file transfer related packets are blocked, it also goes
     * with out saying that the file transfer proxy is then disabled.
     */
    static final String JIVEPROPERTY_FILE_TRANSFER_ENABLED = "xmpp.filetransfer.enabled";

    /**
     * Whether or not the file transfer is enabled by default.
     */
    static final boolean DEFAULT_IS_FILE_TRANSFER_ENABLED = true;

    private org.jivesoftware.openfire.filetransfer.FileTransferManager manager;
    private TransferInterceptor transferInterceptor;

    public FileTransferFilterManager()
    {
        this.manager = XMPPServer.getInstance().getFileTransferManager();
        this.transferInterceptor = new TransferInterceptor();
    }

    public void start() {
        manager.addFileTransferInterceptor(transferInterceptor);
    }

    public void stop() {
        manager.removeFileTransferInterceptor(transferInterceptor);
    }

    public void enableFileTransfer(boolean isEnabled) {
        JiveGlobals.setProperty(JIVEPROPERTY_FILE_TRANSFER_ENABLED, Boolean.toString(isEnabled));
    }

    public boolean isFileTransferEnabled() {
        return JiveGlobals.getBooleanProperty(JIVEPROPERTY_FILE_TRANSFER_ENABLED,
                DEFAULT_IS_FILE_TRANSFER_ENABLED);
    }

    private class TransferInterceptor implements FileTransferInterceptor {

        public void interceptFileTransfer(FileTransfer transfer, boolean isReady)
                throws FileTransferRejectedException
        {
            if(!isFileTransferEnabled()) {
                throw new FileTransferRejectedException();
            }
        }


    }
}