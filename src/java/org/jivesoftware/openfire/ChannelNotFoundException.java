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
