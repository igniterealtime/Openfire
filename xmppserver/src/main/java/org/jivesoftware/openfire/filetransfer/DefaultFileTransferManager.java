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

import org.dom4j.Element;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.filetransfer.proxy.ProxyConnectionManager;
import org.jivesoftware.openfire.filetransfer.proxy.ProxyTransfer;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketInterceptor;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides several utility methods for file transfer manager implementaions to utilize.
 *
 * @author Alexander Wenckus
 */
public class DefaultFileTransferManager extends BasicModule implements FileTransferManager {

    private static final Logger Log = LoggerFactory.getLogger( DefaultFileTransferManager.class );

    private static final String CACHE_NAME = "File Transfer Cache";

    private final Cache<String, FileTransfer> fileTransferMap;

    private final List<FileTransferEventListener> eventListeners = new ArrayList<>();

    /**
     * Default constructor creates the cache.
     */
    public DefaultFileTransferManager() {
        super("File Transfer Manager");
        fileTransferMap = CacheFactory.createCache(CACHE_NAME);
        InterceptorManager.getInstance().addInterceptor(new MetaFileTransferInterceptor());
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
        //noinspection unchecked
        List<Element> elements = element.elements();
        if (elements.isEmpty()) {
            return null;
        }
        for (Element childElement : elements) {
            String childNamespace = childElement.getNamespaceURI();
            if (namespace.equals(childNamespace)) {
                return childElement;
            }
        }

        return null;
    }

    @Override
    public boolean acceptIncomingFileTransferRequest(FileTransfer transfer)
            throws FileTransferRejectedException
    {
        if(transfer != null) {
            fireFileTransferStart( transfer.getSessionID(), false );
            String streamID = transfer.getSessionID();
            JID from = new JID(transfer.getInitiator());
            JID to = new JID(transfer.getTarget());
            cacheFileTransfer(ProxyConnectionManager.createDigest(streamID, from, to), transfer);
            return true;
        }
        return false;
    }

    @Override
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

    private FileTransfer createFileTransfer(JID from,
                                            JID to, Element siElement) {
        String streamID = siElement.attributeValue("id");
        String mimeType = siElement.attributeValue("mime-type");
        // Check profile, the only type we deal with currently is file transfer
        Element fileTransferElement = getChildElement(siElement, NAMESPACE_SI_FILETRANSFER);
        // Not valid form, reject
        if (fileTransferElement == null) {
            return null;
        }
        String fileName = fileTransferElement.attributeValue("name");
        String sizeString = fileTransferElement.attributeValue("size");
        if (fileName == null || sizeString == null) {
            return null;
        }

        long size;
        try {
            size = Long.parseLong(sizeString);
        }
        catch (Exception ex) {
            return null;
        }

        return new FileTransfer(from.toString(), to.toString(), streamID, fileName, size, mimeType);
    }

    @Override
    public void addListener( FileTransferEventListener eventListener )
    {
        eventListeners.add( eventListener );
    }

    @Override
    public void removeListener( FileTransferEventListener eventListener )
    {
        eventListeners.remove( eventListener );
    }

    @Override
    public void fireFileTransferStart( String sid, boolean isReady ) throws FileTransferRejectedException
    {
        final FileTransfer transfer = fileTransferMap.get( sid );
        for ( FileTransferEventListener listener : eventListeners )
        {
            try
            {
                listener.fileTransferStart( transfer, isReady );
            }
            catch ( FileTransferRejectedException ex )
            {
                Log.debug( "Listener '{}' rejected file transfer '{}'.", listener, transfer );
                throw ex;
            }
            catch ( Exception ex )
            {
                Log.warn( "Listener '{}' threw exception when being informed of file transfer complete for transfer '{}'.", listener, transfer, ex );
            }
        }
    }

    @Override
    public void fireFileTransferCompleted( String sid, boolean wasSuccessful )
    {
        final FileTransfer transfer = fileTransferMap.get( sid );
        for ( FileTransferEventListener listener : eventListeners )
        {
            try
            {
                listener.fileTransferComplete( transfer, wasSuccessful );
            }
            catch ( Exception ex )
            {
                Log.warn( "Listener '{}' threw exception when being informed of file transfer complete for transfer '{}'.", listener, transfer, ex );
            }
        }
    }

    /**
     * Interceptor to grab and validate file transfer meta information.
     */
    private class MetaFileTransferInterceptor implements PacketInterceptor {
        @Override
        public void interceptPacket(Packet packet, Session session, boolean incoming,
                                    boolean processed)
                throws PacketRejectedException
        {
            // We only want packets received by the server
            if (!processed && incoming && packet instanceof IQ) {
                IQ iq = (IQ) packet;
                Element childElement = iq.getChildElement();
                if(childElement == null) {
                    return;
                }
                String namespace = childElement.getNamespaceURI();
                String profile = childElement.attributeValue("profile");
                // Check that the SI is about file transfer and try creating a file transfer
                if (NAMESPACE_SI.equals(namespace) && NAMESPACE_SI_FILETRANSFER.equals(profile)) {
                    // If this is a set, check the feature offer
                    if (iq.getType().equals(IQ.Type.set)) {
                        JID from = iq.getFrom();
                        JID to = iq.getTo();

                        FileTransfer transfer = createFileTransfer(from, to, childElement);

                        try {
                            if (transfer == null || !acceptIncomingFileTransferRequest(transfer)) {
                                throw new PacketRejectedException();
                            }
                        }
                        catch (FileTransferRejectedException e) {
                            throw new PacketRejectedException(e);
                        }
                    }
                }
            }
        }
    }
}
