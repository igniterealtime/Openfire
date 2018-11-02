package org.jivesoftware.openfire.plugin.rest.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * The Class GroupEntity.
 */
@XmlRootElement(name = "group")
@XmlType(propOrder = { "name", "description", "admins", "members" })
public class GroupEntity {

    /** The name. */
    private String name;

    /** The description. */
    private String description;

    /** The admins. */
    private List<String> admins;

    /** The members. */
    private List<String> members;

    /**
     * Instantiates a new group entity.
     */
    public GroupEntity() {
    }

    /**
     * Instantiates a new group entity.
     *
     * @param name
     *            the name
     * @param description
     *            the description
     */
    public GroupEntity(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    @XmlElement
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name
     *            the new name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    @XmlElement
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     *
     * @param description
     *            the new description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the admins.
     *
     * @return the admins
     */
    @XmlElementWrapper(name = "admins")
    @XmlElement(name = "admin")
    public List<String> getAdmins() {
        return admins;
    }

    /**
     * Gets the members.
     *
     * @return the members
     */
    @XmlElementWrapper(name = "members")
    @XmlElement(name = "member")
    public List<String> getMembers() {
        return members;
    }

    /**
     * Sets the admins.
     *
     * @param admins the new admins
     */
    public void setAdmins(List<String> admins) {
        this.admins = admins;
    }

    /**
     * Sets the members.
     *
     * @param members the new members
     */
    public void setMembers(List<String> members) {
        this.members = members;
    }

}
