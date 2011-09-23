/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package net.sf.kraken.protocols.qq;

import java.util.Collection;

import net.sf.jqql.beans.ContactInfo;
import net.sf.jqql.beans.QQFriend;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

/**
 * @author Daniel Henninger
 */
public class QQBuddy extends TransportBuddy {

    public QQBuddy(TransportBuddyManager<QQBuddy> manager, QQFriend qqFriend, String nickname, Collection<String> groups) {
        super(manager, String.valueOf(qqFriend.qqNum), nickname, groups);
        this.qqFriend = qqFriend;
    }
    public QQBuddy(TransportBuddyManager<QQBuddy> manager, int qqnum, Collection<String> groups) {
        super(manager, String.valueOf(qqnum), String.valueOf(qqnum), groups);
    }

    public QQFriend qqFriend = null;
    public ContactInfo contactInfo = null;
    
}
