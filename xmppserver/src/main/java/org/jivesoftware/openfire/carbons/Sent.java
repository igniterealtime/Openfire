/*
 * Copyright (C) 2018 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.carbons;

import org.jivesoftware.openfire.forward.Forwarded;
import org.xmpp.packet.PacketExtension;

/**
 * The implementation of the {@code <sent xmlns="urn:xmpp:carbons:2"/>} extension.
 * It indicates, that a message has been sent by the same user from another resource.
 *
 * @author Christian Schudt
 */
public final class Sent extends PacketExtension {
    public Sent(Forwarded forwarded) {
        super("sent", "urn:xmpp:carbons:2");
        element.add(forwarded.getElement());
    }
}
