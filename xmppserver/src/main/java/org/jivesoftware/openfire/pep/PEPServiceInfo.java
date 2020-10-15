/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
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
 *
 */

package org.jivesoftware.openfire.pep;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.pubsub.PubSubServiceInfo;
import org.xmpp.packet.JID;

/**
 * A PubSubService manager that is specific to the implemenation of XEP-163: Personal Eventing Protocol.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class PEPServiceInfo extends PubSubServiceInfo
{
    public PEPServiceInfo( JID owner )
    {
        super( XMPPServer.getInstance().getIQPEPHandler().getServiceManager().getPEPService( owner.asBareJID() ) );
    }
}
