/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.xmpp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterGroup;

/**
 * Apart from the functionality provided by {@link TransportBuddy}, XMPPBuddy
 * instances keep track of which resources a legacy user is using.
 * 
 * @author Daniel Henninger
 */
public class XMPPBuddy extends TransportBuddy {
    
    public final RosterEntry rosterEntry;

    public XMPPBuddy(TransportBuddyManager<XMPPBuddy> manager, String username) {
        this(manager, username, null, Collections.EMPTY_SET, null);
    }
    
    public XMPPBuddy(TransportBuddyManager<XMPPBuddy> manager, String username, String nickname, Collection<RosterGroup> groups, RosterEntry entry) {
        super(manager, username, nickname, null);
        ArrayList<String> groupList = new ArrayList<String>();
        for (RosterGroup group : groups) {
            groupList.add(group.getName());
        }
        this.setGroups(groupList);
        this.rosterEntry = entry;
    }
}
