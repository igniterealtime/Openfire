/**
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.xmpp.component;

import org.xmpp.packet.StreamError;

/**
 * Thrown when an exception occors with a Component.
 *
 * @author Matt Tucker
 */
public class ComponentException extends Exception {

    private StreamError streamError;

    public ComponentException() {
        super();
    }

    public ComponentException(String message) {
        super(message);
    }

    public ComponentException(String message, Throwable cause) {
        super(message, cause);
    }

    public ComponentException(Throwable cause) {
        super(cause);
    }

    public ComponentException(String message, StreamError streamError) {
        super(message);
        this.streamError = streamError;
    }

    public ComponentException(StreamError streamError) {
        super(streamError.getCondition().toXMPP());
        this.streamError = streamError;
    }

    public StreamError getStreamError() {
        return streamError;
    }
}
