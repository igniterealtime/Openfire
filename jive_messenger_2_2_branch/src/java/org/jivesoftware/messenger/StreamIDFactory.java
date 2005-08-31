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
