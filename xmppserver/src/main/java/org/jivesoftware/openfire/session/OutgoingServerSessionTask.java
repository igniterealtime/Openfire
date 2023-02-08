/*
 * Copyright (C) 2007-2009 Jive Software, 2021-2023 Ignite Realtime Foundation . All rights reserved.
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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.util.cache.ExternalizableUtil;

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
    protected DomainPair domainPair;

    public OutgoingServerSessionTask() {
    }

    protected OutgoingServerSessionTask(DomainPair domainPair, Operation operation) {
        super(operation);
        this.domainPair = domainPair;
    }

    Session getSession() {
        return SessionManager.getInstance().getOutgoingServerSession(domainPair);
    }

    public void run() {
        super.run();
        if (operation == Operation.getOutgoingDomainPairs) {
            result = ((OutgoingServerSession) getSession()).getOutgoingDomainPairs();
        }
        else if (operation == Operation.getAuthenticationMethod) {
            result = ((OutgoingServerSession) getSession()).getAuthenticationMethod();
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        ExternalizableUtil.getInstance().writeSerializable(out, domainPair);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        domainPair = (DomainPair) ExternalizableUtil.getInstance().readSerializable(in);
    }

    public String toString() {
        return super.toString() + " operation: " + operation + " domain pair: " + domainPair;
    }
}
