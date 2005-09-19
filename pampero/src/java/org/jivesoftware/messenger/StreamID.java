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

package org.jivesoftware.messenger;

/**
 * A unique identifier for a stream.
 *
 * @author Iain Shigeoka
 */
public interface StreamID {

    /**
     * Obtain a unique identifier for easily identifying this stream in
     * a database.
     *
     * @return The unique ID for this stream
     */
    public String getID();
}