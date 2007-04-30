/**
 * $RCSfile$
 * $Revision: 1217 $
 * $Date: 2005-04-11 18:11:06 -0300 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 1999-2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.filetransfer;

import org.jivesoftware.util.Cacheable;
import org.jivesoftware.util.CacheSizes;

import java.io.Serializable;

/**
 * Contains all of the meta information associated with a file transfer.
 *
 * @author Alexander Wenckus
 */
public class FileTransfer implements Cacheable, Serializable {
    private String sessionID;

    private String initiator;

    private String target;

    private String fileName;

    private long fileSize;

    private String mimeType;

    private FileTransferProgress progress;

    public FileTransfer(String initiator, String target, String sessionID, String fileName,
                        long fileSize, String mimeType)
    {
        this.initiator = initiator;
        this.target = target;
        this.sessionID = sessionID;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    public String getInitiator() {
        return initiator;
    }

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public FileTransferProgress getProgress() {
        return progress;
    }

    public void setProgress(FileTransferProgress progress) {
        this.progress = progress;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(initiator);
        size += CacheSizes.sizeOfString(target);
        size += CacheSizes.sizeOfString(sessionID);
        size += CacheSizes.sizeOfString(fileName);
        size += CacheSizes.sizeOfString(mimeType);
        size += CacheSizes.sizeOfLong();  // File size
        size += CacheSizes.sizeOfObject(); // Progress
        return size;
    }
}
