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

package org.jivesoftware.openfire.disco;

import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.xmpp.packet.JID;
import org.xmpp.resultsetmanagement.Result;

/**
 * An item is associated with an XMPP Entity, usually thought of a children of
 * the parent entity and normally are addressable as a JID.<p>
 *
 * An item associated with an entity may not be addressable as a JID. In order
 * to handle such items, Service Discovery uses an optional 'node' attribute
 * that supplements the 'jid' attribute.
 * 
 * @author Gaston Dombiak
 */
public class DiscoItem implements Result {

    private final JID jid;
    private final String name;
    private final String node;
    private final String action;
    private final Element element;
    
    private volatile String uid = null;

    public DiscoItem(Element element) {
        this.element = element;
        jid = new JID(element.attributeValue("jid"));
        action = element.attributeValue("action");
        name = element.attributeValue("name");
        node = element.attributeValue("node");
    }

    /**
     * Creates a new DiscoItem instance.
     *
     * @param jid
     *            specifies the Jabber ID of the item "owner" or location
     *            (required).
     * @param name
     *            specifies a natural-language name for the item (can be null).
     * @param node
     *            specifies the particular node associated with the JID of the
     *            item "owner" or location (can be null).
     * @param action
     *            specifies the action to be taken for the item.
     * @throws IllegalArgumentException
     *             If a required parameter was null, or if the supplied 'action'
     *             parameter has another value than 'null', "update" or
     *             "remove".
     */
    public DiscoItem(JID jid, String name, String node, String action) {
        if (jid == null) {
            throw new IllegalArgumentException("Argument 'jid' cannot be null.");
        }

        if (action != null && !action.equals("update")
                && !action.equals("remove")) {
            throw new IllegalArgumentException(
                    "Argument 'jid' cannot have any other value than null, \"update\" or \"remove\".");
        }

        this.jid = jid;
        this.name = name;
        this.node = node;
        this.action = action;

        element = DocumentHelper.createElement("item");
        element.addAttribute("jid", jid.toString());
        if (action != null) {
            element.addAttribute("action", action);
        }
        if (name != null) {
            element.addAttribute("name", name);
        }
        if (node != null) {
            element.addAttribute("node", node);
        }
    }

    /**
     * <p>
     * Returns the entity's ID.
     * </p>
     * 
     * @return the entity's ID.
     */
    public JID getJID() {
        return jid;
    }

    /**
     * <p>
     * Returns the node attribute that supplements the 'jid' attribute. A node
     * is merely something that is associated with a JID and for which the JID
     * can provide information.
     * </p>
     * <p>
     * Node attributes SHOULD be used only when trying to provide or query
     * information which is not directly addressable.
     * </p>
     * 
     * @return the node attribute that supplements the 'jid' attribute
     */
    public String getNode() {
        return node;
    }

    /**
     * <p>
     * Returns the entity's name. The entity's name specifies in
     * natural-language the name for the item.
     * </p>
     * 
     * @return the entity's name.
     */
    public String getName() {
        return name;
    }

    /**
     * <p>
     * Returns the action (i.e. update or remove) that indicates what must be
     * done with this item or null if none. An "update" action requests the
     * server to create or update the item. Whilst a "remove" action requests to
     * remove the item.
     * </p>
     * 
     * @return the action (i.e. update or remove) that indicates what must be
     *         done with this item or null if none.
     */
    public String getAction() {
        return action;
    }

    /**
     * Returns a dom4j element that represents this DiscoItem object.
     * 
     * @return element representing this object.
     */
    public Element getElement() {
        return element;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.util.resultsetmanager.Result#getUID()
     */
    @Override
    public String getUID() {
        if (uid == null) {
            synchronized(this) {
                if (uid == null) {
                    final StringBuilder sb = new StringBuilder(jid.toString());
                    if (name != null) {
                        sb.append(name);
                    }
                    if (node != null) {
                        sb.append(node);
                    }
                    if (action != null) {
                        sb.append(action);
                    }
                    uid = sb.toString();
                }
            }
        }
        return uid;
    }

    @Override
    public int hashCode() {
        return getUID().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DiscoItem && getUID().equals(((DiscoItem)obj).getUID());
    }

    @Override
    public String toString() {
        return element.asXML();
    }
    
}
