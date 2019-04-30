/*
 * Copyright (C) 2015 Tom Evans. All rights reserved.
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
package org.jivesoftware.openfire.websocket;

import org.dom4j.Element;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.multiplex.UnknownStanzaException;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.util.JiveGlobals;

/**
 * This class extends Openfire's session packet router with the ACK capabilities
 * specified by XEP-0198: Stream Management.
 * 
 * NOTE: This class does NOT support the XEP-0198 stream resumption capabilities.
 * 
 * XEP-0198 allows either party (client or server) to send unsolicited ack/answer
 * stanzas on a periodic basis. This implementation approximates BOSH ack behavior
 * by sending unsolicited &lt;a /&gt; stanzas from the server to the client after a
 * configurable number of stanzas have been received from the client. 
 * 
 * Setting the system property to "1" would indicate that each client packet should 
 * be ack'd by the server when stream management is enabled for a particular stream. 
 * To disable unsolicited server acks, use the default value for system property
 * "stream.management.unsolicitedAckFrequency" ("0"). This setting does not affect
 * server responses to explicit ack requests from the client.
 */
public class StreamManagementPacketRouter extends SessionPacketRouter {

    public static final String SM_UNSOLICITED_ACK_FREQUENCY = "stream.management.unsolicitedAckFrequency";
    static {
        JiveGlobals.migrateProperty(SM_UNSOLICITED_ACK_FREQUENCY);
    }

    private int unsolicitedAckFrequency = JiveGlobals.getIntProperty(SM_UNSOLICITED_ACK_FREQUENCY, 0);

    public StreamManagementPacketRouter(LocalClientSession session) {
        super(session);
    }

    @Override
    public void route(Element wrappedElement) throws UnknownStanzaException {

        if (StreamManager.NAMESPACE_V3.equals(wrappedElement.getNamespace().getStringValue())) {
            session.getStreamManager().process( wrappedElement );
        } else {
            super.route(wrappedElement);
            if (isUnsolicitedAckExpected()) {
                session.getStreamManager().sendServerAcknowledgement();
            }
        }
    }

    private boolean isUnsolicitedAckExpected() {
        if (!session.getStreamManager().isEnabled()) {
            return false;
        }
        return unsolicitedAckFrequency > 0 && session.getNumClientPackets() % unsolicitedAckFrequency == 0;
    }

}
