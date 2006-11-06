/**
 * $RCSfile$
 * $Revision: 2624 $
 * $Date: 2005-05-11 12:56:11 -0700 (Wed, 11 May 2005) $
 *
 * Copyright 2004 Jive Software.
 *
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
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

package org.xmpp.packet;

import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.QName;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A packet extension represents a child element of a Packet for a given qualified name. The
 * PacketExtension acts as a wrapper on a child element the same way Packet does for a whole
 * element. The wrapper provides an easy way to handle the packet extension.<p>
 *
 * Subclasses of this class should be registered in the static variable
 * <tt>registeredExtensions</tt> when loaded. The registration process associates the new subclass
 * with a given qualified name (ie. element name and namespace). This information will be used by
 * {@link Packet#getExtension(String, String)} for locating the corresponding PacketExtension
 * subclass to return for the requested qualified name.
 *
 * @author Gaston Dombiak
 */
public abstract class PacketExtension {
    
    protected static DocumentFactory docFactory = DocumentFactory.getInstance();
    /**
     * Subclasses of PacketExtension should register the element name and namespace that the
     * subclass is using.
     */
    protected static Map<QName, Class> registeredExtensions = new ConcurrentHashMap<QName, Class>();

    protected Element element;

    /**
     * Returns the extension class to use for the specified element name and namespace. For
     * instance, the DataForm class should be used for the element "x" and
     * namespace "jabber:x:data".
     *
     * @param name the child element name.
     * @param namespace the child element namespace.
     * @return the extension class to use for the specified element name and namespace.
     */
    public static Class getExtensionClass(String name, String namespace) {
        return registeredExtensions.get(QName.get(name, namespace));
    }

    /**
     * Constructs a new Packet extension using the specified name and namespace.
     *
     * @param name the child element name.
     * @param namespace the child element namespace.
     */
    public PacketExtension(String name, String namespace) {
        this.element = docFactory.createDocument().addElement(name, namespace);
    }

    /**
     * Constructs a new PacketExtension.
     *
     * @param element the XML Element that contains the packet extension contents.
     */
    public PacketExtension(Element element) {
        this.element = element;
    }

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
     * Creates a deep copy of this packet extension.
     *
     * @return a deep copy of this packet extension.
     */
    public abstract PacketExtension createCopy();
}
