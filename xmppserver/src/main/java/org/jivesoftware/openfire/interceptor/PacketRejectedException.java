/*
 * Copyright (C) 2004-2008 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.interceptor;

import org.xmpp.packet.PacketError;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Thrown by a PacketInterceptor when a stanza is prevented from being processed.
 *
 * If the stanza was received then it will not be processed. For IQ and Presence stanzas, an error will be sent back to
 * the sender. When the PacketRejectedException contains a rejection message, a Message stanza will be sent back to the
 * sender (for IQ and Presence stanzas, this is sent in addition to the IQ or Presence stanza containing the error).
 *
 * If the stanza was going to be sent then the sending will be aborted.
 *
 * @see PacketInterceptor
 * @author Gaston Dombiak
 */
public class PacketRejectedException extends Exception {
    private static final long serialVersionUID = 2L;

    private Throwable nestedThrowable = null;

    /**
     * Text to include in a message that will be sent to the sender of the packet that got
     * rejected. If no text is specified then no message will be sent to the user.
     */
    private String rejectionMessage;

    /**
     * The packet error to include in the stanza that will be sent to the sender of the stanza that got rejected.
     */
    private PacketError rejectionError;

    public PacketRejectedException() {
        super();
    }

    public PacketRejectedException(String msg) {
        super(msg);
    }

    public PacketRejectedException(Throwable nestedThrowable) {
        this.nestedThrowable = nestedThrowable;
    }

    public PacketRejectedException(String msg, Throwable nestedThrowable) {
        super(msg);
        this.nestedThrowable = nestedThrowable;
    }

    @Override
    public void printStackTrace() {
        super.printStackTrace();
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace();
        }
    }

    @Override
    public void printStackTrace(PrintStream ps) {
        super.printStackTrace(ps);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(ps);
        }
    }

    @Override
    public void printStackTrace(PrintWriter pw) {
        super.printStackTrace(pw);
        if (nestedThrowable != null) {
            nestedThrowable.printStackTrace(pw);
        }
    }

    /**
     * Returns the text to include in a message that will be sent to the sender of the packet
     * that got rejected or {@code null} if none was defined. If no text was specified then
     * no message will be sent to the sender of the rejected packet.
     *
     * @return the text to include in a message that will be sent to the sender of the packet
     *         that got rejected or {@code null} if none was defined.
     */
    public String getRejectionMessage() {
        return rejectionMessage;
    }

    /**
     * Sets the text to include in a message that will be sent to the sender of the packet
     * that got rejected or {@code null} if no message will be sent to the sender of the
     * rejected packet. By default, no message will be sent.
     *
     * @param rejectionMessage the text to include in the notification message for the rejection.
     */
    public void setRejectionMessage(String rejectionMessage) {
        this.rejectionMessage = rejectionMessage;
    }

    /**
     * Gets the error that will be sent to the sender of the inbound stanza that got rejected.
     *
     * When the rejected stanza was a message, this error is added to a message that is sent (possibly also including
     * the message body as specified by {@link #setRejectionMessage(String)}). When the rejected stanza was an IQ or
     * Presence stanza, then the error is used in the error response. For these stanzas, this is a different stanza than
     * the optional Message stanza that is sent when {@link #setRejectionMessage(String)}) is (also) used.
     *
     * @return An optional error to be included in an error response in reaction to the rejection.
     */
    public PacketError getRejectionError()
    {
        return rejectionError;
    }

    /**
     * Adds an error that will be sent to the sender of the inbound stanza that got rejected.
     *
     * When the rejected stanza was a message, this error is added to a message that is sent (possibly also including
     * the message body as specified by {@link #setRejectionMessage(String)}). When the rejected stanza was an IQ or
     * Presence stanza, then the error is used in the error response. For these stanzas, this is a different stanza than
     * the optional Message stanza that is sent when {@link #setRejectionMessage(String)}) is (also) used.
     *
     * @param rejectionError An optional error.
     */
    public void setRejectionError(PacketError rejectionError)
    {
        this.rejectionError = rejectionError;
    }
}
