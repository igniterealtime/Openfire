package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.util.Comparator;

public class SortTransport implements Comparator<GatewaySession>{

	public int compare(GatewaySession gws1, GatewaySession gws2) {
		return gws1.getTransport().compareTo(gws2.getTransport());
	}
}
