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
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.openfire.event.SessionEventDispatcher;
import org.xmpp.packet.IQ;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * A SoftwareVersionManager is the main responsible for sending query to remote entity and 
 * Obtain software information from the remote entity using XEP-0092 .
 * @author Manasse Ngudia
 */
public class SoftwareVersionManager extends BasicModule implements SessionEventListener {
    private static final Logger Log = LoggerFactory.getLogger(SoftwareVersionManager.class);

    public static final SystemProperty<Boolean> VERSION_QUERY_ENABLED = SystemProperty.Builder.ofType( Boolean.class )
        .setKey("xmpp.client.version-query.enabled")
        .setDefaultValue(true)
        .setDynamic(true)
        .build();

    public static final SystemProperty<Duration> VERSION_QUERY_DELAY = SystemProperty.Builder.ofType( Duration.class )
        .setKey("xmpp.client.version-query.delay")
        .setChronoUnit(ChronoUnit.MILLIS)
        .setDefaultValue(Duration.ofSeconds(5))
        .setDynamic(true)
        .build();

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
        if (!VERSION_QUERY_ENABLED.getValue()) {
            return;
        }
        // The server should not send requests to the client before the client session
        // has been established (see IQSessionEstablishmentHandler). Sadly, Openfire
        // does not provide a hook for this. For now, the resource bound event is
        // used instead (which should be immediately followed by session establishment).
        TaskEngine.getInstance().submit( () -> {
            try {
                Thread.sleep( VERSION_QUERY_DELAY.getValue().toMillis() ); // Let time pass for the session establishment to have occurred.

                if (session.isClosed()){
                    return;
                }

                IQ versionRequest = new IQ(IQ.Type.get);
                versionRequest.setTo(session.getAddress());
                versionRequest.setFrom(session.getServerName());
                versionRequest.setChildElement("query", "jabber:iq:version");
                session.process(versionRequest);
            } catch (Exception e) {
                Log.error("Exception while trying to query a client ({}) for its software version.", session.getAddress(), e);
            }
        } );
    }
}
