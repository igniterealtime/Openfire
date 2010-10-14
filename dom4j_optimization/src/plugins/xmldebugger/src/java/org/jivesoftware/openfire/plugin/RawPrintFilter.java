/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
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

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;
import org.jivesoftware.util.JiveGlobals;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MINA filter that prints to the stdout received XML stanzas before they are actually parsed and
 * also prints XML stanzas as sent to the XMPP entities. Moreover, it also prints information when
 * a session is closed.
 *
 * @author Gaston Dombiak
 */
public class RawPrintFilter extends IoFilterAdapter {
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
        if (enabled && message instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) message;
            // Keep current position in the buffer
            int currentPos = byteBuffer.position();
            // Decode buffer
            Charset encoder = Charset.forName("UTF-8");
            CharBuffer charBuffer = encoder.decode(byteBuffer.buf());
            // Print buffer content
            System.out.println(prefix + " - RECV (" + session.hashCode() + "): " + charBuffer);
            // Reset to old position in the buffer
            byteBuffer.position(currentPos);
        }
        // Pass the message to the next filter
        super.messageReceived(nextFilter, session, message);
    }

    @Override
	public void messageSent(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        if (enabled) {
            System.out.println(prefix + " - SENT (" + session.hashCode() + "): " +
                    Charset.forName("UTF-8").decode(((ByteBuffer) message).buf()));
        }

        // Pass the message to the next filter
        super.messageSent(nextFilter, session, message);
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

        super.sessionCreated(nextFilter, session);
    }

    @Override
	public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
        // Update list of sessions using this filter
        sessions.remove(session);
        if (enabled) {
            // Print that a session was closed
            System.out.println("CLOSED (" + session.hashCode() + ") ");
        }

        super.sessionClosed(nextFilter, session);
    }
}