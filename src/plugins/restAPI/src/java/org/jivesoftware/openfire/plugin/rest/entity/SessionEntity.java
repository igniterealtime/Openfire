package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.Date;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "session")
@XmlType(propOrder = { "sessionId", "username", "resource", "node", "sessionStatus", "presenceStatus", "presenceMessage", "priority",
        "hostAddress", "hostName", "creationDate", "lastActionDate", "secure" })
public class SessionEntity {

    private String sessionId;
    private String username;
    private String resource;
    private String node;
    private String sessionStatus;
    private String presenceStatus;
    private String presenceMessage;
    private int priority;
    private String hostAddress;
    private String hostName;

    private Date creationDate;
    private Date lastActionDate;

    private boolean secure;

    public SessionEntity() {
    }

    @XmlElement
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @XmlElement
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @XmlElement
    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    @XmlElement
    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    @XmlElement
    public String getSessionStatus() {
        return sessionStatus;
    }

    public void setSessionStatus(String sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    @XmlElement
    public String getPresenceStatus() {
        return presenceStatus;
    }

    public void setPresenceStatus(String presenceStatus) {
        this.presenceStatus = presenceStatus;
    }

    public String getPresenceMessage() {
        return presenceMessage;
    }

    public void setPresenceMessage(String presenceMessage) {
        this.presenceMessage = presenceMessage;
    }

    @XmlElement
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @XmlElement
    public String getHostAddress() {
        return hostAddress;
    }

    public void setHostAddress(String hostAddress) {
        this.hostAddress = hostAddress;
    }

    @XmlElement
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    @XmlElement
    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    @XmlElement
    public Date getLastActionDate() {
        return lastActionDate;
    }

    public void setLastActionDate(Date lastActionDate) {
        this.lastActionDate = lastActionDate;
    }

    @XmlElement
    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

}
