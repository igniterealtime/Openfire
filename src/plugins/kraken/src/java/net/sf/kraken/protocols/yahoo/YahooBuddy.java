/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.yahoo;

import java.util.Collection;

import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

import org.openymsg.network.YahooUser;

/**
 * @author Daniel Henninger
 */
public class YahooBuddy extends TransportBuddy {

    public YahooBuddy(TransportBuddyManager<YahooBuddy> manager, YahooUser yahooUser, String nickname, Collection<String> groups, PseudoRosterItem rosterItem) {
        super(manager, yahooUser.getId(), nickname, groups);
        this.yahooUser = yahooUser;
        this.pseudoRosterItem = rosterItem;
        String custommsg = this.yahooUser.getCustomStatusMessage();
        if (custommsg != null) {
            this.verboseStatus = custommsg;
        }

        this.setPresenceAndStatus(((YahooTransport)getManager().getSession().getTransport()).convertYahooStatusToXMPP(yahooUser.getStatus(), yahooUser.getCustomStatus()), yahooUser.getCustomStatusMessage());
    }

    public YahooUser yahooUser = null;

    public PseudoRosterItem pseudoRosterItem = null;
    
}
