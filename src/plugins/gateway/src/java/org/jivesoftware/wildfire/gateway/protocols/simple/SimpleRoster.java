/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.wildfire.gateway.protocols.simple;

import java.util.Hashtable;
import org.jivesoftware.util.Log;

/**
 * This class represents the roster of SIP transport.
 * <br><br>
 * By now the SIP has no rosters stored so clients got to implement rosters themselves.
 * @author Patrick Siu
 * @version 0.0.2
 */
public class SimpleRoster {
	private SimpleSession                       mySimpleSession;
	private Hashtable<String, SimpleRosterItem> rosterItemList;
	
	private boolean activated = false;
	
	public SimpleRoster(SimpleSession mySimpleSession) {
		if (mySimpleSession == null)
			throw new NullPointerException("The SIP session provided cannot be null!");
		
		this.mySimpleSession = mySimpleSession;
	}
	
	public synchronized void activate() {
		this.activated = true;
	}
	
	public synchronized void deactivate() {
		this.activated = false;
	}
	
	public void addEntry(String userid, SimpleRosterItem item) {
		rosterItemList.put(userid, item);
	}
	
	public void removeEntry(String userid) {
		rosterItemList.remove(userid);
	}
	
	public SimpleRosterItem getEntry(String userid) {
		return rosterItemList.get(userid);
	}
	
	/**
	 * Loads the SIP roster from persistent store.
	 */
	public void loadRoster() {
		// Roster should be loaded in persistent stores.
	}
	
	/**
	 * Stores the SIP roster into persistent store.
	 */
	public void storeRoster() {
		// Roster should be stored in persistent stores.
	}
	
	public void finalize() {
		Log.debug("SimpleRoster shutting down!");
		rosterItemList.clear();
	}
}
