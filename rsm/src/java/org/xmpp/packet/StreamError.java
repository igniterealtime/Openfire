/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.packet;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.StringWriter;
import java.util.Iterator;

/**
 * A stream error. Stream errors have a condition and they
 * can optionally include explanation text.
 *
 * @author Matt Tucker
 */
public class StreamError {

    private static final String ERROR_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-streams";

    private static DocumentFactory docFactory = DocumentFactory.getInstance();

    private Element element;

    /**
     * Construcs a new StreamError with the specified condition.
     *
     * @param condition the error condition.
     */
    public StreamError(Condition condition) {
        this.element = docFactory.createElement(docFactory.createQName("error", "stream",
                "http://etherx.jabber.org/streams"));
        setCondition(condition);
    }

    /**
     * Constructs a new StreamError with the specified condition and error text.
     *
     * @param condition the error condition.
     * @param text the text description of the error.
     */
    public StreamError(Condition condition, String text) {
        this.element = docFactory.createElement(docFactory.createQName("error", "stream",
                "http://etherx.jabber.org/streams"));
        setCondition(condition);
        setText(text, null);
    }

    /**
     * Constructs a new StreamError with the specified condition and error text.
     *
     * @param condition the error condition.
     * @param text the text description of the error.
     * @param language the language code of the error description (e.g. "en").
     */
    public StreamError(Condition condition, String text, String language) {
        this.element = docFactory.createElement(docFactory.createQName("error", "stream",
                "http://etherx.jabber.org/streams"));
        setCondition(condition);
        setText(text, language);
    }

    /**
     * Constructs a new StreamError using an existing Element. This is useful
     * for parsing incoming error Elements into StreamError objects.
     *
     * @param element the stream error Element.
     */
    public StreamError(Element element) {
        this.element = element;
    }

    /**
     * Returns the error condition.
     *
     * @return the error condition.
     * @see Condition
     */
    public Condition getCondition() {
        for (Iterator i=element.elementIterator(); i.hasNext(); ) {
            Element el = (Element)i.next();
            if (el.getNamespaceURI().equals(ERROR_NAMESPACE) &&
                    !el.getName().equals("text"))
            {
                return Condition.fromXMPP(el.getName());
            }
        }
        return null;
    }

    /**
     * Sets the error condition.
     *
     * @param condition the error condition.
     * @see Condition
     */
    public void setCondition(Condition condition) {
        if (condition == null) {
            throw new NullPointerException("Condition cannot be null");
        }
        Element conditionElement = null;
        for (Iterator i=element.elementIterator(); i.hasNext(); ) {
            Element el = (Element)i.next();
            if (el.getNamespaceURI().equals(ERROR_NAMESPACE) &&
                    !el.getName().equals("text"))
            {
                conditionElement = el;
            }
        }
        if (conditionElement != null) {
            element.remove(conditionElement);
        }

        conditionElement = docFactory.createElement(condition.toXMPP(), ERROR_NAMESPACE);
        element.add(conditionElement);
    }

    /**
     * Returns a text description of the error, or <tt>null</tt> if there
     * is no text description.
     *
     * @return the text description of the error.
     */
    public String getText() {
        return element.elementText("text");
    }

    /**
     * Sets the text description of the error.
     *
     * @param text the text description of the error.
     */
    public void setText(String text) {
        setText(text, null);
    }

    /**
     * Sets the text description of the error. Optionally, a language code
     * can be specified to indicate the language of the description.
     *
     * @param text the text description of the error.
     * @param language the language code of the description, or <tt>null</tt> to specify
     *      no language code.
     */
    public void setText(String text, String language) {
        Element textElement = element.element("text");
        // If text is null, clear the text.
        if (text == null) {
            if (textElement != null) {
                element.remove(textElement);
            }
            return;
        }

        if (textElement == null) {
            textElement = docFactory.createElement("text", ERROR_NAMESPACE);
            if (language != null) {
                textElement.addAttribute(QName.get("lang", "xml",
                        "http://www.w3.org/XML/1998/namespace"), language);
            }
            element.add(textElement);
        }
        textElement.setText(text);
    }

    /**
     * Returns the text description's language code, or <tt>null</tt> if there
     * is no language code associated with the description text.
     *
     * @return the language code of the text description, if it exists.
     */
    public String getTextLanguage() {
        Element textElement = element.element("text");
        if (textElement != null) {
            return textElement.attributeValue(QName.get("lang", "xml",
                        "http://www.w3.org/XML/1998/namespace"));
        }
        return null;
    }

    /**
     * Returns the DOM4J Element that backs the error. The element is the definitive
     * representation of the error and can be manipulated directly to change
     * error contents.
     *
     * @return the DOM4J Element.
     */
    public Element getElement() {
        return element;
    }

    /**
     * Returns the textual XML representation of this stream error.
     *
     * @return the textual XML representation of this stream error.
     */
    public String toXML() {
        return element.asXML();
    }

    public String toString() {
        StringWriter out = new StringWriter();
        XMLWriter writer = new XMLWriter(out, OutputFormat.createPrettyPrint());
        try {
            writer.write(element);
        }
        catch (Exception e) { }
        return out.toString();
    }

    /**
     * Type-safe enumeration for the error condition.<p>
     *
     * Implementation note: XMPP error conditions use "-" characters in
     * their names such as "bad-request". Because "-" characters are not valid
     * identifier parts in Java, they have been converted to "_" characters in
     * the  enumeration names, such as <tt>bad_request</tt>. The {@link #toXMPP()} and
     * {@link #fromXMPP(String)} methods can be used to convert between the
     * enumertation values and XMPP error code strings.
     */
    public enum Condition {

        /**
         * The entity has sent XML that cannot be processed; this error MAY be used
         * instead of the more specific XML-related errors, such as &lt;bad-namespace-prefix/&gt;,
         * &lt;invalid-xml/&gt;, &lt;restricted-xml/&gt;, &lt;unsupported-encoding/&gt;, and
         * &lt;xml-not-well-formed/&gt;, although the more specific errors are preferred.
         */
        bad_format("bad-format"),

        /**
         * The entity has sent a namespace prefix that is unsupported, or has sent no
         * namespace prefix on an element that requires such a prefix.
         */
        bad_namespace_prefix("bad-namespace-prefix"),

        /**
         * The server is closing the active stream for this entity because a new stream
         * has been initiated that conflicts with the existing stream.
         */
        conflict("conflict"),

        /**
         * The entity has not generated any traffic over the stream for some period of
         * time (configurable according to a local service policy).
         */
        connection_timeout("connection-timeout"),

        /**
         * The value of the 'to' attribute provided by the initiating entity in the
         * stream header corresponds to a hostname that is no longer hosted by the server.
         */
        host_gone("host-gone"),

        /**
         * The value of the 'to' attribute provided by the initiating entity in the
         * stream header does not correspond to a hostname that is hosted by the server.
         */
        host_unknown("host-unknown"),

        /**
         * A stanza sent between two servers lacks a 'to' or 'from' attribute
         * (or the attribute has no value).
         */
        improper_addressing("improper-addressing"),

        /**
         * The server has experienced a misconfiguration or an otherwise-undefined
         * internal error that prevents it from servicing the stream.
         */
        internal_server_error("internal-server-error"),

        /**
         * The JID or hostname provided in a 'from' address does not match an authorized
         * JID or validated domain negotiated between servers via SASL or dialback, or
         * between a client and a server via authentication and resource binding.
         */
        invalid_from("invalid-from"),

        /**
         * The stream ID or dialback ID is invalid or does not match an ID previously provided.
         */
        invalid_id("invalid-id"),

        /**
         * the streams namespace name is something other than "http://etherx.jabber.org/streams"
         * or the dialback namespace name is something other than "jabber:server:dialback".
         */
        invalid_namespace("invalid-namespace"),

        /**
         * The entity has sent invalid XML over the stream to a server that performs validation.
         */
        invalid_xml("invalid-xml"),

        /**
         * The entity has attempted to send data before the stream has been authenticated,
         * or otherwise is not authorized to perform an action related to stream
         * negotiation; the receiving entity MUST NOT process the offending stanza before
         * sending the stream error.
         */
        not_authorized("not-authorized"),

        /**
         * The entity has violated some local service policy; the server MAY choose to
         * specify the policy in the <text/> element or an application-specific condition
         * element.
         */
        policy_violation("policy-violation"),

        /**
         * The server is unable to properly connect to a remote entity that is required for
         * authentication or authorization.
         */
        remote_connection_failed("remote-connection-failed"),

        /**
         * The server lacks the system resources necessary to service the stream.
         */
        resource_constraint("resource-constraint"),

        /**
         * The entity has attempted to send restricted XML features such as a comment,
         * processing instruction, DTD, entity reference, or unescaped character.
         */
        restricted_xml("restricted-xml"),

        /**
         * The server will not provide service to the initiating entity but is redirecting
         * traffic to another host; the server SHOULD specify the alternate hostname or IP
         * address (which MUST be a valid domain identifier) as the XML character data of the
         * &lt;see-other-host/&gt; element.
         */
        see_other_host("see-other-host"),

        /**
         * The server is being shut down and all active streams are being closed.
         */
        system_shutdown("system-shutdown"),

        /**
         * The error condition is not one of those defined by the other conditions in this
         * list; this error condition SHOULD be used only in conjunction with an
         * application-specific condition.
         */
        undefined_condition("undefined-condition"),

        /**
         * The initiating entity has encoded the stream in an encoding that is not
         * supported by the server.
         */
        unsupported_encoding("unsupported-encoding"),

        /**
         * The initiating entity has sent a first-level child of the stream that is
         * not supported by the server.
         */
        unsupported_stanza_type("unsupported-stanza-type"),

        /**
         * the value of the 'version' attribute provided by the initiating entity in the
         * stream header specifies a version of XMPP that is not supported by the server;
         * the server MAY specify the version(s) it supports in the &lt;text/&gt; element.
         */
        unsupported_version("unsupported-version"),

        /**
         * The initiating entity has sent XML that is not well-formed.
         */
        xml_not_well_formed("xml-not-well-formed");

        /**
         * Converts a String value into its Condition representation.
         *
         * @param condition the String value.
         * @return the condition corresponding to the String.
         */
        public static Condition fromXMPP(String condition) {
            if (condition == null) {
                throw new NullPointerException();
            }
            condition = condition.toLowerCase();
            if (bad_format.toXMPP().equals(condition)) {
                return bad_format;
            }
            else if (bad_namespace_prefix.toXMPP().equals(condition)) {
                return bad_namespace_prefix;
            }
            else if (conflict.toXMPP().equals(condition)) {
                return conflict;
            }
            else if (connection_timeout.toXMPP().equals(condition)) {
                return connection_timeout;
            }
            else if (host_gone.toXMPP().equals(condition)) {
                return host_gone;
            }
            else if (host_unknown.toXMPP().equals(condition)) {
                return host_unknown;
            }
            else if (improper_addressing.toXMPP().equals(condition)) {
                return improper_addressing;
            }
            else if (internal_server_error.toXMPP().equals(condition)) {
                return internal_server_error;
            }
            else if (invalid_from.toXMPP().equals(condition)) {
                return invalid_from;
            }
            else if (invalid_id.toXMPP().equals(condition)) {
                return invalid_id;
            }
            else if (invalid_namespace.toXMPP().equals(condition)) {
                return invalid_namespace;
            }
            else if (invalid_xml.toXMPP().equals(condition)) {
                return invalid_xml;
            }
            else if (not_authorized.toXMPP().equals(condition)) {
                return not_authorized;
            }
            else if (policy_violation.toXMPP().equals(condition)) {
                return policy_violation;
            }
            else if (remote_connection_failed.toXMPP().equals(condition)) {
                return remote_connection_failed;
            }
            else if (resource_constraint.toXMPP().equals(condition)) {
                return resource_constraint;
            }
            else if (restricted_xml.toXMPP().equals(condition)) {
                return restricted_xml;
            }
            else if (see_other_host.toXMPP().equals(condition)) {
                return see_other_host;
            }
            else if (system_shutdown.toXMPP().equals(condition)) {
                return system_shutdown;
            }
            else if (undefined_condition.toXMPP().equals(condition)) {
                return undefined_condition;
            }
            else if (unsupported_encoding.toXMPP().equals(condition)) {
                return unsupported_encoding;
            }
            else if (unsupported_stanza_type.toXMPP().equals(condition)) {
                return unsupported_stanza_type;
            }
            else if (unsupported_version.toXMPP().equals(condition)) {
                return unsupported_version;
            }
            else if (xml_not_well_formed.toXMPP().equals(condition)) {
                return xml_not_well_formed;
            }
            else {
                throw new IllegalArgumentException("Condition invalid:" + condition);
            }
        }

        private String value;

        private Condition(String value) {
            this.value = value;
        }

        /**
         * Returns the error code as a valid XMPP error code string.
         *
         * @return the XMPP error code value.
         */
        public String toXMPP() {
            return value;
        }
    }
}
