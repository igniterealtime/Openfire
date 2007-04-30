/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.disco;

/**
 * An item is associated with an XMPP Entity, usually thought of a children of the parent
 * entity and normally are addressable as a JID.<p>
 * <p/>
 * An item associated with an entity may not be addressable as a JID. In order to handle
 * such items, Service Discovery uses an optional 'node' attribute that supplements the
 * 'jid' attribute.
 *
 * @author Gaston Dombiak
 */
public interface DiscoItem {

    /**
     * Returns the entity's ID.
     *
     * @return the entity's ID.
     */
    public abstract String getJID();

    /**
     * Returns the node attribute that supplements the 'jid' attribute. A node is merely
     * something that is associated with a JID and for which the JID can provide information.<p>
     * <p/>
     * Node attributes SHOULD be used only when trying to provide or query information which
     * is not directly addressable.
     *
     * @return the node attribute that supplements the 'jid' attribute
     */
    public abstract String getNode();

    /**
     * Returns the entity's name. The entity's name specifies in natural-language the name for the
     * item.
     *
     * @return the entity's name.
     */
    public abstract String getName();

    /**
     * Returns the action (i.e. update or remove) that indicates what must be done with this item or
     * null if none. An "update" action requests the server to create or update the item. Whilst a
     * "remove" action requests to remove the item.
     *
     * @return the action (i.e. update or remove) that indicates what must be done with this item or
     *         null if none.
     */
    public abstract String getAction();
}
