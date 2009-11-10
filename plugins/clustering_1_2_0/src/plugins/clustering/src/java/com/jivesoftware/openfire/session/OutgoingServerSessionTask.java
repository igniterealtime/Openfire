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
import org.jivesoftware.openfire.session.OutgoingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Class that defines possible remote operations that could be performed
 * on remote outgoing server sessions.
 *
 * @author Gaston Dombiak
 */
public class OutgoingServerSessionTask extends RemoteSessionTask {
    private JID address;

    public OutgoingServerSessionTask() {
    }

    protected OutgoingServerSessionTask(JID address, Operation operation) {
        super(operation);
        this.address = address;
    }

    Session getSession() {
        return SessionManager.getInstance().getOutgoingServerSession(address.getDomain());
    }

    public void run() {
        super.run();
        if (operation == Operation.getAuthenticatedDomains) {
            result = ((OutgoingServerSession) getSession()).getAuthenticatedDomains();
        }
        else if (operation == Operation.getHostnames) {
            result = ((OutgoingServerSession) getSession()).getHostnames();
        }
        else if (operation == Operation.isUsingServerDialback) {
            result = ((OutgoingServerSession) getSession()).isUsingServerDialback();
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, address.toString());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        address = new JID(ExternalizableUtil.getInstance().readSafeUTF(in));
    }

    public String toString() {
        return super.toString() + " operation: " + operation + " address: " + address;
    }
}
