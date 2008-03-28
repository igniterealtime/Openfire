/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.plugin;

import org.apache.mina.common.ByteBuffer;
import org.apache.mina.common.IoFilterAdapter;
import org.apache.mina.common.IoSession;

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
    private String prefix;
    private Collection<IoSession> sessions = new ConcurrentLinkedQueue<IoSession>();

    public RawPrintFilter(String prefix) {
        this.prefix = prefix;
    }

    public void messageReceived(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        // Decode the bytebuffer and print it to the stdout
        if (message instanceof ByteBuffer) {
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

    public void messageSent(NextFilter nextFilter, IoSession session, Object message) throws Exception {
        System.out.println(prefix + " - SENT (" + session.hashCode() + "): " +
                Charset.forName("UTF-8").decode(((ByteBuffer) message).buf()));

        // Pass the message to the next filter
        super.messageSent(nextFilter, session, message);
    }


    public void shutdown() {
        // Remove this filter from sessions that are using it
        for (IoSession session : sessions) {
            session.getFilterChain().remove("rawDebugger");
        }
        sessions = null;
    }

    public void sessionCreated(NextFilter nextFilter, IoSession session) throws Exception {
        // Keep track of sessions using this filter
        sessions.add(session);

        super.sessionCreated(nextFilter, session);
    }

    public void sessionClosed(NextFilter nextFilter, IoSession session) throws Exception {
        // Update list of sessions using this filter
        sessions.remove(session);
        // Print that a session was closed
        System.out.println("CLOSED (" + session.hashCode() + ") ");

        super.sessionClosed(nextFilter, session);
    }
}