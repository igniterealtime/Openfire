/**
 * Copyright (C) 2004-2009 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.session;

import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.xmpp.packet.Packet;

/**
 * The session represents a connection between the server and a client (c2s) or
 * another server (s2s) as well as a connection with a component. Authentication and
 * user accounts are associated with c2s connections while s2s has an optional authentication
 * association but no single user user.<p>
 *
 * Obtain object managers from the session in order to access server resources.
 *
 * @author Gaston Dombiak
 */
public abstract class LocalSession implements Session {

	private static final Logger Log = LoggerFactory.getLogger(LocalSession.class);

    /**
     * The utf-8 charset for decoding and encoding Jabber packet streams.
     */
    protected static String CHARSET = "UTF-8";

    /**
     * The Address this session is authenticated as.
     */
    private JID address;

    /**
     * The stream id for this session (random and unique).
     */
    private StreamID streamID;

    /**
     * The current session status.
     */
    protected int status = STATUS_CONNECTED;

    /**
     * The connection that this session represents.
     */
    protected Connection conn;

    protected SessionManager sessionManager;

    private String serverName;

    private long startDate = System.currentTimeMillis();

    private long lastActiveDate;
    private long clientPacketCount = 0;
    private long serverPacketCount = 0;

    /**
	 * Session temporary data. All data stored in this <code>Map</code> disapear when session
	 * finishes.
	 */
	private final Map<String, Object> sessionData = new HashMap<String, Object>();

    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param serverName domain of the XMPP server where the new session belongs.
     * @param connection The connection we are proxying.
     * @param streamID unique identifier for this session.
     */
    public LocalSession(String serverName, Connection connection, StreamID streamID) {
        conn = connection;
        this.streamID = streamID;
        this.serverName = serverName;
        String id = streamID.getID();
        this.address = new JID(null, serverName, id, true);
        this.sessionManager = SessionManager.getInstance();
    }

    /**
      * Obtain the address of the user. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the address of the packet handler.
      */
    public JID getAddress() {
        return address;
    }

    /**
     * Sets the new address of this session. The address is used by services like the core
     * server packet router to determine if a packet should be sent to the handler.
     * Handlers that are working on behalf of the server should use the generic server
     * hostname address (e.g. server.com).
     *
     * @param address the new address of this session.
     */
    public void setAddress(JID address){
        this.address = address;
    }

    /**
     * Returns the connection associated with this Session.
     *
     * @return The connection for this session
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * Obtain the current status of this session.
     *
     * @return The status code for this session
     */
    public int getStatus() {
        return status;
    }

    /**
     * Set the new status of this session. Setting a status may trigger
     * certain events to occur (setting a closed status will close this
     * session).
     *
     * @param status The new status code for this session
     */
    public void setStatus(int status) {
        this.status = status;
    }

    /**
     * Obtain the stream ID associated with this sesison. Stream ID's are generated by the server
     * and should be unique and random.
     *
     * @return This session's assigned stream ID
     */
    public StreamID getStreamID() {
        return streamID;
    }

    /**
     * Obtain the name of the server this session belongs to.
     *
     * @return the server name.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Obtain the date the session was created.
     *
     * @return the session's creation date.
     */
    public Date getCreationDate() {
        return new Date(startDate);
    }

    /**
     * Obtain the time the session last had activity.
     *
     * @return The last time the session received activity.
     */
    public Date getLastActiveDate() {
        return new Date(lastActiveDate);
    }

    /**
     * Increments the number of packets sent from the client to the server.
     */
    public void incrementClientPacketCount() {
        clientPacketCount++;
        lastActiveDate = System.currentTimeMillis();
    }

    /**
     * Increments the number of packets sent from the server to the client.
     */
    public void incrementServerPacketCount() {
        serverPacketCount++;
        lastActiveDate = System.currentTimeMillis();
    }

    /**
     * Obtain the number of packets sent from the client to the server.
     *
     * @return The number of packets sent from the client to the server.
     */
    public long getNumClientPackets() {
        return clientPacketCount;
    }

    /**
     * Obtain the number of packets sent from the server to the client.
     *
     * @return The number of packets sent from the server to the client.
     */
    public long getNumServerPackets() {
        return serverPacketCount;
    }

    /**
	 * Saves given session data. Data are saved to temporary storage only and are accessible during
	 * this session life only and only from this session instance.
	 *
	 * @param key a <code>String</code> value of stored data key ID.
	 * @param value a <code>Object</code> value of data stored in session.
	 * @see #getSessionData(String)
	 */
	public void setSessionData(String key, Object value) {
        synchronized (sessionData) {
            sessionData.put(key, value);
        }
    }

	/**
	 * Retrieves session data. This method gives access to temporary session data only. You can
	 * retrieve earlier saved data giving key ID to receive needed value. Please see
	 * {@link #setSessionData(String, Object)}  description for more details.
	 *
	 * @param key a <code>String</code> value of stored data ID.
	 * @return a <code>Object</code> value of data for given key.
	 * @see #setSessionData(String, Object)
	 */
	public Object getSessionData(String key) {
        synchronized (sessionData) {
    		return sessionData.get(key);
        }
    }

    /**
     * Removes session data. Please see {@link #setSessionData(String, Object)} description
     * for more details.
     *
     * @param key a <code>String</code> value of stored data ID.
     * @see #setSessionData(String, Object)
     */
    public void removeSessionData(String key) {
        synchronized (sessionData) {
            sessionData.remove(key);
        }
    }

    public void process(Packet packet) {
        // Check that the requested packet can be processed
        if (canProcess(packet)) {
            // Perform the actual processing of the packet. This usually implies sending
            // the packet to the entity
            try {
                // Invoke the interceptors before we send the packet
                InterceptorManager.getInstance().invokeInterceptors(packet, this, false, false);
                deliver(packet);
                // Invoke the interceptors after we have sent the packet
                InterceptorManager.getInstance().invokeInterceptors(packet, this, false, true);
            }
            catch (PacketRejectedException e) {
                // An interceptor rejected the packet so do nothing
            }
            catch (Exception e) {
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Returns true if the specified packet can be delivered to the entity. Subclasses will use different
     * criterias to determine of processing is allowed or not. For instance, client sessions will use
     * privacy lists while outgoing server sessions will always allow this action.
     *
     * @param packet the packet to analyze if it must be blocked.
     * @return true if the specified packet must be blocked.
     */
    abstract boolean canProcess(Packet packet);

    abstract void deliver(Packet packet) throws UnauthorizedException;

    public void deliverRawText(String text) {
        if (conn != null) {
            conn.deliverRawText(text);
        }
    }

    /**
     * Returns a text with the available stream features. Each subclass may return different
     * values depending whether the session has been authenticated or not.
     *
     * @return a text with the available stream features or <tt>null</tt> to add nothing.
     */
    public abstract String getAvailableStreamFeatures();

    public void close() {
        if (conn != null) {
            conn.close();
        }
    }

    public boolean validate() {
        return conn.validate();
    }

    public boolean isSecure() {
        return conn.isSecure();
    }

    public boolean isClosed() {
        return conn.isClosed();
    }

    public String getHostAddress() throws UnknownHostException {
        return conn.getHostAddress();
    }

    public String getHostName() throws UnknownHostException {
        return conn.getHostName();
    }

    @Override
	public String toString() {
        return super.toString() + " status: " + status + " address: " + address + " id: " + streamID;
    }

    protected static int[] decodeVersion(String version) {
        int[] answer = new int[] {0 , 0};
        String [] versionString = version.split("\\.");
        answer[0] = Integer.parseInt(versionString[0]);
        answer[1] = Integer.parseInt(versionString[1]);
        return answer;
    }

    /**
     * Returns true if the other peer of this session presented a self-signed certificate. When
     * using self-signed certificate for server-2-server sessions then SASL EXTERNAL will not be
     * used and instead server-dialback will be preferred for vcerifying the identify of the remote
     * server.
     *
     * @return true if the other peer of this session presented a self-signed certificate.
     */
    public boolean isUsingSelfSignedCertificate() {
        return conn.isUsingSelfSignedCertificate();
    }
}
