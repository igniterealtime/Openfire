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
 * Thrown when a channel lookup fails to find the specified channel.
 *
 * @author Matt Tucker
 */
public class ChannelNotFoundException extends RuntimeException {

    public ChannelNotFoundException() {
        super();
    }

    public ChannelNotFoundException(String msg) {
        super(msg);
    }
}
