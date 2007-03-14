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
import java.lang.reflect.Constructor;
import java.util.List;

/**
 * An XMPP packet (also referred to as a stanza). Each packet is backed by a
 * DOM4J Element. A set of convenience methods allows easy manipulation of
 * the Element, or the Element can be accessed directly and manipulated.<p>
 *
 * There are three core packet types:<ul>
 *      <li>{@link Message} -- used to send data between users.
 *      <li>{@link Presence} -- contains user presence information or is used
 *          to manage presence subscriptions.
 *      <li>{@link IQ} -- exchange information and perform queries using a
 *          request/response protocol.
 * </ul>
 *
 * @author Matt Tucker
 */
public abstract class Packet {

    protected static DocumentFactory docFactory = DocumentFactory.getInstance();

    protected Element element;

    // Cache to and from JIDs
    protected JID toJID;
    protected JID fromJID;

    /**
     * Constructs a new Packet. The TO address contained in the XML Element will only be
     * validated. In other words, stringprep operations will only be performed on the TO JID to
     * verify that it is well-formed. The FROM address is assigned by the server so there is no
     * need to verify it.
     *
     * @param element the XML Element that contains the packet contents.
     */
    public Packet(Element element) {
        this(element, false);
    }

    /**
     * Constructs a new Packet. The JID address contained in the XML Element may not be
     * validated. When validation can be skipped then stringprep operations will not be performed
     * on the JIDs to verify that addresses are well-formed. However, when validation cannot be
     * skipped then <tt>only</tt> the TO address will be verified. The FROM address is assigned by
     * the server so there is no need to verify it. 
     *
     * @param element the XML Element that contains the packet contents.
     * @param skipValidation true if stringprep should not be applied to the TO address.
     */
    public Packet(Element element, boolean skipValidation) {
        this.element = element;
        // Apply stringprep profiles to the "to" and "from" values.
        String to = element.attributeValue("to");
        if (to != null) {
            String[] parts = JID.getParts(to);
            toJID = new JID(parts[0], parts[1], parts[2], skipValidation);
            element.addAttribute("to",  toJID.toString());
        }
        String from = element.attributeValue("from");
        if (from != null) {
            String[] parts = JID.getParts(from);
            fromJID = new JID(parts[0], parts[1], parts[2], true);
            element.addAttribute("from",  fromJID.toString());
        }
    }

    /**
     * Constructs a new Packet with no element data. This method is used by
     * extensions of this class that require a more optimized path for creating
     * new packets.
     */
    protected Packet() {

    }

    /**
     * Returns the packet ID, or <tt>null</tt> if the packet does not have an ID.
     * Packet ID's are optional, except for IQ packets.
     *
     * @return the packet ID.
     */
    public String getID() {
        return element.attributeValue("id");
    }

    /**
     * Sets the packet ID. Packet ID's are optional, except for IQ packets.
     *
     * @param ID the packet ID.
     */
    public void setID(String ID) {
        element.addAttribute("id", ID);
    }

    /**
     * Returns the XMPP address (JID) that the packet is addressed to, or <tt>null</tt>
     * if the "to" attribute is not set. The XMPP protocol often makes the "to"
     * attribute optional, so it does not always need to be set.
     *
     * @return the XMPP address (JID) that the packet is addressed to, or <tt>null</tt>
     *      if not set.
     */
    public JID getTo() {
        String to = element.attributeValue("to");
        if (to == null) {
            return null;
        }
        else {
            if (toJID != null && to.equals(toJID.toString())) {
                return toJID;
            }
            else {
                // Return a new JID that bypasses stringprep profile checking.
                // This improves speed and is safe as long as the user doesn't
                // directly manipulate the attributes of the underlying Element
                // that represent JID's.
                String[] parts = JID.getParts(to);
                toJID = new JID(parts[0], parts[1], parts[2], true);
                return toJID;
            }
        }
    }

    /**
     * Sets the XMPP address (JID) that the packet is addressed to. The XMPP protocol
     * often makes the "to" attribute optional, so it does not always need to be set.
     *
     * @param to the XMPP address (JID) that the packet is addressed to.
     */
    public void setTo(String to) {
        // Apply stringprep profiles to value.
        if (to !=  null) {
            toJID = new JID(to);
            to = toJID.toString();
        }
        element.addAttribute("to", to);
    }

    /**
     * Sets the XMPP address (JID) that the packet is address to. The XMPP protocol
     * often makes the "to" attribute optional, so it does not always need to be set.
     *
     * @param to the XMPP address (JID) that the packet is addressed to.
     */
    public void setTo(JID to) {
        toJID = to;
        if (to == null) {
            element.addAttribute("to", null);
        }
        else {
            element.addAttribute("to", to.toString());
        }
    }

    /**
     * Returns the XMPP address (JID) that the packet is from, or <tt>null</tt>
     * if the "from" attribute is not set. The XMPP protocol often makes the "from"
     * attribute optional, so it does not always need to be set.
     *
     * @return the XMPP address that the packet is from, or <tt>null</tt>
     *      if not set.
     */
    public JID getFrom() {
        String from = element.attributeValue("from");
        if (from == null) {
            return null;
        }
        else {
            if (fromJID != null && from.equals(fromJID.toString())) {
                return fromJID;
            }
            else {
                // Return a new JID that bypasses stringprep profile checking.
                // This improves speed and is safe as long as the user doesn't
                // directly manipulate the attributes of the underlying Element
                // that represent JID's.
                String[] parts = JID.getParts(from);
                fromJID = new JID(parts[0], parts[1], parts[2], true);
                return fromJID;
            }
        }
    }

    /**
     * Sets the XMPP address (JID) that the packet comes from. The XMPP protocol
     * often makes the "from" attribute optional, so it does not always need to be set.
     *
     * @param from the XMPP address (JID) that the packet comes from.
     */
    public void setFrom(String from) {
        // Apply stringprep profiles to value.
        if (from != null) {
            fromJID = new JID(from);
            from = fromJID.toString();
        }
        element.addAttribute("from", from);
    }

    /**
     * Sets the XMPP address (JID) that the packet comes from. The XMPP protocol
     * often makes the "from" attribute optional, so it does not always need to be set.
     *
     * @param from the XMPP address (JID) that the packet comes from.
     */
    public void setFrom(JID from) {
        fromJID = from;
        if (from == null) {
            element.addAttribute("from", null);
        }
        else {
            element.addAttribute("from", from.toString());
        }
    }

    /**
     * Adds the element contained in the PacketExtension to the element of this packet.
     * It is important that this is the first and last time the element contained in
     * PacketExtension is added to another Packet. Otherwise, a runtime error will be
     * thrown when trying to add the PacketExtension's element to the Packet's element.
     * Future modifications to the PacketExtension will be reflected in this Packet.
     *
     * @param extension the PacketExtension whose element will be added to this Packet's element.
     */
    public void addExtension(PacketExtension extension) {
        element.add(extension.getElement());
    }

    /**
     * Returns a {@link PacketExtension} on the first element found in this packet
     * for the specified <tt>name</tt> and <tt>namespace</tt> or <tt>null</tt> if
     * none was found.
     *
     * @param name the child element name.
     * @param namespace the child element namespace.
     * @return a PacketExtension on the first element found in this packet for the specified
     *         name and namespace or null if none was found.
     */
    public PacketExtension getExtension(String name, String namespace) {
        List extensions = element.elements(QName.get(name, namespace));
        if (!extensions.isEmpty()) {
            Class extensionClass = PacketExtension.getExtensionClass(name, namespace);
            // If a specific PacketExtension implementation has been registered, use that.
            if (extensionClass != null) {
                try {
                    Constructor constructor = extensionClass.getDeclaredConstructor(Element.class);
                    return (PacketExtension)constructor.newInstance(extensions.get(0));
                }
                catch (Exception e) {
                    // Ignore.
                }
            }
            // Otherwise, use a normal PacketExtension.
            else {
                return new PacketExtension((Element)extensions.get(0));
            }
        }
        return null;
    }

    /**
     * Deletes the first element whose element name and namespace matches the specified
     * element name and namespace.<p>
     *
     * Notice that this method may remove any child element that matches the specified
     * element name and namespace even if that element was not added to the Packet using a
     * {@link PacketExtension}.
     *
     *
     * @param name the child element name.
     * @param namespace the child element namespace.
     * @return true if a child element was removed.
     */
    public boolean deleteExtension(String name, String namespace) {
        List extensions = element.elements(QName.get(name, namespace));
        if (!extensions.isEmpty()) {
            element.remove((Element) extensions.get(0));
            return true;
        }
        return false;
    }

    /**
     * Returns the packet error, or <tt>null</tt> if there is no packet error.
     *
     * @return the packet error.
     */
    public PacketError getError() {
        Element error = element.element("error");
        if (error != null) {
            return new PacketError(error);
        }
        return null;
    }

    /**
     * Sets the packet error. Calling this method will automatically set
     * the packet "type" attribute to "error".
     *
     * @param error the packet error.
     */
    public void setError(PacketError error) {
        if (element == null) {
            throw new NullPointerException("Error cannot be null");
        }
        // Force the packet type to "error".
        element.addAttribute("type", "error");
        // Remove an existing error packet.
        if (element.element("error") != null) {
            element.remove(element.element("error"));
        }
        // Add the error element.
        element.add(error.getElement());
    }

    /**
     * Sets the packet error using the specified condition. Calling this
     * method will automatically set the packet "type" attribute to "error".
     * This is a convenience method equivalent to calling:
     *
     * <tt>setError(new PacketError(condition));</tt>
     *
     * @param condition the error condition.
     */
    public void setError(PacketError.Condition condition) {
       setError(new PacketError(condition));
    }

    /**
     * Creates a deep copy of this packet.
     *
     * @return a deep copy of this packet.
     */
    public abstract Packet createCopy();

    /**
     * Returns the DOM4J Element that backs the packet. The element is the definitive
     * representation of the packet and can be manipulated directly to change
     * packet contents.
     *
     * @return the DOM4J Element that represents the packet.
     */
    public Element getElement() {
        return element;
    }

    /**
     * Returns the textual XML representation of this packet.
     *
     * @return the textual XML representation of this packet.
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
        catch (Exception e) {
            // Ignore.
        }
        return out.toString();
    }
}