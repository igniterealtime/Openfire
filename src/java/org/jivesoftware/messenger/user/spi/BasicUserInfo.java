/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger.user.spi;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.StringUtils;
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

    private String username = null;
    private String name = " ";
    private String email;
    private java.util.Date creationDate;
    private java.util.Date modificationDate;

    /**
     * Create a new UserInfo with default fields.
     *
     * @param username the username.
     */
    public BasicUserInfo(String username) {
        this.username = username;
        creationDate = new java.util.Date();
        modificationDate = new java.util.Date();
    }

    /**
     * <p>Create a new UserInfo given field values.</p>
     *
     * @param username     The username
     * @param name         The user's full name
     * @param email        The user's email address
     */
    public BasicUserInfo(String username, String name, String email,
                         java.util.Date creationDate, java.util.Date modificationDate)
    {
        this.username = username;
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
    }

    public String getUsername() {
        return username;
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
        return username.hashCode();
    }

    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object != null && object instanceof User) {
            return username.equals(((User)object).getUsername());
        }
        else {
            return false;
        }
    }
}