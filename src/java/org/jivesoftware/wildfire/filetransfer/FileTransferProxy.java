/**
 * $Revision$
 * $Date$
 * <p/>
 * Copyright (C) 1999-2005 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.filetransfer;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.wildfire.*;
import org.jivesoftware.wildfire.auth.UnauthorizedException;
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.disco.DiscoInfoProvider;
import org.jivesoftware.wildfire.disco.DiscoItemsProvider;
import org.jivesoftware.wildfire.disco.DiscoServerItem;
import org.jivesoftware.wildfire.disco.ServerItemsProvider;
import org.jivesoftware.wildfire.forms.spi.XDataFormImpl;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Manages the transfering of files between two remote entities on the jabber network.
 * This class acts independtly as a Jabber component from the rest of the server, according to
 * the Jabber <a href="http://www.jabber.org/jeps/jep-0065.html">SOCKS5 bytestreams protocol</a>.
 *
 * @author Alexander Wenckus
 */
public class FileTransferProxy extends BasicModule
        implements ServerItemsProvider, DiscoInfoProvider, DiscoItemsProvider,
        RoutableChannelHandler {

    private static final String NAMESPACE = "http://jabber.org/protocol/bytestreams";

    private String proxyServiceName;

    private IQHandlerInfo info;
    private RoutingTable routingTable;
    private PacketRouter router;
    private String proxyIP;
    private ProxyConnectionManager connectionManager;


    public FileTransferProxy() {
        super("SOCKS5 file transfer proxy");

        info = new IQHandlerInfo("query", NAMESPACE);
    }

    public boolean handleIQ(IQ packet) throws UnauthorizedException {
        Element childElement = packet.getChildElement();
        String namespace = null;

        // ignore errors
        if (packet.getType() == IQ.Type.error) {
            return true;
        }
        if (childElement != null) {
            namespace = childElement.getNamespaceURI();
        }

        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            try {
                IQ reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(packet);
                router.route(reply);
                return true;
            }
            catch (UnauthorizedException e) {
                // Do nothing. This error should never happen
            }
        }
        else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            try {
                // a component
                IQ reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(packet);
                router.route(reply);
                return true;
            }
            catch (UnauthorizedException e) {
                // Do nothing. This error should never happen
            }
        }
        else if (NAMESPACE.equals(namespace)) {
            if (packet.getType() == IQ.Type.get) {
                IQ reply = IQ.createResultIQ(packet);
                Element newChild = reply.setChildElement("query", NAMESPACE);
                Element response = newChild.addElement("streamhost");
                response.addAttribute("jid", getServiceDomain());
                response.addAttribute("host", proxyIP);
                response.addAttribute("port", String.valueOf(connectionManager.getProxyPort()));
                router.route(reply);
                return true;
            }
            else if (packet.getType() == IQ.Type.set && childElement != null) {
                String sid = childElement.attributeValue("sid");
                JID from = packet.getFrom();
                JID to = new JID(childElement.elementTextTrim("activate"));

                IQ reply = IQ.createResultIQ(packet);
                try {
                    connectionManager.activate(from, to, sid);
                }
                catch (IllegalArgumentException ie) {
                    Log.error("Error activating connection", ie);
                    reply.setType(IQ.Type.error);
                    reply.setError(new PacketError(PacketError.Condition.not_allowed));
                }

                router.route(reply);
                return true;
            }
        }
        return false;
    }

    public IQHandlerInfo getInfo() {
        return info;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        proxyServiceName = JiveGlobals.getProperty("xmpp.proxy.service", "proxy");
        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();

        // Load the external IP and port information
        try {
            proxyIP = JiveGlobals.getProperty("xmpp.proxy.externalip",
                    InetAddress.getLocalHost().getHostAddress());
        }
        catch (UnknownHostException e) {
            Log.error("Couldn't discover local host", e);
        }

        connectionManager = new ProxyConnectionManager();
    }

    public void start() {
        super.start();

        if (isEnabled()) {
            connectionManager.processConnections(getProxyPort());
            routingTable.addRoute(getAddress(), this);
        }
    }

    public void stop() {
        super.stop();

        routingTable.removeRoute(getAddress());
        connectionManager.disable();
    }

    public void destroy() {
        super.destroy();

        connectionManager.shutdown();
    }

    public void setEnabled(boolean isEnabled) {
        JiveGlobals.setProperty("xmpp.proxy.enabled", Boolean.toString(isEnabled));
        if (isEnabled) {
            start();
        }
        else {
            stop();
        }
    }

    public boolean isEnabled() {
        return connectionManager.isRunning() ||
                JiveGlobals.getBooleanProperty("xmpp.proxy.enabled", true);
    }

    public void setProxyPort(int port) {
        JiveGlobals.setProperty("xmpp.proxy.port", Integer.toString(port));
    }

    public int getProxyPort() {
        return JiveGlobals.getIntProperty("xmpp.proxy.port", 7777);
    }

    /**
     * Returns the fully-qualifed domain name of this chat service.
     * The domain is composed by the service name and the
     * name of the XMPP server where the service is running.
     *
     * @return the file transfer server domain (service name + host name).
     */
    public String getServiceDomain() {
        return proxyServiceName + "." + XMPPServer.getInstance().getServerInfo().getName();
    }

    public JID getAddress() {
        return new JID(null, getServiceDomain(), null);
    }

    public Iterator getItems() {
        ArrayList<DiscoServerItem> items = new ArrayList<DiscoServerItem>();

        items.add(new DiscoServerItem() {
            public String getJID() {
                return getServiceDomain();
            }

            public String getName() {
                return "Socks 5 Bytestreams Proxy";
            }

            public String getAction() {
                return null;
            }

            public String getNode() {
                return null;
            }

            public DiscoInfoProvider getDiscoInfoProvider() {
                return FileTransferProxy.this;
            }

            public DiscoItemsProvider getDiscoItemsProvider() {
                return FileTransferProxy.this;
            }
        });
        return items.iterator();
    }

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        List<Element> identities = new ArrayList<Element>();
        // Answer the identity of the proxy
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "proxy");
        identity.addAttribute("name", "SOCKS5 Bytestreams Service");
        identity.addAttribute("type", "bytestreams");

        identities.add(identity);

        return identities.iterator();
    }

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        return Arrays.asList(new String[]{NAMESPACE, "http://jabber.org/protocol/disco#info"})
                .iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        return true;
    }

    public Iterator<Element> getItems(String name, String node, JID senderJID) {
        // A proxy server has no items
        return new ArrayList<Element>().iterator();
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        // Check if the packet is a disco request or a packet with namespace iq:register
        if (packet instanceof IQ) {
            if (handleIQ((IQ) packet)) {
                return;
            }
            else {
                IQ reply = IQ.createResultIQ((IQ) packet);
                reply.setChildElement(((IQ) packet).getChildElement().createCopy());
                reply.setError(PacketError.Condition.feature_not_implemented);
                router.route(reply);
            }
        }
    }
}
