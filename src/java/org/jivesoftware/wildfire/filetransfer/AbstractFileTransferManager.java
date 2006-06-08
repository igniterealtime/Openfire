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

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Cache;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.dom4j.Element;

import java.util.Map;
import java.util.List;

/**
 * Provides several utility methods for file transfer manager implementaions to utilize.
 *
 * @author Alexander Wenckus
 */
public abstract class AbstractFileTransferManager implements FileTransferManager {
    private static final String CACHE_NAME = "File Transfer Cache";

    private final Map<String, FileTransfer> fileTransferMap;

    /**
     * Default constructor creates the cache.
     */
    public AbstractFileTransferManager() {
        fileTransferMap = createCache(CACHE_NAME, "fileTransfer", 128 * 1024, 1000 * 60 * 10);
    }

    private Cache<String, FileTransfer> createCache(String name, String propertiesName, int size, long expirationTime) {
        size = JiveGlobals.getIntProperty("cache." + propertiesName + ".size", size);
        expirationTime = (long) JiveGlobals.getIntProperty(
                "cache." + propertiesName + ".expirationTime", (int) expirationTime);
        return new Cache<String, FileTransfer>(name, size, expirationTime);
    }

    /**
     * Returns true if the proxy transfer should be matched to an existing file transfer in the system.
     *
     * @return Returns true if the proxy transfer should be matched to an existing file transfer in the system.
     */
    public boolean isMatchProxyTransfer() {
        return JiveGlobals.getBooleanProperty("xmpp.proxy.transfer.required", true);
    }

    public void cacheFileTransfer(String key, FileTransfer transfer) {
        fileTransferMap.put(key, transfer);
    }

    public FileTransfer retrieveFileTransfer(String key) {
        return fileTransferMap.get(key);
    }

    public static Element getChildElement(Element element, String namespace) {
        List elements = element.elements();
        if (elements.isEmpty()) {
            return null;
        }
        for (int i = 0; i < elements.size(); i++) {
            Element childElement = (Element) elements.get(i);
            String childNamespace = childElement.getNamespaceURI();
            if (namespace.equals(childNamespace)) {
                return childElement;
            }
        }

        return null;
    }

    public void registerProxyTransfer(String transferDigest, ProxyTransfer proxyTransfer)
            throws UnauthorizedException
    {
        FileTransfer transfer = retrieveFileTransfer(transferDigest);
        if (isMatchProxyTransfer() && transfer == null) {
            throw new UnauthorizedException("Unable to match proxy transfer with a file transfer");
        }
        else if (transfer == null) {
            return;
        }

        transfer.setProgress(proxyTransfer);
        cacheFileTransfer(transferDigest, transfer);
    }
}
