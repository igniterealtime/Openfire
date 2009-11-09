/**
 * $RCSfile$
 * $Revision: 1530 $
 * $Date: 2005-06-17 18:38:27 -0300 (Fri, 17 Jun 2005) $
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

package org.jivesoftware.openfire.server;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An OutgoingServerSocketReader is responsible for reading and queueing the DOM Element sent by
 * a remote server. Since the DOM Elements are received using the outgoing connection only special
 * stanzas may be sent by the remote server (eg. db:result stanzas for answering if the
 * Authoritative Server verified the key sent by this server).<p>
 *
 * This class is also responsible for closing the outgoing connection if the remote server sent
 * an end of the stream element.
 *
 * @author Gaston Dombiak
 */
public class OutgoingServerSocketReader {

	private static final Logger Log = LoggerFactory.getLogger(OutgoingServerSocketReader.class);

    private OutgoingServerSession session;
    private boolean open = true;
    private XMPPPacketReader reader = null;
    /**
     * Queue that holds the elements read by the XMPPPacketReader.
     */
    private BlockingQueue<Element> elements = new LinkedBlockingQueue<Element>(10000);

    public OutgoingServerSocketReader(XMPPPacketReader reader) {
        this.reader = reader;
        init();
    }

    /**
     * Returns the OutgoingServerSession for which this reader is working for or <tt>null</tt> if
     * a OutgoingServerSession was not created yet. While the OutgoingServerSession is being
     * created it is possible to have a reader with no session.
     *
     * @return the OutgoingServerSession for which this reader is working for or <tt>null</tt> if
     *         a OutgoingServerSession was not created yet.
     */
    public OutgoingServerSession getSession() {
        return session;
    }

    /**
     * Sets the OutgoingServerSession for which this reader is working for.
     *
     * @param session the OutgoingServerSession for which this reader is working for
     */
    public void setSession(OutgoingServerSession session) {
        this.session = session;
    }

    /**
     * Retrieves and removes the first received element that was stored in the queue, waiting
     * if necessary up to the specified wait time if no elements are present on this queue.
     *
     * @param timeout how long to wait before giving up, in units of <tt>unit</tt>.
     * @param unit a <tt>TimeUnit</tt> determining how to interpret the <tt>timeout</tt> parameter.
     * @return the head of this queue, or <tt>null</tt> if the specified waiting time elapses
     *         before an element is present.
     * @throws InterruptedException if interrupted while waiting.
     */
    public Element getElement(long timeout, TimeUnit unit) throws InterruptedException {
        return elements.poll(timeout, unit);
    }

    private void init() {
        // Create a thread that will read and store DOM Elements.
        Thread thread = new Thread("Outgoing Server Reader") {
            public void run() {
                while (open) {
                    Element doc;
                    try {
                        doc = reader.parseDocument().getRootElement();

                        if (doc == null) {
                            // Stop reading the stream since the remote server has sent an end of
                            // stream element and probably closed the connection.
                            closeSession();
                        }
                        else {
                            elements.add(doc);
                        }
                    }
                    catch (IOException e) {
                        String message = "Finishing Outgoing Server Reader. ";
                        if (session != null) {
                            message = message + "Closing session: " + session.toString();
                        }
                        else {
                            message = message + "No session to close.";
                        }
                        Log.debug("OutgoingServerSocketReader: "+message, e);
                        closeSession();
                    }
                    catch (Exception e) {
                        String message = "Finishing Outgoing Server Reader. ";
                        if (session != null) {
                            message = message + "Closing session: " + session.toString();
                        }
                        else {
                            message = message + "No session to close.";
                        }
                        Log.error(message, e);
                        closeSession();
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void closeSession() {
        open = false;
        if (session != null) {
            session.close();
        }
    }
}
