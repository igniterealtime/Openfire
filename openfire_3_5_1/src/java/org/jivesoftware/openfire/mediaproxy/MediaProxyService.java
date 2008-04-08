/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.mediaproxy;

import org.dom4j.Attribute;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.*;
import org.jivesoftware.openfire.forms.spi.XDataFormImpl;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * A proxy service for UDP traffic such as RTP. It provides Jingle transport candidates
 * to be used for media transmission. The media proxy is especially useful for users
 * behind NAT devices or firewalls that prevent peer to peer communication..
 *
 * @author Thiago Camargo
 */
public class MediaProxyService extends BasicModule
        implements ServerItemsProvider, RoutableChannelHandler, DiscoInfoProvider, DiscoItemsProvider {

    private String serviceName;
    private RoutingTable routingTable;
    private PacketRouter router;
    private Echo echo = null;
    private int echoPort = 10020;
    private SessionManager sessionManager = null;

    private MediaProxy mediaProxy = null;
    private boolean enabled = true;

    public static final String NAMESPACE = "http://www.jivesoftware.com/protocol/rtpbridge";

    /**
     * Constructs a new MediaProxyService.
     */
    public MediaProxyService() {
        super("Media Proxy Service");
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);

        sessionManager = server.getSessionManager();
        // In some cases, the domain name of the server may not be the actual address of the machine
        // (ie, when using DNS SRV records). In that case, the "mediaproxy.externalip" property should be
        // set to the IP address of the actual server where the media proxy is listening.
        String ipAddress = JiveGlobals.getProperty("mediaproxy.externalip", server.getServerInfo().getXMPPDomain());
        mediaProxy = new MediaProxy(ipAddress);

        String defaultName = "rtpbridge";
        serviceName = JiveGlobals.getProperty("mediaproxy.serviceName", defaultName);
        serviceName = serviceName.equals("") ? defaultName : serviceName;

        echoPort = JiveGlobals.getIntProperty("mediaproxy.echoPort", echoPort);

        routingTable = server.getRoutingTable();
        router = server.getPacketRouter();

        initMediaProxy();
    }

    public void start() {
        if (isEnabled()) {

            try {
                echo = new Echo(echoPort);
                Thread t = new Thread(echo);
                t.start();
            } catch (UnknownHostException e) {
                // Ignore
            } catch (SocketException e) {
                // Ignore
            }

            routingTable.addComponentRoute(getAddress(), this);
            XMPPServer.getInstance().getIQDiscoItemsHandler().addServerItemsProvider(this);
        } else {
            if (echo != null) echo.cancel();
            XMPPServer.getInstance().getIQDiscoItemsHandler().removeComponentItem(getAddress().toString());
        }
    }

    public void stop() {
        super.stop();
        mediaProxy.stopProxy();
        XMPPServer.getInstance().getIQDiscoItemsHandler().removeComponentItem(getAddress().toString());
        routingTable.removeComponentRoute(getAddress());
        if (echo != null) echo.cancel();
    }

    // Component Interface

    public String getName() {
        // Get the name from the plugin.xml file.
        return serviceName;
    }

    public Iterator<DiscoItem> getItems(String name, String node, JID senderJID) {
        // A proxy server has no items
        return new ArrayList<DiscoItem>().iterator();
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
        } else if (NAMESPACE.equals(namespace) && enabled) {

            Element candidateElement = childElementCopy.element("candidate");
            String sid = childElementCopy.attribute("sid").getValue() + "-" + iq.getFrom();

            if (candidateElement != null) {
                childElementCopy.remove(candidateElement);
                Element candidate = childElementCopy.addElement("candidate ");
                ProxyCandidate proxyCandidate = mediaProxy.addRelayAgent(sid, iq.getFrom().toString());
                Log.debug("MediaProxyService: "+sid);
                proxyCandidate.start();
                candidate.addAttribute("name", "voicechannel");
                candidate.addAttribute("ip", mediaProxy.getPublicIP());
                candidate.addAttribute("porta", String.valueOf(proxyCandidate.getLocalPortA()));
                candidate.addAttribute("portb", String.valueOf(proxyCandidate.getLocalPortB()));
                candidate.addAttribute("pass", proxyCandidate.getPass());

            } else {
                candidateElement = childElementCopy.element("relay");
                if (candidateElement != null) {
                    MediaProxySession session = mediaProxy.getSession(sid);
                    Log.debug("MediaProxyService: "+sid);
                    if (session != null) {
                        Attribute pass = candidateElement.attribute("pass");

                        if (pass != null && pass.getValue().trim().equals(session.getPass().trim())) {
                            Attribute portA = candidateElement.attribute("porta");
                            Attribute portB = candidateElement.attribute("portb");
                            Attribute hostA = candidateElement.attribute("hosta");
                            Attribute hostB = candidateElement.attribute("hostb");

                            try {
                                if (hostA != null && portA != null) {
                                    for (int i = 0; i < 2; i++) {
                                        session.sendFromPortA(hostB.getValue(), Integer.parseInt(portB.getValue()));
                                    }
                                }
                            }
                            catch (Exception e) {
                                Log.error(e);
                            }

                        } else {
                            reply.setError(PacketError.Condition.forbidden);
                        }
                    }
                    childElementCopy.remove(candidateElement);
                } else {
                    candidateElement = childElementCopy.element("publicip");
                    if (candidateElement != null) {
                        childElementCopy.remove(candidateElement);
                        Element publicIp = childElementCopy.addElement("publicip");
                        try {
                            String ip = sessionManager.getSession(iq.getFrom()).getHostAddress();
                            if (ip != null) {
                                publicIp.addAttribute("ip", ip);
                            }
                        } catch (UnknownHostException e) {
                            Log.error(e);
                        }

                    } else {
                        childElementCopy.remove(candidateElement);
                        reply.setError(PacketError.Condition.forbidden);
                    }

                }
            }
        } else {
            // Answer an error since the server can't handle the requested namespace
            reply.setError(PacketError.Condition.service_unavailable);
        }

        try {
            if (Log.isDebugEnabled()) {
                Log.debug("MediaProxyService: RETURNED:" + reply.toXML());
            }
            router.route(reply);
        }
        catch (Exception e) {
            Log.error(e);
        }
    }

    /**
     * Load config using JiveGlobals
     */
    private void initMediaProxy() {
        try {
            long idleTime =
                    Long.valueOf(JiveGlobals.getProperty("mediaproxy.idleTimeout"));
            mediaProxy.setIdleTime(idleTime);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            long lifetime =
                    Long.valueOf(JiveGlobals.getProperty("mediaproxy.lifetime"));
            mediaProxy.setLifetime(lifetime);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            int minPort = Integer.valueOf(JiveGlobals.getProperty("mediaproxy.portMin"));
            mediaProxy.setMinPort(minPort);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        try {
            int maxPort = JiveGlobals.getIntProperty("mediaproxy.portMax", mediaProxy.getMaxPort());
            mediaProxy.setMaxPort(maxPort);
        }
        catch (NumberFormatException e) {
            // Do nothing let the default values to be used.
        }
        this.enabled = JiveGlobals.getBooleanProperty("mediaproxy.enabled");
    }

    /**
     * Returns the fully-qualifed domain name of this chat service.
     * The domain is composed by the service name and the
     * name of the XMPP server where the service is running.
     *
     * @return the file transfer server domain (service name + host name).
     */
    public String getServiceDomain() {
        return serviceName + "." + XMPPServer.getInstance().getServerInfo().getXMPPDomain();
    }

    public JID getAddress() {
        return new JID(null, getServiceDomain(), null);
    }

    public Iterator<DiscoServerItem> getItems()
	{
		List<DiscoServerItem> items = new ArrayList<DiscoServerItem>();
		if (!isEnabled())
		{
			return items.iterator();
		}

		final DiscoServerItem item = new DiscoServerItem(new JID(
			getServiceDomain()), "Media Proxy Service", null, null, this, this);
		items.add(item);
		return items.iterator();
	}

    public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
        List<Element> identities = new ArrayList<Element>();
        // Answer the identity of the proxy
        Element identity = DocumentHelper.createElement("identity");
        identity.addAttribute("category", "proxy");
        identity.addAttribute("name", "Media Proxy Service");
        identity.addAttribute("type", "rtpbridge");

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
     * Return the list of active Agents
     *
     * @return list of active agents
     */
    public Collection<MediaProxySession> getAgents() {
        return mediaProxy.getSessions();
    }

    /**
     * Set the keep alive delay of the mediaproxy agents.
     * When an agent stay more then this delay, the agent is destroyed.
     *
     * @param delay time in millis
     */
    public void setKeepAliveDelay(long delay) {
        mediaProxy.setIdleTime(delay);
    }

    /**
     * Returns the maximum amount of time (in milleseconds) that a session can
     * be idle before it's closed.
     *
     * @return the max idle time in millis.
     */
    public long getIdleTime() {
        return mediaProxy.getIdleTime();
    }

    /**
     * Set Minimal port value to listen for incoming packets.
     *
     * @param minPort port value to listen for incoming packets
     */
    public void setMinPort(int minPort) {
        mediaProxy.setMinPort(minPort);
    }

    /**
     * Set Maximum port value to listen for incoming packets.
     *
     * @param maxPort port value to listen for incoming packets
     */
    public void setMaxPort(int maxPort) {
        mediaProxy.setMaxPort(maxPort);
    }

    /**
     * Get Minimal port value to listen from incoming packets.
     *
     * @return minPort
     */
    public int getMinPort() {
        return mediaProxy.getMinPort();
    }

    /**
     * Get Maximum port value to listen from incoming packets.
     *
     * @return maxPort
     */
    public int getMaxPort() {
        return mediaProxy.getMaxPort();
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
     * Set the service enable status.
     *
     * @param enabled boolean value setting enabled or disabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (isEnabled()) {
            start();
        } else {
            stop();
        }
    }

    /**
     * Stops every running agents
     */
    public void stopAgents() {
        mediaProxy.stopProxy();
    }

    /**
     * Get the Life Time of Sessions
     *
     * @return lifetime in seconds
     */
    public long getLifetime() {
        return mediaProxy.getLifetime();
    }

    /**
     * Set the Life time of Sessions
     *
     * @param lifetime lifetime in seconds
     */
    public void setLifetime(long lifetime) {
        mediaProxy.setLifetime(lifetime);
    }

    /**
     * Get the Port used to the UDP Echo Test
     *
     * @return port number
     */
    public int getEchoPort() {
        return echoPort;
    }

    /**
     * Set the Port used to the UDP Echo Test
     *
     * @param echoPort port number
     */
    public void setEchoPort(int echoPort) {
        this.echoPort = echoPort;
    }
}
