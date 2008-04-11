/**
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.xmpp.packet;

import org.dom4j.Element;

import java.util.Iterator;

/**
 * Message packet.<p>
 *
 * A message can have one of several {@link Type Types}. For each message type,
 * different message fields are typically used as follows:
 *
 * <p>
 * <table border="1">
 * <tr><td>&nbsp;</td><td colspan="5"><b>Message type</b></td></tr>
 * <tr><td><i>Field</i></td><td><b>Normal</b></td><td><b>Chat</b></td><td><b>Group Chat</b></td><td><b>Headline</b></td><td><b>Error</b></td></tr>
 * <tr><td><i>subject</i></td> <td>SHOULD</td><td>SHOULD NOT</td><td>SHOULD NOT</td><td>SHOULD NOT</td><td>SHOULD NOT</td></tr>
 * <tr><td><i>thread</i></td>  <td>OPTIONAL</td><td>SHOULD</td><td>OPTIONAL</td><td>OPTIONAL</td><td>SHOULD NOT</td></tr>
 * <tr><td><i>body</i></td>    <td>SHOULD</td><td>SHOULD</td><td>SHOULD</td><td>SHOULD</td><td>SHOULD NOT</td></tr>
 * <tr><td><i>error</i></td>   <td>MUST NOT</td><td>MUST NOT</td><td>MUST NOT</td><td>MUST NOT</td><td>MUST</td></tr>
 * </table>
 */
public class Message extends Packet {

    /**
     * Constructs a new Message.
     */
    public Message() {
        this.element = docFactory.createDocument().addElement("message");
    }

     /**
     * Constructs a new Message using an existing Element. This is useful
     * for parsing incoming message Elements into Message objects.
     *
     * @param element the message Element.
     */
    public Message(Element element) {
        super(element);
    }

    /**
     * Constructs a new Message using an existing Element. This is useful
     * for parsing incoming message Elements into Message objects. Stringprep validation
     * on the TO address can be disabled. The FROM address will not be validated since the
     * server is the one that sets that value.
     *
     * @param element the message Element.
     * @param skipValidation true if stringprep should not be applied to the TO address.
     */
    public Message(Element element, boolean skipValidation) {
        super(element, skipValidation);
    }

    /**
     * Constructs a new Message that is a copy of an existing Message.
     *
     * @param message the message packet.
     * @see #createCopy()
     */
    private Message(Message message) {
        Element elementCopy = message.element.createCopy();
        docFactory.createDocument().add(elementCopy);
        this.element = elementCopy;
        // Copy cached JIDs (for performance reasons)
        this.toJID = message.toJID;
        this.fromJID = message.fromJID;
    }

    /**
     * Returns the type of this message
     *
     * @return the message type.
     * @see Type
     */
    public Type getType() {
        String type = element.attributeValue("type");
        if (type != null) {
            return Type.valueOf(type);
        }
        else {
            return Type.normal;
        }
    }

    /**
     * Sets the type of this message.
     *
     * @param type the message type.
     * @see Type
     */
    public void setType(Type type) {
        element.addAttribute("type", type==null?null:type.toString());
    }

    /**
     * Returns the subject of this message or <tt>null</tt> if there is no subject..
     *
     * @return the subject.
     */
    public String getSubject() {
        return element.elementText("subject");
    }

    /**
     * Sets the subject of this message.
     *
     * @param subject the subject.
     */
    public void setSubject(String subject) {
        Element subjectElement = element.element("subject");
        // If subject is null, clear the subject.
        if (subject == null && subjectElement != null) {
            element.remove(subjectElement);
            return;
        }
        // Do nothing if the new subject is null
        if (subject == null) {
            return;
        }
        if (subjectElement == null) {
            subjectElement = element.addElement("subject");
        }
        subjectElement.setText(subject);
    }

    /**
     * Returns the body of this message or <tt>null</tt> if there is no body.
     *
     * @return the body.
     */
    public String getBody() {
        return element.elementText("body");
    }

    /**
     * Sets the body of this message.
     *
     * @param body the body.
     */
    public void setBody(String body) {
        Element bodyElement = element.element("body");
        // If body is null, clear the body.
        if (body == null) {
            if (bodyElement != null) {
                element.remove(bodyElement);
            }
            return;
        }
        if (bodyElement == null) {
            bodyElement = element.addElement("body");
        }
        bodyElement.setText(body);
    }

    /**
     * Returns the thread value of this message, an identifier that is used for
     * tracking a conversation thread ("instant messaging session")
     * between two entities. If the thread is not set, <tt>null</tt> will be
     * returned.
     *
     * @return the thread value.
     */
    public String getThread() {
        return element.elementText("thread");
    }

    /**
     * Sets the thread value of this message, an identifier that is used for
     * tracking a conversation thread ("instant messaging session")
     * between two entities.
     *
     * @param thread thread value.
     */
    public void setThread(String thread) {
        Element threadElement = element.element("thread");
        // If thread is null, clear the thread.
        if (thread == null) {
            if (threadElement != null) {
                element.remove(threadElement);
            }
            return;
        }

        if (threadElement == null) {
            threadElement = element.addElement("thread");
        }
        threadElement.setText(thread);
    }

    /**
     * Returns the first child element of this packet that matches the
     * given name and namespace. If no matching element is found,
     * <tt>null</tt> will be returned. This is a convenience method to avoid
     * manipulating this underlying packet's Element instance directly.<p>
     *
     * Child elements in extended namespaces are used to extend the features
     * of XMPP. Examples include a "user is typing" indicator and invitations to
     * group chat rooms. Although any valid XML can be included in a child element
     * in an extended namespace, many common features have been standardized
     * as <a href="http://www.jabber.org/jeps">Jabber Enhancement Proposals</a>
     * (JEPs).
     *
     * @param name the element name.
     * @param namespace the element namespace.
     * @return the first matching child element, or <tt>null</tt> if there
     *      is no matching child element.
     */
    public Element getChildElement(String name, String namespace) {
        for (Iterator i=element.elementIterator(name); i.hasNext(); ) {
            Element element = (Element)i.next();
            if (element.getNamespaceURI().equals(namespace)) {
                return element;
            }
        }
        return null;
    }

    /**
     * Adds a new child element to this packet with the given name and
     * namespace. The newly created Element is returned. This is a
     * convenience method to avoid manipulating this underlying packet's
     * Element instance directly.<p>
     *
     * Child elements in extended namespaces are used to extend the features
     * of XMPP. Examples include a "user is typing" indicator and invitations to
     * group chat rooms. Although any valid XML can be included in a child element
     * in an extended namespace, many common features have been standardized
     * as <a href="http://www.jabber.org/jeps">Jabber Enhancement Proposals</a>
     * (JEPs).
     *
     * @param name the element name.
     * @param namespace the element namespace.
     * @return the newly created child element.
     */
    public Element addChildElement(String name, String namespace) {
        return element.addElement(name, namespace);
    }

    /**
     * Returns a deep copy of this Message.
     *
     * @return a deep copy of this Message.
     */
    public Message createCopy() {
        return new Message(this);
    }

    /**
     * Type-safe enumeration for the type of a message. The types are:
     *
     *  <ul>
     *      <li>{@link #normal Message.Type.normal} -- (Default) a normal text message
     *          used in email like interface.
     *      <li>{@link #chat Message.Type.cha}t -- a typically short text message used
     *          in line-by-line chat interfaces.
     *      <li>{@link #groupchat Message.Type.groupchat} -- a chat message sent to a
     *          groupchat server for group chats.
     *      <li>{@link #headline Message.Type.headline} -- a text message to be displayed
     *          in scrolling marquee displays.
     *      <li>{@link #error Message.Type.error} -- indicates a messaging error.
     * </ul>
     */
    public enum Type {

        /**
         * (Default) a normal text message used in email like interface.
         */
        normal,

        /**
         * Typically short text message used in line-by-line chat interfaces.
         */
        chat,

        /**
         * Chat message sent to a groupchat server for group chats.
         */
        groupchat,

        /**
         * Text message to be displayed in scrolling marquee displays.
         */
        headline,

        /**
         * Indicates a messaging error.
         */
        error;
    }
}