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

package org.jivesoftware.openfire.session;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.Presence;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Client session information to be used when running in a cluster. The session
 * information is shared between cluster nodes and is meant to be used by remote
 * sessions to avoid invocation remote calls and instead use cached information.
 * This optimization should give an important boost to the application specifically
 * while users are logging in.<p>
 *
 * Session information is stored after a user authenticated and bound a resource.
 *
 * @author Gaston Dombiak
 */
public class ClientSessionInfo implements Externalizable {
    private Presence presence;
    private String defaultList;
    private String activeList;
    private int conflictCount;
    private boolean offlineFloodStopped;

    public ClientSessionInfo() {
    }

    public ClientSessionInfo(LocalClientSession session) {
        presence = session.getPresence();
        defaultList = session.getDefaultList() != null ? session.getDefaultList().getName() : null;
        activeList = session.getActiveList() != null ? session.getActiveList().getName() : null;
        conflictCount = session.getConflictCount();
        offlineFloodStopped = session.isOfflineFloodStopped();
    }

    public Presence getPresence() {
        return presence;
    }

    public String getDefaultList() {
        return defaultList;
    }

    public String getActiveList() {
        return activeList;
    }

    public int getConflictCount() {
        return conflictCount;
    }

    public boolean isOfflineFloodStopped() {
        return offlineFloodStopped;
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        ExternalizableUtil.getInstance().writeBoolean(out, defaultList != null);
        if (defaultList != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, defaultList);
        }
        ExternalizableUtil.getInstance().writeBoolean(out, activeList != null);
        if (activeList != null) {
            ExternalizableUtil.getInstance().writeSafeUTF(out, activeList);
        }
        ExternalizableUtil.getInstance().writeInt(out, conflictCount);
        ExternalizableUtil.getInstance().writeBoolean(out, offlineFloodStopped);
    }

    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
        presence = new Presence(packetElement, true);
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            defaultList = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        if (ExternalizableUtil.getInstance().readBoolean(in)) {
            activeList = ExternalizableUtil.getInstance().readSafeUTF(in);
        }
        conflictCount = ExternalizableUtil.getInstance().readInt(in);
        offlineFloodStopped = ExternalizableUtil.getInstance().readBoolean(in);
    }
}
