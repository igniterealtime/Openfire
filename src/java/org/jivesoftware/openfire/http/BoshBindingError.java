/**
 * $Revision:$
 * $Date:$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.openfire.http;

import javax.servlet.http.HttpServletResponse;

/**
 *  An enum defining all errors which can happen during a BOSH session.
 */
public enum BoshBindingError {
    /**
     * The format of an HTTP header or binding element received from the client is unacceptable
     * (e.g., syntax error), or Script Syntax is not supported.
     */
    badRequest(Type.terminal, "bad-request", HttpServletResponse.SC_BAD_REQUEST),
    /**
     * The target domain specified in the 'to' attribute or the target host or port specified in the
     * 'route' attribute is no longer serviced by the connection manager.
     */
    hostGone(Type.terminal, "host-gone"),
    /**
     * The target domain specified in the 'to' attribute or the target host or port specified in the
     * 'route' attribute is unknown to the connection manager.
     */
    hostUnknown(Type.terminal, "host-unknown"),
    /**
     * The initialization element lacks a 'to' or 'route' attribute (or the attribute has no value)
     * but the connection manager requires one.
     */
    improperAddressing(Type.terminal, "improper-addressing"),
    /**
     * The connection manager has experienced an internal error that prevents it from servicing the
     * request.
     */
    internalServerError(Type.terminal, "internal-server-error"),
    /**
     * (1) 'sid' is not valid, (2) 'stream' is not valid, (3) 'rid' is larger than the upper limit
     * of the expected window, (4) connection manager is unable to resend response, (5) 'key'
     * sequence is invalid
     */
    itemNotFound(Type.terminal, "item-not-found", HttpServletResponse.SC_NOT_FOUND),
    /**
     * Another request being processed at the same time as this request caused the session to
     * terminate.
     */
    otherRequest(Type.terminal, "other-request"),
    /**
     * The client has broken the session rules (polling too frequently, requesting too frequently,
     * too many simultaneous requests).
     */
    policyViolation(Type.terminal, "policy-violation",
            HttpServletResponse.SC_FORBIDDEN),
    /**
     * The connection manager was unable to connect to, or unable to connect securely to, or has
     * lost its connection to, the server.
     */
    remoteConnectionFailed(Type.terminal, "remote-connection-failed"),
    /**
     * Encapsulates an error in the protocol being transported.
     */
    remoteStreamError(Type.terminal, "remote-stream-error"),
    /**
     * The connection manager does not operate at this URI (e.g., the connection manager accepts
     * only SSL or TLS connections at some https: URI rather than the http: URI requested by the
     * client). The client may try POSTing to the URI in the content of the &lt;uri/&gt; child
     * element.
     */
    seeOtherUri(Type.terminal, "see-other-uri"),
    /**
     * The connection manager is being shut down. All active HTTP sessions are being terminated. No
     * new sessions can be created.
     */
    systemShutdown(Type.terminal, "system-shutdown"),
    /**
     * The error is not one of those defined herein; the connection manager SHOULD include
     * application-specific information in the content of the &lt;body&gt; wrapper.
     */
    undefinedCondition(Type.terminal, "undefined-condition");

    private Type errorType;
    private String condition;
    private int legacyErrorCode = HttpServletResponse.SC_BAD_REQUEST;

    BoshBindingError(Type errorType, String condition, int legacyErrorCode) {
        this(errorType, condition);
        this.legacyErrorCode = legacyErrorCode;
    }

    BoshBindingError(Type errorType, String condition) {
        this.errorType = errorType;
        this.condition = condition;
    }

    public Type getErrorType() {
        return errorType;
    }

    /**
     * Returns the condition that caused the binding error. This should be returned to the client
     * so that the client can take appropriate action.
     *
     * @return the condition that caused the binding error.
     */
    public String getCondition() {
        return condition;
    }

    /**
     * Returns the legacy HTTP error code which is related to the binding error. With the 1.6
     * version of BOSH the use of HTTP errors was deprecated in favor of using errors inside of the
     * response to the client so that they could be more easily processed on the client side.
     *
     * @return the legacy HTTP error code which is related to the binding error.
     */
    public int getLegacyErrorCode() {
        return legacyErrorCode;
    }

    public enum Type {
        /**
         * The terminal error condition prevents the client from making any further requests until a
         * new session is established.
         */
        terminal(null),
        /**
         * In the case of a recoverable binding error the client MUST repeat the HTTP request and
         * all the preceding HTTP requests that have not received responses. The content of these
         * requests MUST be identical to the &lt;body&gt; elements of the original requests. This
         * allows the connection manager to recover a session after the previous request was lost
         * due to a communication failure.
         */
        recoverable("error");
        private String type;

        Type(String type) {
            this.type = type;
        }

        /**
         * Returns the type that will be displayed to the client.
         *
         * @return the type that will be displayed to the client.
         */
        public String getType() {
            if (type == null) {
                return name();
            }
            else {
                return type;
            }
        }
    }
}
