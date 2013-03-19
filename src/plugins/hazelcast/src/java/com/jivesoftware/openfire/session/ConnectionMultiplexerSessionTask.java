/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.openfire.session;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.session.Session;
import org.xmpp.packet.JID;

/**
 * Class that defines possible remote operations that could be performed
 * on remote connection manager sessions.
 *
 * @author Gaston Dombiak
 */
public class ConnectionMultiplexerSessionTask extends RemoteSessionTask {

    private JID address;

    public ConnectionMultiplexerSessionTask() {
    }

    protected ConnectionMultiplexerSessionTask(JID address, Operation operation) {
        super(operation);
        this.address = address;
    }

    Session getSession() {
        return SessionManager.getInstance().getConnectionMultiplexerSession(address);
    }

    public String toString() {
        return super.toString() + " operation: " + operation + " address: " + address;
    }
}
