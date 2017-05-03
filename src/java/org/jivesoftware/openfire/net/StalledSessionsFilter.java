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

package org.jivesoftware.openfire.net;

import java.io.IOException;
import java.util.Date;

import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MINA filter that will close sessions that are failing to read outgoing traffic
 * and whose outgoing queue is around 5MB. Use the system property <tt>session.stalled.cap</tt>
 * to set the max number of bytes allowed in the outgoing queue of a session before considering
 * it stalled.
 *
 * @author Gaston Dombiak
 */
public class StalledSessionsFilter extends IoFilterAdapter {
	
	private static final Logger Log = LoggerFactory.getLogger(StalledSessionsFilter.class);

    private static final int bytesCap = JiveGlobals.getIntProperty("session.stalled.cap", 5242880);

    @Override
	public void filterWrite(NextFilter nextFilter, IoSession session, WriteRequest writeRequest)
            throws Exception {
        if (!session.isClosing()) {
            // Get number of pending requests
            long pendingBytes = session.getScheduledWriteBytes();
            if (pendingBytes > bytesCap) {
                // Get last time we were able to send something to the connected client
                long writeTime = session.getLastWriteTime();
                int pendingRequests = session.getScheduledWriteMessages();
                Log.debug("About to kill session with pendingBytes: " + pendingBytes + " pendingWrites: " +
                        pendingRequests + " lastWrite: " + new Date(writeTime) + "session: " + session);
                // Close the session and throw an exception
                session.close(false);
                throw new IOException("Closing session that seems to be stalled. Preventing OOM");
            }
        }
        // Call next filter (everything is fine)
        super.filterWrite(nextFilter, session, writeRequest);
    }
}
