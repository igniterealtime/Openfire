/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.messenger.auth.UnauthorizedException;
import org.jivesoftware.messenger.user.User;
import org.jivesoftware.messenger.user.UserInfo;

/**
 * <p>The simplest implementation of UserInfo.</p>
 * <p>Useful if you are retrieving user info from a database and just need
 * to stuff it into a data structure.</p>
 *
 * @author Iain Shigeoka
 * @see User
 */
public class BasicUserInfo implements UserInfo {

    /**
     * User id of -2 means no user id has been set yet. -1 is reserved for "anonymous user" and 0 is
     * reserved for "all users".
     */
    private long id = -2;
    private String name = " ";
    private boolean nameVisible = true;
    private String email;
    private boolean emailVisible = false; // Hide email addresses by default
    private java.util.Date creationDate;
    private java.util.Date modificationDate;

    /**
     * <p>Create a new UserInfo with default fields.</p>
     *
     * @param id The user's id this info belongs to
     */
    public BasicUserInfo(long id) {
        this.id = id;
        creationDate = new java.util.Date();
        modificationDate = new java.util.Date();
    }

    /**
     * <p>Create a new UserInfo given field values.</p>
     *
     * @param id           The user's id this info belongs to
     * @param name         The user's full name
     * @param email        The user's email address
     * @param nameVisible  True if the user's name should be visible to other users of the system
     * @param emailVisible True if the user's email should be visible to other users of the system
     */
    public BasicUserInfo(long id, String name, String email,
                         boolean nameVisible, boolean emailVisible, java.util.Date creationDate, java.util.Date modificationDate) {
        this.id = id;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        if (email == null || "".equals(email)) {
            this.email = " ";
        }
        else {
            this.email = email;
        }
        if (name != null) {
            this.name = name;
        }
        this.nameVisible = nameVisible;
        this.emailVisible = emailVisible;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) throws UnauthorizedException {
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null");
        }

        this.name = name;
        // Update modification date
        modificationDate.setTime(System.currentTimeMillis());
    }

    public boolean isNameVisible() {
        return nameVisible;
    }

    public void setNameVisible(boolean visible) throws UnauthorizedException {
        nameVisible = visible;
        // Update modification date
        modificationDate.setTime(System.currentTimeMillis());
    }

    public String getEmail() {
        return StringUtils.escapeHTMLTags(email);
    }

    public void setEmail(String email) throws UnauthorizedException {
        if (email == null || "".equals(email)) {
            email = " ";
        }

        this.email = email;
        // Update modification date
        modificationDate.setTime(System.currentTimeMillis());
    }

    public boolean isEmailVisible() {
        return emailVisible;
    }

    public void setEmailVisible(boolean visible) throws UnauthorizedException {
        emailVisible = visible;
        // Update modification date
        modificationDate.setTime(System.currentTimeMillis());
    }

    public java.util.Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(java.util.Date creationDate) throws UnauthorizedException {
        if (creationDate == null) {
            throw new IllegalArgumentException("Creation date cannot be null");
        }

        this.creationDate.setTime(creationDate.getTime());
    }

    public java.util.Date getModificationDate() {
        return modificationDate;
    }

    public void setModificationDate(java.util.Date modificationDate) throws UnauthorizedException {
        if (modificationDate == null) {
            throw new IllegalArgumentException("Modification date cannot be null");
        }

        this.modificationDate.setTime(modificationDate.getTime());
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfLong();                // id
        size += CacheSizes.sizeOfString(name);          // name
        size += CacheSizes.sizeOfString(email);         // email
        size += CacheSizes.sizeOfBoolean();             // nameVisible
        size += CacheSizes.sizeOfBoolean();             // emailVisible
        size += CacheSizes.sizeOfDate();                // creationDate
        size += CacheSizes.sizeOfDate();                // modificationDate

        return size;
    }

    /**
     * Returns a String representation of the User object using the username.
     *
     * @return a String representation of the User object.
     */
    public String toString() {
        return name;
    }

    public int hashCode() {
        return (int)id;
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof User) {
            return id == ((User)object).getID();
        }
        else {
            return false;
        }
    }
}
