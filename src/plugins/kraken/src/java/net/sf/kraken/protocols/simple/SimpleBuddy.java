/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.protocols.simple;

import javax.sip.Dialog;

import net.sf.kraken.pseudoroster.PseudoRosterItem;
import net.sf.kraken.roster.TransportBuddy;
import net.sf.kraken.roster.TransportBuddyManager;

/**
 * This class represents a roster item of SIP transport.
 * @author Patrick Siu
 * @author Daniel Henninger
 */
public class SimpleBuddy extends TransportBuddy {
	private SimplePresence presence;
	private Dialog         outgoingDialog;

    public PseudoRosterItem pseudoRosterItem = null;

    public SimpleBuddy(TransportBuddyManager<SimpleBuddy> manager, String username, PseudoRosterItem rosterItem) {
        super(manager, username, null, null);
        pseudoRosterItem = rosterItem;
        this.setNickname(rosterItem.getNickname());
        this.setGroups(rosterItem.getGroups());

		presence = new SimplePresence();
		presence.setTupleStatus(SimplePresence.TupleStatus.CLOSED);
		
		outgoingDialog = null;
	}

	public void updatePresence(String newPresence) throws Exception {
		presence.parse(newPresence);
	}
	
	public void setOutgoingDialog(Dialog outgoingDialog) {
		this.outgoingDialog = outgoingDialog;
	}
	
	public Dialog getOutgoingDialog() {
		return outgoingDialog;
	}
}
