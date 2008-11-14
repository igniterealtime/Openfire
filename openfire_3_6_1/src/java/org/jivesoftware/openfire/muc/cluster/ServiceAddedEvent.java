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
import org.jivesoftware.openfire.muc.MultiUserChatService;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will add a service to the cluster node. When a service is added
 * in a cluster node the rest of the cluster nodes will need to add a copy.
 * They do -not- need to create database entries for the new service as the originator
 * will have already done that.  This event assumes that it's the default representation
 * of a MUC service, and therefore should not pass information about internal component
 * generated MUC services.
 *
 * @author Daniel Henninger
 */
public class ServiceAddedEvent implements ClusterTask {
    private String subdomain;
    private String description;
    private Boolean isHidden;

    public ServiceAddedEvent() {
    }

    public ServiceAddedEvent(String subdomain, String description, Boolean isHidden) {
        this.subdomain = subdomain;
        this.description = description;
        this.isHidden = isHidden;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        // If it's registered already, no need to create it.  Most likely this is because the service
        // is provided by an internal component that registered at startup.  This scenario, however,
        // should really never occur.
        if (!XMPPServer.getInstance().getMultiUserChatManager().isServiceRegistered(subdomain)) {
            MultiUserChatService service = new MultiUserChatServiceImpl(subdomain, description, isHidden);
            XMPPServer.getInstance().getMultiUserChatManager().registerMultiUserChatService(service);
        }
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, subdomain);
        ExternalizableUtil.getInstance().writeSafeUTF(out, description);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        subdomain = ExternalizableUtil.getInstance().readSafeUTF(in);
        description = ExternalizableUtil.getInstance().readSafeUTF(in);
    }
}
