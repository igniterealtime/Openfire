/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.lockout;

import org.jivesoftware.util.cache.Cacheable;
import org.jivesoftware.util.cache.CacheSizes;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.util.Date;
import java.io.Externalizable;
import java.io.ObjectOutput;
import java.io.IOException;
import java.io.ObjectInput;

/**
 * A LockOutFlag represents the current disabled status set on a particular user account.
 * It can have a start and end time (a period of time in which the disabled status is active).
 *
 * @author Daniel Henninger
 */
public class LockOutFlag implements Cacheable, Externalizable {

    private String username;
    private Date startTime = null;
    private Date endTime = null;

    /**
     * Constructor added for Externalizable. Do not use this constructor.
     */
    public LockOutFlag() {

    }

    /**
     * Creates a representation of a lock out flag, including which user it is attached to
     * and an optional start and end time.
     *
     * @param username User the flag is attached to.
     * @param startTime Optional start time for the disabled status to start.
     * @param endTime Optional end time for the disabled status to end.
     */
    public LockOutFlag(String username, Date startTime, Date endTime) {
        this.username = username;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Retrieves the username that this lock out flag is attached to.
     *
     * @return Username the flag is attached to.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Retrieves the date/time at which the account this flag references will begin having a disabled status.
     * This can be null if there is no start time set, meaning that the start time is immediate.
     *
     * @return The Date when the disabled status will start, or null for immediately.
     */
    public Date getStartTime() {
        return startTime;
    }

    /**
     * Sets the start time for when the account will be disabled, or null if immediate.
     *
     * @param startTime Date when the disabled status will start, or null for immediately.
     */
    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    /**
     * Retrieves the date/time at which the account this flag references will stop having a disabled status.
     * This can be null if the disabled status is to continue until manually disabled.
     *
     * @return The Date when the disabled status will end, or null for "forever".
     */
    public Date getEndTime() {
        return endTime;
    }

    /**
     * Sets the end time for when the account will be reenabled, or null if manual reenable required.
     *
     * @param endTime Date when the disabled status will end, or null for forever.
     */
    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();              // overhead of object
        size += CacheSizes.sizeOfString(username);
        size += CacheSizes.sizeOfDate();
        size += CacheSizes.sizeOfDate();

        return size;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, username);
        ExternalizableUtil.getInstance().writeLong(out, startTime != null ? startTime.getTime() : -1);
        ExternalizableUtil.getInstance().writeLong(out, endTime != null ? endTime.getTime() : -1);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        username = ExternalizableUtil.getInstance().readSafeUTF(in);
        long st = ExternalizableUtil.getInstance().readLong(in);
        startTime = st != -1 ? new Date(st) : null;
        long et = ExternalizableUtil.getInstance().readLong(in);
        endTime = et != -1 ? new Date(et) : null;
    }
}
