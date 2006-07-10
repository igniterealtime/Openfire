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
package org.jivesoftware.wildfire.filetransfer.spi;

import org.xmpp.packet.JID;
import org.dom4j.Element;
import org.jivesoftware.wildfire.filetransfer.AbstractFileTransferManager;
import org.jivesoftware.wildfire.filetransfer.FileTransfer;
import org.jivesoftware.wildfire.filetransfer.ProxyConnectionManager;

/**
 * The default implementation of the file transfer manager. The only acceptance criteria for a proxy
 * transfer is employed in the <i>ProxyConnectionManager</i>, it checks that the file transfers stored
 * here.
 */
public class DefaultFileTransferManager extends AbstractFileTransferManager {
    public boolean acceptIncomingFileTransferRequest(String packetID, JID from, JID to, Element siElement) {
        String streamID = siElement.attributeValue("id");
        String mimeType = siElement.attributeValue("mime-type");
        String profile = siElement.attributeValue("profile");
        // Check profile, the only type we deal with currently is file transfer
        if ("http://jabber.org/protocol/si/profile/file-transfer".equals(profile)) {
            Element fileTransferElement =
                    getChildElement(siElement, "http://jabber.org/protocol/si/profile/file-transfer");
            // Not valid form, reject
            if (fileTransferElement == null) {
                return false;
            }
            String fileName = fileTransferElement.attributeValue("name");
            long size = Long.parseLong(fileTransferElement.attributeValue("size"));

            FileTransfer transfer = new FileTransfer(from.toString(), to.toString(),
                    streamID, fileName, size, mimeType);
            cacheFileTransfer(ProxyConnectionManager.createDigest(streamID, from, to), transfer);
        }
        return true;
    }
}
