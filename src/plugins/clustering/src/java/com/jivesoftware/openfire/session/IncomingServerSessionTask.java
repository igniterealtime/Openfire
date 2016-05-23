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
import org.jivesoftware.openfire.session.IncomingServerSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Class that defines possible remote operations that could be performed
 * on remote incoming server sessions.
 *
 * @author Gaston Dombiak
 */
public class IncomingServerSessionTask extends RemoteSessionTask {
    private String streamID;

    public IncomingServerSessionTask() {
        super();
    }

    protected IncomingServerSessionTask(Operation operation, String streamID) {
        super(operation);
        this.streamID = streamID;
    }

    Session getSession() {
        return SessionManager.getInstance().getIncomingServerSession(streamID);
    }

    public void run() {
        super.run();

        if (operation == Operation.getLocalDomain) {
            result = ((IncomingServerSession) getSession()).getLocalDomain();
        }
        else if (operation == Operation.getAddress) {
            result = getSession().getAddress();
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSafeUTF(out, streamID);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        streamID = ExternalizableUtil.getInstance().readSafeUTF(in);
    }

    public String toString() {
        return super.toString() + " operation: " + operation + " streamID: " + streamID;
    }
}
