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
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.interceptor.InterceptorManager;
import org.jivesoftware.wildfire.interceptor.PacketInterceptor;
import org.jivesoftware.wildfire.interceptor.PacketRejectedException;
import org.jivesoftware.wildfire.Session;
import org.dom4j.Element;
import org.xmpp.packet.Packet;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.util.Map;
import java.util.List;
import java.util.Arrays;

/**
 * Provides several utility methods for file transfer manager implementaions to utilize.
 *
 * @author Alexander Wenckus
 */
public abstract class AbstractFileTransferManager
        extends BasicModule implements FileTransferManager
{

    private static final List<String> FILETRANSFER_NAMESPACES
            = Arrays.asList(NAMESPACE_BYTESTREAMS, NAMESPACE_SI);

    private static final String CACHE_NAME = "File Transfer Cache";

    private final Map<String, FileTransfer> fileTransferMap;

    /**
     * Default constructor creates the cache.
     */
    public AbstractFileTransferManager() {
        super("File Transfer Manager");
        fileTransferMap = createCache(CACHE_NAME, "fileTransfer", 128 * 1024, 1000 * 60 * 10);
        InterceptorManager.getInstance().addInterceptor(new MetaFileTransferInterceptor());
    }

    private Cache<String, FileTransfer> createCache(String name, String propertiesName, int size,
                                                    long expirationTime)
    {
        size = JiveGlobals.getIntProperty("cache." + propertiesName + ".size", size);
        expirationTime = (long) JiveGlobals.getIntProperty(
                "cache." + propertiesName + ".expirationTime", (int) expirationTime);
        return new Cache<String, FileTransfer>(name, size, expirationTime);
    }

    /**
     * Returns true if the proxy transfer should be matched to an existing file transfer
     * in the system.
     *
     * @return Returns true if the proxy transfer should be matched to an existing file
     * transfer in the system.
     */
    public boolean isMatchProxyTransfer() {
        return JiveGlobals.getBooleanProperty("xmpp.proxy.transfer.required", true);
    }

    protected void cacheFileTransfer(String key, FileTransfer transfer) {
        fileTransferMap.put(key, transfer);
    }

    protected FileTransfer retrieveFileTransfer(String key) {
        return fileTransferMap.get(key);
    }

    protected static Element getChildElement(Element element, String namespace) {
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

    public void enableFileTransfer(boolean isEnabled) {
        JiveGlobals.setProperty(JIVEPROPERTY_FILE_TRANSFER_ENABLED, Boolean.toString(isEnabled));
    }

    public boolean isFileTransferEnabled() {
        return JiveGlobals.getBooleanProperty(JIVEPROPERTY_FILE_TRANSFER_ENABLED,
                DEFAULT_IS_FILE_TRANSFER_ENABLED);
    }

    public void addFileTransferInterceptor(FileTransferInterceptor interceptor) {
    }

    public void removeFileTransferInterceptor(FileTransferInterceptor interceptor) {
    }

    public void fireFileTransferIntercept(String transferDigest)
            throws FileTransferRejectedException
    {
    }

    /**
     * Interceptor to grab and validate file transfer meta information.
     */
    private class MetaFileTransferInterceptor implements PacketInterceptor {
        public void interceptPacket(Packet packet, Session session, boolean incoming,
                                    boolean processed)
                throws PacketRejectedException
        {
            // We only want packets recieved by the server
            if (!processed && incoming && packet instanceof IQ) {
                IQ iq = (IQ) packet;
                Element childElement = iq.getChildElement();
                if(childElement == null) {
                    return;
                }

                String namespace = childElement.getNamespaceURI();
                if(!isFileTransferEnabled() && FILETRANSFER_NAMESPACES.contains(namespace)) {
                    throw new PacketRejectedException("File Transfer Disabled");
                }

                if (NAMESPACE_SI.equals(namespace)) {
                    // If this is a set, check the feature offer
                    if (iq.getType().equals(IQ.Type.set)) {
                        JID from = iq.getFrom();
                        JID to = iq.getTo();
                        String packetID = iq.getID();
                        if (!acceptIncomingFileTransferRequest(packetID, from, to, childElement)) {
                            throw new PacketRejectedException();
                        }
                    }
                }
            }
        }
    }
}
