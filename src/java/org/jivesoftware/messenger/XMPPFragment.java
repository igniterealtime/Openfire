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

import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * <p>Represents an XML fragment with particular meaning in XMPP.</p>
 * <p>XMPP systems will be dealing with many XML fragments and this class
 * allows the system to generically handle fragments without particular knowledge
 * of what they contain (and hiding optimizations in representing, storing,
 * and sending these fragments). The most common fragments the system deals
 * with are XMPP packets of the three cardinal types: Message, Presence,
 * and IQ. However, these packets can contain protocol specific, or custom
 * meta-data represented by additional fragments.</p>
 * <p>Fragments allows parsers to breakdown XMPP packets into fragment object
 * models. A higher abstraction level than XML DOM's that must create a node
 * for every XML element. However, they also may be implemented as simple
 * wrappers around raw XML DOM's if necessary.</p>
 *
 * @author Iain Shigeoka
 */
public interface XMPPFragment {

    /**
     * <p>Returns a namespace associated with this meta-data or null if none has been associated.</p>
     *
     * @return the namespace associated with this meta-data.
     */
    public String getNamespace();

    /**
     * <p>Sets a namespace associated with this meta-data or null if none has been associated.</p>
     *
     * @param namespace the namespace associated with this meta-data.
     */
    public void setNamespace(String namespace);

    /**
     * <p>Returns a name associated with this meta-data or null if none has been associated.</p>
     *
     * @return the name associated with this meta-data.
     */
    public String getName();

    /**
     * Sets a namespace associated with this meta-data or null if none has been associated.
     *
     * @param name The namespace associated with this meta-data
     */
    public void setName(String name);

    /**
     * <p>Sends the fragment as a string to the given writer.</p>
     * <p>A fragment will always be written as a completely self
     * contained, well-formed XML fragment.</p>
     *
     * @param xmlSerializer The serializer to send the jabber packet
     * @param version       The XMPP version for the stream (0 is old Jabber, 1 is XMPP 1.0)
     * @throws XMLStreamException If there is a problem with the connection
     */
    void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException;

    /**
     * <p>Generates an independent, deep copy of this packet.</p>
     *
     * @return The deep copy of the packet
     */
    XMPPFragment createDeepCopy();

    /**
     * <p>Adds another fragment as a child fragment of this one.</p>
     * <p>Child fragments can be used to build a DOM like tree of fragments. However,
     * the typical use case in Messenger packets is to store meta-data as child fragments
     * and standard data as member fields accessed via standard setter/getters.</p>
     *
     * @param fragment the fragment to add to this packet.
     */
    void addFragment(XMPPFragment fragment);

    /**
     * <p>Obtain an iterator of child fragments.</p>
     *
     * @return An iterator over all child fragments.
     */
    Iterator getFragments();

    /**
     * Returns the fragment whose name and namespace matches the requested ones or <tt>null</tt> if
     * none. It is assumed that there is at most one fragment per name and namespace.
     *
     * @param name      the name of the fragment to search.
     * @param namespace the namespace of the fragment to search.
     * @return the fragment whose name and namespace matches the requested ones or <tt>null</tt> if
     *         none.
     */
    XMPPFragment getFragment(String name, String namespace);

    /**
     * Remove any child fragments from this fragment.
     */
    void clearFragments();

    /**
     * <p>Make a best guess estimate on size in bytes of an XML string representation of
     * this fragment.</p>
     * <p>There are several server operations that can be helped by hints on fragment sizes
     * such as offline storage, auditing, caching, etc. To this end, this method provides
     * a guess where speed of calculation and lowest resource impact are highest priorities
     * (you should not generate the XML string and count the bytes).</p>
     *
     * @return an estimate in bytes of the size of this fragment as an XML string 
     *      (assume UTF-8 encoding)
     */
    int getSize();
}
