package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class SecurityAuditLog.
 */
@XmlRootElement(name = "log")
public class SecurityAuditLog {

    /** The log id. */
    private long logId;
    
    /** The username. */
    private String username;
    
    /** The timestamp. */
    private long timestamp;
    
    /** The summary. */
    private String summary;
    
    /** The node. */
    private String node;
    
    /** The details. */
    private String details;

	/**
	 * Instantiates a new security audit log.
	 */
	public SecurityAuditLog() {
	}

	/**
	 * Instantiates a new security audit log.
	 *
	 * @param logId the log id
	 * @param username the username
	 * @param timestamp the timestamp
	 * @param summary the summary
	 * @param node the node
	 * @param details the details
	 */
	public SecurityAuditLog(long logId, String username, long timestamp, String summary, String node, String details) {
		this.logId = logId;
		this.username = username;
		this.timestamp = timestamp;
		this.summary = summary;
		this.node = node;
		this.details = details;
	}

	/**
	 * Gets the log id.
	 *
	 * @return the log id
	 */
	@XmlElement
	public long getLogId() {
		return logId;
	}

	/**
	 * Sets the log id.
	 *
	 * @param logId the new log id
	 */
	public void setLogId(long logId) {
		this.logId = logId;
	}

	/**
	 * Gets the username.
	 *
	 * @return the username
	 */
	@XmlElement
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the username.
	 *
	 * @param username the new username
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Gets the timestamp.
	 *
	 * @return the timestamp
	 */
	@XmlElement
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * Sets the timestamp.
	 *
	 * @param timestamp the new timestamp
	 */
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	/**
	 * Gets the summary.
	 *
	 * @return the summary
	 */
	@XmlElement
	public String getSummary() {
		return summary;
	}

	/**
	 * Sets the summary.
	 *
	 * @param summary the new summary
	 */
	public void setSummary(String summary) {
		this.summary = summary;
	}

	/**
	 * Gets the node.
	 *
	 * @return the node
	 */
	@XmlElement
	public String getNode() {
		return node;
	}

	/**
	 * Sets the node.
	 *
	 * @param node the new node
	 */
	public void setNode(String node) {
		this.node = node;
	}

	/**
	 * Gets the details.
	 *
	 * @return the details
	 */
	@XmlElement
	public String getDetails() {
		return details;
	}

	/**
	 * Sets the details.
	 *
	 * @param details the new details
	 */
	public void setDetails(String details) {
		this.details = details;
	}


	
}
