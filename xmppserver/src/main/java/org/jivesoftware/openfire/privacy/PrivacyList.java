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

package org.jivesoftware.openfire.privacy;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.StringReader;
import java.util.*;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CannotCalculateSizeException;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * A privacy list contains a set of rules that define if communication with the list owner
 * is allowed or denied. Users may have zero, one or more privacy lists. When a list is the
 * default list then that list is going to be used by default for all user sessions or analyze,
 * when user is offline, if communication may proceed (e.g. define if a message should be stored
 * offline). A user may configure is he wants to have a default list or not. When no default list
 * is defined then communication will not be blocked. However, users may define an active list
 * for a particular session. Active lists override default list (if there is one) and will be used
 * only for the duration of the session.
 *
 * @author Gaston Dombiak
 */
public class PrivacyList implements Cacheable, Externalizable {

    private static final Logger Log = LoggerFactory.getLogger(PrivacyList.class);

    /**
     * Reuse the same factory for all the connections.
     */
    private static XmlPullParserFactory factory = null;
    private static ThreadLocal<XMPPPacketReader> localParser = null;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
        // Create xmpp parser to keep in each thread
        localParser = new ThreadLocal<XMPPPacketReader>() {
            @Override
            protected XMPPPacketReader initialValue() {
                XMPPPacketReader parser = new XMPPPacketReader();
                factory.setNamespaceAware(true);
                parser.setXPPFactory(factory);
                return parser;
            }
        };
    }

    private JID userJID;
    private String name;
    private boolean isDefault;
    private List<PrivacyItem> items = new ArrayList<>();

    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public PrivacyList() {
    }

    public PrivacyList(String username, String name, boolean isDefault, Element listElement) {
        this.userJID = XMPPServer.getInstance().createJID(username, null, true);
        this.name = name;
        this.isDefault = isDefault;
        // Set the new list items
        updateList(listElement);
    }

    /**
     * Returns the JID of the user that owns this privacy list.
     *
     * @return the JID of the user that owns this privacy list.
     */
    public JID getUserJID() {
        return userJID;
    }

    /**
     * Returns the name that uniquely identifies this list among the users lists.
     *
     * @return the name that uniquely identifies this list among the users lists.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns true if this privacy list is the default list to apply for the user. Default
     * privacy lists can be overriden per session by setting an active privacy list.
     *
     * @return true if this privacy list is the default list to apply for the user.
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Sets if this privacy list is the default list to apply for the user. Default
     * privacy lists can be overriden per session by setting an active privacy list.
     *
     * @param isDefault true if this privacy list is the default list to apply for the user.
     */
    public void setDefaultList(boolean isDefault) {
        this.isDefault = isDefault;
        // Trigger event that this list has been modified
        PrivacyListManager.getInstance().dispatchModifiedEvent(this);
    }

    /**
     * Returns true if the specified packet must be blocked based on this privacy list rules.
     * Rules are going to be analyzed based on their order (in ascending order). When a rule
     * is matched then communication will be blocked or allowed based on that rule. No more
     * further analysis is going to be made.
     *
     * @param packet the packet to analyze if it must be blocked.
     * @return true if the specified packet must be blocked based on this privacy list rules.
     */
    public boolean shouldBlockPacket(Packet packet) {
        if (packet.getFrom() == null) {
            // Sender is the server so it's not denied
            return false;
        }
        // Iterate over the rules and check each rule condition
        Roster roster = getRoster();
        for (PrivacyItem item : items) {
            if (item.matchesCondition(packet, roster, userJID)) {
                if (item.isAllow()) {
                    return false;
                }
                if (Log.isDebugEnabled()) {
                    Log.debug("PrivacyList: Packet was blocked: " + packet);
                }
                return true;
            }
        }
        // If no rule blocked the communication then allow the packet to flow
        return false;
    }

    /**
     * Returns all JIDs that are on the blocklist (as defined in XEP-0191).
     *
     * @return a collection of JIDs (possibly empty, never null)
     */
    public Set<JID> getBlockedJIDs()
    {
        final Set<JID> result = new HashSet<>();

        for ( final PrivacyItem item : items )
        {
            if ( !item.isAllow() && item.isType( PrivacyItem.Type.jid ) )
            {
                if ( item.getJID() != null )
                {
                    result.add( item.getJID() );
                }
            }
        }

        return result;
    }

    /**
     * Returns the 'raw' items on the privacy list.
     *
     * @return privacy list items.
     */
    public List<PrivacyItem> getItems()
    {
        return items;
    }

    /**
     * Returns an Element with the privacy list XML representation.
     *
     * @return an Element with the privacy list XML representation.
     */
    public Element asElement() {
        //Element listElement = DocumentFactory.getInstance().createDocument().addElement("list");
        Element listElement = DocumentFactory.getInstance().createDocument()
                .addElement("list", "jabber:iq:privacy");
        listElement.addAttribute("name", getName());
        // Add the list items to the result
        for (PrivacyItem item : items) {
            listElement.add(item.asElement());
        }
        return listElement;
    }

    /**
     * Sets the new list items based on the specified Element. The Element must contain
     * a list of item elements.
     *
     * @param listElement the element containing a list of items.
     */
    public void updateList(Element listElement) {
        updateList(listElement, true);
    }

    /**
     * Sets the new list items based on the specified Element. The Element must contain
     * a list of item elements.
     *
     * @param listElement the element containing a list of items.
     * @param notify true if a provicy list modified event will be triggered.
     */
    private void updateList(Element listElement, boolean notify) {
        // Reset the list of items of this list
        items = new ArrayList<>();

        List<Element> itemsElements = listElement.elements("item");
        for (Element itemElement : itemsElements) {
            PrivacyItem newItem = new PrivacyItem(itemElement);
            items.add(newItem);
            // If the user's roster is required to evaluation whether a packet must be blocked
            // then ensure that the roster is available
            if (newItem.isRosterRequired()) {
                Roster roster = getRoster();
                if (roster == null) {
                    Log.warn("Privacy item removed since roster of user was not found: " + userJID.getNode());
                    items.remove(newItem);
                }
            }
        }
        // Sort items collections
        Collections.sort(items);
        if (notify) {
            // Trigger event that this list has been modified
            PrivacyListManager.getInstance().dispatchModifiedEvent(this);
        }
    }

    private Roster getRoster() {
        try {
            return XMPPServer.getInstance().getRosterManager().getRoster(userJID.getNode());
        } catch (UserNotFoundException e) {
            Log.warn("Roster not found for user: " + userJID);
        }
        return null;
    }

    @Override
    public int getCachedSize() throws CannotCalculateSizeException {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();                      // overhead of object
        size += CacheSizes.sizeOfString(userJID.toString());    // userJID
        size += CacheSizes.sizeOfString(name);                  // name
        size += CacheSizes.sizeOfBoolean();                     // isDefault
        size += CacheSizes.sizeOfCollection(items);             // items of the list
        return size;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof PrivacyList) {
            return name.equals(((PrivacyList)object).getName());
        }
        else {
            return false;
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, userJID);
        ExternalizableUtil.getInstance().writeSafeUTF(out, name);
        ExternalizableUtil.getInstance().writeBoolean(out, isDefault);
        ExternalizableUtil.getInstance().writeSafeUTF(out, asElement().asXML());
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        userJID = (JID) ExternalizableUtil.getInstance().readSerializable(in);
        name = ExternalizableUtil.getInstance().readSafeUTF(in);
        isDefault = ExternalizableUtil.getInstance().readBoolean(in);
        String xml = ExternalizableUtil.getInstance().readSafeUTF(in);
        try {
            Element element = localParser.get().read(new StringReader(xml)).getRootElement();
            updateList(element, false);
        } catch (Exception e) {
            Log.error("Error while parsing Privacy Property", e);
        }
    }
}
