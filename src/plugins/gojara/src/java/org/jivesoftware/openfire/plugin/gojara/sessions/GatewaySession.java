package org.jivesoftware.openfire.plugin.gojara.sessions;

import java.util.Date;

/**
 * @author axel.frederik.brand
 * Class for storing Gateway Session Objects for Iteration for JSP
 */
public class GatewaySession {
	private String username;
	private String transport;
	private Date lastActivity;
	
	public GatewaySession(String username, String transport, Date lastActivity) {
		this.username = username;
		this.transport = transport;
		this.lastActivity = lastActivity;
	}

	public String getUsername() {
		return username;
	}

	public String getTransport() {
		return transport;
	}

	public Date getLastActivity() {
		return lastActivity;
	}

	@Override
	public String toString() {
		return "GatewaySession [username=" + username + ", transport=" + transport + ", lastActivity=" + lastActivity + "]";
	}
	

}
