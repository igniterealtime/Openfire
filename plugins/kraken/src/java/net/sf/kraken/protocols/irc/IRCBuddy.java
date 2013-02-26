/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.irc;

import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

/**
 * @author Daniel Henninger
 */
public class IRCBuddy extends TransportBuddy {

    public IRCBuddy(TransportBuddyManager<IRCBuddy> manager, String username, PseudoRosterItem item) {
        super(manager, username, null, null);
        pseudoRosterItem = item;
        this.setNickname(item.getNickname());
        this.setGroups(item.getGroups());
    }

    public PseudoRosterItem pseudoRosterItem = null;
    
}
