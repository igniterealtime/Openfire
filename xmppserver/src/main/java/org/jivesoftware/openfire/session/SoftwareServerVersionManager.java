/*
 * Copyright (C) 2019 Ignite Realtime Foundation. All rights reserved.
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
import org.jivesoftware.openfire.event.ServerSessionEventListener;
import org.jivesoftware.openfire.event.ServerSessionEventDispatcher;
import org.xmpp.packet.IQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A SoftwareServerVersionManager is the main responsible for sending query to remote entity and 
 * Obtain software information from the remote entity server using XEP-0092 .
 * @author Manasse Ngudia manasse@mnsuccess.com
 */
public class SoftwareServerVersionManager extends BasicModule implements ServerSessionEventListener {
    private static final Logger Log = LoggerFactory.getLogger(SoftwareServerVersionManager.class);

    public SoftwareServerVersionManager() {
        super("Software Server Version Manager");
    }

    @Override
    public void start() throws IllegalStateException {
        super.start();
        ServerSessionEventDispatcher.addListener(this);
    }

    @Override
    public void stop() {
        super.stop();
        ServerSessionEventDispatcher.removeListener(this);
    }

    @Override
    public void sessionCreated(Session session) {
        try {
            IQ versionRequest = new IQ(IQ.Type.get);
            versionRequest.setTo(session.getAddress());
            versionRequest.setFrom(session.getServerName());
            versionRequest.setChildElement("query", "jabber:iq:version");
            session.process(versionRequest);
        } catch (Exception e) {
            Log.error("Exception while trying to query a server for its software version.", e);;
        }
       
    }

    @Override
    public void sessionDestroyed(Session session) {

    }

    

}
