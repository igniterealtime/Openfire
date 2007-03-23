/**
 * $RCSfile$
 * $Revision: 3144 $
 * $Date: 2005-12-01 14:20:11 -0300 (Thu, 01 Dec 2005) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.wildfire.stun;

import de.javawi.jstun.test.demo.StunServer;
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
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * STUN Server and Service Module
 * Provides especial Address discovery for p2p sessions to be used for media transmission and receiving of UDP packets.
 * Especialy used for behind NAT users to ensure connectivity between parties.
 *
 * @author Thiago Camargo
 */
public class STUNService extends BasicModule implements ServerItemsProvider, RoutableChannelHandler, DiscoInfoProvider, DiscoItemsProvider {

    private String serviceName;
    private RoutingTable routingTable;
    private PacketRouter router;

    private StunServer stunServer = null;
    private String name = "stun";
    private boolean enabled = false;
    private boolean localEnabled = false;

    private String primaryAddress;
    private String secondaryAddress;
    private int primaryPort = 3478;
    private int secondaryPort = 3479;

    private SessionManager sessionManager = null;

    private List<StunServerAddress> externalServers = null;

    private String defaultExternalAddresses = "stun.xten.net:3478;jivesoftware.com:3478;igniterealtime.org:3478";

    public static final String NAMESPACE = "google:jingleinfo";

    /**
     * Constructs a new STUN Service
     */
    public STUNService() {
        super("STUN Service");
    }

    /**
     * Load config using JiveGlobals
     */
    private void loadSTUNConfig() {

        primaryAddress = JiveGlobals.getProperty("stun.address.primary");
        secondaryAddress = JiveGlobals.getProperty("stun.address.secondary");

        String addresses = JiveGlobals.getProperty("stun.external.addresses");

        if (addresses == null) addresses = defaultExternalAddresses;

        externalServers = getStunServerAddresses(addresses);

        if (primaryAddress == null || primaryAddress.equals(""))
            primaryAddress = JiveGlobals.getProperty("xmpp.domain",
                    JiveGlobals.getXMLProperty("network.interface", "localhost"));

        if (secondaryAddress == null || secondaryAddress.equals(""))
            secondaryAddress = "127.0.0.1";

        try {
            primaryPort = Integer.valueOf(JiveGlobals.getProperty("stun.port.primary"));
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            secondaryPort = Integer.valueOf(JiveGlobals.getProperty("stun.port.secondary"));
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }

        this.enabled = JiveGlobals.getBooleanProperty("stun.enabled", true);
        this.localEnabled = JiveGlobals.getBooleanProperty("stun.local.enabled", true);

    }

    public void destroy() {
        super.destroy();
        stunServer = null;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        sessionManager = server.getSessionManager();
        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();
        serviceName = JiveGlobals.getProperty("stun.serviceName", name);
        serviceName = serviceName == null ? name : serviceName.equals("") ? name : serviceName;
        loadSTUNConfig();
    }

    public void start() {
        if (isEnabled()) {
            startService();
            if (isLocalEnabled())
                startLocalServer();
        } else {
            XMPPServer.getInstance().getIQDiscoItemsHandler().removeServerItemsProvider(this);
        }
    }

    public void startLocalServer() {
        try {

            InetAddress primary = InetAddress.getByName(primaryAddress);
            InetAddress secondary = InetAddress.getByName(secondaryAddress);

            if (primary != null && secondary != null) {

                stunServer = new StunServer(primaryPort, primary, secondaryPort, secondary);
                stunServer.start();

            } else
                setLocalEnabled(false);

        } catch (SocketException e) {
            Log.error("Disabling STUN server", e);
            setLocalEnabled(false);
        } catch (UnknownHostException e) {
            Log.error("Disabling STUN server", e);
            setLocalEnabled(false);
        }
    }

    public void startService() {
        routingTable.addRoute(getAddress(), this);
        XMPPServer server = XMPPServer.getInstance();
        server.getIQDiscoItemsHandler().addServerItemsProvider(this);
    }

    public void stop() {
        super.stop();
        this.enabled = false;
        XMPPServer.getInstance().getIQDiscoItemsHandler().removeComponentItem(getAddress().toString());
        if (routingTable != null)
            routingTable.removeRoute(getAddress());
    }

    public void stopLocal() {
        if (stunServer != null)
            stunServer.stop();
        stunServer = null;
    }

    public String getName() {
        return serviceName;
    }

    public Iterator<Element> getItems(String name, String node, JID senderJID) {
        List<Element> identities = new ArrayList<Element>();
        // Answer the identity of the proxy
        Element identity = DocumentHelper.createElement("item");
        identity.addAttribute("jid", getServiceDomain());
        identity.addAttribute("name", "STUN Service");
        identities.add(identity);

        return identities.iterator();
    }

    public void process(Packet packet) throws UnauthorizedException, PacketException {
        // Check if user is allowed to send packet to this service
        if (packet instanceof IQ) {
            // Handle disco packets
            IQ iq = (IQ) packet;
            // Ignore IQs of type ERROR or RESULT
            if (IQ.Type.error == iq.getType() || IQ.Type.result == iq.getType()) {
                return;
            }
            processIQ(iq);
        }
    }

    private void processIQ(IQ iq) {
        IQ reply = IQ.createResultIQ(iq);
        Element childElement = iq.getChildElement();
        String namespace = childElement.getNamespaceURI();
        Element childElementCopy = iq.getChildElement().createCopy();
        reply.setChildElement(childElementCopy);

        if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
            reply = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ(iq);
            router.route(reply);
            return;
        } else if ("http://jabber.org/protocol/disco#items".equals(namespace)) {
            // a component
            reply = XMPPServer.getInstance().getIQDiscoItemsHandler().handleIQ(iq);
            router.route(reply);
            return;
        } else if (NAMESPACE.equals(namespace)) {

            if (isEnabled()) {
                Element stun = childElementCopy.addElement("stun");

                if (isLocalEnabled()) {
                    StunServerAddress local = null;
                    local = new StunServerAddress(primaryAddress, String.valueOf(primaryPort));
                    if (!externalServers.contains(local)) {
                        Element server = stun.addElement("server");
                        server.addAttribute("host", local.getServer());
                        server.addAttribute("udp", local.getPort());
                    }
                }
                for (StunServerAddress stunServerAddress : externalServers) {
                    Element server = stun.addElement("server");
                    server.addAttribute("host", stunServerAddress.getServer());
                    server.addAttribute("udp", stunServerAddress.getPort());
                }

                try {
                    String ip = sessionManager.getSession(iq.getFrom()).getConnection().getInetAddress().getHostAddress();
                    if (ip != null) {
                        Element publicIp = childElementCopy.addElement("publicip");
                        publicIp.addAttribute("ip", ip);
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }

            }

        } else {
            // Answer an error since the server can't handle the requested namespace
            reply.setError(PacketError.Condition.service_unavailable);
        }

        try {
            Log.debug("RETURNED:" + reply.toXML());
            router.route(reply);
        }

        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Returns the fully-qualifed domain name of this chat service.
     * The domain is composed by the service name and the
     * name of the XMPP server where the service is running.
     *
     * @return the file transfer server domain (service name + host name).
     */
    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getName();
    }

    public JID getAddress() {
        return new JID(null, getServiceDomain(), null);
    }

    public List<StunServerAddress> getExternalServers() {
        return externalServers;
    }

    public void addExternalServer(String server, String port) {
        externalServers.add(new StunServerAddress(server, port));

        String property = "";
        for (StunServerAddress stunServerAddress : externalServers) {
            if (!property.equals("")) property += ";";
            property += stunServerAddress.getServer() + ":" + stunServerAddress.getPort();
        }
        JiveGlobals.setProperty("stun.external.addresses", property);
    }

    public void removeExternalServer(int index) {
        externalServers.remove(index);
        String property = "";
        for (StunServerAddress stunServerAddress : externalServers) {
            if (!property.equals("")) property += ";";
            property += stunServerAddress.getServer() + ":" + stunServerAddress.getPort();
        }
        JiveGlobals.setProperty("stun.external.addresses", property);
    }

    public Iterator<DiscoServerItem> getItems() {
        List<DiscoServerItem> items = new ArrayList<DiscoServerItem>();
        if (!isEnabled()) {
            return items.iterator();
        }

        items.add(new DiscoServerItem() {
            public String getJID() {
                return getServiceDomain();
            }

            public String getName() {
                return "STUN Service";
            }

            public String getAction() {
                return null;
            }

            public String getNode() {
                return null;
            }

            public DiscoInfoProvider getDiscoInfoProvider() {
                return STUNService.this;
            }

            public DiscoItemsProvider getDiscoItemsProvider() {
                return STUNService.this;
            }
        });
        return items.iterator();
    }

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        List<Element> identities = new ArrayList<Element>();
        // Answer the identity of the proxy
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "proxy");
        identity.addAttribute("name", "STUN Service");
        identity.addAttribute("type", "stun");
        identities.add(identity);

        return identities.iterator();
    }

    public Iterator<String> getFeatures(String name, String node, JID senderJID) {
        return Arrays.asList(NAMESPACE,
                "http://jabber.org/protocol/disco#info").iterator();
    }

    public XDataFormImpl getExtendedInfo(String name, String node, JID senderJID) {
        return null;
    }

    public boolean hasInfo(String name, String node, JID senderJID) {
        return true;
    }

    /**
     * Get if the service is enabled.
     *
     * @return enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get if the local STUN Server is enabled.
     *
     * @return enabled
     */
    public boolean isLocalEnabled() {
        return localEnabled;
    }

    /**
     * Set the service enable status.
     *
     * @param enabled      boolean to enable or disable
     * @param localEnabled local Server enable or disable
     */
    public void setEnabled(boolean enabled, boolean localEnabled) {
        this.enabled = enabled;
        this.localEnabled = localEnabled;
        if (isEnabled()) {
            startService();
            if (isLocalEnabled())
                startLocalServer();
        } else {
            stop();
        }
    }

    /**
     * Set the Local STUN Server enable status.
     *
     * @param enabled boolean to enable or disable
     */
    public void setLocalEnabled(boolean enabled) {
        this.localEnabled = enabled;
        if (isLocalEnabled())
            startLocalServer();
        else
            stopLocal();
    }

    /**
     * Get the secondary Port used by the STUN server
     *
     * @return secondary Port used by the STUN server
     */
    public int getSecondaryPort() {
        return secondaryPort;
    }

    /**
     * Get the primary Port used by the STUN server
     *
     * @return primary Port used by the STUN server
     */
    public int getPrimaryPort() {
        return primaryPort;
    }

    /**
     * Get the secondary Address used by the STUN server
     *
     * @return secondary Address used by the STUN server
     */
    public String getSecondaryAddress() {
        return secondaryAddress;
    }

    /**
     * Get the primary Address used by the STUN server
     *
     * @return primary Address used by the STUN server
     */
    public String getPrimaryAddress() {
        return primaryAddress;
    }

    public List<InetAddress> getAddresses() {
        List<InetAddress> list = new ArrayList<InetAddress>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                Enumeration<InetAddress> iaddresses = iface.getInetAddresses();
                while (iaddresses.hasMoreElements()) {
                    InetAddress iaddress = iaddresses.nextElement();
                    if (!iaddress.isLoopbackAddress() && !iaddress.isLinkLocalAddress()) {
                        list.add(iaddress);
                    }
                }
            }
        } catch (Exception e) {
            // Do Nothing
        }
        return list;
    }

    /**
     * Abstraction method used to convert a String into a STUN Server Address List
     *
     * @param addresses the String representation of Server Addresses with their respective Ports (server1:port1;server2:port2)
     * @return STUN Server Addresses List
     */
    public List<StunServerAddress> getStunServerAddresses(String addresses) {

        List<StunServerAddress> list = new ArrayList<StunServerAddress>();

        if (addresses.equals("")) return list;

        String servers[] = addresses.split(";");

        for (String server : servers) {
            String address[] = server.split(":");
            StunServerAddress aux = new StunServerAddress(address[0], address[1]);
            if (!list.contains(aux))
                list.add(aux);
        }

        return list;
    }

}
