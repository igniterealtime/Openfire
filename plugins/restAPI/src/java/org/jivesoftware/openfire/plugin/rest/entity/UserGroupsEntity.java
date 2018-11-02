package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class UserGroupsEntity.
 */
@XmlRootElement(name = "groups")
public class UserGroupsEntity {

    /** The group names. */
    private List<String> groupNames;

    /**
     * Instantiates a new user groups entity.
     */
    public UserGroupsEntity() {

    }

    /**
     * Instantiates a new user groups entity.
     *
     * @param groupNames
     *            the group names
     */
    public UserGroupsEntity(List<String> groupNames) {
        this.groupNames = groupNames;
    }

    /**
     * Gets the group names.
     *
     * @return the group names
     */
    @XmlElement(name = "groupname")
    public List<String> getGroupNames() {
        return groupNames;
    }

    /**
     * Sets the group names.
     *
     * @param groupNames
     *            the new group names
     */
    public void setGroupNames(List<String> groupNames) {
        this.groupNames = groupNames;
    }

}
