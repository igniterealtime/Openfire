/*
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

package org.jivesoftware.openfire.plugin.session;

import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.Locale;

import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.cluster.ClusterNodeInfo;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.Session;
import org.jivesoftware.util.cache.CacheFactory;
import org.jivesoftware.util.cache.ClusterTask;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * Base class for sessions being hosted in other cluster nodes. Almost all
 * messages will be forwarded to the actual session in some remote cluster node.
 * Only some few messages will be local operations like getting the session's address
 * or the session status. And only some operations will be cached locally for a brief
 * period for content that is highly used and not frequently modified.
 *
 * @author Gaston Dombiak
 */
public abstract class RemoteSession implements Session {

    protected byte[] nodeID;
    protected JID address;

    // Cache content that never changes
    protected StreamID streamID;
    private Date creationDate;
    private String serverName;
    private String hostAddress;
    private String hostName;

    public RemoteSession(byte[] nodeID, JID address) {
        this.nodeID = nodeID;
        this.address = address;
    }

    public JID getAddress() {
        return address;
    }

    /**
     * Remote sessions are always authenticated. Otherwise, they won't be visibile to other
     * cluster nodes. When the session is closed it will no longer be visible to other nodes
     * so {@link #STATUS_CLOSED} is never returned. 
     *
     * @return the authenticated status.
     */
    public int getStatus() {
        return STATUS_AUTHENTICATED;
    }

    public StreamID getStreamID() {
        // Get it once and cache it since it never changes
        if (streamID == null) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getStreamID);
            streamID = (StreamID) doSynchronousClusterTask(task);
        }
        return streamID;
    }

    public String getServerName() {
        if (serverName == null) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getServerName);
            serverName = (String) doSynchronousClusterTask(task);
        }
        return serverName;
    }

    public Date getCreationDate() {
        // Get it once and cache it since it never changes
        if (creationDate == null) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getCreationDate);
            creationDate = (Date) doSynchronousClusterTask(task);
        }
        return creationDate;
    }

    public Date getLastActiveDate() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getLastActiveDate);
        return (Date) doSynchronousClusterTask(task);
    }

    public long getNumClientPackets() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getNumClientPackets);
        final Object clusterTaskResult = doSynchronousClusterTask(task);
        return clusterTaskResult == null ? -1 : (Long) clusterTaskResult;
    }

    public long getNumServerPackets() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getNumServerPackets);
        final Object clusterTaskResult = doSynchronousClusterTask(task);
        return clusterTaskResult == null ? -1 : (Long) clusterTaskResult;
    }

    public String getCipherSuiteName() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getCipherSuiteName);
        return (String) doSynchronousClusterTask(task);
    }

    public Certificate[] getPeerCertificates() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getPeerCertificates);
        return (Certificate[]) doSynchronousClusterTask(task);
    }

    public void process(Packet packet) {
        doClusterTask(getProcessPacketTask(packet));
    }

    public void close() {
        doSynchronousClusterTask(getRemoteSessionTask(RemoteSessionTask.Operation.close));
    }

    public boolean isClosed() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.isClosed);
        final Object clusterTaskResult = doSynchronousClusterTask(task);
        return clusterTaskResult == null ? false : (Boolean) clusterTaskResult;
    }

    public boolean isSecure() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.isSecure);
        final Object clusterTaskResult = doSynchronousClusterTask(task);
        return clusterTaskResult == null ? false : (Boolean) clusterTaskResult;
    }

    public String getHostAddress() throws UnknownHostException {
        if (hostAddress == null) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getHostAddress);
            hostAddress = (String) doSynchronousClusterTask(task);
        }
        return hostAddress;
    }

    public String getHostName() throws UnknownHostException {
        if (hostName == null) {
            ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.getHostName);
            hostName = (String) doSynchronousClusterTask(task);
        }
        return hostName;
    }

    public void deliverRawText(String text) {
        doClusterTask(getDeliverRawTextTask(text));
    }

    public boolean validate() {
        ClusterTask task = getRemoteSessionTask(RemoteSessionTask.Operation.validate);
        final Object clusterTaskResult = doSynchronousClusterTask(task);
        return clusterTaskResult == null ? false : (Boolean) clusterTaskResult;
    }

    abstract RemoteSessionTask getRemoteSessionTask(RemoteSessionTask.Operation operation);
    abstract ClusterTask getDeliverRawTextTask(String text);
    abstract ClusterTask getProcessPacketTask(Packet packet);

    /**
     * Invokes a task on the remote cluster member synchronously and returns the result of
     * the remote operation.
     *
     * @param task        the ClusterTask object to be invoked on a given cluster member.
     * @return result of remote operation.
     */
    protected Object doSynchronousClusterTask(ClusterTask task) {
    	ClusterNodeInfo info = CacheFactory.getClusterNodeInfo(nodeID);
    	Object result = null;
    	if (info == null && task instanceof RemoteSessionTask) { // clean up invalid session
    		Session remoteSession = ((RemoteSessionTask)task).getSession();
    		if (remoteSession instanceof ClientSession) {
            	SessionManager.getInstance().removeSession(null, remoteSession.getAddress(), false, false);
    		}
    	} else {
        	result = (info == null) ? null : CacheFactory.doSynchronousClusterTask(task, nodeID);
        }
    	return result;
    }

    /**
     * Invokes a task on the remote cluster member in an asynchronous fashion.
     *
     * @param task the task to be invoked on the specified cluster member.
     */
    protected void doClusterTask(ClusterTask task) {
    	ClusterNodeInfo info = CacheFactory.getClusterNodeInfo(nodeID);
    	if (info == null && task instanceof RemoteSessionTask) { // clean up invalid session
    		Session remoteSession = ((RemoteSessionTask)task).getSession();
    		if (remoteSession instanceof ClientSession) {
            	SessionManager.getInstance().removeSession(null, remoteSession.getAddress(), false, false);
    		}
		} else {
			CacheFactory.doClusterTask(task, nodeID);
	    }
    }

    @Override
    public final Locale getLanguage() {
        return Locale.getDefault();
    }
}
