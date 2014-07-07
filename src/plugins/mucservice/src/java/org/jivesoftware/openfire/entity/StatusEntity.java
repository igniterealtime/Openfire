package org.jivesoftware.openfire.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "status")
public class StatusEntity {

	private String xmppDomain;
	private int activeSessions;
	private int activeDatabaseConnections;
	private int maxDatabaseConnections;
	private String openfireVersion;
	private String hostname;

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

	@XmlElement
	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	@XmlElement
	public int getActiveDatabaseConnections() {
		return activeDatabaseConnections;
	}

	public void setActiveDatabaseConnections(int connections) {
		this.activeDatabaseConnections = connections;
	}

	@XmlElement
	public String getOpenfireVersion() {
		return openfireVersion;
	}

	public void setOpenfireVersion(String openfireVersion) {
		this.openfireVersion = openfireVersion;
	}

	@XmlElement
	public int getMaxDatabaseConnections() {
		return maxDatabaseConnections;
	}

	public void setMaxDatabaseConnections(int maxDatabaseConnections) {
		this.maxDatabaseConnections = maxDatabaseConnections;
	}
}