package org.jivesoftware.openfire.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "status")
public class StatusEntity {
	
	private String xmppDomain;
	private int activeSessions;

	public StatusEntity() {
	}

	@XmlElement
	public String getXmppDomain() {
		return xmppDomain;
	}

	public void setXmppDomain(String xmppDomain) {
		this.xmppDomain = xmppDomain;
	}

	@XmlElement
	public int getActiveSessions() {
		return activeSessions;
	}

	public void setActiveSessions(int activeSessions) {
		this.activeSessions = activeSessions;
	}
}