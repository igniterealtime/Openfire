/**
 * Copyright (C) 2004-2007 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.xmpp.packet;

import org.dom4j.Element;

import java.util.Iterator;

/**
 * Presence packet. Presence packets are used to express an entity's current
 * network availability and to notify other entities of that availability.
 * Presence packets are also used to negotiate and manage subscriptions to the
 * presence of other entities.<p>
 *
 * A presence optionally has a {@link Type}.
 *
 * @author Matt Tucker
 */
public class Presence extends Packet {

    /**
     * Constructs a new Presence.
     */
    public Presence() {
        this.element = docFactory.createDocument().addElement("presence");
    }

    /**
     * Constructs a new Presence with the specified type.
     *
     * @param type the presence type.
     */
    public Presence(Presence.Type type) {
        this();
        setType(type);
    }

    /**
     * Constructs a new Presence using an existing Element. This is useful
     * for parsing incoming presence Elements into Presence objects.
     *
     * @param element the presence Element.
     */
    public Presence(Element element) {
        super(element);
    }

    /**
     * Constructs a new Presence using an existing Element. This is useful
     * for parsing incoming Presence Elements into Presence objects. Stringprep validation
     * on the TO address can be disabled. The FROM address will not be validated since the
     * server is the one that sets that value.
     *
     * @param element the Presence Element.
     * @param skipValidation true if stringprep should not be applied to the TO address.
     */
    public Presence(Element element, boolean skipValidation) {
        super(element, skipValidation);
    }

    /**
     * Constructs a new Presence that is a copy of an existing Presence.
     *
     * @param presence the presence packet.
     * @see #createCopy() 
     */
    private Presence(Presence presence) {
        Element elementCopy = presence.element.createCopy();
        docFactory.createDocument().add(elementCopy);
        this.element = elementCopy;
        // Copy cached JIDs (for performance reasons)
        this.toJID = presence.toJID;
        this.fromJID = presence.fromJID;
    }

    /**
     * Returns true if the presence type is "available". This is a
     * convenience method that is equivalent to:
     *
     * <pre>getType() == null</pre>
     *
     */
    public boolean isAvailable() {
        return getType() == null;
    }

    /**
     * Returns the type of this presence. If the presence is "available", the
     * type will be <tt>null</tt> (in XMPP, no value for the type attribute is
     * defined as available).
     *
     * @return the presence type or <tt>null</tt> if "available".
     * @see Type
     */
    public Type getType() {
        String type = element.attributeValue("type");
        if (type == null) {
            return null;
        }
        else {
            return Type.valueOf(type);
        }
    }

    /**
     * Sets the type of this presence.
     *
     * @param type the presence type.
     * @see Type
     */
    public void setType(Type type) {
        element.addAttribute("type", type==null?null:type.toString());
    }

    /**
     * Returns the presence "show" value, which specifies a particular availability
     * status. If the &lt;show&gt; element is not present, this method will return
     * <tt>null</tt>. The show value can only be set if the presence type is "avaialble".
     * A <tt>null</tt> show value is used to represent "available", which is the
     * default.
     *
     * @return the presence show value..
     * @see Show
     */
    public Show getShow() {
        String show = element.elementText("show");
        if (show == null) {
            return null;
        }
        else {
            return Show.valueOf(show);
        }
    }

    /**
     * Sets the presence "show" value, which specifies a particular availability
     * status. The show value can only be set if the presence type is "available".
     * A <tt>null</tt> show value is used to represent "available", which is the
     * default.
     *
     * @param show the presence show value.
     * @throws IllegalArgumentException if the presence type is not available.
     * @see Show
     */
    public void setShow(Show show) {
        Element showElement = element.element("show");
        // If show is null, clear the subject.
        if (show == null) {
            if (showElement != null) {
                element.remove(showElement);
            }
            return;
        }
        if (showElement == null) {
            if (!isAvailable()) {
                throw new IllegalArgumentException("Cannot set 'show' if 'type' attribute is set.");
            }
            showElement = element.addElement("show");
        }
        showElement.setText(show.toString());
    }

    /**
     * Returns the status of this presence packet, a natural-language description
     * of availability status.
     *
     * @return the status.
     */
    public String getStatus() {
        return element.elementText("status");
    }

    /**
     * Sets the status of this presence packet, a natural-language description
     * of availability status.
     *
     * @param status the status.
     */
    public void setStatus(String status) {
        Element statusElement = element.element("status");
        // If subject is null, clear the subject.
        if (status == null) {
            if (statusElement != null) {
                element.remove(statusElement);
            }
            return;
        }

        if (statusElement == null) {
            statusElement = element.addElement("status");
        }
        statusElement.setText(status);
    }

    /**
     * Returns the priority. The valid priority range is -128 through 128.
     * If no priority element exists in the packet, this method will return
     * the default value of 0.
     *
     * @return the priority.
     */
    public int getPriority() {
        String priority = element.elementText("priority");
        if (priority == null) {
            return 0;
        }
        else {
            try {
                return Integer.parseInt(priority);
            }
            catch (Exception e) {
                return 0;
            }
        }
    }

    /**
     * Sets the priority. The valid priority range is -128 through 128.
     *
     * @param priority the priority.
     * @throws IllegalArgumentException if the priority is less than -128 or greater
     *      than 128.
     */
    public void setPriority(int priority) {
        if (priority < -128 || priority > 128) {
            throw new IllegalArgumentException("Priority value of " + priority +
                    " is outside the valid range of -128 through 128");
        }
        Element priorityElement = element.element("priority");
        if (priorityElement == null) {
            priorityElement = element.addElement("priority");
        }
        priorityElement.setText(Integer.toString(priority));
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
     * Returns a deep copy of this Presence.
     *
     * @return a deep copy of this Presence.
     */
    public Presence createCopy() {
        return new Presence(this);
    }

    /**
     * Represents the type of a presence packet. Note: the presence is assumed
     * to be "available" when the type attribute of the packet is <tt>null</tt>.
     * The valid types are:
     *
     *  <ul>
     *      <li>{@link #unavailable Presence.Type.unavailable} -- signals that the
     *          entity is no longer available for communication.
     *      <li>{@link #subscribe Presence.Type.subscribe} -- the sender wishes to
     *          subscribe to the recipient's presence.
     *      <li>{@link #subscribed Presence.Type.subscribed} -- the sender has allowed
     *          the recipient to receive their presence.
     *      <li>{@link #unsubscribe Presence.Type.unsubscribe} -- the sender is
     *          unsubscribing from another entity's presence.
     *      <li>{@link #unsubscribed Presence.Type.unsubcribed} -- the subscription
     *          request has been denied or a previously-granted subscription has been cancelled.
     *      <li>{@link #probe Presence.Type.probe} -- a request for an entity's current
     *          presence; SHOULD be generated only by a server on behalf of a user.
     *      <li>{@link #error Presence.Type.error} -- an error has occurred regarding
     *          processing or delivery of a previously-sent presence stanza.
     * </ul>
     */
    public enum Type {

        /**
         * Typically short text message used in line-by-line chat interfaces.
         */
        unavailable,

        /**
         * The sender wishes to subscribe to the recipient's presence.
         */
        subscribe,

        /**
         * The sender has allowed the recipient to receive their presence.
         */
        subscribed,

        /**
         * The sender is unsubscribing from another entity's presence.
         */
        unsubscribe,

        /**
         * The subscription request has been denied or a previously-granted
         * subscription has been cancelled.
         */
        unsubscribed,

        /**
         * A request for an entity's current presence; SHOULD be
         * generated only by a server on behalf of a user.
         */
        probe,

        /**
         * An error has occurred regarding processing or delivery
         * of a previously-sent presence stanza.
         */
        error;
    }

    /**
     * Represents the presence "show" value. Note: a <tt>null</tt> "show" value is the
     * default, which means "available". Valid values are:
     *
     * <ul>
     *      <li>{@link #chat Presence.Show.chat} -- the entity or resource is actively
     *          interested in chatting.
     *      <li>{@link #away Presence.Show.away} -- the entity or resource is
     *          temporarily away.
     *      <li>{@link #dnd Presence.Show.dnd} -- the entity or resource is busy
     *          (dnd = "Do Not Disturb").
     *      <li>{@link #xa Presence.Show.xa} -- the entity or resource is away for an
     *          extended period (xa = "eXtended Away").
     * </ul>
     */
    public enum Show {

        /**
         * The entity or resource is actively interested in chatting.
         */
        chat,

        /**
         * The entity or resource is temporarily away.
         */
        away,

        /**
         * The entity or resource is away for an extended period (xa = "eXtended Away").
         */
        xa,

        /**
         * The entity or resource is busy (dnd = "Do Not Disturb").
         */
        dnd;
    }
}