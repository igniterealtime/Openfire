/**
 * $RCSfile$
 * $Revision: 655 $
 * $Date: 2004-12-09 21:54:27 -0300 (Thu, 09 Dec 2004) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

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