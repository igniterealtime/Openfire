package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.util.Comparator;

/**
 * Comparator to sort GatewaySessions
 * 
 * @author axel.frederik.brand
 * 
 */
public class SortLastActivity implements Comparator<GatewaySession> {

	public int compare(GatewaySession gws1, GatewaySession gws2) {
		return gws1.getLastActivity().compareTo(gws2.getLastActivity());
	}
}
