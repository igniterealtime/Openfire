package org.jivesoftware.openfire.plugin.rest.entity;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class SessionsCount.
 */
@XmlRootElement(name = "sessions")
public class SessionsCount {

    /** The local sessions. */
    private int localSessions;
    
    /** The cluster sessions. */
    private int clusterSessions;
    
    /**
     * The Constructor.
     */
    public SessionsCount() {

    }
    
    /**
     * The Constructor.
     *
     * @param localSessions the local sessions
     * @param clusterSessions the cluster sessions
     */
    public SessionsCount(int localSessions, int clusterSessions) {
        this.localSessions = localSessions;
        this.clusterSessions = clusterSessions;
    }

    /**
     * Gets the local sessions.
     *
     * @return the local sessions
     */
    @XmlElement()
    public int getLocalSessions() {
        return localSessions;
    }

    /**
     * Sets the local sessions.
     *
     * @param localSessions the local sessions
     */
    public void setLocalSessions(int localSessions) {
        this.localSessions = localSessions;
    }

    /**
     * Gets the cluster sessions.
     *
     * @return the cluster sessions
     */
    @XmlElement()
    public int getClusterSessions() {
        return clusterSessions;
    }

    /**
     * Sets the cluster sessions.
     *
     * @param clusterSessions the cluster sessions
     */
    public void setClusterSessions(int clusterSessions) {
        this.clusterSessions = clusterSessions;
    }
}
