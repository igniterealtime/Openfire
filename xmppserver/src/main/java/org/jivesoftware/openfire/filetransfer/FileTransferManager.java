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

import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.Module;
import org.jivesoftware.openfire.filetransfer.proxy.ProxyTransfer;

/**
 * Manages all file transfer currently happening originating from and/or ending at users of the
 * server. From here, file transfers can be administered and stats can be tracked.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferManager extends Module {
    /**
     * The Stream Initiation, SI, namespace.
     */
    String NAMESPACE_SI = "http://jabber.org/protocol/si";

    /**
     * Namespace for the file transfer profile of Stream Initiation.
     */
    String NAMESPACE_SI_FILETRANSFER =
            "http://jabber.org/protocol/si/profile/file-transfer";

    /**
     * Bytestreams namespace
     */
    String NAMESPACE_BYTESTREAMS = "http://jabber.org/protocol/bytestreams";

    /**
     * Checks an incoming file transfer request to see if it should be accepted or rejected.
     * If it is accepted true will be returned and if it is rejected false will be returned.
     *
     * @param transfer the transfer to test for acceptance
     * @return true if it should be accepted false if it should not.
     * @throws FileTransferRejectedException if the request was rejected (can this ever happen?)
     */
    boolean acceptIncomingFileTransferRequest(FileTransfer transfer) throws FileTransferRejectedException;

    /**
     * Registers that a transfer has begun through the proxy connected to the server.
     *
     * @param transferDigest the digest of the initiator + target + sessionID that uniquely
     * identifies a file transfer
     * @param proxyTransfer the related proxy transfer.
     * @throws UnauthorizedException when in the current server configuration this transfer
     * should not be permitted.
     */
    void registerProxyTransfer(String transferDigest, ProxyTransfer proxyTransfer)
            throws UnauthorizedException;

    /**
     * Registers an event listener that will be notified of file transfer related events.
     *
     * @param eventListener an event listener (cannot be null).
     */
    void addListener( FileTransferEventListener eventListener );

    /**
     * Unregisters an event listener from the list of event listeners that are notified of file transfer related events.
     *
     * @param eventListener an event listener (cannot be null).
     */
    void removeListener( FileTransferEventListener eventListener );

    /**
     * Invokes {@link FileTransferEventListener#fileTransferStart(FileTransfer, boolean)} for all registered event
     * listeners.
     *
     * @param sid The session id of the file transfer that is being intercepted (cannot be null).
     * @param isReady true if the transfer is ready to commence or false if this is related to the
     *                 initial file transfer request. An exception at this point will cause the transfer to
     *                 not go through.
     * @throws FileTransferRejectedException When at least one of the listeners aborts the file transfer.
     */
    void fireFileTransferStart( String sid, boolean isReady )
            throws FileTransferRejectedException;

    /**
     * Invokes {@link FileTransferEventListener#fileTransferComplete(FileTransfer, boolean)} for all registered event
     * listeners.
     *
     * @param sid The session id of the file transfer that is being intercepted (cannot be null).
     * @param wasSuccessful false when an exception was thrown during file transfer, otherwise true.
     */
    void fireFileTransferCompleted( String sid, boolean wasSuccessful );
}
