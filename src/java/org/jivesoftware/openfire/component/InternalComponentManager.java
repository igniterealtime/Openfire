/**
 * $RCSfile$
 * $Revision: 3126 $
 * $Date: 2005-11-30 15:20:53 -0300 (Wed, 30 Nov 2005) $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.component;

import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.IQDiscoItemsHandler;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.util.*;
import org.xmpp.component.Component;
import org.xmpp.component.ComponentException;
import org.xmpp.component.ComponentManager;
import org.xmpp.component.ComponentManagerFactory;
import org.xmpp.packet.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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

    private ConcurrentMap<String, ComponentLifecycleImpl> components
            = new ConcurrentHashMap<String, ComponentLifecycleImpl>();
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
    private final RoutingTable routingTable;
    private final IQDiscoItemsHandler discoItemsHandler;
    private final JiveProperties jiveProperties;

    public InternalComponentManager() {
        this(XMPPServer.getInstance().getRoutingTable(),
                XMPPServer.getInstance().getIQDiscoItemsHandler(), JiveProperties.getInstance());
    }

    public InternalComponentManager(RoutingTable routingTable, IQDiscoItemsHandler discoItemsHandler,
                                    JiveProperties jiveProperties)
    {
        super("Internal Component Manager");
        instance = this;
        this.routingTable = routingTable;
        this.discoItemsHandler = discoItemsHandler;
        this.jiveProperties = jiveProperties;
    }

    public static InternalComponentManager getInstance() {
        return instance;
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
    }

    public void start() {
        // Set this ComponentManager as the current component manager
        ComponentManagerFactory.setComponentManager(instance);

        XMPPServer server = XMPPServer.getInstance();
        serverDomain = server.getServerInfo().getName();
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
        addComponent(subdomain, component, null);
    }

    private void startComponent(String subdomain, Component component) {
       JID componentJID = new JID(subdomain + "." + serverDomain);

        // Add the route to the new service provided by the component
        routingTable.addComponentRoute(componentJID,
                new RoutableComponent(componentJID, component));

        // Initialize the new component
        try {
            component.start();

            // Check for potential interested users.
            checkPresences();
            // Send a disco#info request to the new component. If the component provides information
            // then it will be added to the list of discoverable server items.
            checkDiscoSupport(component, componentJID);
            Log.debug("Component registered for domain: " + subdomain);

            // Notify listeners that a new component has been registered
            for (ComponentEventListener listener : listeners) {
                listener.componentRegistered(component, componentJID);
            }
        }
        catch (RuntimeException e) {
            // Remove the route
            routingTable.removeComponentRoute(componentJID);
            // Rethrow the exception
            throw e;
        }

    }


    public ComponentLifecycle addComponent(String subdomain, Component component,
                                           String jiveProperty)
            throws ComponentException
    {
        ComponentLifecycleImpl componentLifecycle = new ComponentLifecycleImpl(subdomain, component);

        ComponentLifecycleImpl oldLifecycle = components.putIfAbsent(subdomain, componentLifecycle);
        if(oldLifecycle != null) {
            throw new IllegalArgumentException("Domain (" + subdomain +
                    ") already taken by another component: " + oldLifecycle.component);
        }

        try {
            component.initialize(getComponentJID(subdomain), this);
        } catch (ComponentException e) {
            components.remove(subdomain, componentLifecycle);
            throw e;
        }
        catch (RuntimeException e) {
            components.remove(subdomain, componentLifecycle);
            throw e;
        }

        componentLifecycle.setJiveProperty(jiveProperty);
        return componentLifecycle;
    }

    public void removeComponent(String subdomain) {
        Log.debug("Unregistering component for domain: " + subdomain);
        ComponentLifecycleImpl component = components.remove(subdomain);
        // Remove any info stored with the component being removed
        componentInfo.remove(subdomain);
        stopComponent(subdomain, component.component);
    }

    private void stopComponent(String subdomain, Component component) {
        JID componentJID = getComponentJID(subdomain);

        // Remove the route for the service provided by the component
        routingTable.removeComponentRoute(componentJID);

        // Remove the disco item from the server for the component that is being removed
        discoItemsHandler.removeComponentItem(componentJID.toBareJID());

        // Ask the component to shutdown
        if (component != null) {
            component.shutdown();
        }

        // Notify listeners that an existing component has been unregistered
        for (ComponentEventListener listener : listeners) {
            listener.componentUnregistered(component, componentJID);
        }
        Log.debug("Component unregistered for domain: " + subdomain);
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
        });
        sendPacket(component, packet);
        IQ reply = null;
        try {
            reply = answer.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Ignore
        }
        if (reply == null) {
            reply = IQ.createResultIQ(packet);
            reply.setError(PacketError.Condition.item_not_found);
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
        for (Map.Entry<String, ComponentLifecycleImpl> entry : components.entrySet()) {
            String subdomain = entry.getKey();
            Component component = entry.getValue().component;
            JID componentJID = new JID(subdomain + "." + serverDomain);
            listener.componentRegistered(component, componentJID);
            // Check if there is disco#info stored for the component
            IQ disco = componentInfo.get(subdomain);
            if (disco != null) {
                listener.componentInfoReceived(component, disco);
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

    private JID getComponentJID(String subdomain) {
      return new JID(subdomain + "." + serverDomain);
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
                Log.debug(msg);
            }

            public void debug(String msg, Throwable throwable) {
                Log.debug(msg, throwable);
            }

            public void debug(Throwable throwable) {
                Log.debug(throwable);
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
     * @return the component with the specified id.
     */
    private Component getComponent(JID componentJID) {
        if (componentJID.getNode() != null) {
            return null;
        }
        Component component = components.get(componentJID.getDomain()).component;
        if (component != null) {
            return component;
        }
        else {
            // Search again for those JIDs whose domain include the server name but this
            // time remove the server name from the JID's domain
            String serverName = componentJID.getDomain();
            int index = serverName.lastIndexOf("." + serverDomain);
            if (index > -1) {
                return components.get(serverName.substring(0, index)).component;
            }
        }
        return null;
    }

    /**
     * Returns true if a component is associated to the specified address. Components
     * registered with this JVM or other cluster nodes are going to be considered.
     *
     * @param componentJID the address of the component. This is the complete domain.
     * @return true if a component is associated to the specified address.
     */
    public boolean hasComponent(JID componentJID) {
        if (componentJID.getNode() != null || componentJID.getResource() != null) {
            return false;
        }
//        if (componentJID.getDomain().lastIndexOf("." + serverDomain) == -1) {
//            componentJID = new JID(componentJID.getDomain() + "." + serverDomain);
//        }
        return routingTable.hasComponentRoute(componentJID);
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
                routingTable.routePacket(probee, presence);

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
        sendPacket(component, iq);
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
        Component component = getComponent(packet.getFrom());
        // Only process packets that were sent by registered components
        if (component != null) {
            if (packet instanceof IQ && IQ.Type.result == ((IQ) packet).getType()) {
                IQ iq = (IQ) packet;
                Element childElement = iq.getChildElement();
                String namespace = null;
                if (childElement != null) {
                    namespace = childElement.getNamespaceURI();
                }
                if ("http://jabber.org/protocol/disco#info".equals(namespace) && childElement != null) {
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
                        if (component instanceof ComponentSession.ExternalComponent) {
                            ComponentSession.ExternalComponent externalComponent =
                                    (ComponentSession.ExternalComponent) component;
                            externalComponent.setName(identity.attributeValue("name"));
                            externalComponent.setType(identity.attributeValue("type"));
                            externalComponent.setCategory(identity.attributeValue("category"));
                        }
                    }
                    catch (Exception e) {
                        Log.error("Error processing disco packet of component: " + component +
                                " - " + packet.toXML(), e);
                    }
                    // Store the IQ disco#info returned by the component
                    String subdomain = packet.getFrom().getDomain().replace("." + serverDomain, "");
                    componentInfo.put(subdomain, iq);
                    // Notify listeners that a component answered the disco#info request
                    for (ComponentEventListener listener : listeners) {
                        listener.componentInfoReceived(component, iq);
                    }
                }
            }
        }
    }

    /**
     * Exposes a Component as a RoutableChannelHandler.
     */
    public static class RoutableComponent implements RoutableChannelHandler {

        private JID jid;
        private Component component;

        public RoutableComponent(JID jid, Component component) {
            this.jid = jid;
            this.component = component;
        }

        public JID getAddress() {
            return jid;
        }

        public void process(Packet packet) throws PacketException {
            component.processPacket(packet);
        }
    }

    private class ComponentLifecycleImpl implements ComponentLifecycle, PropertyEventListener {
        private String jiveProperty;

        private boolean isRunning = false;

        private final Component component;
        private final String subdomain;

        private ComponentLifecycleImpl(String subdomain, Component component) {
            this.subdomain = subdomain;
            this.component = component;
        }

        public synchronized void start() {
            if(jiveProperty != null) {
                jiveProperties.put(jiveProperty, Boolean.TRUE.toString());
            }
            else {
                startComponent();
            }
        }

        private synchronized void startComponent() {
            if(isRunning) {
                return;
            }

            InternalComponentManager.this.startComponent(subdomain, component);

            isRunning = true;
        }

        public synchronized void stop() {
            if(jiveProperty != null) {
                jiveProperties.put(jiveProperty, Boolean.FALSE.toString());
            }
            else {
                stopComponent();
            }
        }

        private synchronized void stopComponent() {
            if(!isRunning) {
                return;
            }

            InternalComponentManager.this.stopComponent(subdomain, component);

            isRunning = false;
        }

        public void setJiveProperty(String jiveProperty) {
            if(jiveProperty == null) {
                this.jiveProperty = null;
                PropertyEventDispatcher.removeListener(this);
                startComponent();
            }

            this.jiveProperty = jiveProperty;
            PropertyEventDispatcher.addListener(this);

            if(JiveGlobals.getBooleanProperty(jiveProperty, true)) {
                startComponent();
            }
            else {
                stopComponent();
            }
        }

        public synchronized boolean isRunning() {
            return isRunning;
        }

        public void propertySet(String property, Map<String, Object> params) {
            if(property.equals(jiveProperty)) {
                boolean enabled = Boolean.FALSE.toString().equals(params.get("value"));
                if(enabled) {
                    startComponent();
                }
                else {
                    stopComponent();
                }
            }
        }

        public void propertyDeleted(String property, Map<String, Object> params) {
            if(property.equals(jiveProperty)) {
                startComponent();
            }
        }

        public void xmlPropertySet(String property, Map<String, Object> params) {

        }

        public void xmlPropertyDeleted(String property, Map<String, Object> params) {

        }
    }
}