/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.openfire.muc.spi.MUCPersistenceManager;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.jivesoftware.util.Log;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will update a service configuring in the cluster node. When a service is update
 * in a cluster node the rest of the cluster nodes will need to reread their configuration
 * from the database.
 *
 * @author Daniel Henninger
 */
public class ServiceUpdatedEvent implements ClusterTask {
    private String subdomain;

    public ServiceUpdatedEvent() {
    }

    public ServiceUpdatedEvent(String subdomain) {
        this.subdomain = subdomain;
    }

    public Object getResult() {
        return null;
    }

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

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, subdomain);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
