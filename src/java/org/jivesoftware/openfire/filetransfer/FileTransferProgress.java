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

import java.util.concurrent.Future;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An interface to track the progress of a file transfer through the server. This interface is used
 * by {@link FileTransfer} to make this information available if it is in the system.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferProgress {
    public long getAmountTransfered() throws UnsupportedOperationException;

    /**
     * Returns the fully qualified JID of the initiator of the file transfer.
     *
     * @return the fully qualified JID of the initiator of the file transfer.
     */
    public String getInitiator();

    public void setInitiator(String initiator);

    /**
     * Returns the full qualified JID of the target of the file transfer.
     *
     * @return the fully qualified JID of the target
     */
    public String getTarget();

    public void setTarget(String target);

    /**
     * Returns the unique session id that correlates to the file transfer.
     *
     * @return Returns the unique session id that correlates to the file transfer.
     */
    public String getSessionID();

    public void setSessionID(String streamID);

    /**
     * When the file transfer is being caried out by another thread this will set the Future
     * relating to the thread that is carrying out the transfer.
     *
     * @param future the furute that is carrying out the transfer
     */
    public void setTransferFuture(Future<?> future);

    public void setInputStream(InputStream initiatorInputStream);

    public InputStream getInputStream();

    public void setOutputStream(OutputStream targetOutputStream);

    public OutputStream getOutputStream();
}
