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

package org.jivesoftware.database;

/**
 * An interface for loading Jive database objects.
 *
 * @author Jive Software
 */
public interface DatabaseObjectFactory {

    /**
     * Returns the object associated with <code>id</code> or null if the object could not be loaded.
     *
     * @param id the id of the object to load.
     * @return the object specified by <code>id</code> or null if it could not be loaded.
     */
    public Object loadObject(long id);
}
