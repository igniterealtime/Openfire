/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.dom4j.Element;
import org.dom4j.QName;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.event.UserEventListener;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;
import org.xmpp.component.IQResultListener;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;
import org.xmpp.packet.Presence;

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
     * Key:   Packet ID of our disco#info request.
     * Value: The 'ver' hash string from the original caps packet.
     */
    private Map<String, EntityCapabilities> verAttributes;

    private EntityCapabilitiesManager() {
        entityCapabilitiesMap = CacheFactory.createCache("Entity Capabilities");
        entityCapabilitiesUserMap = CacheFactory.createCache("Entity Capabilities Users");
        verAttributes = new HashMap<String, EntityCapabilities>();
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
            
            final EntityCapabilities caps = new EntityCapabilities();
            caps.setHashAttribute(hashAttribute);
            caps.setVerAttribute(newVerAttribute);
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

            entityCapabilitiesMap.put(caps.getVerAttribute(), caps);
            entityCapabilitiesUserMap.put(packet.getFrom(), caps.getVerAttribute());
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
    private static List<String> getIdentitiesFrom(IQ packet) {
        List<String> discoIdentities = new ArrayList<String>();
        Element query = packet.getChildElement();
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
        List<String> discoFeatures = new ArrayList<String>();
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
		List<String> results = new ArrayList<String>();
		Element query = packet.getChildElement();
		Iterator<Element> extensionIterator = query.elementIterator(QName.get(
				"x", "jabber:x:data"));
		if (extensionIterator != null) {
			while (extensionIterator.hasNext()) {
				Element extensionElement = extensionIterator.next();
				final StringBuilder formType = new StringBuilder();

				Iterator<Element> fieldIterator = extensionElement
						.elementIterator("field");
				List<String> vars = new ArrayList<String>();
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
						List<String> values = new ArrayList<String>();
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
