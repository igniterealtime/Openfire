/**
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007-2009 Jive Software. All rights reserved.
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

package com.jivesoftware.openfire.session;

import org.dom4j.Element;
import org.dom4j.tree.DefaultElement;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.privacy.PrivacyList;
import org.jivesoftware.openfire.privacy.PrivacyListManager;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.ClientSessionInfo;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.ClusterTask;
import org.jivesoftware.util.cache.ExternalizableUtil;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;
import org.xmpp.packet.Presence;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Surrogate for client sessions hosted in some remote cluster node.
 *
 * @author Gaston Dombiak
 */
public class RemoteClientSession extends RemoteSession implements ClientSession {

    private long initialized = -1;

    public RemoteClientSession(byte[] nodeID, JID address) {
        super(nodeID, address);
    }

    public PrivacyList getActiveList() {
        Cache<String, ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
        ClientSessionInfo sessionInfo = cache.get(getAddress().toString());
        if (sessionInfo != null && sessionInfo.getActiveList() != null) {
            return PrivacyListManager.getInstance().getPrivacyList(address.getNode(), sessionInfo.getActiveList());
        }
        return null;
    }

    public void setActiveList(PrivacyList activeList) {
        // Highly unlikely that a list is change to a remote session but still possible
        doClusterTask(new SetPrivacyListTask(address, true, activeList));
    }

    public PrivacyList getDefaultList() {
        Cache<String, ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
        ClientSessionInfo sessionInfo = cache.get(getAddress().toString());
        if (sessionInfo != null && sessionInfo.getDefaultList() != null) {
            return PrivacyListManager.getInstance().getPrivacyList(address.getNode(), sessionInfo.getDefaultList());
        }
        return null;
    }

    public void setDefaultList(PrivacyList defaultList) {
        // Highly unlikely that a list is change to a remote session but still possible
        doClusterTask(new SetPrivacyListTask(address, false, defaultList));
    }

    public String getUsername() throws UserNotFoundException {
        return address.getNode();
    }

    public boolean isAnonymousUser() {
        return SessionManager.getInstance().isAnonymousRoute(getAddress());
    }

    public boolean isInitialized() {
        if (initialized == -1) {
            Presence presence = getPresence();
            if (presence != null && presence.isAvailable()) {
                // Optimization to avoid making a remote call
                initialized = 1;
            }
            else {
                ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.isInitialized);
                initialized = (Boolean) doSynchronousClusterTask(task) ? 1 : 0;
            }
        }
        return initialized == 1;
    }

    public void setInitialized(boolean isInit) {
        doClusterTask(new SetInitializedTask(address, isInit));
    }

    public boolean canFloodOfflineMessages() {
        // Code copied from LocalClientSession to avoid remote calls
        if(isOfflineFloodStopped()) {
            return false;
        }
        String username = getAddress().getNode();
        for (ClientSession session : SessionManager.getInstance().getSessions(username)) {
            if (session.isOfflineFloodStopped()) {
                return false;
            }
        }
        return true;
    }

    public boolean isOfflineFloodStopped() {
        Cache<String, ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
        ClientSessionInfo sessionInfo = cache.get(getAddress().toString());
        return sessionInfo != null && sessionInfo.isOfflineFloodStopped();
    }

    public Presence getPresence() {
        Cache<String,ClientSessionInfo> cache = SessionManager.getInstance().getSessionInfoCache();
        ClientSessionInfo sessionInfo = cache.get(getAddress().toString());
        if (sessionInfo != null) {
            return sessionInfo.getPresence();
            }
        return null;
    }

    public void setPresence(Presence presence) {
        try {
            doClusterTask(new SetPresenceTask(address, presence));
        } catch (IllegalStateException e) {
            // Remote node is down
            if (presence.getType() == Presence.Type.unavailable) {
                // Ignore unavailable presence (since session is already unavailable - at least to us)
                return;
            }
            throw e;
        }
    }

    public int incrementConflictCount() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.incrementConflictCount);
        return (Integer) doSynchronousClusterTask(task);
    }

    RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation) {
        return new ClientSessionTask(address, operation);
    }

    ClusterTask getDeliverRawTextTask(String text) {
        return new DeliverRawTextTask(this, address, text);
    }

    ClusterTask getProcessPacketTask(Packet packet) {
        return new ProcessPacketTask(this, address, packet);
    }

    private static class SetPresenceTask extends ClientSessionTask {
        private Presence presence;

        public SetPresenceTask() {
            super();
        }

        protected SetPresenceTask(JID address, Presence presence) {
            super(address, null);
            this.presence = presence;
        }

        public void run() {
            ((ClientSession)getSession()).setPresence(presence);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeSerializable(out, (DefaultElement) presence.getElement());
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            Element packetElement = (Element) ExternalizableUtil.getInstance().readSerializable(in);
            presence = new Presence(packetElement, true);
        }
    }

    private static class SetPrivacyListTask extends ClientSessionTask {
        private boolean activeList;
        private String listName;

        public SetPrivacyListTask() {
            super();
        }

        protected SetPrivacyListTask(JID address, boolean activeList, PrivacyList list) {
            super(address, null);
            this.activeList = activeList;
            this.listName = list != null ? list.getName() : null;
        }

        public void run() {
            ClientSession session = ((ClientSession) getSession());
            PrivacyList list = null;
            // Get the privacy list to set
            if (listName != null) {
                try {
                    String username = session.getUsername();
                    list = PrivacyListManager.getInstance().getPrivacyList(username, listName);
                } catch (UserNotFoundException e) {
                    // Should never happen
                }
            }
            // Set the privacy list to the session
            if (activeList) {
                session.setActiveList(list);
            }
            else {
                session.setDefaultList(list);
            }
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeBoolean(out, activeList);
            ExternalizableUtil.getInstance().writeBoolean(out, listName != null);
            if (listName != null) {
                ExternalizableUtil.getInstance().writeSafeUTF(out, listName);
            }
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            activeList = ExternalizableUtil.getInstance().readBoolean(in);
            if (ExternalizableUtil.getInstance().readBoolean(in)) {
                listName = ExternalizableUtil.getInstance().readSafeUTF(in);

            }
        }
    }

    private static class SetInitializedTask extends ClientSessionTask {
        private boolean initialized;

        public SetInitializedTask() {
            super();
        }

        protected SetInitializedTask(JID address, boolean initialized) {
            super(address, null);
            this.initialized = initialized;
        }

        public void run() {
            ((ClientSession) getSession()).setInitialized(initialized);
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            super.writeExternal(out);
            ExternalizableUtil.getInstance().writeBoolean(out, initialized);
        }

        public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
            super.readExternal(in);
            initialized = ExternalizableUtil.getInstance().readBoolean(in);
        }
    }
}
