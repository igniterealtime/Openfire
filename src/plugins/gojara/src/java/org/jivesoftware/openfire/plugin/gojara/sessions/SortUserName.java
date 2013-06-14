package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.util.Comparator;

/**
 * Comparator to sort GatewaySessions
 * 
 * @author axel.frederik.brand
 * 
 */
public class SortUserName implements Comparator<GatewaySession> {

	public int compare(GatewaySession gw1, GatewaySession gw2) {
		return gw1.getUsername().compareTo(gw2.getUsername());
	}
}
