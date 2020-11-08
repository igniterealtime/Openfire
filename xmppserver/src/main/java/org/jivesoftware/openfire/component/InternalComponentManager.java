/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.component;

import org.dom4j.Element;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.disco.IQDiscoInfoHandler;
import org.jivesoftware.openfire.disco.IQDiscoItemsHandler;
import org.jivesoftware.openfire.session.ComponentSession;
import org.jivesoftware.openfire.session.LocalComponentSession;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.CacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.*;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Manages the registration and delegation of Components, which includes External Components as well as components
 * that run in the Openfire JVM.
 *
 * The ComponentManager is responsible for managing registration and delegation of {@link Component Components},
 * as well as offering a facade around basic server functionality such as sending and receiving of packets.
 *
 * This component manager will be an internal service whose JID will be component.[domain]. So the
 * component manager will be able to send packets to other internal or external components and also
 * receive packets from other components or even from trusted clients (e.g. ad-hoc commands).
 *
 * @author Derek DeMoro
 */
public class InternalComponentManager extends BasicModule implements ClusterEventListener, ComponentManager, RoutableChannelHandler {

    private static final Logger Log = LoggerFactory.getLogger(InternalComponentManager.class);

    public static final String COMPONENT_CACHE_NAME = "Components";

    /**
     * Cache (unlimited, never expire) that tracks what component is available on which cluster node.
     * Key: component address, Value: identifier of each cluster node holding serving the component.
     */
    private Cache<JID, HashSet<NodeID>> componentCache;

    /**
     * A map that maps an address to a (facade for a) component.
     *
     * This map only contains data for components connected to the local cluster node.
     */
    final private Map<String, RoutableComponents> routables = new ConcurrentHashMap<>();

    /**
     * A map that has service discovery information for components.
     *
     * This map contains data for components all over the cluster.
     */
    private Map<String, IQ> componentInfo = new ConcurrentHashMap<>();

    /**
     * A map that maps the identify of an entity that requests a presence probe from
     * a component (where the value is the JID of the component.
     */
    // FIXME this construct limits every probee to exactly one presence probe. If the probee requests another probe after the first, the first request is silently overwritten. That's unlikely to be intended.
    private Map<JID, JID> presenceMap = new ConcurrentHashMap<>();

    /**
     * Holds the list of listeners that will be notified of component events.
     */
    private List<ComponentEventListener> listeners = new CopyOnWriteArrayList<>();

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

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        routingTable = server.getRoutingTable();
        componentCache = CacheFactory.createCache( COMPONENT_CACHE_NAME );
        ClusterManager.addListener( this );
    }

    @Override
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

    @Override
    public void stop() {
        super.stop();
        synchronized ( routables ) {
            // OF-1700: Shut down each connected component properly, which benefits other cluster nodes.
            final Set<String> subdomains = routables.keySet();
            subdomains.forEach( this::removeComponent );
        }
        if (getAddress() != null) {
            // Remove the route to this service
            XMPPServer.getInstance().getRoutingTable().removeComponentRoute(getAddress());
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        ClusterManager.removeListener( this );
    }

    @Override
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
            boolean isNewComponentRoute = false; // A subdomain can be served by more than one Component instance (mainly for load distribution).
            if (routable != null) {
                routable.addComponent(component);
            }
            else {
                routable = new RoutableComponents(componentJID, component);
                routables.put(subdomain, routable);

                if (!routingTable.hasComponentRoute(componentJID)) {
                    isNewComponentRoute = true;
                }
                // Add the route to the new service provided by the component
                routingTable.addComponentRoute(componentJID, routable);
                CacheUtil.addValueToMultiValuedCache( componentCache, componentJID, XMPPServer.getInstance().getNodeID(), HashSet::new );
            }

            // Initialize the new component
            try {
                component.initialize(componentJID, this);
                component.start();

                if (isNewComponentRoute) {
                    // Notify listeners that a new component has been registered
                    notifyComponentRegistered(componentJID);
                    // Alert other nodes of new registered domain event
                    CacheFactory.doClusterTask(new NotifyComponentRegistered(componentJID));
                }

                // Check for potential interested users.
                checkPresences();

                if (isNewComponentRoute) {
                    // Send a disco#info request to the new component. If the component provides information
                    // then it will be added to the list of discoverable server items.
                    checkDiscoSupport( component, componentJID );
                }
                if (isNewComponentRoute) {
                    // Send a SoftwareVersion (xep-0092) request to the new component. If the component provides information
                    // then it will be added to the sesssion for that component.
                    sendSoftwareVersion( component, componentJID );
                }
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
                    CacheUtil.removeValueFromMultiValuedCache( componentCache, componentJID, XMPPServer.getInstance().getNodeID() );
                    XMPPServer.getInstance().getRoutingTable().removeComponentRoute(componentJID);
                }
            }
        }
    }

    void notifyComponentRegistered(JID componentJID) {
        for (ComponentEventListener listener : listeners) {
            try {
                listener.componentRegistered(componentJID);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentRegistered' event!", e);
            }
        }
    }

    /**
     * Removes a component. The {@link Component#shutdown} method will be called on the
     * component. Note that if the component was an external component that was connected
     * several times then all its connections will be terminated.
     *
     * @param subdomain the subdomain of the component's address.
     */
    @Override
    public void removeComponent(String subdomain) {
        RoutableComponents components = null;
        if (routables == null || (components = routables.get(subdomain)) == null) {
            return;
        }
        List<Component> componentsToRemove = new ArrayList<>(components.getComponents());
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
                CacheUtil.removeValueFromMultiValuedCache( componentCache, componentJID, XMPPServer.getInstance().getNodeID() );
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
            try {
                listener.componentUnregistered(componentJID);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentUnregistered' event!", e);
            }
        }
    }

    void removeComponentInfo(JID componentJID) {
        // Remove any info stored with the component being removed
        componentInfo.remove(componentJID.getDomain());
    }

    @Override
    public void sendPacket(Component component, Packet packet) {
        if (packet != null && packet.getFrom() == null) {
            throw new IllegalArgumentException("Packet with no FROM address was received from component.");
        }
        
        PacketRouter router = XMPPServer.getInstance().getPacketRouter();
        if (router != null) {
            router.route(packet);
        }
    }

    @Override
    public IQ query(Component component, IQ packet, long timeout) throws ComponentException {
        final LinkedBlockingQueue<IQ> answer = new LinkedBlockingQueue<>(8);
        XMPPServer.getInstance().getIQRouter().addIQResultListener(packet.getID(), new IQResultListener() {
            @Override
            public void receivedAnswer(IQ packet) {
                answer.offer(packet);
            }

            @Override
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

    @Override
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

    IQ getComponentInfo( JID domain )
    {
        return componentInfo.get( domain.toString() );
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

    @Override
    public String getProperty(String name) {
        return JiveGlobals.getProperty(name);
    }

    @Override
    public void setProperty(String name, String value) {
        //Ignore
    }

    @Override
    public String getServerName() {
        return serverDomain;
    }

    public String getHomeDirectory() {
        return JiveGlobals.getHomeDirectory();
    }

    @Override
    public boolean isExternalMode() {
        return false;
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
     * Registers Probers who have not yet been serviced.
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

    /**
     *  Send a SoftwareVersion request to the new component. If the component provides information
     *  then it will be added to the  session of current component.
     *
     * @param component the new component that was added to this manager.
     * @param componentJID the XMPP address of the new component.
     */
    private void sendSoftwareVersion(Component component, JID componentJID) {
        // Build a "jabber:iq:version" request that will be sent to the component
        IQ iq = new IQ(IQ.Type.get);
        iq.setFrom(getAddress());
        iq.setTo(componentJID);
        iq.setChildElement("query", "jabber:iq:version");
        // Send the "jabber:iq:version" request to the component. The reply (if any) will be processed in
        // #process(Packet) or org.jivesoftware.openfire.net.ComponentStanzaHandler#rocessIQ(Packet)
        //sendPacket(component, iq);
        component.processPacket(iq);
    }

    @Override
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
    @Override
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
                    }else if ("query".equals(childElement.getQName().getName()) && "jabber:iq:version".equals(namespace)) {
                        try {
                            List<Element> elements =  childElement.elements();
                            for (Component component : components) {
                                if (component instanceof LocalComponentSession.LocalExternalComponent) {
                                    LocalComponentSession.LocalExternalComponent externalComponent =
                                            ( LocalComponentSession.LocalExternalComponent) component;
                                    LocalComponentSession session = externalComponent.getSession();
                                    if(session != null && session.getAddress() == iq.getFrom()){
                                        if (elements.size() >0){
                                            for (Element element : elements){
                                                session.setSoftwareVersionData(element.getName(), element.getStringValue());
                                            }
                                        } 
                                    }    

                                }
                            }  
                        } catch (Exception e) {
                            Log.error(e.getMessage(), e);
                        }
                    }else if("query".equals(childElement.getQName().getName()) && "http://jabber.org/protocol/disco#info".equals(namespace)){
                        //XEP-0232 if responses service discovery can include detailed information about the software application
                        for (Component component : components) {
                            if (component instanceof LocalComponentSession.LocalExternalComponent) {
                                LocalComponentSession.LocalExternalComponent externalComponent =
                                        ( LocalComponentSession.LocalExternalComponent) component;
                                LocalComponentSession session = externalComponent.getSession();
                                if(session != null && session.getAddress() == iq.getFrom()){
                                    IQDiscoInfoHandler.setSoftwareVersionDataFormFromDiscoInfo(childElement, session);
                                }    
                            }
                        }  
                    }
                }
            }
        }
    }

    void notifyComponentInfo(IQ iq) {
        for (ComponentEventListener listener : listeners) {
            try {
                listener.componentInfoReceived(iq);
            } catch (Exception e) {
                Log.warn("An exception occurred while dispatching a 'componentInfoReceived' event!", e);
            }
        }
    }

    void addComponentInfo(IQ iq) {
        componentInfo.put(iq.getFrom().getDomain(), iq);
    }

    @Override
    public void joinedCluster()
    {
        // The local node joined a cluster.
        //
        // Upon joining a cluster, clustered caches are reset to their clustered equivalent (by the swap from the local
        // cache implementation to the clustered cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.joinedCluster). This means that they now hold data that's
        // available on all other cluster nodes. Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // We joined a cluster. Determine what components run on other nodes,
        // and raise events for those that are not running on this node.
        // Eventing should be limited to the local cluster node, as other nodes
        // would already have had events when the components became available to
        // them.
        componentCache.entrySet().stream()
            .filter( entrySet -> !entrySet.getValue().contains( XMPPServer.getInstance().getNodeID() ) )
            .forEach( entry -> {
                Log.debug( "The local cluster node joined the cluster. The component '{}' is living on one (or more) other cluster nodes, but not ours. Invoking the 'component registered' event, and requesting service info.", entry.getKey());
                notifyComponentRegistered( entry.getKey() );
                // Request one of the cluster nodes to sent us a NotifyComponentInfo instance. This async request does not block the current thread, and will update the instance eventually, after which notifications are sent out.
                CacheFactory.doClusterTask( new RequestComponentInfoNotification( entry.getKey(), XMPPServer.getInstance().getNodeID() ), entry.getValue().iterator().next().toByteArray() );
            } );

        // Additionally, let other cluster nodes know about the components that the local
        // node offers, to allow them to raise events if needed.
        componentCache.entrySet().stream()
            .filter( entrySet -> entrySet.getValue().contains( XMPPServer.getInstance().getNodeID() ) && entrySet.getValue().size() == 1 )
            .map( Map.Entry::getKey )
            .forEach( componentJID -> {
                Log.debug( "The local cluster node joined the cluster. The component '{}' is living on our cluster node, but not on any others. Invoking the 'component registered' (and if applicable, the 'component info received') event on the remote nodes.", componentJID );
                CacheFactory.doClusterTask( new NotifyComponentRegistered( componentJID ) );
                final IQ info = componentInfo.get( componentJID.toString() );
                if ( info != null )
                {
                    CacheFactory.doClusterTask( new NotifyComponentInfo( info ) );
                }
            } );
    }

    @Override
    public void joinedCluster( final byte[] nodeID )
    {
        // Another node joined a cluster that we're already part of. It is expected that
        // the implementation of #joinedCluster() as executed on the cluster node that just
        // joined will synchronize all relevant data. This method need not do anything.
    }

    @Override
    public void leftCluster()
    {
        // The local cluster node left the cluster.
        if (XMPPServer.getInstance().isShuttingDown()) {
            // Do not put effort in restoring the correct state if we're shutting down anyway.
            return;
        }

        // Upon leaving a cluster, clustered caches are reset to their local equivalent (by the swap from the clustered
        // cache implementation to the default cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.leftCluster). This means that they now hold no data (as a new cache
        // has been created). Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // Determine what components that we are broadcasting were available only on other
        // cluster nodes than the local one. For these, unavailability events need
        // to be sent out (eventing will be limited to the local node, as we're no longer
        // part of a cluster).
        final Iterator<Map.Entry<String, IQ>> iterator = componentInfo.entrySet().iterator();
        while (iterator.hasNext()) {
            // TODO: Iterating over the 'componentInfo' is not ideal, as there might be components for which there is no recorded data in 'componentInfo'. There currently is no other way to determine what components were active on the cluster though.
            final Map.Entry<String, IQ> entry = iterator.next();
            final JID domain = new JID(entry.getKey());
            if ( !componentCache.containsKey( domain ) ) {
                Log.debug( "The local cluster node left the cluster. The component '{}' was living on one (or more) other cluster nodes, and is no longer available. Invoking the 'component unregistered' event.", domain );
                notifyComponentUnregistered( domain );

                // Also clean up the cache of cluster-wide service discovery results.
                iterator.remove();
            }
        }
    }

    @Override
    public void leftCluster( final byte[] nodeID )
    {
        // Another node left the cluster.
        //
        // If the cluster node leaves in an orderly fashion, it might have broadcasted
        // the necessary events itself. This cannot be depended on, as the cluster node
        // might have disconnected unexpectedly (as a result of a crash or network issue).
        //
        // Determine what components were available only on that node, and remove them.
        // For these, 'component unavailable' events need to be sent out. If the cluster
        // node exited cleanly, we won't find any entries to work on. If it did not
        // exit cleanly, all remaining cluster nodes will be in a race to clean up the
        // same data. The implementation below accounts for that, by only having the
        // senior cluster node to perform the cleanup.
        if (ClusterManager.isSeniorClusterMember())
        {
            final Map<Boolean, Map<JID, HashSet<NodeID>>> modified = CacheUtil.removeValueFromMultiValuedCache(componentCache, NodeID.getInstance(nodeID));

            modified.get(false).keySet().forEach(removedDomain -> {
                Log.debug("Cluster node {} just left the cluster, and was the only node on which component '{}' was living. Invoking the 'component unregistered' event on all remaining cluster nodes.",
                          NodeID.getInstance(nodeID),
                          removedDomain);
                notifyComponentUnregistered(removedDomain);

                // As we have removed the disappeared components from the clustered cache, other nodes
                // can no longer determine what components became unavailable. We'll need to broadcast
                // the unavailable event over the entire cluster!
                CacheFactory.doClusterTask(new NotifyComponentUnregistered(removedDomain));

                // Also clean up the cache of cluster-wide service discovery results.
                componentInfo.remove(removedDomain.toString());
            });
        }
    }

    @Override
    public void markedAsSeniorClusterMember()
    {
        // No action needed.
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining ({@link #joinedCluster()} or leaving
     * ({@link #leftCluster()} a cluster.
     */
    private void restoreCacheContent() {
        Log.trace( "Restoring cache content for cache '{}' by adding all component domains that are provided by the local cluster node.", componentCache.getName() );
        routingTable.getComponentsDomains().stream()
            .map(JID::new)
            .filter(domain -> routingTable.isLocalRoute(domain))
            .forEach(domain -> CacheUtil.addValueToMultiValuedCache( componentCache, domain, XMPPServer.getInstance().getNodeID(), HashSet::new ) );
    }

    /**
     * Exposes a Component as a RoutableChannelHandler.
     */
    private static class RoutableComponents implements RoutableChannelHandler {

        private JID jid;
        final private List<Component> components = new ArrayList<>();

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

        @Override
        public JID getAddress() {
            return jid;
        }

        @Override
        public void process(Packet packet) throws PacketException {
            Component component = getNextComponent();
            component.processPacket(packet);
        }
    }
}
