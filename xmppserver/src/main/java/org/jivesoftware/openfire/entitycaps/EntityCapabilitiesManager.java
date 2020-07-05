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

package org.jivesoftware.openfire.entitycaps;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.UserEventDispatcher;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * Implements server side mechanics for XEP-0115: "Entity Capabilities"
 * Version 1.4
 * 
 * In particular, EntityCapabilitiesManager is useful for processing
 * "filtered-notifications" for use with Pubsub (XEP-0060) for contacts that
 * may not want to receive notifications for all payload types.
 * 
 * The server's role in managing Entity Capabilities is to cache previously
 * encountered entity capabilities for XMPP clients supporting the same
 * identities and features. If the server has not seen a particular
 * combination of identities and features, a Discover Information query is
 * sent to that client and its reply is cached for future use by clients
 * sharing those same entity capabilities.
 * 
 * @author Armando Jagucki
 * @see <a href="https://xmpp.org/extensions/xep-0115.html>XEP-0115: Entity Capabilities</a>
 */
public class EntityCapabilitiesManager extends BasicModule implements IQResultListener, UserEventListener {

    private static final Logger Log = LoggerFactory.getLogger( EntityCapabilitiesManager.class );

    /**
     * A XEP-0115 described identifier for the Openfire server software,
     * intended to be used as a value of 'node' attributes.
     */
    public static final String OPENFIRE_IDENTIFIER_NODE = "https://www.igniterealtime.org/projects/openfire/";

    /**
     * Entity Capabilities cache map. This cache stores entity capabilities
     * that may be shared among users.
     * 
     * When we want to look up the entity capabilities for a user, we first
     * find their most recently advertised 'ver' hash using the
     * {@link #entityCapabilitiesUserMap}. Then we use that 'ver' hash as a
     * key into this map.
     * 
     * Key:   The 'ver' hash string that encapsulates identities+features.
     * Value: EntityCapabilities object representing the encapsulated values.
     */
    private Cache<String, EntityCapabilities> entityCapabilitiesMap;

    /**
     * Entity Capabilities user cache map. This map is used to determine which
     * entity capabilities are in use for a particular user.
     * 
     * When we want to look up the entity capabilities for a user, we first
     * find their most recently advertised 'ver' hash using this map. Then we
     * use this 'ver' hash as a key into the {@link #entityCapabilitiesMap}.
     * 
     * Key:   The JID of the user.
     * Value: The 'ver' hash string that encapsulates identities+features.
     */
    private Cache<JID, String> entityCapabilitiesUserMap;

    /**
     * Entity Capabilities that were registered for a particular user, but
     * are in progress of being updated (a new 'ver' value has been received).
     *
     * The old value is kept to be able to provide a 'diff' between the old
     * and new capabilities, after the new 'ver' value has been looked up.
     */
    private Map<JID, String> capabilitiesBeingUpdated;

    /**
     * Ver attributes are the hash strings that correspond to a certain
     * combination of entity capabilities. This hash string, representing a
     * particular identities+features combination, is found in the 'ver'
     * attribute of the caps element in a presence packet (caps packet).
     * 
     * Each unrecognized caps packet that is encountered has its verAttribute
     * added to this map. Since results to our disco#info queries can be
     * received in any order, the map is used by {@link #isValid(IQ)} so the
     * method can be sure it is comparing its generated 'ver' hash to the
     * correct 'ver' hash in the map, that was previously encountered in the
     * caps packet.
     * 
     * Key:   Packet ID of our disco#info request.
     * Value: The 'ver' hash string from the original caps packet.
     */
    private Map<String, EntityCapabilities> verAttributes;

    /**
     * Listeners that are invoked when new or changed capabilities for an entity are detected.
     */
    private final Set<EntityCapabilitiesListener> allUserCapabilitiesListeners = new CopyOnWriteArraySet<>();

    /**
     * Listeners that are invoked when new or changed capabilities for a specific entity are detected.
     */
    private final SetMultimap<JID, EntityCapabilitiesListener> userSpecificCapabilitiesListener = HashMultimap.create();

    public EntityCapabilitiesManager() {
        super( "Entity Capabilities Manager" );
    }

    @Override
    public void initialize( final XMPPServer server )
    {
        super.initialize( server );
        entityCapabilitiesMap = CacheFactory.createLocalCache("Entity Capabilities");
        entityCapabilitiesUserMap = CacheFactory.createLocalCache("Entity Capabilities Users");
        capabilitiesBeingUpdated = new HashMap<>();
        verAttributes = new HashMap<>();
        UserEventDispatcher.addListener( this );
    }

    @Override
    public void destroy()
    {
        UserEventDispatcher.removeListener( this );
        allUserCapabilitiesListeners.clear();
        userSpecificCapabilitiesListener.clear();
    }

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     * @deprecated Replaced by {@link XMPPServer#getEntityCapabilitiesManager}
     */
    @Deprecated
    public static EntityCapabilitiesManager getInstance() {
        return XMPPServer.getInstance().getEntityCapabilitiesManager();
    }

    public void process(Presence packet) {
        if (Presence.Type.unavailable == packet.getType()) {
            if (packet.getFrom() != null ) {
                this.capabilitiesBeingUpdated.remove( packet.getFrom() );
                final String oldVer = this.entityCapabilitiesUserMap.remove( packet.getFrom() );
                if ( oldVer != null ) {
                    checkObsolete( oldVer );
                }
            }
            return;
        }

        // Examine the packet and check if it has caps info,
        // if not -- do nothing by returning.
        Element capsElement = packet.getChildElement("c", "http://jabber.org/protocol/caps");
        if (capsElement == null) {
            return;
        }

        // Examine the packet and check if it's in legacy format (pre version 1.4
        // of XEP-0115). If so, do nothing by returning.
        // TODO: if this packet is in legacy format, we SHOULD check the 'node',
        // 'ver', and 'ext' combinations as specified in the archived version
        // 1.3 of the specification, and cache the results. See JM-1447
        final String hashAttribute = capsElement.attributeValue("hash");
        if (hashAttribute == null || hashAttribute.trim().length() == 0) {
            return;
        }
        
        // Examine the packet and check if it has and a 'ver' hash
        // if not -- do nothing by returning.
        final String newVerAttribute = capsElement.attributeValue("ver");
        if (newVerAttribute == null || newVerAttribute.trim().length() == 0) {
            return;
        }

        // Check to see if the 'ver' hash is already in our cache.
        EntityCapabilities caps;
        if ((caps = entityCapabilitiesMap.get(newVerAttribute)) != null) {
            // The 'ver' hash is in the cache already, so let's update the
            // entityCapabilitiesUserMap for the user that sent the caps
            // packet.
            Log.trace( "Registering 'ver' (for recognized caps) for {}", packet.getFrom() );
            registerCapabilities( packet.getFrom(), caps );
        }
        else {
            // If this entity previously had another registration, that now no longer is valid.
            final String ver = entityCapabilitiesUserMap.remove(packet.getFrom());
            if ( ver != null ) {
                capabilitiesBeingUpdated.put( packet.getFrom(), ver );
            }

            // The 'ver' hash is not in the cache so send out a disco#info query
            // so that we may begin recognizing this 'ver' hash.
            IQ iq = new IQ(IQ.Type.get);
            iq.setTo(packet.getFrom());

            String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
            iq.setFrom(serverName);

            iq.setChildElement("query", "http://jabber.org/protocol/disco#info");

            String packetId = iq.getID();
            
            caps = new EntityCapabilities();
            caps.setHashAttribute(hashAttribute);
            caps.setVerAttribute(newVerAttribute);
            Log.trace( "Querying 'ver' for unrecognized caps. Querying: {}", packet.getFrom() );
            verAttributes.put(packetId, caps);

            final IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
            iqRouter.addIQResultListener(packetId, this);
            iqRouter.route(iq);
        }
    }

    /**
     * Determines whether or not a particular 'ver' attribute is stored in the
     * {@link #entityCapabilitiesMap} cache.
     * 
     * @param verAttribute the 'ver' hash to check for.
     * @return true if the caps cache contains the 'ver' hash already, false if not.
     */
    private boolean isInCapsCache(String verAttribute) {
        return entityCapabilitiesMap.containsKey(verAttribute);
    }

    /**
     * Determines whether or not the packet received from a disco#info result
     * was valid by comparing its 'ver' hash (identites+features encapsulated
     * hash) with the 'ver' hash of the original caps packet that the
     * disco#info query was sent on behalf of.
     * 
     * @param packet the disco#info result packet.
     * @return true if the packet's generated 'ver' hash matches the 'ver'
     *         hash of the original caps packet.
     */
    private boolean isValid(IQ packet) {
        if (packet.getType() != IQ.Type.result)
            return false;

        final EntityCapabilities original = verAttributes.get(packet.getID());
        if (original == null) {
            return false;
        }
        final String newVerHash = generateVerHash(packet, original.getHashAttribute());

        return newVerHash.equals(original.getVerAttribute());
    }

    /**
     * Generates a 'ver' hash attribute used in validation to help prevent
     * poisoning of entity capabilities information.
     * 
     * @see #isValid(IQ)
     * 
     * The value of the 'ver' attribute is generated according to the method
     * outlined in XEP-0115.
     * 
     * @param packet IQ reply to the entity cap request.
     * @param algorithm The hashing algorithm to use (e.g. SHA-1)
     * @return the generated 'ver' hash
     */
    public static String generateVerHash(IQ packet, String algorithm) {
        // Initialize an empty string S.
        final StringBuilder s = new StringBuilder();

        // Sort the service discovery identities by category and then by type
        // (if it exists), formatted as 'category' '/' 'type' / 'lang' / 'name'
        final List<String> discoIdentities = getIdentitiesFrom(packet);
        Collections.sort(discoIdentities);

        // For each identity, append the 'category/type/lang/name' to S, 
        // followed by the '<' character.
        for (String discoIdentity : discoIdentities) {
            s.append(discoIdentity);
            s.append('<');
        }

        // Sort the supported service discovery features.
        final List<String> discoFeatures = getFeaturesFrom(packet);
        Collections.sort(discoFeatures);

        // For each feature, append the feature to S, followed by the '<'
        // character.
        for (String discoFeature : discoFeatures) {
            s.append(discoFeature);
            s.append('<');
        }
        
        // If the service discovery information response includes XEP-0128 
        // data forms, sort the forms by the FORM_TYPE (i.e., by the XML
        // character data of the <value/> element).
        final List<String> extendedDataForms = getExtendedDataForms(packet);
        Collections.sort(extendedDataForms);
        
        for (String extendedDataForm : extendedDataForms) {
            s.append(extendedDataForm);
            // no need to add '<', this is done in #getExtendedDataForms()
        }
        
        // Compute ver by hashing S using the SHA-1 algorithm as specified in
        // RFC 3174 (with binary output) and encoding the hash using Base64 as
        // specified in Section 4 of RFC 4648 (note: the Base64 output
        // MUST NOT include whitespace and MUST set padding bits to zero).
        final String hashed = StringUtils.hash(s.toString(), "SHA-1");
        return StringUtils.encodeBase64(StringUtils.decodeHex(hashed));
    }

    @Override
    public void answerTimeout(String packetId) {
        // If we never received an answer, we can discard the cached
        // 'ver' attribute.
        verAttributes.remove(packetId);
    }

    @Override
    public void receivedAnswer(IQ packet) {
        String packetId = packet.getID();

        if (isValid(packet)) {
            // The packet was validated, so it can be added to the Entity
            // Capabilities cache map.

            // Add the resolved identities and features to the entity 
            // EntityCapabilitiesManager.capabilities object and add it 
            // to the cache map...
            EntityCapabilities caps = verAttributes.get(packetId);

            // Store identities.
            List<String> identities = getIdentitiesFrom(packet);
            for (String identity : identities) {
                caps.addIdentity(identity);
            }

            // Store features.
            List<String> features = getFeaturesFrom(packet);
            for (String feature : features) {
                caps.addFeature(feature);
            }

            Log.trace( "Received response to querying 'ver'. Caps now recognized. Received response from: {}", packet.getFrom() );
            registerCapabilities( packet.getFrom(), caps );
        }

        // Remove cached 'ver' attribute.
        verAttributes.remove(packetId);
    }

    /**
     * Returns the entity capabilities for a specific JID. The specified JID
     * should be a full JID that identified the entity's connection.
     * 
     * @param jid the full JID of entity
     * @return the entity capabilities of jid, or null if these are unavailable.
     */
    public EntityCapabilities getEntityCapabilities(JID jid) {
        String verAttribute = entityCapabilitiesUserMap.get(jid);
        if ( verAttribute == null ) {
            return null;
        }
        return entityCapabilitiesMap.get(verAttribute);
    }

    /**
     * Extracts a list of identities from an IQ packet.
     * 
     * @param packet the packet
     * @return a list of identities
     */
    private static List<String> getIdentitiesFrom(IQ packet) {
        List<String> discoIdentities = new ArrayList<>();
        Element query = packet.getChildElement();
        if (query == null) {
            return discoIdentities;
        }
        Iterator<Element> identitiesIterator = query.elementIterator("identity");
        if (identitiesIterator != null) {
            while (identitiesIterator.hasNext()) {
                Element identityElement = identitiesIterator.next();

                StringBuilder discoIdentity = new StringBuilder();

                String cat = identityElement.attributeValue("category");
                String type = identityElement.attributeValue("type");
                String lang = identityElement.attributeValue("xml:lang");
                String name = identityElement.attributeValue("name");

                if (cat != null) {
                    discoIdentity.append(cat);
                }
                discoIdentity.append('/');

                if (type != null) {
                    discoIdentity.append(type);
                }
                discoIdentity.append('/');

                if (lang != null) {
                    discoIdentity.append(lang);
                }
                discoIdentity.append('/');

                if (name != null) {
                    discoIdentity.append(name);
                }

                discoIdentities.add(discoIdentity.toString());
            }
        }
        return discoIdentities;
    }

    /**
     * Extracts a list of features from an IQ packet.
     * 
     * @param packet the packet
     * @return a list of features
     */
    private static List<String> getFeaturesFrom(IQ packet) {
        List<String> discoFeatures = new ArrayList<>();
        Element query = packet.getChildElement();
        Iterator<Element> featuresIterator = query.elementIterator("feature");
        if (featuresIterator != null) {
            while (featuresIterator.hasNext()) {
                Element featureElement = featuresIterator.next();
                String discoFeature = featureElement.attributeValue("var");

                discoFeatures.add(discoFeature);
            }
        }
        return discoFeatures;
    }

    /**
     * Extracts a list of extended service discovery information from an IQ
     * packet.
     * 
     * @param packet
     *            the packet
     * @return a list of extended service discoverin information features.
     */
    private static List<String> getExtendedDataForms(IQ packet) {
        List<String> results = new ArrayList<>();
        Element query = packet.getChildElement();
        Iterator<Element> extensionIterator = query.elementIterator(QName.get(
                "x", "jabber:x:data"));
        if (extensionIterator != null) {
            while (extensionIterator.hasNext()) {
                Element extensionElement = extensionIterator.next();
                final StringBuilder formType = new StringBuilder();

                Iterator<Element> fieldIterator = extensionElement
                        .elementIterator("field");
                List<String> vars = new ArrayList<>();
                while (fieldIterator != null && fieldIterator.hasNext()) {
                    final Element fieldElement = fieldIterator.next();
                    if (fieldElement.attributeValue("var").equals("FORM_TYPE")) {
                        formType
                                .append(fieldElement.element("value").getText());
                        formType.append('<');
                    } else {
                        final StringBuilder var = new StringBuilder();
                        var.append(fieldElement.attributeValue("var"));
                        var.append('<');
                        Iterator<Element> valIter = fieldElement
                                .elementIterator("value");
                        List<String> values = new ArrayList<>();
                        while (valIter != null && valIter.hasNext()) {
                            Element value = valIter.next();
                            values.add(value.getText());
                        }
                        Collections.sort(values);
                        for (String v : values) {
                            var.append(v);
                            var.append('<');
                        }
                        vars.add(var.toString());
                    }
                }
                Collections.sort(vars);
                for (String v : vars) {
                    formType.append(v);
                }

                results.add(formType.toString());
            }
        }
        return results;
    }

    /**
     * Registers that a particular entity has a particular set of capabilities, invoking event listeners when
     * appropriate.
     *
     * @param entity The entity for which a set of capabilities is detected.
     * @param newCapabilities The capabilities that are detected.
     */
    protected void registerCapabilities( @Nonnull JID entity, @Nonnull EntityCapabilities newCapabilities )
    {
        entityCapabilitiesMap.put( newCapabilities.getVerAttribute(), newCapabilities );
        String oldVerAttribute = entityCapabilitiesUserMap.put(entity, newCapabilities.getVerAttribute());
        String updatedVerValue = capabilitiesBeingUpdated.remove( entity );
        if (oldVerAttribute == null) {
            oldVerAttribute = updatedVerValue;
        }

        // Invoke listeners when capabilities changed.
        if (oldVerAttribute == null )
        {
            dispatch( entity, newCapabilities, null );
        }
        else if ( !oldVerAttribute.equals( newCapabilities.getVerAttribute() ) )
        {
            final EntityCapabilities oldCapabilities = entityCapabilitiesMap.get( oldVerAttribute );
            dispatch( entity, newCapabilities, oldCapabilities );
        }

        // If this replaced another 'ver' hash, purge the capabilities if needed.
        if ( oldVerAttribute != null && !oldVerAttribute.equals( newCapabilities.getVerAttribute() ) )
        {
            checkObsolete( oldVerAttribute );
        }
    }

    /**
     * Registers an event listener that will be invoked when the detected entity capabilities for a particular entity
     * have changed.
     *
     * This method supports multiple event listeners per JID. Registration of the same combination of entity and listener
     * will be ignored.
     *
     * @param entity The entity for which to listen for events.
     * @param listener The event listener to be invoked when entity capabilities have changed.
     */
    public void addListener( @Nonnull JID entity, @Nonnull EntityCapabilitiesListener listener ) {
        userSpecificCapabilitiesListener.put( entity, listener );
    }

    /**
     * Removes a previously registered event listener for a particular entity, if such a combination is currently
     * registered.
     *
     * @param entity The entity for which the listener was registered.
     * @param listener The event listener to be removed.
     */
    public void removeListener( @Nonnull JID entity, @Nonnull EntityCapabilitiesListener listener ) {
        userSpecificCapabilitiesListener.remove( entity, listener );
    }

    /**
     * Removes all previously registered event listener for a particular entity, if any were registered.
     *
     * @param entity The entity for which listeners are to removed.
     */
    public void removeListeners( @Nonnull JID entity ) {
        userSpecificCapabilitiesListener.removeAll( entity );
    }

    /**
     * Registers an event listener that will be invoked when the detected entity capabilities for any entity
     * have changed.
     *
     * If the listener already is registered, the invocation of this method will be a no-op.
     *
     * @param listener The event listener to be invoked when entity capabilities have changed.
     */
    public void addListener( @Nonnull EntityCapabilitiesListener listener ) {
        allUserCapabilitiesListeners.add( listener );
    }

    /**
     * Removes a previously registered event listener, if such a listener is currently registered.
     *
     * @param listener The event listener to be removed.
     */
    public void removeListener( @Nonnull EntityCapabilitiesListener listener ) {
        allUserCapabilitiesListeners.remove( listener );
    }

    /**
     * Invokes the entityCapabilitiesChanged method of all currently registered event listeners.
     *
     * It is assumed that this method is used to notify listeners of a change in capabilities for a particular entity.
     *
     * @param entity The entity for which an event is to be dispatched
     * @param updatedEntityCapabilities The most up-to-date capabilities.
     * @param previousEntityCapabilities The capabilities, if any, prior to the update.
     */
    protected void dispatch( @Nonnull JID entity, @Nonnull EntityCapabilities updatedEntityCapabilities, @Nullable EntityCapabilities previousEntityCapabilities ) {
        Log.trace( "Dispatching entity capabilities changed listeners for '{}'", entity );

        // Calculate diffs
        final Set<String> featuresInUpdate = updatedEntityCapabilities.getFeatures();
        final Set<String> featuresExisting = previousEntityCapabilities == null ? Collections.emptySet() : previousEntityCapabilities.getFeatures();
        final Set<String> identitiesInUpdate = updatedEntityCapabilities.getIdentities();
        final Set<String> identitiesExisting = previousEntityCapabilities == null ? Collections.emptySet() :previousEntityCapabilities.getIdentities();

        final Set<String> featuresAdded = new HashSet<>(featuresInUpdate);
        featuresAdded.removeAll( featuresExisting );

        final Set<String> featuresRemoved = new HashSet<>(featuresExisting);
        featuresRemoved.removeAll( featuresInUpdate );

        final Set<String> identitiesAdded = new HashSet<>(identitiesInUpdate);
        identitiesAdded.removeAll( identitiesExisting );

        final Set<String> identitiesRemoved = new HashSet<>(identitiesExisting);
        identitiesRemoved.removeAll( identitiesInUpdate );

        // Invoke event listeners.
        for ( final EntityCapabilitiesListener listener : allUserCapabilitiesListeners ) {
            try {
                listener.entityCapabilitiesChanged( entity, updatedEntityCapabilities, featuresAdded, featuresRemoved, identitiesAdded, identitiesRemoved );
            } catch ( Exception e ) {
                Log.warn( "An exception occurred while dispatching entity capabilities changed event for entity '{}' to listener '{}'.", entity, listener, e );
            }
        }
        final Set<EntityCapabilitiesListener> userSpecificListeners = userSpecificCapabilitiesListener.get(entity);
        if ( userSpecificListeners != null )
        {
            for ( final EntityCapabilitiesListener listener : userSpecificListeners )
            {
                try
                {
                    listener.entityCapabilitiesChanged( entity, updatedEntityCapabilities, featuresAdded, featuresRemoved, identitiesAdded, identitiesRemoved );
                }
                catch ( Exception e )
                {
                    Log.warn("An exception occurred while dispatching entity capabilities changed event for entity '{}' to listener '{}'.", entity, listener, e);
                }
            }
        }
    }

    @Override
    public void userDeleting(User user, Map<String, Object> params) {
        // Delete this user's association in entityCapabilitiesUserMap.
        final JID bareJid = XMPPServer.getInstance().createJID(user.getUsername(), null, true);

        // Remember: Cache's are not regular maps. The EntrySet is immutable.
        // We'll first find the keys, then remove them in a separate call.
        final Set<JID> jidsToRemove = entityCapabilitiesUserMap.keySet().stream()
            .filter( jid -> jid.asBareJID().equals( bareJid ) )
            .collect( Collectors.toSet() );

        final Set<String> deletedUserVerHashes = new HashSet<>();
        for ( final JID jidToRemove : jidsToRemove )
        {
            deletedUserVerHashes.add( entityCapabilitiesUserMap.remove( jidToRemove ) );
        }

        // If there are no other references to the deleted user's 'ver' hash,
        // it is safe to remove that 'ver' hash's associated entity
        // capabilities from the entityCapabilitiesMap cache.
        deletedUserVerHashes.forEach( this::checkObsolete );
    }

    /**
     * Verifies if the provided 'ver' hash is used for any user. If not, the cache entry
     * containing the entity capabilities are removed from the cache.
     *
     * @param verHash an 'ver' hash (cannot be null).
     */
    protected void checkObsolete( String verHash ) {
        if ( entityCapabilitiesUserMap.containsValue( verHash ) )
        {
            return;
        }

        entityCapabilitiesMap.remove( verHash );
    }

    @Override
    public void userCreated(User user, Map<String, Object> params) {
        // Do nothing.
    }

    @Override
    public void userModified(User user, Map<String, Object> params) {
        // Do nothing.
    }

    /**
     * Returns the 'ver' hash for this server.
     *
     * @return A 'ver' hash, or null if none could be generated.
     */
    public static String getLocalDomainVerHash()
    {
        // TODO Cache results to increase performance.
        final IQ discoInfoRequest = new IQ( IQ.Type.get );
        discoInfoRequest.setChildElement( "query", "http://jabber.org/protocol/disco#info" );
        final IQ discoInfoResponse = XMPPServer.getInstance().getIQDiscoInfoHandler().handleIQ( discoInfoRequest );
        if ( discoInfoResponse.getType() == IQ.Type.result )
        {
            return EntityCapabilitiesManager.generateVerHash( discoInfoResponse, "SHA-1" );
        }
        return null;
    }

    /** Exposed for test use only */
    void clearCaches()
    {
        entityCapabilitiesMap.clear();
        entityCapabilitiesUserMap.clear();
        verAttributes.clear();
        capabilitiesBeingUpdated.clear();
    }
}
