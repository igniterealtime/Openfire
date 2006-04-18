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

/**
 * Manages all file transfer currently happening originating from and/or ending at users of the server. From here,
 * file transfers can be administered and stats can be tracked.
 *
 * @author Alexander Wenckus
 */
public interface FileTransferManager {
    /**
     * Checks an incoming file transfer request to see if it should be accepted or rejected.
     * If it is accepted true will be returned and if it is rejected false will be returned.
     *
     * @param packetID The packet ID of the packet being parsed.
     * @param from The offerer The offerer of the file transfer.
     * @param to The receiver The potential reciever of the file transfer.
     * @param siElement The Stream Initiation element
     * @return True if it should be accepted false if it should not.
     */
    boolean acceptIncomingFileTransferRequest(String packetID, JID from, JID to, Element siElement);

    /**
     * Registers that a transfer has begun through the proxy connected to the server.
     *
     * @param transferDigest The digest of the initiator + target + sessionID that uniquely identifies a file transfer
     * @param proxyTransfer The related proxy transfer.
     * @throws UnauthorizedException Thrown when in the current server configuration this transfer should not be
     * permitted.
     */
    public void registerProxyTransfer(String transferDigest, ProxyTransfer proxyTransfer) throws UnauthorizedException;
}
