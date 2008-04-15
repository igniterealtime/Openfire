/**
 * $RCSfile$
 * $Revision: 18746 $
 * $Date: 2005-04-11 16:11:12 -0700 (Mon, 11 Apr 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.xmpp.workgroup;

import java.util.Collection;

/**
 * <p>All workgroup entities have associated, custom properties to extend the basic workgroup capabilities.</p>
 *
 * @author Derek DeMoro
 */
public interface DbProperties {

    /**
     * Returns an extended property of the user. Each user can have an arbitrary number of extended
     * properties. This lets particular skins or filters provide enhanced functionality that is not
     * part of the base interface.
     *
     * @param name the name of the property to get.
     * @return the value of the property
     */
    public String getProperty(String name);

    /**
     * Sets an extended property of the user. Each user can have an arbitrary number of extended
     * properties. This lets particular skins or filters provide enhanced functionality that is not
     * part of the base interface. Property names and values must be valid Strings. If <tt>null</tt>
     * or an empty length String is used, a NullPointerException will be thrown.
     *
     * @param name the name of the property to set.
     * @param value the new value for the property.
     * @throws UnauthorizedException if not allowed to edit.
     */
    public void setProperty(String name, String value) throws UnauthorizedException;

    /**
     * Deletes an extended property. If the property specified by <code>name</code> does not exist,
     * this method will do nothing.
     *
     * @param name the name of the property to delete.
     * @throws UnauthorizedException if not allowed to edit.
     */
    public void deleteProperty(String name) throws UnauthorizedException;

    /**
     * Returns an Iterator for all the names of the extended user properties.
     *
     * @return an Iterator for the property names.
     */
    public Collection<String> getPropertyNames();
}
