/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.spi.AbstractFragment;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * A error object representing an XMPP error. Error packets are tricky because
 * Jabber (old style) and XMPP errors are completely different. This class does
 * it's best to abstract the entire error generation process. The packet will
 * serialize to XML according to the underlying session version (none indicating
 * Jabber, v 1.0 indicating XMPP.</p>
 *
 * @author Iain Shigeoka
 */
public class XMPPError extends AbstractFragment {

    /**
     * The mandatory error message.
     */
    private Code code;

    /**
     * Create an error with the given code.
     *
     * @param code The error code
     */
    public XMPPError(Code code) {
        this.code = code;
    }

    /**
     * Returns the error code for this error. XMPP defines several standard error
     * codes that MUST be included in the 'error' attribute of error packets.
     *
     * @return the error code.
     */
    Code getCode() {
        return code;
    }

    /**
     * Set the error code for this error. XMPP defines several standard error codes
     * that MUST be included in the 'error' attribute of error packets.
     *
     * @param code The error code
     */
    void setCode(Code code) {
        this.code = code;
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
        xmlSerializer.writeStartElement("jabber:client", "error");
        xmlSerializer.writeAttribute("code", Integer.toString(code.getValue()));
        xmlSerializer.writeEndElement();
    }

    public XMPPFragment createDeepCopy() {
        XMPPError error = new XMPPError(code);
        Iterator frags = getFragments();
        while (frags.hasNext()) {
            error.addFragment((XMPPFragment)frags.next());
        }
        return error;
    }

    /**
     * Represents an error code.
     */
    public enum Code {

        NONE(-1),
        REDIRECT(302),
        BAD_REQUEST(400),
        UNAUTHORIZED(401),
        PAYMENT_REQUIRED(402), 
        FORBIDDEN(403),
        NOT_FOUND(404),
        NOT_ALLOWED(405),
        NOT_ACCEPTABLE(406),
        REGISTRATION_REQUIRED(407),
        REQUEST_TIMEOUT(408),
        CONFLICT(409),
        INTERNAL_SERVER_ERROR(500),
        NOT_IMPLEMENTED(501),
        REMOTE_SERVER_ERROR(502),
        SERVICE_UNAVAILABLE(503),
        REMOTE_SERVER_TIMEOUT(504);

        private int value;

        /**
         * Create a code with the given integer error code value.
         *
         * @param value the error value of the code
         */
        private Code(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
