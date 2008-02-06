/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.entitycaps;

import org.dom4j.Element;
import org.jivesoftware.openfire.IQResultListener;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

import java.util.*;

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
 *
 */
public class EntityCapabilitiesManager implements IQResultListener, UserEventListener {

    private static final EntityCapabilitiesManager instance = new EntityCapabilitiesManager();

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
     * We use a cache for this map so it is cluster safe for remote users
     * whose disco#info replies are handled by new nodes in the cluster (after
     * an s2s disconnection for example).
     * 
     * Key:   Packet ID of our disco#info request.
     * Value: The 'ver' hash string from the original caps packet.
     */
    private Cache<String, String> verAttributes;

    private EntityCapabilitiesManager() {
        entityCapabilitiesMap = CacheFactory.createCache("Entity Capabilities");
        entityCapabilitiesUserMap = CacheFactory.createCache("Entity Capabilities Users");
        verAttributes = CacheFactory.createCache("Entity Capabilities Pending Hashes");
    }

    /**
     * Returns the unique instance of this class.
     *
     * @return the unique instance of this class.
     */
    public static EntityCapabilitiesManager getInstance() {
        return instance;
    }

    public void process(Presence packet) {
        // Ignore unavailable presences
        if (Presence.Type.unavailable == packet.getType()) {
            return;
        }

        // Examine the packet and check if it has caps info and a 'ver' hash,
        // if not -- do nothing by returning.
        Element capsElement = packet.getChildElement("c", "http://jabber.org/protocol/caps");
        if (capsElement == null) {
            return;
        }

        String newVerAttribute = capsElement.attributeValue("ver");
        if (newVerAttribute == null) {
            return;
        }

        // Check to see if the 'ver' hash is already in our cache.
        if (isInCapsCache(newVerAttribute)) {
            // The 'ver' hash is in the cache already, so let's update the
            // entityCapabilitiesUserMap for the user that sent the caps
            // packet.
            entityCapabilitiesUserMap.put(packet.getFrom(), newVerAttribute);
        }
        else {
            // The 'ver' hash is not in the cache so send out a disco#info query
            // so that we may begin recognizing this 'ver' hash.
            IQ iq = new IQ(IQ.Type.get);
            iq.setTo(packet.getFrom());

            String serverName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
            iq.setFrom(serverName);

            iq.setChildElement("query", "http://jabber.org/protocol/disco#info");

            String packetId = iq.getID();
            verAttributes.put(packetId, newVerAttribute);

            IQRouter iqRouter = XMPPServer.getInstance().getIQRouter();
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
        String newVerHash = generateVerHash(packet);

        String originalVerAttribute = verAttributes.get(packet.getID());
        return originalVerAttribute.equals(newVerHash);
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
     * @return the generated 'ver' hash
     */
    private String generateVerHash(IQ packet) {
        // Initialize an empty string S.
        String S = "";

        // Sort the service discovery identities by category and then by type
        // (if it exists), formatted as 'category' '/' 'type'.
        List<String> discoIdentities = getIdentitiesFrom(packet);
        Collections.sort(discoIdentities);

        // For each identity, append the 'category/type' to S, followed by the
        // '<' character.
        for (String discoIdentity : discoIdentities) {
            S += discoIdentity;
            S += '<';
        }

        // Sort the supported features.
        List<String> discoFeatures = getFeaturesFrom(packet);
        Collections.sort(discoFeatures);

        // For each feature, append the feature to S, followed by the '<'
        // character.
        for (String discoFeature : discoFeatures) {
            S += discoFeature;
            S += '<';
        }

        // Compute ver by hashing S using the SHA-1 algorithm as specified in
        // RFC 3174 (with binary output) and encoding the hash using Base64 as
        // specified in Section 4 of RFC 4648 (note: the Base64 output
        // MUST NOT include whitespace and MUST set padding bits to zero).
        S = StringUtils.hash(S, "SHA-1");
        S = StringUtils.encodeBase64(StringUtils.decodeHex(S));

        return S;
    }

    public void answerTimeout(String packetId) {
        // If we never received an answer, we can discard the cached
        // 'ver' attribute.
        verAttributes.remove(packetId);
    }

    public void receivedAnswer(IQ packet) {
        String packetId = packet.getID();

        if (isValid(packet)) {
            // The packet was validated, so it can be added to the Entity
            // Capabilities cache map.

            // Create the entity capabilities object and add it to the cache map...
            EntityCapabilities entityCapabilities = new EntityCapabilities();

            // Store identities.
            List<String> identities = getIdentitiesFrom(packet);
            for (String identity : identities) {
                entityCapabilities.addIdentity(identity);
            }

            // Store features.
            List<String> features = getFeaturesFrom(packet);
            for (String feature : features) {
                entityCapabilities.addFeature(feature);
            }

            String originalVerAttribute = verAttributes.get(packetId);
            entityCapabilities.setVerAttribute(originalVerAttribute);

            entityCapabilitiesMap.put(originalVerAttribute, entityCapabilities);
            entityCapabilitiesUserMap.put(packet.getFrom(), originalVerAttribute);
        }

        // Remove cached 'ver' attribute.
        verAttributes.remove(packetId);
    }

    /**
     * Returns the entity capabilities for a specific JID. The specified JID
     * should be a full JID that identitied the entity's connection.
     * 
     * @param jid the full JID of entity
     * @return the entity capabilities of jid.
     */
    public EntityCapabilities getEntityCapabilities(JID jid) {
        String verAttribute = entityCapabilitiesUserMap.get(jid);
        return entityCapabilitiesMap.get(verAttribute);
    }

    /**
     * Extracts a list of identities from an IQ packet.
     * 
     * @param packet the packet
     * @return a list of identities
     */
    private List<String> getIdentitiesFrom(IQ packet) {
        List<String> discoIdentities = new ArrayList<String>();
        Element query = packet.getChildElement();
        Iterator identitiesIterator = query.elementIterator("identity");
        if (identitiesIterator != null) {
            while (identitiesIterator.hasNext()) {
                Element identityElement = (Element) identitiesIterator.next();

                String discoIdentity = identityElement.attributeValue("category");
                discoIdentity += '/';
                discoIdentity += identityElement.attributeValue("type");

                discoIdentities.add(discoIdentity);
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
    private List<String> getFeaturesFrom(IQ packet) {
        List<String> discoFeatures = new ArrayList<String>();
        Element query = packet.getChildElement();
        Iterator featuresIterator = query.elementIterator("feature");
        if (featuresIterator != null) {
            while (featuresIterator.hasNext()) {
                Element featureElement = (Element) featuresIterator.next();
                String discoFeature = featureElement.attributeValue("var");

                discoFeatures.add(discoFeature);
            }
        }
        return discoFeatures;
    }

    public void userDeleting(User user, Map<String, Object> params) {
        // Delete this user's association in entityCapabilitiesUserMap.
        JID jid = XMPPServer.getInstance().createJID(user.getUsername(), null, true);
        String verHashOfUser = entityCapabilitiesUserMap.remove(jid);

        // If there are no other references to the deleted user's 'ver' hash,
        // it is safe to remove that 'ver' hash's associated entity
        // capabilities from the entityCapabilitiesMap cache.
        for (String verHash : entityCapabilitiesUserMap.values()) {
            if (verHash.equals(verHashOfUser)) {
                // A different user is making use of the deleted user's same
                // 'ver' hash, so let's not remove the associated entity
                // capabilities from the entityCapabilitiesMap.
                return;
            }
        }
        entityCapabilitiesMap.remove(verHashOfUser);
    }

    public void userCreated(User user, Map<String, Object> params) {
        // Do nothing.
    }

    public void userModified(User user, Map<String, Object> params) {
        // Do nothing.
    }
}
