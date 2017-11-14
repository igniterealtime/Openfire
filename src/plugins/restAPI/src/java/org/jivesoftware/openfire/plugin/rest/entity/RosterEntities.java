package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class RosterEntities.
 */
@XmlRootElement(name = "roster")
public class RosterEntities {

    /** The roster. */
    List<RosterItemEntity> roster;

    /**
     * Instantiates a new roster entities.
     */
    public RosterEntities() {

    }

    /**
     * Instantiates a new roster entities.
     *
     * @param roster
     *            the roster
     */
    public RosterEntities(List<RosterItemEntity> roster) {
        this.roster = roster;
    }

    /**
     * Gets the roster.
     *
     * @return the roster
     */
    @XmlElement(name = "rosterItem")
    public List<RosterItemEntity> getRoster() {
        return roster;
    }

    /**
     * Sets the roster.
     *
     * @param roster
     *            the new roster
     */
    public void setRoster(List<RosterItemEntity> roster) {
        this.roster = roster;
    }

}
