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
