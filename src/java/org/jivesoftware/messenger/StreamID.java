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
    public long getID();
}