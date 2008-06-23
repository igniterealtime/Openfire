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

package org.jivesoftware.openfire.component;

import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Task that will be executed on other cluster nodes to trigger the event that a component was
 * removed from a cluster node.
 *
 * @author Gaston Dombiak
 */
public class NotifyComponentUnregistered implements ClusterTask {
    private JID componentJID;

    public NotifyComponentUnregistered() {
    }

    public NotifyComponentUnregistered(JID componentJID) {
        this.componentJID = componentJID;
    }

    public Object getResult() {
        return null;
    }

    public void run() {
        final InternalComponentManager manager = InternalComponentManager.getInstance();
        manager.removeComponentInfo(componentJID);
        manager.notifyComponentUnregistered(componentJID);
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSafeUTF(out, componentJID.toString());
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        componentJID = new JID(ExternalizableUtil.getInstance().readSafeUTF(in));
    }
}
