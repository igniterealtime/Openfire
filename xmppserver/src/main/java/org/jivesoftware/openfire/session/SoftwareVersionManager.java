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

package org.jivesoftware.openfire.session;

import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.event.SessionEventListener;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.xmpp.packet.IQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dom4j.DocumentHelper;
import org.dom4j.QName;

/**
 * A SoftwareVersionManager is the main responsible for sending query to remote entity and 
 * Obtain software information from the remote entity using XEP-0092 .
 * @author Manasse Ngudia
 */
public class SoftwareVersionManager extends BasicModule implements SessionEventListener {
    private static final Logger Log = LoggerFactory.getLogger(SoftwareVersionManager.class);

    public SoftwareVersionManager() {
        super("Software Version Manager");
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
        SessionEventDispatcher.addListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        SessionEventDispatcher.removeListener(this);
    }

    @Override
    public void sessionCreated(Session session) {

    }

    @Override
    public void sessionDestroyed(Session session) {

    }

    @Override
    public void anonymousSessionCreated(Session session) {

    }

    @Override
    public void anonymousSessionDestroyed(Session session) {

    }

    @Override
    public void resourceBound(Session session) {
        try {
            IQ reply = new IQ(IQ.Type.get);
            reply.setID(session.getStreamID().getID());
            reply.setTo(session.getAddress());
            reply.setFrom(session.getServerName());
            reply.setChildElement(DocumentHelper.createElement(QName.get("query", "jabber:iq:version")));
            session.process(reply);
        } catch (Exception e) {
            Log.error(e.getMessage(), e);;
        }
        
    }


}