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

package org.jivesoftware.openfire.muc.cluster;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task that will update a service configuring in the cluster node. When a service is update
 * in a cluster node the rest of the cluster nodes will need to reread their configuration
 * from the database.
 *
 * @author Daniel Henninger
 */
public class ServiceUpdatedEvent implements ClusterTask<Void> {
    
    private static final Logger Log = LoggerFactory.getLogger(ServiceUpdatedEvent.class);

    private String subdomain;

    public ServiceUpdatedEvent() {
    }

    public ServiceUpdatedEvent(String subdomain) {
        this.subdomain = subdomain;
    }

    @Override
    public Void getResult() {
        return null;
    }

    @Override
    public void run() {
        MultiUserChatService service = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(subdomain);
        if (service != null) {
            if (service instanceof MultiUserChatServiceImpl) {
                MUCPersistenceManager.refreshProperties(subdomain);
                ((MultiUserChatServiceImpl)service).initializeSettings();
            }
            else {
                // Ok.  We don't handle non default implementations for this.  Why are we seeing it?
            }
        }
        else {
            // Hrm.  We got an update for something that we don't have.
            Log.warn("ServiceUpdatedEvent: Received update for service we are not running: "+subdomain);
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, subdomain);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
