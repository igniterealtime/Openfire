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

import org.xmpp.packet.JID;
import org.dom4j.Element;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.Module;

/**
 * Manages all file transfer currently happening originating from and/or ending at users of the
 * server. From here, file transfers can be administered and stats can be tracked.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferManager extends Module {

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

    /**
     * Stream Initiation, SI, namespace
     */
    static final String NAMESPACE_SI = "http://jabber.org/protocol/si";

    /**
     * Bytestreams namespace
     */
    static final String NAMESPACE_BYTESTREAMS = "http://jabber.org/protocol/bytestreams";

    /**
     * Checks an incoming file transfer request to see if it should be accepted or rejected.
     * If it is accepted true will be returned and if it is rejected false will be returned.
     *
     * @param packetID the packet ID of the packet being parsed.
     * @param from the offerer The offerer of the file transfer.
     * @param to The receiver the potential reciever of the file transfer.
     * @param siElement the Stream Initiation element
     * @return true if it should be accepted false if it should not.
     */
    boolean acceptIncomingFileTransferRequest(String packetID, JID from, JID to, Element siElement);

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
     * Enables or disable all file transfers going through the server. This includes the blocking
     * of file transfer related packets from reaching their recipients.
     *
     * @param isEnabled true if file transfer should be enabled and false if it should not be.
     */
    void enableFileTransfer(boolean isEnabled);

    /**
     * Returns whether or not file transfer is currently enabled in the server.
     *
     * @return true if file transfer is enabled and false if it is not
     */
    boolean isFileTransferEnabled();

    void addFileTransferInterceptor(FileTransferInterceptor interceptor);

    void removeFileTransferInterceptor(FileTransferInterceptor interceptor);

    void fireFileTransferIntercept(String transferDigest) throws FileTransferRejectedException;
}
