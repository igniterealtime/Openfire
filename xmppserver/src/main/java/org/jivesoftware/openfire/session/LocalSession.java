/*
 * Copyright (C) 2004-2009 Jive Software, 2017-2023 Ignite Realtime Foundation. All rights reserved.
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

import org.dom4j.Element;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.interceptor.InterceptorManager;
import org.jivesoftware.openfire.interceptor.PacketRejectedException;
import org.jivesoftware.openfire.streammanagement.StreamManager;
import org.jivesoftware.util.LocaleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The session represents a connection between the server and a client (c2s) or
 * another server (s2s) as well as a connection with a component. Authentication and
 * user accounts are associated with c2s connections while s2s has an optional authentication
 * association but no single user.
 *
 * Obtain object managers from the session in order to access server resources.
 *
 * @author Gaston Dombiak
 */
public abstract class LocalSession implements Session {

    private static final Logger Log = LoggerFactory.getLogger(LocalSession.class);

    /**
     * The Address this session is authenticated as.
     */
    protected JID address;

    /**
     * The stream id for this session (random and unique).
     */
    protected final StreamID streamID;

    /**
     * The current session status.
     */
    protected Status status = Status.CONNECTED;

    /**
     * The connection that this session represents.
     */
    protected Connection conn;

    protected SessionManager sessionManager;

    protected final String serverName;

    protected final long startDate = System.currentTimeMillis();

    private long lastActiveDate;

    private AtomicLong clientPacketCount = new AtomicLong( 0 );

    private AtomicLong serverPacketCount = new AtomicLong( 0 );

    /**
     * Session temporary data.
     *
     * All data stored in this <code>Map</code> disappears when the session finishes.
     */
    private final Map<String, Object> sessionData = new HashMap<>();

    /**
     * Software Version (XEP-0092) data as obtained from the peer on this connection.
     */
    private Map<String, String> softwareVersionData = new HashMap<>();

    /**
     * XEP-0198 Stream Manager
     */
    protected final StreamManager streamManager;

    /**
     * A lock to protect the connection changes.
     */
    private final Lock lock = new ReentrantLock();

    private final Locale language;

    /**
     * Creates a session with an underlying connection and permission protection.
     *
     * @param serverName domain of the XMPP server where the new session belongs.
     * @param connection The connection we are proxying.
     * @param streamID unique identifier for this session.
     * @param language The language to use for this session.
     */
    public LocalSession(String serverName, Connection connection, StreamID streamID, Locale language) {
        if (connection == null) {
            throw new IllegalArgumentException("connection must not be null");
        }
        conn = connection;
        this.streamID = streamID;
        this.serverName = serverName;
        this.address = new JID(null, serverName, UUID.randomUUID().toString(), true);
        this.sessionManager = SessionManager.getInstance();
        this.streamManager = new StreamManager(this);
        this.language = language;
        this.lastActiveDate = startDate;

    }

    /**
     * Returns true if the session is detached (that is, if the underlying connection
     * has been closed while the session instance itself has not been closed).
     *
     * @return true if session detached
     */
    public boolean isDetached() {
        return this.sessionManager.isDetached(this);
    }

    /**
     * Set the session to detached mode, indicating that the underlying connection
     * has been closed.
     */
    public void setDetached() {
        lock.lock();
        try {
            Log.debug("Setting session with address {} and streamID {} in detached mode.", this.address, this.streamID );
            this.sessionManager.addDetached(this);
            this.conn = null;
        }finally {
            lock.unlock();
        }
    }

    Connection releaseConnection()
    {
        lock.lock();
        try {
            Log.debug("Releasing connection from session with address {} and streamID {}.", this.address, this.streamID );
            final Connection result = conn;
            this.conn = null;
            this.close();
            return result;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reattach the (existing) session to the connection provided by a new session (a session that will be replaced
     * by the older, pre-existing session). The connection must already be initialized as a running XML Stream, normally
     * by having run through XEP-0198 resumption.
     *
     * @param connectionProvider Session from which to obtain the connection from.
     * @param h the sequence number of the last handled stanza sent over the former stream
     */
    public void reattach(LocalSession connectionProvider, long h) {
        lock.lock();
        try {
            Log.debug("Reattaching session with address {} and streamID {} using connection from session with address {} and streamID {}.", this.address, this.streamID, connectionProvider.getAddress(), connectionProvider.getStreamID());
            if (this.conn != null && !this.conn.isClosed())
            {
                this.conn.close(new StreamError(StreamError.Condition.conflict, "The stream previously served over this connection is resumed on a new connection."));
            }
            this.conn = connectionProvider.releaseConnection();
            this.conn.reinit(this);
        }finally {
            lock.unlock();
        }
        this.status = Session.Status.AUTHENTICATED;
        this.sessionManager.removeDetached(this);
        this.streamManager.onResume(new JID(null, this.serverName, null, true), h);
        this.sessionManager.removeSession((LocalClientSession)connectionProvider);
    }

    /**
      * Obtain the address of the user. The address is used by services like the core
      * server packet router to determine if a packet should be sent to the handler.
      * Handlers that are working on behalf of the server should use the generic server
      * hostname address (e.g. server.com).
      *
      * @return the address of the packet handler.
      */
    @Override
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
     * Note that null can be returned, for example when the session is detached.
     *
     * @return The connection for this session
     */
    @Nullable
    public Connection getConnection() {
        return conn;
    }

    /**
     * Obtain the current status of this session.
     *
     * @return The status code for this session
     */
    @Override
    public Status getStatus() {
        return status;
    }

    /**
     * Set the new status of this session. Setting a status may trigger
     * certain events to occur (setting a closed status will close this
     * session).
     *
     * @param status The new status code for this session
     */
    public void setStatus(Status status) {
        if (status == Status.CLOSED && this.streamManager.getResume()) {
            Log.debug( "Suppressing close for session with address {} and streamID {}.", this.address, this.streamID );
            return;
        }
        this.status = status;
    }

    /**
     * Obtain the stream ID associated with this sesison. Stream ID's are generated by the server
     * and should be unique and random.
     *
     * @return This session's assigned stream ID
     */
    @Override
    public StreamID getStreamID() {
        return streamID;
    }

    /**
     * Obtain the name of the server this session belongs to.
     *
     * @return the server name.
     */
    @Override
    public String getServerName() {
        return serverName;
    }

    /**
     * Obtain the date the session was created.
     *
     * @return the session's creation date.
     */
    @Override
    public Date getCreationDate() {
        return new Date(startDate);
    }

    /**
     * Obtain the time the session last had activity.
     *
     * @return The last time the session received activity.
     */
    @Override
    public Date getLastActiveDate() {
        return new Date(lastActiveDate);
    }

    /**
     * Increments the number of packets sent from the client to the server.
     */
    public void incrementClientPacketCount() {
        clientPacketCount.incrementAndGet();
        lastActiveDate = System.currentTimeMillis();
        streamManager.incrementServerProcessedStanzas();
    }

    /**
     * Increments the number of packets sent from the server to the client.
     */
    public void incrementServerPacketCount() {
        serverPacketCount.incrementAndGet();
        lastActiveDate = System.currentTimeMillis();
    }

    /**
     * Obtain the number of packets sent from the client to the server.
     *
     * @return The number of packets sent from the client to the server.
     */
    @Override
    public long getNumClientPackets() {
        return clientPacketCount.get();
    }

    /**
     * Obtain the number of packets sent from the server to the client.
     *
     * @return The number of packets sent from the server to the client.
     */
    @Override
    public long getNumServerPackets() {
        return serverPacketCount.get();
    }

    /**
     * Saves given session data. Data are saved to temporary storage only and are accessible during
     * this session life only and only from this session instance.
     *
     * @param key a <code>String</code> value of stored data key ID.
     * @param value a <code>Object</code> value of data stored in session.
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     *         (A {@code null} return can also indicate that the map
     *         previously associated {@code null} with {@code key}.)
     * @see #getSessionData(String)
     */
    public Object setSessionData(String key, Object value) {
        synchronized (sessionData) {
            return sessionData.put(key, value);
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
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}.
     * @see #setSessionData(String, Object)
     */
    public Object removeSessionData(String key) {
        synchronized (sessionData) {
            return sessionData.remove(key);
        }
    }

    /**
     * Get XEP-0198 Stream manager for session
     * @return The StreamManager for the session.
     */
    public StreamManager getStreamManager() {
        return streamManager;
    }

    @Override
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
        } else {
            // http://xmpp.org/extensions/xep-0016.html#protocol-error
            if (packet instanceof Message) {
                // For message stanzas, the server SHOULD return an error, which SHOULD be <service-unavailable/>.
                if (((Message)packet).getType() == Message.Type.error){
                    Log.debug("Avoid generating an error in response to a stanza that itself is an error (to avoid the chance of entering an endless back-and-forth of exchanging errors). Suppress sending an {} error in response to: {}", PacketError.Condition.service_unavailable, packet);
                    return;
                }
                Message message = (Message) packet;
                Message result = message.createCopy();
                result.setTo(message.getFrom());
                result.setFrom(message.getTo());
                result.setError(PacketError.Condition.service_unavailable);
                Log.trace("Responding with 'service-unavailable' as message cannot be processed to: {}", packet);
                XMPPServer.getInstance().getPacketRouter().route(result);
            } else if (packet instanceof IQ) {
                // For IQ stanzas of type "get" or "set", the server MUST return an error, which SHOULD be <service-unavailable/>.
                // IQ stanzas of other types MUST be silently dropped by the server.
                IQ iq = (IQ) packet;
                if (iq.getType() == IQ.Type.get || iq.getType() == IQ.Type.set) {
                    Log.trace("Responding with 'service-unavailable' as IQ request cannot be processed to: {}", packet);
                    IQ result = IQ.createResultIQ(iq);
                    result.setError(PacketError.Condition.service_unavailable);
                    XMPPServer.getInstance().getPacketRouter().route(result);
                }
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

    @Override
    public void deliverRawText(String text) {
        Connection connection = conn;
        if (connection == null )
        {
            Log.debug( "Unable to deliver raw text in session with address {} and streamID {}, as its connection is null. Dropping: {}", this.address, this.streamID, text );
            return;
        }
        connection.deliverRawText(text);
    }

    /**
     * Returns a text with the available stream features. Each subclass may return different
     * values depending whether the session has been authenticated or not.
     *
     * @return a text with the available stream features or {@code null} to add nothing.
     */
    public abstract List<Element> getAvailableStreamFeatures();

    @Override
    public void close() {
        Optional.ofNullable(conn)
         .ifPresent(Connection::close);
    }

    @Override
    public boolean validate() {
        return Optional.ofNullable(conn)
                .map(Connection::validate)
                .orElse(Boolean.FALSE);
    }

    @Override
    @Deprecated // Remove in Openfire 4.9 or later.
    public boolean isSecure() {
        return isEncrypted();
    }

    @Override
    public boolean isEncrypted() {
        return Optional.ofNullable(conn)
            .map(Connection::isEncrypted)
            .orElse(Boolean.FALSE);
    }

    @Override
    public Certificate[] getPeerCertificates() {
        return Optional.ofNullable(conn)
                .map(Connection::getPeerCertificates)
                .orElse(new Certificate[0]);
    }

    @Override
    public boolean isClosed() {
        return Optional.ofNullable(conn)
                .map(Connection::isClosed)
                .orElse(Boolean.TRUE);
    }

    @Override
    public String getHostAddress() throws UnknownHostException {
        Connection connection = conn;
        if (connection == null) {
            throw new UnknownHostException("Detached session");
        }
        return connection.getHostAddress();
    }

    @Override
    public String getHostName() throws UnknownHostException {
        Connection connection = conn;
        if (connection == null) {
            throw new UnknownHostException("Detached session");
        }
        return connection.getHostName();
    }

    @Override
    public String toString()
    {
        return this.getClass().getSimpleName() + "{" +
            "address=" + address +
            ", streamID=" + streamID +
            ", status=" + status +
            ", isEncrypted=" + isEncrypted() +
            ", isDetached=" + isDetached() +
            ", serverName='" + serverName + '\'' +
            '}';
    }

    /**
     * @deprecated Replaced by {@link Session#decodeVersion(String)}
     */
    @Deprecated // Remove in or after Openfire 4.9.0
    protected static int[] decodeVersion(String version) {
        return Session.decodeVersion(version);
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
        return Optional.ofNullable(conn)
                .map(Connection::isUsingSelfSignedCertificate)
                .orElse(Boolean.FALSE);
    }

    @Override
    @Nonnull
    public String getTLSProtocolName() {
        return Optional.ofNullable(conn)
            .map(c -> c.getTLSProtocolName().orElse("NONE"))
            .orElse("NONE");
    }

    @Override
    @Nonnull
    public String getCipherSuiteName() {
        return Optional.ofNullable(conn)
            .map(c -> c.getCipherSuiteName().orElse("NONE"))
            .orElse("NONE");
    }

    @Override
    public final Locale getLanguage() {
        return language;
    }

    /**
     * Retrieves Software Version data. This method gives access to temporary Software Version data only.
     * @return a Map collection value of data .
     */
    @Override
    public Map<String, String> getSoftwareVersion() {
        return softwareVersionData;
    }

    /**
     * Saves given session data. Data is saved to temporary storage only and is accessible during
     * this session life only and only from this session instance.
     * @param key a <code>String</code> value of stored data key ID.
     * @param value a <code>String</code> value of data stored in session.
     */
    public void setSoftwareVersionData(String key, String value) {
        softwareVersionData.put(key, value);
    }

}
