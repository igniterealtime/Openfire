/*
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    /**
     * Returns the number of bytes that has been transferred.
     *
     * @return the number of bytes that has been transferred.
     * @throws UnsupportedOperationException if this information cannot be retrieved
     */
    long getAmountTransferred() throws UnsupportedOperationException;

    /**
     * Returns the fully qualified JID of the initiator of the file transfer.
     *
     * @return the fully qualified JID of the initiator of the file transfer.
     */
    String getInitiator();

    void setInitiator( String initiator );

    /**
     * Returns the full qualified JID of the target of the file transfer.
     *
     * @return the fully qualified JID of the target
     */
    String getTarget();

    void setTarget( String target );

    /**
     * Returns the unique session id that correlates to the file transfer.
     *
     * @return Returns the unique session id that correlates to the file transfer.
     */
    String getSessionID();

    void setSessionID( String streamID );

    /**
     * When the file transfer is being caried out by another thread this will set the Future
     * relating to the thread that is carrying out the transfer.
     *
     * @param future the furute that is carrying out the transfer
     */
    void setTransferFuture( Future<?> future );

    void setInputStream( InputStream initiatorInputStream );

    InputStream getInputStream();

    void setOutputStream( OutputStream targetOutputStream );

    OutputStream getOutputStream();
}
