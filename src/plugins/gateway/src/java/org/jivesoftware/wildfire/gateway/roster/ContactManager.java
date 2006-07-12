/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.roster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.xmpp.packet.JID;

/**
 * 
 * Manage contacts for a paticular JID.
 * 
 * @author Noah Campbell
 */
public class ContactManager {

    /**
     * Construct a new <code>ContactManager</code>
     */
    ContactManager() { }

    /**
     * The fcs
     *
     * @see java.util.Set
     */
    private final Set<AbstractForeignContact> fcs = new HashSet<AbstractForeignContact>();

    /**
     * Maintain a mapping of JIDs to their contact list.
     */
    private final Map<NormalizedJID, Roster> contactLists = 
        new HashMap<NormalizedJID, Roster>();

    /**
     * Return a roster for a JID.
     * 
     * @param name The <code>JID</code> to lookup.
     * @return roster The roster for the <code>JID</code>.
     */
    public synchronized Roster getRoster(JID name) {
        Roster r = contactLists.get(NormalizedJID.wrap(name));
        if (r == null) {
            r = new Roster();
            contactLists.put(NormalizedJID.wrap(name), r);
        }

        return r;
    }

    /**
     * @return foreignContacts A {@code java.util.Set} of {@code ForeignContact}s.
     */
    public Set<AbstractForeignContact> getAllForeignContacts() {
        return this.fcs;
    }

    /**
     * Remove the <code>JID</code> from the contact list.
     * 
     * @param jid
     */
    void remove(NormalizedJID jid) {
        contactLists.remove(jid);    
    }

}
