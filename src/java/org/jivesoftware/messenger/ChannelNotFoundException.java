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
