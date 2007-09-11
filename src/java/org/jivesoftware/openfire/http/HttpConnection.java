/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.http;

import org.mortbay.util.ajax.Continuation;

/**
 * Represents one HTTP connection with a client using the HTTP Binding service. The client will wait
 * on {@link #getResponse()} until the server forwards a message to it or the wait time on the
 * session timesout.
 *
 * @author Alexander Wenckus
 */
public class HttpConnection {
    private long requestId;
    private String body;
    private HttpSession session;
    private Continuation continuation;
    private boolean isClosed;
    private boolean isSecure = false;
    private boolean isDelivered;

    private static final String CONNECTION_CLOSED = "connection closed";

    /**
     * Constructs an HTTP Connection.
     *
     * @param requestId the ID which uniquely identifies this request.
     * @param isSecure true if this connection is using HTTPS
     */
    public HttpConnection(long requestId, boolean isSecure) {
        this.requestId = requestId;
        this.isSecure = isSecure;
        this.isDelivered = false;
    }

    /**
     * The connection should be closed without delivering a stanza to the requestor.
     */
    public void close() {
        if (isClosed) {
            return;
        }

        try {
            deliverBody(CONNECTION_CLOSED);
        }
        catch (HttpConnectionClosedException e) {
            /* Shouldn't happen */
        }
    }

    /**
     * Returns true if this connection has been closed, either a response was delivered to the
     * client or the server closed the connection aburbtly.
     *
     * @return true if this connection has been closed.
     */
    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Returns true if this connection is using HTTPS.
     *
     * @return true if this connection is using HTTPS.
     */
    public boolean isSecure() {
        return isSecure;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    /**
     * Delivers content to the client. The content should be valid XMPP wrapped inside of a body.
     * A <i>null</i> value for body indicates that the connection should be closed and the client
     * sent an empty body.
     *
     * @param body the XMPP content to be forwarded to the client inside of a body tag.
     *
     * @throws HttpConnectionClosedException when this connection to the client has already recieved
     * a deliverable to forward to the client
     */
    public void deliverBody(String body) throws HttpConnectionClosedException {
        if(body == null) {
            throw new IllegalArgumentException("Body cannot be null!");
        }
        // We only want to use this function once so we will close it when the body is delivered.
        if (isClosed) {
            throw new HttpConnectionClosedException("The http connection is no longer " +
                    "available to deliver content");
        }
        else {
            isClosed = true;
        }

        if (continuation != null) {
            continuation.setObject(body);
            continuation.resume();
        }
        else {
            this.body = body;
        }
    }

    /**
     * A call that will cause a wait, or in the case of Jetty the thread to be freed, if there is no
     * deliverable currently available. Once the response becomes available, it is returned.
     *
     * @return the deliverable to send to the client
     * @throws HttpBindTimeoutException to indicate that the maximum wait time requested by the
     * client has been surpassed and an empty response should be returned.
     */
    public String getResponse() throws HttpBindTimeoutException {
        if (body == null && continuation != null) {
            try {
                body = waitForResponse();
            }
            catch (HttpBindTimeoutException e) {
                this.isClosed = true;
                throw e;
            }
        }
        else if (body == null) {
            throw new IllegalStateException("Continuation not set, cannot wait for deliverable.");
        }
        return body;
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

    void setContinuation(Continuation continuation) {
        this.continuation = continuation;
    }

    private String waitForResponse() throws HttpBindTimeoutException {
        if (continuation.suspend(session.getWait() * 1000)) {
            String deliverable = (String) continuation.getObject();
            // This will occur when the hold attribute of a session has been exceded.
            this.isDelivered = true;
            if (deliverable == null) {
                throw new HttpBindTimeoutException();
            }
            else if(CONNECTION_CLOSED.equals(deliverable)) {
                return null;
            }
            return deliverable;
        }
        this.isDelivered = true;
        throw new HttpBindTimeoutException("Request " + requestId + " exceeded response time from " +
                "server of " + session.getWait() + " seconds.");
    }
}
