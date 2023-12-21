/*
 * Copyright (C) 2005-2008 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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

package org.jivesoftware.openfire.http;

import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.servlet.AsyncContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

/**
 * Represents one HTTP connection with a client using the HTTP Binding service. The client will wait
 * on a response until the server forwards a message to it or the wait time on the
 * session timeout.
 *
 * @author Alexander Wenckus
 */
public class HttpConnection {

    private static final Logger Log = LoggerFactory.getLogger(HttpConnection.class);

    private final long requestId;

    private final boolean isRestart;

    private final Duration pause;

    private final boolean isTerminate;

    private final boolean isPoll;

    @Nullable
    private HttpSession session;

    @GuardedBy("this")
    private boolean isClosed;

    @Nonnull
    private final AsyncContext context;

    @Nonnull
    private final List<Element> inboundDataQueue;

    /**
     * Constructs an HTTP Connection.
     *
     * @param body the BOSH data that is in this request.
     * @param context execution context of the servlet request that created this instance.
     */
    public HttpConnection(@Nonnull final HttpBindBody body, @Nonnull final AsyncContext context)
    {
        this.requestId = body.getRid();
        this.inboundDataQueue = body.getStanzaElements();
        this.isRestart = body.isRestart();
        this.pause = body.getPause();
        this.isTerminate = "terminate".equals(body.getType());
        this.context = context;
        this.isPoll = body.isPoll();
    }

    /**
     * The connection should be closed without delivering a stanza to the requestor.
     */
    public void close() {
        synchronized (this) {
            if (isClosed) {
                return;
            }
        }

        try {
            deliverBody(null, true);
        }
        catch (HttpConnectionClosedException | IOException e) {
            Log.warn("Unexpected exception occurred while trying to close an HttpException.", e);
        }
    }

    /**
     * Returns true if this connection has been closed, either a response was delivered to the
     * client or the server closed the connection abruptly.
     *
     * @return true if this connection has been closed.
     */
    public synchronized boolean isClosed() {
        return isClosed;
    }

    /**
     * @deprecated Renamed. See {@link #isEncrypted()}
     */
    @Deprecated // Remove in Openfire 4.9 or later.
    public boolean isSecure() {
        return isEncrypted();
    }

    /**
     * Returns true if this connection is using HTTPS.
     *
     * @return true if this connection is using HTTPS.
     */
    public boolean isEncrypted() {
        return context.getRequest().isSecure();
    }

    /**
     * Delivers content to the client. The content should be valid XMPP wrapped inside of a body.
     * A <i>null</i> value for body indicates that the connection should be closed and the client
     * sent an empty body.
     *
     * @param body the XMPP content to be forwarded to the client inside of a body tag.
     * @param async when false, this method blocks until the data has been delivered to the client.
     *
     * @throws HttpConnectionClosedException when this connection to the client has already received
     * a deliverable to forward to the client
     * @throws IOException if an input or output exception occurred
     */
    public void deliverBody(@Nullable final String body, final boolean async) throws HttpConnectionClosedException, IOException
    {
        if (session == null) {
            // This indicates that there's an implementation error in Openfire.
            throw new IllegalStateException("Cannot be used before bound to a session.");
        }

        // We only want to use this function once so we will close it when the body is delivered.
        synchronized (this) {
            if (isClosed) {
                throw new HttpConnectionClosedException("The http connection is no longer " +
                        "available to deliver content");
            }
            isClosed = true;
        }

        HttpBindServlet.respond(getSession(), this.context, body != null ? body : session.createEmptyBody(false), async);
    }

    /**
     * The list of stanzas that was sent by the client to the server over this connection. Possibly empty.
     *
     * @return An ordered collection of stanzas (possibly empty).
     */
    @Nonnull
    public List<Element> getInboundDataQueue() {
        return inboundDataQueue;
    }

    /**
     * Returns the ID which uniquely identifies this connection.
     *
     * @return the ID which uniquely identifies this connection.
     */
    public long getRequestId() {
        return requestId;
    }

    /**
     * Set the session that this connection belongs to.
     *
     * @param session the session that this connection belongs to.
     */
    void setSession(@Nonnull HttpSession session) {
        this.session = session;
    }

    /**
     * Returns the session that this connection belongs to.
     *
     * Although technically, this method can return null, it is expected that a session is bound to this connection
     * almost immediately after it is created.
     *
     * @return the session that this connection belongs to.
     */
    @Nullable
    public HttpSession getSession() {
        return session;
    }

    /**
     * Returns the Internet Protocol (IP) address of the client
     * or last proxy that sent the request.
     *
     * @return IP address of the remote peer
     * @throws UnknownHostException if no IP address for the peer could be found,
     */
    @Nonnull
    public InetAddress getRemoteAddr() throws UnknownHostException
    {
        return InetAddress.getByName(context.getRequest().getRemoteAddr());
    }

    /**
     * Returns if the request that was sent is a 'restart request'.
     *
     * @return if the request that was sent is a 'restart request'.
     */
    public boolean isRestart() {
        return isRestart;
    }

    /**
     * Returns the number of seconds of pause that the client is requesting, or null if it's not requesting a pause.
     *
     * @return The amount of seconds of pause that is being requested, or null.
     */
    public Duration getPause() {
        return pause;
    }

    /**
     * Returns if the request that was sent is a request to terminate the session.
     *
     * @return if the request that was sent is a request to terminate the session.
     */
    public boolean isTerminate() {
        return isTerminate;
    }

    /**
     * Returns if the request that was sent is a request is polling for data, without providing any data itself.
     *
     * @return if the request that was sent is a request is polling for data, without providing any data itself.
     */
    public boolean isPoll() {
        return isPoll;
    }

    /**
     * Returns the peer certificates for this connection. 
     * 
     * @return the peer certificates for this connection or null.
     */
    public X509Certificate[] getPeerCertificates() {
        return (X509Certificate[]) context.getRequest().getAttribute("javax.servlet.request.X509Certificate");
    }

    @Override
    public String toString() {
        return (session != null ? session.toString() : "[Anonymous]")
                + " rid: " + this.getRequestId();
    }
}
