/**
 * $RCSfile$
 * $Revision: 128 $
 * $Date: 2004-10-25 20:42:00 -0300 (Mon, 25 Oct 2004) $
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire;

/**
 * Generates stream ids in different ways depending on the server set up.
 *
 * @author Iain Shigeoka
 */
public interface StreamIDFactory {

    /**
     * Generate a stream id.
     *
     * @return A new, unique stream id
     */
    public StreamID createStreamID();
}
