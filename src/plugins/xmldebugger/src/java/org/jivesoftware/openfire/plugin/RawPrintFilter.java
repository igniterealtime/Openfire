/*
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.jivesoftware.util.JiveGlobals;

/**
 * MINA filter that prints to the stdout received XML stanzas before they are actually parsed and
 * also prints XML stanzas as sent to the XMPP entities. Moreover, it also prints information when
 * a session is closed.
 *
 * @author Gaston Dombiak
 */
public class RawPrintFilter extends IoFilterAdapter {

    public static final String FILTER_NAME = "rawDebugger";

    private boolean enabled = true;
    private String prefix;
    private Collection<IoSession> sessions = new ConcurrentLinkedQueue<IoSession>();

    public RawPrintFilter(String prefix) {
        this.prefix = prefix;
        this.enabled = JiveGlobals.getBooleanProperty("plugin.xmldebugger." + prefix.toLowerCase(), true);
    }

    @Override
    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        // Decode the bytebuffer and print it to the stdout
        if (enabled && message instanceof IoBuffer) {
            logBuffer(session, (IoBuffer) message, "RECV");
        }
        // Pass the message to the next filter
        super.messageReceived(nextFilter, session, message);
    }

    private void logBuffer(final IoSession session, final IoBuffer ioBuffer, final String receiveOrSend) {
        // Keep current position in the buffer
        int currentPos = ioBuffer.position();
        // Decode buffer
        CharBuffer charBuffer = Charset.forName("UTF-8").decode(ioBuffer.buf());
        // Print buffer content
        System.out.println(messagePrefix(session, receiveOrSend) + ": " + charBuffer);
        // Reset to old position in the buffer
        ioBuffer.position(currentPos);
    }

    private String messagePrefix(final IoSession session, final String messageType) {
        return String.format("%1$s %2$15s - %3$s - (%4$11s)", prefix, session.getRemoteAddress(), messageType, session.hashCode());
    }

    @Override
    public void messageSent(NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        if (enabled && writeRequest.getMessage() instanceof IoBuffer) {
            logBuffer(session, (IoBuffer) writeRequest.getMessage(), "SENT");
        }
        // Pass the message to the next filter
        super.messageSent(nextFilter, session, writeRequest);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("plugin.xmldebugger." + prefix.toLowerCase(), Boolean.toString(enabled)); 
    }

    public void shutdown() {
        // Remove this filter from sessions that are using it
        for (IoSession session : sessions) {
            session.getFilterChain().remove("rawDebugger");
        }
        sessions = null;
    }

    @Override
    public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
        // Keep track of sessions using this filter
        sessions.add(session);
        if (enabled) {
            // Print that a session was closed
            System.out.println(messagePrefix(session, "OPEN"));
        }
        super.sessionCreated(nextFilter, session);
    }

    @Override
    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
        // Update list of sessions using this filter
        sessions.remove(session);
        if (enabled) {
            // Print that a session was closed
            System.out.println(messagePrefix(session, "CLSD"));
        }
        super.sessionClosed(nextFilter, session);
    }
}
