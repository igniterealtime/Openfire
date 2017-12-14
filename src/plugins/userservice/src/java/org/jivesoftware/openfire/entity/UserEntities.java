package org.jivesoftware.openfire.entity;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * The Class UserEntities.
 */
@XmlRootElement(name = "users")
public class UserEntities {

    /** The users. */
    List<UserEntity> users;

    /**
     * Instantiates a new user entities.
     */
    public UserEntities() {

    }

    /**
     * Instantiates a new user entities.
     *
     * @param users
     *            the users
     */
    public UserEntities(List<UserEntity> users) {
        this.users = users;
    }

    /**
     * Gets the users.
     *
     * @return the users
     */
    @XmlElement(name = "user")
    public List<UserEntity> getUsers() {
        return users;
    }

    /**
     * Sets the users.
     *
     * @param users
     *            the new users
     */
    public void setUsers(List<UserEntity> users) {
        this.users = users;
    }

}
