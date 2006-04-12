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
package org.jivesoftware.wildfire.filetransfer;

/**
 * An interface to track the progress of a file transfer through the server. This interface is used
 * by {@link FileTransfer} to make this information available if it is in the system.
 */
public interface FileTransferProgress {
    public long getAmountTransfered() throws UnsupportedOperationException;

    public String getInitiator();

    public String getTarget();

    public String getSessionID();
}
