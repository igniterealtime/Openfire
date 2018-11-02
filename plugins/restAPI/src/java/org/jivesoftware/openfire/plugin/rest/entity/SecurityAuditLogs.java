package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;

@XmlRootElement(name = "logs")
public class SecurityAuditLogs {
	List<SecurityAuditLog> securityAuditLog;

	public SecurityAuditLogs() {
	}

	public SecurityAuditLogs(List<SecurityAuditLog> securityAuditLog) {
		this.securityAuditLog = securityAuditLog;
	}

	@XmlElement(name = "log")
	@JsonProperty(value = "logs")
	public List<SecurityAuditLog> getSecurityAuditLog() {
		return securityAuditLog;
	}

	public void setSecurityAuditLog(List<SecurityAuditLog> securityAuditLog) {
		this.securityAuditLog = securityAuditLog;
	}
}
