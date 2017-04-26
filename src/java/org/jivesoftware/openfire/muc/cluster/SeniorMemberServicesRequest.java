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
public class SeniorMemberServicesRequest implements ClusterTask<List<ServiceInfo>> {
    private List<ServiceInfo> services;

    public SeniorMemberServicesRequest() {
    }

    @Override
    public List<ServiceInfo> getResult() {
        return services;
    }

    @Override
    public void run() {
        services = new ArrayList<>();
        // Get all services and include them in the reply
        for (MultiUserChatService mucService : XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatServices()) {
            services.add(new ServiceInfo(mucService));
        }
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        // Do nothing
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        // Do nothing
    }
}
