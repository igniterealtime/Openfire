/**
 * $RCSfile: $
 * $Revision: $
 * $Date: $
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

 package org.jivesoftware.openfire.session;


import java.util.*;

import org.dom4j.Element;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.net.SSLConfig;
import org.jivesoftware.openfire.net.SocketConnection;
import org.jivesoftware.openfire.server.ServerDialback;
import org.jivesoftware.util.CertificateManager;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

import org.jivesoftware.openfire.session.LocalIncomingServerSession;


public class HubServerSession extends LocalIncomingServerSession {

	private static Logger Log = LoggerFactory.getLogger("HubServerSession");

    public HubServerSession(String serverName, Connection connection, StreamID streamID)
    {
       	super(serverName, connection, streamID, serverName);
        usingServerDialback = false;
    }

    @Override boolean canProcess(Packet packet) {
        return true;
    }


    @Override void deliver(Packet packet) throws UnauthorizedException {

    }
}
