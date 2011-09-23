/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.sametime;

import java.util.Arrays;

import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

/**
 * @author Daniel Henninger
 */
public class SameTimeBuddy extends TransportBuddy {

    public SameTimeBuddy(TransportBuddyManager<SameTimeBuddy> manager, String uin, String nickname, String group) {
        super(manager, uin, nickname, null);
        if (group != null) {
            this.setGroups(Arrays.asList(group));
        }
    }

}
