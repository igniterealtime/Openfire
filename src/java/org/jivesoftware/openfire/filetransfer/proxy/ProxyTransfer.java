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
    void setTransferDigest( String digest );

    /**
     * Returns the transfer digest uniquely identifies a file transfer in the system.
     *  
     * @return the transfer digest uniquely identifies a file transfer in the system.
     */
    String getTransferDigest();

    /**
     * Returns true if the Bytestream is ready to be activated and the proxy transfer can begin.
     *
     * @return true if the Bytestream is ready to be activated.
     */
    boolean isActivatable();

    /**
     * Transfers the file from the initiator to the target.
     *
     * @throws java.io.IOException when an error occurs either reading from the input stream or
     * writing to the output stream.
     */
    void doTransfer() throws IOException;
}
