package org.jivesoftware.openfire.plugin.gojara.database;

import java.sql.Timestamp;
import java.util.Date;

/**
 * This class should represent a Session Entry 
 */
public class SessionEntry {
	private String username;
	private String transport;
	private long last_activity;
	/**
	 */
	public SessionEntry(String username, String transport, long last_activity) {
		this.username = username;
		this.transport = transport;
		this.last_activity = last_activity;

	}
	
	public String getUsername() {
		return username;
	}
	public String getTransport() {
		return transport;
	}
	public long getLast_activity() {
		return last_activity;
	}

	public Date getLast_activityAsDate() {
		Timestamp stamp = new Timestamp(last_activity);
		Date date = new Date(stamp.getTime());
		return date;
	}
}
