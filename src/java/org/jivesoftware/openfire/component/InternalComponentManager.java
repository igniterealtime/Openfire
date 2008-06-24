/**
 * $RCSfile$
 * $Revision: 3126 $
 * $Date: 2005-11-30 15:20:53 -0300 (Wed, 30 Nov 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.component;

import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.IQDiscoItemsHandler;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages the registration and delegation of Components. The ComponentManager
 * is responsible for managing registration and delegation of {@link Component Components},
 * as well as offering a facade around basic server functionallity such as sending and
 * receiving of packets.<p>
 *
 * This component manager will be an internal service whose JID will be component.[domain]. So the
 * component manager will be able to send packets to other internal or external components and also
 * receive packets from other components or even from trusted clients (e.g. ad-hoc commands).
 *
 * @author Derek DeMoro
 */
public class InternalComponentManager extends BasicModule implements ComponentManager, RoutableChannelHandler {

    final private Map<String, RoutableComponents> routables = new ConcurrentHashMap<String, RoutableComponents>();
    private Map<String, IQ> componentInfo = new ConcurrentHashMap<String, IQ>();
    private Map<JID, JID> presenceMap = new ConcurrentHashMap<JID, JID>();
    /**
     * Holds the list of listeners that will be notified of component events.
     */
    private List<ComponentEventListener> listeners = new CopyOnWriteArrayList<ComponentEventListener>();

    private static InternalComponentManager instance;
    /**
     * XMPP address of this internal service. The address is of the form: component.[domain]
     */
    private JID serviceAddress;
    /**
     * Holds the domain of the server. We are using an iv since we use this value many times
     * in many methods.
     */
    private String serverDomain;
    private RoutingTable routingTable;

    public InternalComponentManager() {
        super("Internal Component Manager");
        instance = this;
    }

    public static InternalComponentManager getInstance() {
        return instance;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        routingTable = server.getRoutingTable();
    }

    public void start() {
        // Set this ComponentManager as the current component manager
        ComponentManagerFactory.setComponentManager(instance);

        XMPPServer server = XMPPServer.getInstance();
        serverDomain = server.getServerInfo().getXMPPDomain();
        // Set the address of this internal service. component.[domain]
        serviceAddress = new JID(null, "component." + serverDomain, null);
        if (!server.isSetupMode()) {
            // Add a route to this service
            server.getRoutingTable().addComponentRoute(getAddress(), this);
        }
    }

    public void stop() {
        super.stop();
        if (getAddress() != null) {
            // Remove the route to this service
            XMPPServer.getInstance().getRoutingTable().removeComponentRoute(getAddress());
        }
    }

    public void addComponent(String subdomain, Component component) throws ComponentException {
        synchronized (routables) {
            RoutableComponents routable = routables.get(subdomain);
            if (routable != null && routable.hasComponent(component)) {
                // This component has already registered with this subdomain.
                // TODO: Is this all we should do?  Should we return an error?
                return;
            }
            Log.debug("InternalComponentManager: Registering component for domain: " + subdomain);
            JID componentJID = new JID(subdomain + "." + serverDomain);
            boolean notifyListeners = false;
            if (routable != null) {
                routable.addComponent(component);
            }
            else {
                routable = new RoutableComponents(componentJID, component);
                routables.put(subdomain, routable);

                if (!routingTable.hasComponentRoute(componentJID)) {
                    notifyListeners = true;
                }
                // Add the route to the new service provided by the component
                routingTable.addComponentRoute(componentJID, routable);
            }

            // Initialize the new component
            try {
                component.initialize(componentJID, this);
                component.start();

                if (notifyListeners) {
                    // Notify listeners that a new component has been registered
                    notifyComponentRegistered(componentJID);
                    // Alert other nodes of new registered domain event
                    CacheFactory.doClusterTask(new NotifyComponentRegistered(componentJID));
                }

                // Check for potential interested users.
                checkPresences();
                // Send a disco#info request to the new component. If the component provides information
                // then it will be added to the list of discoverable server items.
                checkDiscoSupport(component, componentJID);
                Log.debug("InternalComponentManager: Component registered for domain: " + subdomain);
            }
            catch (Exception e) {
                // Unregister the component's domain
                routable.removeComponent(component);
                if (e instanceof ComponentException) {
                    // Rethrow the exception
                    throw (ComponentException)e;
                }
                // Rethrow the exception
                throw new ComponentException(e);
            }
            finally {
                if (routable.numberOfComponents() == 0) {
                    // If there are no more components associated with this subdomain, remove it.
                    routables.remove(subdomain);
                    // Remove the route
                    XMPPServer.getInstance().getRoutingTable().removeComponentRoute(componentJID);
                }
            }
        }
    }

    void notifyComponentRegistered(JID componentJID) {
        for (ComponentEventListener listener : listeners) {
            listener.componentRegistered(componentJID);
        }
    }

    /**
     * Removes a component. The {@link Component#shutdown} method will be called on the
     * component. Note that if the component was an external component that was connected
     * several times then all its connections will be terminated.
     *
     * @param subdomain the subdomain of the component's address.
     */
    public void removeComponent(String subdomain) {
        List<Component> componentsToRemove = new ArrayList<Component>(routables.get(subdomain).getComponents());
        for (Component component : componentsToRemove) {
            removeComponent(subdomain, component);
        }
    }

    /**
     * Removes a given component. Unlike {@link #removeComponent(String)} this method will just
     * remove a single component instead of all components associated to the subdomain. External
     * components may connect several times and register for the same subdomain. This method
     * just removes a singled connection not all of them.
     *
     * @param subdomain the subdomain of the component's address.
     * @param component specific component to remove.
     */
    public void removeComponent(String subdomain, Component component) {
        if (component == null) {
            return;
        }
        synchronized (routables) {
            Log.debug("InternalComponentManager: Unregistering component for domain: " + subdomain);
            RoutableComponents routable = routables.get(subdomain);
            routable.removeComponent(component);
            if (routable.numberOfComponents() == 0) {
                routables.remove(subdomain);

                JID componentJID = new JID(subdomain + "." + serverDomain);

                // Remove the route for the service provided by the component
                routingTable.removeComponentRoute(componentJID);

                // Ask the component to shutdown
                component.shutdown();

                if (!routingTable.hasComponentRoute(componentJID)) {
                    // Remove the disco item from the server for the component that is being removed
                    IQDiscoItemsHandler iqDiscoItemsHandler = XMPPServer.getInstance().getIQDiscoItemsHandler();
                    if (iqDiscoItemsHandler != null) {
                        iqDiscoItemsHandler.removeComponentItem(componentJID.toBareJID());
                    }
                    removeComponentInfo(componentJID);
                    // Notify listeners that an existing component has been unregistered
                    notifyComponentUnregistered(componentJID);
                    // Alert other nodes of component removed event
                    CacheFactory.doClusterTask(new NotifyComponentUnregistered(componentJID));
                }
                Log.debug("InternalComponentManager: Component unregistered for domain: " + subdomain);
            }
            else {
                Log.debug("InternalComponentManager: Other components still tied to domain: " + subdomain);
            }
        }
    }

    void notifyComponentUnregistered(JID componentJID) {
        for (ComponentEventListener listener : listeners) {
            listener.componentUnregistered(componentJID);
        }
    }

    void removeComponentInfo(JID componentJID) {
        // Remove any info stored with the component being removed
        componentInfo.remove(componentJID.getDomain());
    }

    public void sendPacket(Component component, Packet packet) {
        if (packet != null && packet.getFrom() == null) {
            throw new IllegalArgumentException("Packet with no FROM address was received from component.");
        }
        
        PacketRouter router = XMPPServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    public IQ query(Component component, IQ packet, int timeout) throws ComponentException {
        final LinkedBlockingQueue<IQ> answer = new LinkedBlockingQueue<IQ>(8);
        XMPPServer.getInstance().getIQRouter().addIQResultListener(packet.getID(), new IQResultListener() {
            public void receivedAnswer(IQ packet) {
                answer.offer(packet);
            }

            public void answerTimeout(String packetId) {
                Log.warn("An answer to a previously sent IQ stanza was never received. Packet id: " + packetId);
            }
        });
        sendPacket(component, packet);
        IQ reply = null;
        try {
            reply = answer.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Ignore
        }
        return reply;
    }

    public void query(Component component, IQ packet, IQResultListener listener) throws ComponentException {
        XMPPServer.getInstance().getIQRouter().addIQResultListener(packet.getID(), listener);
        sendPacket(component, packet);
    }

    /**
     * Adds a new listener that will be notified of component events. Events being
     * notified are: 1) when a component is added to the component manager, 2) when
     * a component is deleted and 3) when disco#info is received from a component.
     *
     * @param listener the new listener to notify of component events.
     */
    public void addListener(ComponentEventListener listener) {
        listeners.add(listener);
        // Notify the new listener about existing components
        for (String domain : routingTable.getComponentsDomains()) {
            JID componentJID = new JID(domain);
            listener.componentRegistered(componentJID);
            // Check if there is disco#info stored for the component
            IQ disco = componentInfo.get(domain);
            if (disco != null) {
                listener.componentInfoReceived(disco);
            }
        }
    }

    /**
     * Removes the specified listener from the listeners being notified of component
     * events.
     *
     * @param listener the listener to remove.
     */
    public void removeListener(ComponentEventListener listener) {
        listeners.remove(listener);
    }

    public String getProperty(String name) {
        return JiveGlobals.getProperty(name);
    }

    public void setProperty(String name, String value) {
        //Ignore
    }

    public String getServerName() {
        return serverDomain;
    }

    public String getHomeDirectory() {
        return JiveGlobals.getHomeDirectory();
    }

    public boolean isExternalMode() {
        return false;
    }

    public org.xmpp.component.Log getLog() {
        return new  org.xmpp.component.Log() {
            public void error(String msg) {
                Log.error(msg);
            }

            public void error(String msg, Throwable throwable) {
                Log.error(msg, throwable);
            }

            public void error(Throwable throwable) {
                Log.error(throwable);
            }

            public void warn(String msg) {
                Log.warn(msg);
            }

            public void warn(String msg, Throwable throwable) {
                Log.warn(msg, throwable);
            }

            public void warn(Throwable throwable) {
                Log.warn(throwable);
            }

            public void info(String msg) {
                Log.info(msg);
            }

            public void info(String msg, Throwable throwable) {
                Log.info(msg, throwable);
            }

            public void info(Throwable throwable) {
                Log.info(throwable);
            }

            public void debug(String msg) {
                Log.debug("InternalComponentManager: "+msg);
            }

            public void debug(String msg, Throwable throwable) {
                Log.debug(msg, throwable);
            }

            public void debug(Throwable throwable) {
                Log.debug("InternalComponentManager: ",throwable);
            }
        };
    }

    /**
     * Retrieves the <code>Component</code> which is mapped to the specified JID. The
     * look up will only be done on components that were registered with this JVM. That
     * means that components registered in other cluster nodes are not going to be
     * considered.
     *
     * @param componentJID the jid mapped to the component.
     * @return the list of components with the specified id.
     */
    private List<Component> getComponents(JID componentJID) {
        synchronized (routables) {
            if (componentJID.getNode() != null) {
                return Collections.emptyList();
            }
            RoutableComponents routable = routables.get(componentJID.getDomain());
            if (routable != null) {
                return routable.getComponents();
            }
            else {
                // Search again for those JIDs whose domain include the server name but this
                // time remove the server name from the JID's domain
                String serverName = componentJID.getDomain();
                int index = serverName.lastIndexOf("." + serverDomain);
                if (index > -1) {
                    routable = routables.get(serverName.substring(0, index));
                    if (routable != null) {
                        return routable.getComponents();
                    }
                }
            }
            return Collections.emptyList();
        }
    }

    /**
     * Returns true if a component is associated to the specified address. Components
     * registered with this JVM or other cluster nodes are going to be considered.
     *
     * @param componentJID the address of the component. This is the complete domain.
     * @return true if a component is associated to the specified address.
     */
    public boolean hasComponent(JID componentJID) {
        synchronized (routables) {
            if (componentJID.getNode() != null || componentJID.getResource() != null) {
                return false;
            }
    //        if (componentJID.getDomain().lastIndexOf("." + serverDomain) == -1) {
    //            componentJID = new JID(componentJID.getDomain() + "." + serverDomain);
    //        }
            return routingTable.hasComponentRoute(componentJID);
        }
    }

    /**
     * Registers Probeers who have not yet been serviced.
     *
     * @param prober the jid probing.
     * @param probee the presence being probed.
     */
    public void addPresenceRequest(JID prober, JID probee) {
        presenceMap.put(prober, probee);
    }

    private void checkPresences() {
        for (JID prober : presenceMap.keySet()) {
            JID probee = presenceMap.get(prober);

            if (routingTable.hasComponentRoute(probee)) {
                Presence presence = new Presence();
                presence.setFrom(prober);
                presence.setTo(probee);
                routingTable.routePacket(probee, presence, false);

                // No reason to hold onto prober reference.
                presenceMap.remove(prober);
            }
        }
    }

    /**
     *  Send a disco#info request to the new component. If the component provides information
     *  then it will be added to the list of discoverable server items.
     *
     * @param component the new component that was added to this manager.
     * @param componentJID the XMPP address of the new component.
     */
    private void checkDiscoSupport(Component component, JID componentJID) {
        // Build a disco#info request that will be sent to the component
        IQ iq = new IQ(IQ.Type.get);
        iq.setFrom(getAddress());
        iq.setTo(componentJID);
        iq.setChildElement("query", "http://jabber.org/protocol/disco#info");
        // Send the disco#info request to the component. The reply (if any) will be processed in
        // #process(Packet)
//        sendPacket(component, iq);
        component.processPacket(iq);
    }

    public JID getAddress() {
        return serviceAddress;
    }

    /**
     * Processes packets that were sent to this service. Currently only packets that were sent from
     * registered components are being processed. In the future, we may also process packet of
     * trusted clients. Trusted clients may be able to execute ad-hoc commands such as adding or
     * removing components.
     *
     * @param packet the packet to process.
     */
    public void process(Packet packet) throws PacketException {
        List<Component> components = getComponents(packet.getFrom());
        // Only process packets that were sent by registered components
        if (!components.isEmpty()) {
            if (packet instanceof IQ && IQ.Type.result == ((IQ) packet).getType()) {
                IQ iq = (IQ) packet;
                Element childElement = iq.getChildElement();
                if (childElement != null) {
                    String namespace = childElement.getNamespaceURI();
                    if ("http://jabber.org/protocol/disco#info".equals(namespace)) {
                        // Add a disco item to the server for the component that supports disco
                        Element identity = childElement.element("identity");
                        if (identity == null) {
                            // Do nothing since there are no identities in the disco#info packet
                            return;
                        }
                        try {
                            XMPPServer.getInstance().getIQDiscoItemsHandler().addComponentItem(packet.getFrom()
                                    .toBareJID(),
                                    identity.attributeValue("name"));
                            for (Component component : components) {
                                if (component instanceof ComponentSession.ExternalComponent) {
                                    ComponentSession.ExternalComponent externalComponent =
                                            (ComponentSession.ExternalComponent) component;
                                    externalComponent.setName(identity.attributeValue("name"));
                                    externalComponent.setType(identity.attributeValue("type"));
                                    externalComponent.setCategory(identity.attributeValue("category"));
                                }
                            }
                        }
                        catch (Exception e) {
                            Log.error("Error processing disco packet of components: " + components +
                                    " - " + packet.toXML(), e);
                        }
                        // Store the IQ disco#info returned by the component
                        addComponentInfo(iq);
                        // Notify listeners that a component answered the disco#info request
                        notifyComponentInfo(iq);
                        // Alert other cluster nodes
                        CacheFactory.doClusterTask(new NotifyComponentInfo(iq));
                    }
                }
            }
        }
    }

    void notifyComponentInfo(IQ iq) {
        for (ComponentEventListener listener : listeners) {
            listener.componentInfoReceived(iq);
        }
    }

    void addComponentInfo(IQ iq) {
        componentInfo.put(iq.getFrom().getDomain(), iq);
    }

    /**
     * Exposes a Component as a RoutableChannelHandler.
     */
    private static class RoutableComponents implements RoutableChannelHandler {

        private JID jid;
        final private List<Component> components = new ArrayList<Component>();

        public RoutableComponents(JID jid, Component component) {
            this.jid = jid;
            addComponent(component);
        }

        public void addComponent(Component component) {
            synchronized (components) {
                components.add(component);
            }
        }

        public void removeComponent(Component component) {
            synchronized (components) {
                components.remove(component);
            }
        }

        public void removeAllComponents() {
            synchronized (components) {
                components.clear();
            }
        }

        public Boolean hasComponent(Component component) {
            return components.contains(component);
        }

        public Integer numberOfComponents() {
            return components.size();
        }

        public List<Component> getComponents() {
            return components;
        }

        private Component getNextComponent() {
            Component component;
            synchronized (components) {
                component = components.get(0);
                Collections.rotate(components, 1);
            }
            return component;
        }

        public JID getAddress() {
            return jid;
        }

        public void process(Packet packet) throws PacketException {
            Component component = getNextComponent();
            component.processPacket(packet);
        }
    }
}