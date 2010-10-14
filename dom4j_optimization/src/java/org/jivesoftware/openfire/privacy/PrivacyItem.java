/**
 * $RCSfile$
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

package org.jivesoftware.openfire.privacy;

import org.dom4j.Element;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.xmpp.packet.*;

import java.util.Collection;
import java.util.Collections;

/**
 * A privacy item acts a rule that when matched defines if a packet should be blocked or not. 
 *
 * @author Gaston Dombiak
 */
class PrivacyItem implements Cacheable, Comparable {

    private int order;
    private boolean allow;
    private Type type;
    private JID jidValue;
    private RosterItem.SubType subscriptionValue;
    private String groupValue;
    private boolean filterEverything;
    private boolean filterIQ;
    private boolean filterMessage;
    private boolean filterPresence_in;
    private boolean filterPresence_out;
    /**
     * Copy of the element that defined this item.
     */
    private Element itemElement;

    PrivacyItem(Element itemElement) {
        this.allow = "allow".equals(itemElement.attributeValue("action"));
        this.order = Integer.parseInt(itemElement.attributeValue("order"));
        String typeAttribute = itemElement.attributeValue("type");
        if (typeAttribute != null) {
            this.type = Type.valueOf(typeAttribute);
            // Decode the proper value based on the rule type
            String value = itemElement.attributeValue("value");
            if (type == Type.jid) {
                // Decode the specified JID
                this.jidValue = new JID(value);
            }
            else if (type == Type.subscription) {
                // Decode the specified subscription type
                if ("both".equals(value)) {
                    this.subscriptionValue = RosterItem.SUB_BOTH;
                }
                else if ("to".equals(value)) {
                    this.subscriptionValue = RosterItem.SUB_TO;
                }
                else if ("from".equals(value)) {
                    this.subscriptionValue = RosterItem.SUB_FROM;
                }
                else {
                    this.subscriptionValue = RosterItem.SUB_NONE;
                }
            }
            else {
                // Decode the specified group name
                this.groupValue = value;
            }
        }
        // Set what type of stanzas should be filters (i.e. blocked or allowed)
        this.filterIQ = itemElement.element("iq") != null;
        this.filterMessage = itemElement.element("message") != null;
        this.filterPresence_in = itemElement.element("presence-in") != null;
        this.filterPresence_out = itemElement.element("presence-out") != null;
        if (!filterIQ && !filterMessage && !filterPresence_in && !filterPresence_out) {
            // If none was defined then block all stanzas
            filterEverything = true;
        }
        // Keep a copy of the item element that defines this item
        this.itemElement = itemElement.createCopy();
    }

    Element asElement() {
        return itemElement.createCopy();
    }

    /**
     * Returns true if this privacy item needs the user roster to figure out
     * if a packet must be blocked.
     *
     * @return true if this privacy item needs the user roster to figure out
     *         if a packet must be blocked.
     */
    boolean isRosterRequired() {
        return type == Type.group || type == Type.subscription;
    }

    public int compareTo(Object object) {
        if (object instanceof PrivacyItem) {
            return this.order - ((PrivacyItem) object).order;
        }
        return getClass().getName().compareTo(object.getClass().getName());
    }

    /**
     * Returns true if the packet to analyze matches the condition defined by this rule.
     * Variables involved in the analysis are: type (e.g. jid, group, etc.), value (based
     * on the type) and granular control that defines which type of packets should be
     * considered.
     *
     * @param packet the packet to analyze if matches the rule's condition.
     * @param roster the roster of the owner of the privacy list.
     * @param userJID the JID of the owner of the privacy list.
     * @return true if the packet to analyze matches the condition defined by this rule.
     */
    boolean matchesCondition(Packet packet, Roster roster, JID userJID) {
        return matchesPacketSenderCondition(packet, roster, userJID) &&
                matchesPacketTypeCondition(packet, userJID);
    }

    boolean isAllow() {
        return allow;
    }

    private boolean matchesPacketSenderCondition(Packet packet, Roster roster, JID userJID) {
        if (type == null) {
            // This is the "fall-through" case
            return true;
        }
        boolean isPresence = packet.getClass().equals(Presence.class);
        boolean incoming = true;
        if (packet.getFrom() != null) {
            incoming = !userJID.toBareJID().equals(packet.getFrom().toBareJID());
        }
        boolean matches = false;
        if (isPresence && !incoming && (filterEverything || filterPresence_out)) {
            // If this is an outgoing presence and we are filtering by outgoing presence
            // notification then use the receipient of the packet in the analysis
            matches = verifyJID(packet.getTo(), roster);
        }
        if (!matches && incoming &&
                (filterEverything || filterPresence_in || filterIQ || filterMessage)) {
            matches = verifyJID(packet.getFrom(), roster);
        }
        return matches;
    }

    private boolean verifyJID(JID jid, Roster roster) {
        if (jid == null) {
            return false;
        }
        if (type == Type.jid) {
            if (jidValue.getResource() != null) {
                // Rule is filtering by exact resource match
                // (e.g. <user@domain/resource> or <domain/resource>)
                return jid.equals(jidValue);
            }
            else if (jidValue.getNode() != null) {
                // Rule is filtering by any resource matches (e.g. <user@domain>)
                return jid.toBareJID().equals(jidValue.toBareJID());
            }
            else {
                // Rule is filtering by domain (e.g. <domain>)
                return jid.getDomain().equals(jidValue.getDomain());
            }
        }
        else if (type == Type.group) {
            Collection<String> contactGroups;
            try {
                // Get the groups where the contact belongs
                RosterItem item = roster.getRosterItem(jid);
                contactGroups = item.getGroups();
            }
            catch (UserNotFoundException e) {
                // Sender is not in the user's roster
                contactGroups = Collections.emptyList();
            }
            // Check if the contact belongs to the specified group
            return contactGroups.contains(groupValue);
        }
        else {
            RosterItem.SubType contactSubscription = RosterItem.SUB_NONE;
            try {
                // Get the groups where the contact belongs
                RosterItem item = roster.getRosterItem(jid);
                contactSubscription = item.getSubStatus();
            }
            catch (UserNotFoundException e) {
                // Sender is not in the user's roster
            }
            // Check if the contact has the specified subscription status
            return contactSubscription == subscriptionValue;
        }
    }

    private boolean matchesPacketTypeCondition(Packet packet, JID userJID) {
        if (filterEverything) {
            // This includes all type of packets (including subscription-related presences)
            return true;
        }
        Class packetClass = packet.getClass();
        if (Message.class.equals(packetClass)) {
            return filterMessage;
        }
        else if (Presence.class.equals(packetClass)) {
            Presence.Type presenceType = ((Presence) packet).getType();
            // Only filter presences of type available or unavailable
            // (ignore subscription-related presences)
            if (presenceType == null || presenceType == Presence.Type.unavailable) {
                // Calculate if packet is being received by the user
                JID to = packet.getTo();
                boolean incoming = to != null && to.toBareJID().equals(userJID.toBareJID());
                if (incoming) {
                    return filterPresence_in;
                }
                else {
                    return filterPresence_out;
                }
            }
        }
        else if (IQ.class.equals(packetClass)) {
            return filterIQ;
        }
        return false;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();                      // overhead of object
        size += CacheSizes.sizeOfInt();                         // order
        size += CacheSizes.sizeOfBoolean();                     // allow
        //size += CacheSizes.sizeOfString(jidValue.toString());   // type
        if (jidValue != null ) {
            size += CacheSizes.sizeOfString(jidValue.toString()); // jidValue
        }
        //size += CacheSizes.sizeOfString(name);                  // subscriptionValue
        if (groupValue != null) {
            size += CacheSizes.sizeOfString(groupValue);        // groupValue
        }
        size += CacheSizes.sizeOfBoolean();                     // filterEverything
        size += CacheSizes.sizeOfBoolean();                     // filterIQ
        size += CacheSizes.sizeOfBoolean();                     // filterMessage
        size += CacheSizes.sizeOfBoolean();                     // filterPresence_in
        size += CacheSizes.sizeOfBoolean();                     // filterPresence_out
        return size;
    }

    /**
     * Type defines if the rule is based on JIDs, roster groups or presence subscription types.
     */
    private static enum Type {
        /**
         * JID being analyzed should belong to a roster group of the list's owner.
         */
        group,
        /**
         * JID being analyzed should have a resource match, domain match or bare JID match.
         */
        jid,
        /**
         * JID being analyzed should belong to a contact present in the owner's roster with
         * the specified subscription status.
         */
        subscription
    }
}
