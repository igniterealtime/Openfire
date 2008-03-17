/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.muc.cluster;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.util.cache.ClusterTask;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Task to be requested by a node that joins a cluster and be executed in the senior cluster member to get
 * the services with rooms with occupants. The list of services with rooms with occupants is returned to
 * the new cluster node so that the new cluster node can be updated and have the same information shared
 * by the cluster.<p>
 *
 * Moreover, each existing cluster node will also need to learn the services and rooms with occupants that
 * exist in the new cluster node and replicate them. This work is accomplished using
 * {@link GetNewMemberRoomsRequest} on each service.
 *
 * @author Daniel Henninger
 */
public class SeniorMemberServicesRequest implements ClusterTask {
    private List<ServiceInfo> services;

    public SeniorMemberServicesRequest() {
    }

    public Object getResult() {
        return services;
    }

    public void run() {
        services = new ArrayList<ServiceInfo>();
        // Get all services and include them in the reply
        for (MultiUserChatService mucService : XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices()) {
            services.add(new ServiceInfo(mucService));
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        // Do nothing
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Do nothing
    }
}
