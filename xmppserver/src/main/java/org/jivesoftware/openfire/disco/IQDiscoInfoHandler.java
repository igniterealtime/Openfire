/*
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.disco;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.handler.IQBlockingHandler;
import org.jivesoftware.openfire.handler.IQPrivateHandler;
import org.jivesoftware.util.cache.CacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jivesoftware.admin.AdminConsole;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.cluster.ClusterEventListener;
import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.openfire.cluster.NodeID;
import org.jivesoftware.openfire.entitycaps.EntityCapabilitiesManager;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.SystemProperty;
import org.xmpp.forms.DataForm;
import org.xmpp.forms.FormField;
import org.xmpp.forms.FormField.Type;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.PacketError;
import org.xmpp.resultsetmanagement.ResultSet;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.Lock;

/**
 * IQDiscoInfoHandler is responsible for handling disco#info requests. This class holds a map with
 * the main entities and the associated DiscoInfoProvider. We are considering the host of the
 * recipient JIDs as main entities. It's the DiscoInfoProvider responsibility to provide information
 * about the JID's name together with any possible requested node.
 * <p>
 * For example, let's have in the entities map the following entries: "localhost" and
 * "conference.localhost". Associated with each entry we have different DiscoInfoProviders. Now we
 * receive a disco#info request for the following JID: "room@conference.localhost" which is a disco
 * request for a MUC room. So IQDiscoInfoHandler will look for the DiscoInfoProvider associated
 * with the JID's host which in this case is "conference.localhost". Once we have located the
 * provider we will delegate to the provider the responsibility to provide the info specific to
 * the JID's name which in this case is "room". Among the information that a room could provide we
 * could find its identity and the features it supports (e.g. 'muc_passwordprotected',
 * 'muc_unmoderated', etc.). Finally, after we have collected all the information provided by the
 * provider we will add it to the reply. On the other hand, if no provider was found or the provider
 * has no information for the requested name/node then a not-found error will be returned.</p>
 *
 * @author Gaston Dombiak
 */
public class IQDiscoInfoHandler extends IQHandler implements ClusterEventListener {

    private static final Logger Log = LoggerFactory.getLogger(IQDiscoInfoHandler.class);
    public static final String NAMESPACE_DISCO_INFO = "http://jabber.org/protocol/disco#info";
    private Map<String, DiscoInfoProvider> entities = new HashMap<>();
    private Set<String> localServerFeatures = new CopyOnWriteArraySet<>();
    private Cache<String, HashSet<NodeID>> serverFeatures;
    private List<ServerIdentitiesProvider> serverIdentityProviders = new ArrayList<>();
    private Map<String, DiscoInfoProvider> serverNodeProviders = new ConcurrentHashMap<>();
    private IQHandlerInfo info;

    private List<UserIdentitiesProvider> anonymousUserIdentityProviders = new ArrayList<>();
    private List<UserIdentitiesProvider> registeredUserIdentityProviders = new ArrayList<>();
    private List<UserFeaturesProvider> anonymousUserFeatureProviders = new ArrayList<>();
    private List<UserFeaturesProvider> registeredUserFeatureProviders = new ArrayList<>();

    public static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("xmpp.iqdiscoinfo.xformsoftwareversion")
        .setDefaultValue(Boolean.TRUE)
        .setDynamic(Boolean.TRUE)
        .build();

    public IQDiscoInfoHandler() {
        super("XMPP Disco Info Handler");
        info = new IQHandlerInfo("query", NAMESPACE_DISCO_INFO);

        anonymousUserIdentityProviders.add( new UserIdentitiesProvider()
        {
            @Override
            public Iterator<Element> getIdentities()
            {
                final Element userIdentity = DocumentHelper.createElement( "identity" );
                userIdentity.addAttribute( "category", "account" );
                userIdentity.addAttribute( "type", "anonymous" );

                return Collections.singleton( userIdentity ).iterator();
            }
        } );

        registeredUserIdentityProviders.add( new UserIdentitiesProvider()
        {
            @Override
            public Iterator<Element> getIdentities()
            {
                final Element userIdentity = DocumentHelper.createElement( "identity" );
                userIdentity.addAttribute( "category", "account" );
                userIdentity.addAttribute( "type", "registered" );

                return Collections.singleton( userIdentity ).iterator();
            }
        } );
    }

    @Override
    public IQHandlerInfo getInfo() {
        return info;
    }

    @Override
    public IQ handleIQ(IQ packet) {
        // Create a copy of the sent pack that will be used as the reply
        // we only need to add the requested info to the reply if any otherwise add 
        // a not found error
        IQ reply = IQ.createResultIQ(packet);

        // Look for a DiscoInfoProvider associated with the requested entity.
        // We consider the host of the recipient JID of the packet as the entity. It's the 
        // DiscoInfoProvider responsibility to provide information about the JID's name together 
        // with any possible requested node.  
        DiscoInfoProvider infoProvider = getProvider(packet.getTo() == null ?
                XMPPServer.getInstance().getServerInfo().getXMPPDomain() : packet.getTo().getDomain());
        if (infoProvider != null) {
            // Get the JID's name
            String name = packet.getTo() == null ? null : packet.getTo().getNode();
            if (name == null || name.trim().length() == 0) {
                name = null;
            }
            // Get the requested node
            Element iq = packet.getChildElement();
            String node = iq.attributeValue("node");
            //String node = metaData.getProperty("query:node");

            // Legacy implementation assumes that, when querying the server, the node is null.
            // This is not true for the XEP-0115 based requests for the server itself. As a
            // hack, the node value is considered 'null' when the request that's being handled
            // appears to be such a request.
            if ( node != null && node.startsWith( EntityCapabilitiesManager.OPENFIRE_IDENTIFIER_NODE + "#" ) ) {
                node = null;
            }

            // Check if we have information about the requested name and node
            if (infoProvider.hasInfo(name, node, packet.getFrom())) {
                reply.setChildElement(iq.createCopy());
                Element queryElement = reply.getChildElement();

                // Add to the reply all the identities provided by the DiscoInfoProvider
                Element identity;
                Iterator<Element> identities = infoProvider.getIdentities(name, node, packet.getFrom());
                while (identities.hasNext()) {
                    identity = identities.next();
                    identity.setQName(new QName(identity.getName(), queryElement.getNamespace()));
                    queryElement.add((Element)identity.clone());
                }

                // Add to the reply all the features provided by the DiscoInfoProvider
                Iterator<String> features = infoProvider.getFeatures(name, node, packet.getFrom());
                boolean hasDiscoInfoFeature = false;
                boolean hasDiscoItemsFeature = false;
                boolean hasResultSetManagementFeature = false;

                while (features.hasNext()) {
                    final String feature = features.next();
                    queryElement.addElement("feature").addAttribute("var", feature);
                    if (feature.equals(NAMESPACE_DISCO_INFO)) {
                        hasDiscoInfoFeature = true;
                    } else if (feature.equals("http://jabber.org/protocol/disco#items")) {
                        hasDiscoItemsFeature = true;
                    } else if (feature.equals(ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT)) {
                        hasResultSetManagementFeature = true;
                    }
                }

                if (hasDiscoItemsFeature && !hasResultSetManagementFeature) {
                    // IQDiscoItemsHandler provides result set management
                    // support.
                    queryElement.addElement("feature").addAttribute("var",
                            ResultSet.NAMESPACE_RESULT_SET_MANAGEMENT);
                }

                if (!hasDiscoInfoFeature) {
                    // XEP-0030 requires that every entity that supports service
                    // discovery broadcasts the disco#info feature.
                    queryElement.addElement("feature").addAttribute("var", NAMESPACE_DISCO_INFO);
                    // XEP-0411 requires that every entity that supports service
                    // dicovery broadcasts the conversion between 'PEP' and 'Private Storage' feature
                    if(XMPPServer.getInstance().getPrivateStorage().isEnabled()){ 
                        //allow only if private storage is enabled
                       queryElement.addElement("feature").addAttribute("var", "urn:xmpp:bookmarks-conversion:0");
                    }
                }
                // Add to the reply the multiple extended info (XDataForm) provided by the DiscoInfoProvider
                final Set<DataForm> dataForms = infoProvider.getExtendedInfos( name, node, packet.getFrom() );
                if ( dataForms != null ) {
                    dataForms.forEach( dataForm -> queryElement.add( dataForm.getElement() ) );
                }
            }
            else {
                // If the DiscoInfoProvider has no information for the requested name and node 
                // then answer a not found error
                reply.setChildElement(packet.getChildElement().createCopy());
                reply.setError(PacketError.Condition.item_not_found);
            }
        }
        else {
            // If we didn't find a DiscoInfoProvider then answer a not found error
            reply.setChildElement(packet.getChildElement().createCopy());
            reply.setError(PacketError.Condition.item_not_found);
        }

        return reply;
    }

    /**
     * Sets the DiscoInfoProvider to use when a disco#info packet is sent to the server itself
     * and the specified node. For instance, if node matches "http://jabber.org/protocol/offline"
     * then a special DiscoInfoProvider should be use to return information about offline messages.
     *
     * @param node the node that the provider will handle.
     * @param provider the DiscoInfoProvider that will handle disco#info packets sent with the
     *        specified node.
     */
    public void setServerNodeInfoProvider(String node, DiscoInfoProvider provider) {
        serverNodeProviders.put(node, provider);
    }

    /**
     * Removes the DiscoInfoProvider to use when a disco#info packet is sent to the server itself
     * and the specified node.
     *
     * @param node the node that the provider was handling.
     */
    public void removeServerNodeInfoProvider(String node) {
        serverNodeProviders.remove(node);
    }

    /**
     * Returns the DiscoInfoProvider responsible for providing information about a given entity or
     * null if none was found.
     *
     * @param name the name of the identity.
     * @return the DiscoInfoProvider responsible for providing information about a given entity or
     *         null if none was found.
     */
    private DiscoInfoProvider getProvider(String name) {
        return entities.get(name);
    }

    /**
     * Sets that a given DiscoInfoProvider will provide information about a given entity. This
     * message must be used when new modules (e.g. MUC) are implemented and need to provide
     * information about them.
     *
     * @param name     the name of the entity.
     * @param provider the DiscoInfoProvider that will provide the entity's information.
     */
    protected void setProvider(String name, DiscoInfoProvider provider) {
        entities.put(name, provider);
    }

    /**
     * Removes the DiscoInfoProvider related to a given entity.
     *
     * @param name the name of the entity.
     */
    protected void removeProvider(String name) {
        entities.remove(name);
    }

    /**
     * Adds the features provided by the new service that implements the ServerFeaturesProvider
     * interface. This information will be used whenever a disco for information is made against
     * the server (i.e. the packet's target is the server).
     * Example of features are: jabber:iq:agents, jabber:iq:time, etc.
     *
     * @param provider the ServerFeaturesProvider that provides new server features.
     */
    public void addServerFeaturesProvider(ServerFeaturesProvider provider) {
        for (Iterator<String> it = provider.getFeatures(); it.hasNext();) {
            addServerFeature(it.next());
        }
    }

    /**
     * Adds the "discoverable" identities provided by the provider whenever a disco for info is made against the server.
     *
     * @param provider The provider of identities.
     */
    public void addServerIdentitiesProvider(ServerIdentitiesProvider provider) {
        if ( provider == null )
        {
            throw new NullPointerException( "Argument 'provider' cannot be null." );
        }
        serverIdentityProviders.add( provider );
    }

    /**
     * Removes this provider of identities.
     *
     * @param provider The provider of identities.
     */
    public void removeServerIdentitiesProvider(ServerIdentitiesProvider provider) {
        if ( provider == null )
        {
            throw new NullPointerException( "Argument 'provider' cannot be null." );
        }
        serverIdentityProviders.remove( provider );
    }

    /**
     * Adds the "discoverable" user identities provided by the provider whenever a disco for info is made against users
     * of the server.
     *
     * @param provider The provider of user identities.
     */
    public void addUserIdentitiesProvider(UserIdentitiesProvider provider) {
        if ( provider == null )
        {
            throw new NullPointerException( "Argument 'provider' cannot be null." );
        }
        registeredUserIdentityProviders.add( provider );
    }

    /**
     * Removes this provider of user identities.
     *
     * @param provider The provider of identities.
     */
    public void removeUserIdentitiesProvider(UserIdentitiesProvider provider) {
        if ( provider == null )
        {
            throw new NullPointerException( "Argument 'provider' cannot be null." );
        }
        registeredUserIdentityProviders.remove( provider );
    }

    /**
     * Adds the "discoverable" user features provided by the provider whenever a disco for info is made against users
     * of the server.
     *
     * @param provider The provider of user features.
     */
    public void addUserFeaturesProvider(UserFeaturesProvider provider) {
        if ( provider == null )
        {
            throw new NullPointerException( "Argument 'provider' cannot be null." );
        }
        registeredUserFeatureProviders.add( provider );
    }

    /**
     * Removes this provider of user features.
     *
     * @param provider The provider of features.
     */
    public void removeUserFeaturesProvider(UserFeaturesProvider provider) {
        if ( provider == null )
        {
            throw new NullPointerException( "Argument 'provider' cannot be null." );
        }
        registeredUserFeatureProviders.remove( provider );
    }

    /**
     * Adds one specific feature to the information returned whenever a disco for information is
     * made against the server.
     *
     * @param namespace the namespace identifying the new server feature.
     */
    public void addServerFeature(String namespace) {
        if (localServerFeatures.add(namespace)) {
            Lock lock = serverFeatures.getLock(namespace);
            lock.lock();
            try {
                HashSet<NodeID> nodeIDs = serverFeatures.get(namespace);
                if (nodeIDs == null) {
                    nodeIDs = new HashSet<>();
                }
                nodeIDs.add(XMPPServer.getInstance().getNodeID());
                serverFeatures.put(namespace, nodeIDs);
            }
            finally {
                lock.unlock();
            }
        }
    }

    /**
     * Removes a feature from the information returned whenever a disco for information is
     * made against the server.
     *
     * @param namespace the namespace of the feature to be removed.
     */
    public void removeServerFeature(String namespace) {
        if (localServerFeatures.remove(namespace)) {
            Lock lock = serverFeatures.getLock(namespace);
            lock.lock();
            try {
                HashSet<NodeID> nodeIDs = serverFeatures.get(namespace);
                if (nodeIDs != null) {
                    nodeIDs.remove(XMPPServer.getInstance().getNodeID());
                    if (nodeIDs.isEmpty()) {
                        serverFeatures.remove(namespace);
                    }
                    else {
                        serverFeatures.put(namespace, nodeIDs);
                    }
                }
            }
            finally {
                lock.unlock();
            }
        }
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);
        serverFeatures = CacheFactory.createCache("Disco Server Features");
        addServerFeature(NAMESPACE_DISCO_INFO);
        setProvider(server.getServerInfo().getXMPPDomain(), getServerInfoProvider());
        // Listen to cluster events
        ClusterManager.addListener(this);
    }

    @Override
    public void joinedCluster() {
        // The local node joined a cluster.
        //
        // Upon joining a cluster, clustered caches are reset to their clustered equivalent (by the swap from the local
        // cache implementation to the clustered cache implementation that's done in the implementation of
        // org.jivesoftware.util.cache.CacheFactory.joinedCluster). This means that they now hold data that's
        // available on all other cluster nodes. Data that's available on the local node needs to be added again.
        restoreCacheContent();

        // It does not appear to be needed to invoke any kind of event listeners for the data that was gained by joining
        // the cluster (eg: new server features provided by other cluster nodes now available to the local cluster node):
        // the only cache that's being used in this implementation does not have an associated event listening mechanism
        // when data is added to or removed from it.
    }

    @Override
    public void joinedCluster(byte[] nodeID) {
        // Another node joined a cluster that we're already part of. It is expected that
        // the implementation of #joinedCluster() as executed on the cluster node that just
        // joined will synchronize all relevant data. This method need not do anything.
    }

    @Override
    public void leftCluster() {
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

        // It does not appear to be needed to invoke any kind of event listeners for the data that was lost by leaving
        // the cluster (eg: server features provided only by other cluster nodes, now unavailable to the local cluster
        // node): the only cache that's being used in this implementation does not have an associated event listening
        // mechanism when data is added to or removed from it.
    }

    @Override
    public void leftCluster(byte[] nodeID) {
        // Another node left the cluster.
        //
        // If the cluster node leaves in an orderly fashion, it might have broadcasted
        // the necessary events itself. This cannot be depended on, as the cluster node
        // might have disconnected unexpectedly (as a result of a crash or network issue).
        //
        // Determine what data was available only on that node, and remove that.
        //
        // All remaining cluster nodes will be in a race to clean up the
        // same data. The implementation below accounts for that, by only having the
        // senior cluster node to perform the cleanup.
        if (ClusterManager.isSeniorClusterMember()) {
            // Remove server features added by node that is gone
            final NodeID leftNode = NodeID.getInstance(nodeID);
            CacheUtil.removeValueFromMultiValuedCache(serverFeatures, leftNode);
        }
    }

    @Override
    public void markedAsSeniorClusterMember() {
        // Do nothing
    }

    /**
     * When the local node is joining or leaving a cluster, {@link org.jivesoftware.util.cache.CacheFactory} will swap
     * the implementation used to instantiate caches. This causes the cache content to be 'reset': it will no longer
     * contain the data that's provided by the local node. This method restores data that's provided by the local node
     * in the cache. It is expected to be invoked right after joining ({@link #joinedCluster()} or leaving
     * ({@link #leftCluster()} a cluster.
     */
    private void restoreCacheContent() {
        Log.trace( "Restoring cache content for cache '{}' by adding all server features that are provided by the local cluster node.", serverFeatures.getName() );
        for (String feature : localServerFeatures) {
            CacheUtil.addValueToMultiValuedCache( serverFeatures, feature, XMPPServer.getInstance().getNodeID(), HashSet::new );
        }
    }

    /**
     * return the first DataForm for a Collections
     * of Set DataForms
     * @param dataForms collection from which to return the first element (cannot be null, can be an empty collection).
     * @return first dataform from the collection. Null if the collection was empty.
     */
    public static DataForm getFirstDataForm(Set<DataForm> dataForms){
        if(dataForms == null || dataForms.isEmpty()){
            return null;
        }
        if (dataForms.size() > 1) {
            Log.warn("Set Data List contains "+dataForms.size()+" DataForms."+
            "Only the first one of the DataForms will be returned.");
        }
        return  dataForms.stream().filter(Objects::nonNull).findAny().orElse(null);
    }

    /**
     * Set all Software Version data  
     * responsed by the peer for the Software information request Service Discovery (XEP-0232)
     * @param query represented on the response of the peer
     * @param session represented the LocalSession with peer
     */
    public static void setSoftwareVersionDataFormFromDiscoInfo(Element query ,LocalSession session){
        boolean containDisco = false;
        boolean typeformDataSoftwareInfo = false;
        if (query != null && session != null){
            for (Element element : query.elements()){
                if ("feature".equals(element.getName()) 
                    && NAMESPACE_DISCO_INFO.equals(element.attributeValue("var")) ){
                    containDisco = true;
                }
                if (containDisco && "x".equals(element.getName()) 
                    && "jabber:x:data".equals(element.getNamespaceURI())
                    && "result".equals(element.attributeValue("type"))){
                    for (Element field : element.elements()){
                        if (field == null) {
                            continue;
                        }
                        if (field.attributeValue("var").equals("FORM_TYPE")
                            && field.element("value")!= null
                            && field.element("value").getText().equals("urn:xmpp:dataforms:softwareinfo")) { 
                            typeformDataSoftwareInfo = true;     
                        }
                        if (typeformDataSoftwareInfo) {
                            if (field.element("value") != null && !"urn:xmpp:dataforms:softwareinfo".equals(field.element("value").getText())) {
                                session.setSoftwareVersionData(field.attributeValue("var"), field.element("value").getText());
                            }
                            else if (field.element("media") != null && field.element("media").element("uri") != null) {
                                session.setSoftwareVersionData("image", field.element("media").element("uri").getText());
                            }
                        }
                    }    
                }
            }
        }
    }

    /**
     * Returns the DiscoInfoProvider responsible for providing information at the server level. This
     * means that this DiscoInfoProvider will provide information whenever a disco request whose
     * recipient JID is the server (e.g. localhost) is made.
     *
     * @return the DiscoInfoProvider responsible for providing information at the server level.
     */
    private DiscoInfoProvider getServerInfoProvider() {
        return new DiscoInfoProvider() {

            @Override
            public Iterator<Element> getIdentities(String name, String node, JID senderJID) {
                if (node != null && serverNodeProviders.get(node) != null) {
                    // Redirect the request to the disco info provider of the specified node
                    return serverNodeProviders.get(node).getIdentities(name, node, senderJID);
                }
                if (name == null || name.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                    // Answer identity of the server itself.
                    final ArrayList<Element> identities = new ArrayList<>();
                    final Element identity = DocumentHelper.createElement("identity");
                    identity.addAttribute("category", "server");
                    identity.addAttribute("name", JiveGlobals.getProperty("xmpp.server.name", "Openfire Server"));
                    identity.addAttribute("type", "im");
                    identities.add(identity);

                    // Include identities from modules that implement ServerIdentitiesProvider
                    for (ServerIdentitiesProvider provider : serverIdentityProviders )
                    {
                        final Iterator<Element> iterator = provider.getIdentities();
                        while ( iterator.hasNext() )
                        {
                            identities.add( iterator.next() );
                        }
                    }
                    return identities.iterator();
                }
                else if (node != null) {
                    return XMPPServer.getInstance().getIQPEPHandler().getIdentities(name, node, senderJID);
                }
                else {
                    // Answer with identities of users of the server.
                    final Collection<UserIdentitiesProvider> providers;
                    if (SessionManager.getInstance().isAnonymousRoute(name))
                    {
                        // Answer identity of an anonymous user.
                        providers = anonymousUserIdentityProviders;
                    }
                    else
                    {
                        // Answer identity of a registered user.
                        // Note: We know that this user exists because #hasInfo returned true
                        providers = registeredUserIdentityProviders;
                    }

                    final Set<Element> result = new HashSet<>();
                    for ( final UserIdentitiesProvider provider : providers )
                    {
                        final Iterator<Element> identities = provider.getIdentities();
                        while ( identities.hasNext() )
                        {
                            result.add( identities.next() );
                        }
                    }
                    return result.iterator();
                }
            }

            @Override
            public Iterator<String> getFeatures(String name, String node, JID senderJID) {
                if (node != null && serverNodeProviders.get(node) != null) {
                    // Redirect the request to the disco info provider of the specified node
                    return serverNodeProviders.get(node).getFeatures(name, node, senderJID);
                }
                if (name == null || name.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                    // Answer features of the server itself.
                    final Set<String> result = new HashSet<>(serverFeatures.keySet());

                    if ( !UserManager.getInstance().isRegisteredUser( senderJID, false ) ) {
                        // Remove features not available to users from other servers or anonymous users.
                        // TODO this should be determined dynamically.
                        result.remove(IQPrivateHandler.NAMESPACE);
                        result.remove(IQBlockingHandler.NAMESPACE);
                    }
                    return result.iterator();
                }
                else if (node != null) {
                    return XMPPServer.getInstance().getIQPEPHandler().getFeatures(name, node, senderJID);
                }
                else {
                    // Answer with features of users of the server.
                    final Collection<UserFeaturesProvider> providers;
                    if (SessionManager.getInstance().isAnonymousRoute(name))
                    {
                        // Answer features of an anonymous user.
                        providers = anonymousUserFeatureProviders;
                    }
                    else
                    {
                        // Answer features of a registered user.
                        // Note: We know that this user exists because #hasInfo returned true
                        providers = registeredUserFeatureProviders;
                    }
                    final Set<String> result = new HashSet<>();
                    for ( final UserFeaturesProvider provider : providers )
                    {
                        final Iterator<String> features = provider.getFeatures();
                        while ( features.hasNext() )
                        {
                            result.add( features.next() );
                        }
                    }
                    return result.iterator();
                }
            }

            @Override
            public boolean hasInfo(String name, String node, JID senderJID) {
                if (node != null) {
                    if (serverNodeProviders.get(node) != null) {
                        // Redirect the request to the disco info provider of the specified node
                        return serverNodeProviders.get(node).hasInfo(name, node, senderJID);
                    }
                    if (name != null) {
                        return XMPPServer.getInstance().getIQPEPHandler().hasInfo(name, node, senderJID);
                    }
                    // Unknown node
                    return false;
                }

                // True if it is an info request of the server, a registered user or an
                // anonymous user. We now support disco of user's bare JIDs
                if (name == null) {
                    return true;
                }
                if (SessionManager.getInstance().isAnonymousRoute(name)) {
                    return true;
                }
                try {
                    if ( UserManager.getInstance().getUser(name) != null ) {
                        return true;
                    }
                } catch (UserNotFoundException e) {
                    return false;
                }
                return false;
            }

            @Override
            public DataForm getExtendedInfo(String name, String node, JID senderJID) {
                return IQDiscoInfoHandler.getFirstDataForm(this.getExtendedInfos(name, node, senderJID));
            }

            @Override
            public Set<DataForm> getExtendedInfos(String name, String node, JID senderJID) {
                if (node != null && serverNodeProviders.get(node) != null) {
                    // Redirect the request to the disco info provider of the specified node
                    return serverNodeProviders.get(node).getExtendedInfos(name, node, senderJID);
                }
                if (name == null || name.equals(XMPPServer.getInstance().getServerInfo().getXMPPDomain())) {
                    // Answer extended info of the server itself.

                    // XEP-0157 Contact addresses for XMPP Services
                    if ( !JiveGlobals.getBooleanProperty( "admin.disable-exposure" ) )
                    {
                        final Collection<JID> admins = XMPPServer.getInstance().getAdmins();
                        if ( admins == null || admins.isEmpty() )
                        {
                            return null;
                        }

                        final DataForm dataForm = new DataForm(DataForm.Type.result);

                        final FormField fieldType = dataForm.addField();
                        fieldType.setVariable("FORM_TYPE");
                        fieldType.setType(FormField.Type.hidden);
                        fieldType.addValue("http://jabber.org/network/serverinfo");

                        final FormField fieldAdminAddresses = dataForm.addField();
                        fieldAdminAddresses.setVariable("admin-addresses");
                        fieldAdminAddresses.setType(Type.list_multi);

                        final UserManager userManager = UserManager.getInstance();
                        for ( final JID admin : admins )
                        {
                            fieldAdminAddresses.addValue( "xmpp:" + admin.asBareJID() );
                            if ( admin.getDomain().equals( XMPPServer.getInstance().getServerInfo().getXMPPDomain() ) )
                            try
                            {
                                final String email = userManager.getUser( admin.getNode() ).getEmail();
                                if ( email != null && !email.trim().isEmpty() )
                                {
                                    fieldAdminAddresses.addValue( "mailto:" + email );
                                }
                            }
                            catch (Exception e)
                            {
                                continue;
                            }
                        }

                        //XEP-0232 includes extended information about Software Version in a data form
                        final DataForm dataFormSoftwareVersion = new DataForm(DataForm.Type.result);

                        final FormField fieldTypeSoftwareVersion = dataFormSoftwareVersion.addField();
                        fieldTypeSoftwareVersion.setVariable("FORM_TYPE");
                        fieldTypeSoftwareVersion.setType(FormField.Type.hidden);
                        fieldTypeSoftwareVersion.addValue("urn:xmpp:dataforms:softwareinfo");

                        final FormField fieldOs = dataFormSoftwareVersion.addField();
                        fieldOs.setVariable("os");
                        fieldOs.addValue( System.getProperty("os.name"));

                        final FormField fieldOsVersion = dataFormSoftwareVersion.addField();
                        fieldOsVersion.setVariable("os_version");
                        fieldOsVersion .addValue(System.getProperty("os.version")+" "+System.getProperty("os.arch")+" - Java " + System.getProperty("java.version"));

                        final FormField fieldSoftware = dataFormSoftwareVersion.addField();
                        fieldSoftware.setVariable("software");
                        fieldSoftware.addValue(AdminConsole.getAppName());

                        final FormField fieldSoftwareVersion = dataFormSoftwareVersion.addField();
                        fieldSoftwareVersion.setVariable("software_version");
                        fieldSoftwareVersion.addValue(AdminConsole.getVersionString());

                        final Set<DataForm> dataForms = new HashSet<>();
                        if (ENABLED.getValue()){
                            dataForms.add(dataFormSoftwareVersion);
                        }
                        dataForms.add(dataForm);
                        return dataForms;
                    }
                }
                if (node != null && name != null) {
                    return XMPPServer.getInstance().getIQPEPHandler().getExtendedInfos(name, node, senderJID);
                }
                return Collections.emptySet();
            }
        };
    }
}
