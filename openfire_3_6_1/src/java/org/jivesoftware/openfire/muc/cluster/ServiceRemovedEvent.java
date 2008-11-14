/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will remove a service from the cluster node. When a service is destroyed
 * in a cluster node the rest of the cluster nodes will need to destroy their copy.
 *
 * @author Daniel Henninger
 */
public class ServiceRemovedEvent implements ClusterTask {
    private String subdomain;

    public ServiceRemovedEvent() {
    }

    public ServiceRemovedEvent(String subdomain) {
        this.subdomain = subdomain;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        XMPPServer.getInstance().getMultiUserChatManager().unregisterMultiUserChatService(subdomain);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, subdomain);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
