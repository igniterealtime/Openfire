/**
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.stun;

import de.javawi.jstun.test.demo.StunServer;
import org.dom4j.Element;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * STUN server and service module. It provides address discovery for p2p sessions to be
 * used for media transmission and receiving of UDP packets. It's especially useful for
 * clients behind NAT devices.
 *
 * @author Thiago Camargo
 */
public class STUNService extends BasicModule {

    private static final String ELEMENT_NAME = "stun";
    private static final String NAMESPACE = "google:jingleinfo";
    private static final String DEFAULT_EXTERNAL_ADDRESSES =
            "stun.xten.net:3478;" +
                    "jivesoftware.com:3478;" +
                    "igniterealtime.org:3478;" +
                    "stun.fwdnet.net:3478";

    private IQHandler stunIQHandler;
    private StunServer stunServer = null;
    private boolean enabled = false;
    private boolean localEnabled = false;

    private String primaryAddress = null;
    private String secondaryAddress = null;
    private int primaryPort;
    private int secondaryPort;

    private List<StunServerAddress> externalServers = null;

    /**
     * Constructs a new STUN Service
     */
    public STUNService() {
        super("STUN Service");
    }

    public void destroy() {
        super.destroy();
        stunServer = null;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        this.enabled = JiveGlobals.getBooleanProperty("stun.enabled", true);

        primaryAddress = JiveGlobals.getProperty("stun.address.primary");
        secondaryAddress = JiveGlobals.getProperty("stun.address.secondary");

        String addresses = JiveGlobals.getProperty("stun.external.addresses");
        // If no custom external addresses are defined, use the defaults.
        if (addresses == null) {
            addresses = DEFAULT_EXTERNAL_ADDRESSES;
        }
        externalServers = getStunServerAddresses(addresses);

        primaryPort = JiveGlobals.getIntProperty("stun.port.primary", 3478);
        secondaryPort = JiveGlobals.getIntProperty("stun.port.secondary", 3479);

        this.localEnabled = JiveGlobals.getBooleanProperty("stun.local.enabled", false);
        // If the local server is supposed to be enabled, ensure that primary and secondary
        // addresses are defined.
        if (localEnabled) {
            if (primaryAddress == null || secondaryAddress == null) {
                Log.warn("STUN server cannot be enabled. Primary and secondary addresses must be defined.");
                localEnabled = false;
            }
        }

        // Add listeners for STUN service being enabled and disabled via manual property changes.
        PropertyEventDispatcher.addListener(new PropertyEventListener() {

            public void propertySet(String property, Map<String, Object> params) {
                if (property.equals("stun.enabled")) {
                    boolean oldValue = enabled;
                    enabled = JiveGlobals.getBooleanProperty("stun.enabled", true);
                    //
                    if (enabled && !oldValue) {
                        startSTUNService();
                    } else if (!enabled && oldValue) {
                        stop();
                    }
                } else if (property.equals("stun.local.enabled")) {
                    localEnabled = JiveGlobals.getBooleanProperty("stun.local.enabled", false);
                }
            }

            public void propertyDeleted(String property, Map<String, Object> params) {
                if (property.equals("stun.enabled")) {
                    enabled = true;
                } else if (property.equals("stun.local.enabled")) {
                    localEnabled = false;
                }
            }

            public void xmlPropertySet(String property, Map<String, Object> params) {
                // Ignore.
            }

            public void xmlPropertyDeleted(String property, Map<String, Object> params) {
                // Ignore.
            }
        });
    }

    public void start() {
        if (isEnabled()) {
            startSTUNService();
            if (isLocalEnabled()) {
                startLocalServer();
            }
        }
    }

    private void startLocalServer() {
        try {
            InetAddress primary = InetAddress.getByName(primaryAddress);
            InetAddress secondary = InetAddress.getByName(secondaryAddress);

            if (primary != null && secondary != null) {
                stunServer = new StunServer(primaryPort, primary, secondaryPort, secondary);
                stunServer.start();
            } else {
                setLocalEnabled(false);
            }
        }
        catch (SocketException e) {
            Log.error("Disabling STUN server", e);
            setLocalEnabled(false);
        }
        catch (UnknownHostException e) {
            Log.error("Disabling STUN server", e);
            setLocalEnabled(false);
        }
    }

    private void startSTUNService() {
        XMPPServer server = XMPPServer.getInstance();
        // Register the STUN feature in disco.
        server.getIQDiscoInfoHandler().addServerFeature(NAMESPACE);
        // Add an IQ handler.
        stunIQHandler = new STUNIQHandler();
        server.getIQRouter().addHandler(stunIQHandler);
    }

    private void stopSTUNService() {
        XMPPServer server = XMPPServer.getInstance();
        server.getIQDiscoInfoHandler().removeServerFeature(NAMESPACE);
        if (stunIQHandler != null) {
            server.getIQRouter().removeHandler(stunIQHandler);
            stunIQHandler = null;
        }
    }

    public void stop() {
        super.stop();
        this.enabled = false;
        stopSTUNService();
        stopLocal();
    }

    private void stopLocal() {
        if (stunServer != null) {
            stunServer.stop();
        }
        stunServer = null;
    }

    public String getName() {
        return "stun";
    }

    public List<StunServerAddress> getExternalServers() {
        return externalServers;
    }

    public void addExternalServer(String server, String port) {
        externalServers.add(new StunServerAddress(server, port));

        String property = "";
        for (StunServerAddress stunServerAddress : externalServers) {
            if (!property.equals("")) {
                property += ";";
            }
            property += stunServerAddress.getServer() + ":" + stunServerAddress.getPort();
        }
        JiveGlobals.setProperty("stun.external.addresses", property);
    }

    public void removeExternalServer(int index) {
        externalServers.remove(index);
        String property = "";
        for (StunServerAddress stunServerAddress : externalServers) {
            if (!property.equals("")) {
                property += ";";
            }
            property += stunServerAddress.getServer() + ":" + stunServerAddress.getPort();
        }
        JiveGlobals.setProperty("stun.external.addresses", property);
    }

    /**
     * Returns true if the service is enabled.
     *
     * @return enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns true if the local STUN server is enabled.
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
        if (enabled && !this.enabled) {
            startSTUNService();
            if (isLocalEnabled()) {
                startLocalServer();
            }
        } else {
            stopSTUNService();
        }
        this.enabled = enabled;
        this.localEnabled = localEnabled;
    }

    /**
     * Set the Local STUN Server enable status.
     *
     * @param enabled boolean to enable or disable
     */
    public void setLocalEnabled(boolean enabled) {
        this.localEnabled = enabled;
        if (isLocalEnabled()) {
            startLocalServer();
        } else {
            stopLocal();
        }
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
        }
        catch (Exception e) {
            // Do Nothing
        }
        return list;
    }

    /**
     * Abstraction method used to convert a String into a STUN Server Address List
     *
     * @param addresses the String representation of server addresses with their
     *                  respective ports (server1:port1;server2:port2).
     * @return STUN server addresses list.
     */
    private List<StunServerAddress> getStunServerAddresses(String addresses) {

        List<StunServerAddress> list = new ArrayList<StunServerAddress>();

        if (addresses.equals("")) {
            return list;
        }

        String servers[] = addresses.split(";");

        for (String server : servers) {
            String address[] = server.split(":");
            StunServerAddress aux = new StunServerAddress(address[0], address[1]);
            if (!list.contains(aux)) {
                list.add(aux);
            }
        }
        return list;
    }

    /**
     * An IQ handler for STUN requests.
     */
    private class STUNIQHandler extends IQHandler {

        public STUNIQHandler() {
            super("stun");
        }

        public IQ handleIQ(IQ iq) throws UnauthorizedException {
            IQ reply = IQ.createResultIQ(iq);
            Element childElement = iq.getChildElement();
            String namespace = childElement.getNamespaceURI();
            Element childElementCopy = iq.getChildElement().createCopy();
            reply.setChildElement(childElementCopy);

            if (NAMESPACE.equals(namespace)) {
                if (isEnabled()) {
                    Element stun = childElementCopy.addElement("stun");
                    // If the local server is configured as a STUN server, send it as the first item.
                    if (isLocalEnabled()) {
                        StunServerAddress local;
                        local = new StunServerAddress(primaryAddress, String.valueOf(primaryPort));
                        if (!externalServers.contains(local)) {
                            Element server = stun.addElement("server");
                            server.addAttribute("host", local.getServer());
                            server.addAttribute("udp", local.getPort());
                        }
                    }
                    // Add any external STUN servers that are specified.
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
                    }
                    catch (UnknownHostException e) {
                        e.printStackTrace();
                    }
                }

            } else {
                // Answer an error since the server can't handle the requested namespace
                reply.setError(PacketError.Condition.service_unavailable);
            }

            try {
                Log.debug("RETURNED:" + reply.toXML());
            }
            catch (Exception e) {
                Log.error(e);
            }
            return reply;
        }

        public IQHandlerInfo getInfo() {
            return new IQHandlerInfo(ELEMENT_NAME, NAMESPACE);
        }
    }
}