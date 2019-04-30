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

package org.jivesoftware.openfire.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.AsyncContext;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.X509Certificate;

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

    private HttpSession session;
    private boolean isClosed;

    private final AsyncContext context;

    /**
     * Constructs an HTTP Connection.
     *
     * @param requestId the ID which uniquely identifies this request.
     * @param context execution context of the servlet request that created this instance.
     */
    public HttpConnection(long requestId, AsyncContext context) {
        this.requestId = requestId;
        this.context = context;
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
     * Returns true if this connection is using HTTPS.
     *
     * @return true if this connection is using HTTPS.
     */
    public boolean isSecure() {
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
    public void deliverBody(String body, boolean async) throws HttpConnectionClosedException, IOException {
        // We only want to use this function once so we will close it when the body is delivered.
        synchronized (this) {
            if (isClosed) {
                throw new HttpConnectionClosedException("The http connection is no longer " +
                        "available to deliver content");
            }
            isClosed = true;
        }

        if (body == null) {
            body = getSession().createEmptyBody(false);
        }
        HttpBindServlet.respond(getSession(), this.context, body, async);
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
    void setSession(HttpSession session) {
        this.session = session;
    }

    /**
     * Returns the session that this connection belongs to.
     *
     * @return the session that this connection belongs to.
     */
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
    public InetAddress getRemoteAddr() throws UnknownHostException
    {
        return InetAddress.getByName(context.getRequest().getRemoteAddr());
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
