/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.myspaceim;

import java.util.Arrays;

import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

/**
 * @author Daniel Henninger
 */
public class MySpaceIMBuddy extends TransportBuddy {

    public MySpaceIMBuddy(TransportBuddyManager<MySpaceIMBuddy> manager, Integer userid) {
        super(manager, String.valueOf(userid), String.valueOf(userid), Arrays.asList("IM Buddies"));
    }

}
