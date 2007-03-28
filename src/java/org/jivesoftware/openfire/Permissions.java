/**
 * $RCSfile$
 * $Revision: 691 $
 * $Date: 2004-12-13 15:06:54 -0300 (Mon, 13 Dec 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

import org.jivesoftware.util.CacheSizes;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.util.Cacheable;

/**
 * Represents a set of permissions that an entity has for an object in the system. For example,
 * the rights that a user has for a category. Permissions are used by the protection proxy objects
 * defined for each major component of the system to provide access rights.<p>
 * <p/>
 * A Permissions object is internally represented as a long with each bit indicating whether
 * a particular permission is set. The constants defined by extensions of this class define the bit
 * masks that can be used for permission operations. For example, the following code creates
 * permissions:<pre>
 * <p/>
 * // Create a permissions object with only read permissions set to true.
 * Permissions perms1 = new Permissions(ForumPermissions.READ_FORUM);
 * // Create a permissions object with read and system admin permissions set to true.
 * Permissions perms2 = new Permissions(ForumPermissions.READ_FORUM |
 *          ForumPermissions.SYSTEM_ADMIN);</pre>
 * <p/>
 * If we were to view the bits of each variable, <tt>perms1</tt> would be
 * <tt>0000000000000000000000000000000000000000000000000000000000000001</tt> and
 * <tt>perms2</tt> would be
 * <tt>0000000000000000000000000000000010000000000000000000000000000001</tt>.<p>
 *
 * @author Matt Tucker
 */
public class Permissions implements Cacheable {

    /**
     * No permissions.
     */
    public static final long NONE = 0x000000000000000L;

    /**
     * Permission to see the online status of a particular user.
     */
    public static final long VIEW_ONLINE_STATUS = 0x100000000000000L;

    /**
     * Permission to administer a particular user.
     */
    public static final long USER_ADMIN = 0x200000000000000L;

    /**
     * Permission to administer a particular group.
     */
    public static final long GROUP_ADMIN = 0x400000000000000L;

    /**
     * Permission to administer the entire sytem.
     */
    public static final long SYSTEM_ADMIN = 0x800000000000000L;

    /**
     * A long holding permission values. We use the bits in the number to extract up to 63
     * different permissions.
     */
    private long permissions;

    /**
     * Create a new permissions object with the specified permissions.
     *
     * @param permissions integer bitmask values to for the new Permissions.
     */
    public Permissions(long permissions) {
        this.permissions = permissions;
    }

    /**
     * Creates a new ForumPermission object by combining two permissions
     * objects. The higher permission of each permission type will be used.
     *
     * @param permissions1 the first permissions to use when creating the new Permissions.
     * @param permissions2 the second permissions to use when creating the new Permissions.
     */
    public Permissions(Permissions permissions1, Permissions permissions2) {
        permissions = permissions1.permissions | permissions2.permissions;
    }

    /**
     * Returns true if one or more of the permission types is set to true.
     *
     * @param permissionTypes
     * @return true if one or more of the permission types is set to true, false otherwise.
     */
    public boolean hasPermission(long permissionTypes) {
        return (permissions & permissionTypes) != 0;
    }

    /**
     * Sets the permissions given by a bit mask to true or false. For example, the following
     * would set the READ_FORUM and SYSTEM_ADMIN permissions to true:
     * <p/>
     * <pre>
     * permissions.set(ForumPermissions.READ_FORUM | ForumPermissions.SYSTEM_ADMIN, true);
     * </pre>
     *
     * @param permissionTypes the permission types to set.
     * @param value           true to enable the permission, false to disable.
     */
    public void set(long permissionTypes, boolean value) {
        if (value) {
            permissions = permissions | permissionTypes;
        }
        else {
            permissionTypes = ~permissionTypes;
            permissions = permissions & permissionTypes;
        }
    }

    /**
     * Returns the long value (bitmask) of the permissions that are set.
     *
     * @return the long value of the object.
     */
    public long value() {
        return permissions;
    }

    public String toString() {
        return StringUtils.zeroPadString(Long.toBinaryString(permissions), 63);
    }

    // Cacheable Interface

    public int getCachedSize() {
        // Approximate the size of the object in bytes by calculating the size
        // of each field.
        int size = 0;
        size += CacheSizes.sizeOfObject();                 // overhead of object
        size += CacheSizes.sizeOfLong();                   // permissions bits
        return size;
    }
}