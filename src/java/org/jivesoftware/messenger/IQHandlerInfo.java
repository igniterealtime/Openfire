/* RCSFile: $
 * Revision: $
 * Date: $
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * <p>A simple meta-data class that stores several related tools for
 * generic IQ protocol handling.</p>
 * <p/>
 * <p>To handle an IQ packet, the server needs to know:</p>
 * <ul>
 * <li>The fully qualified name of the iq sub-element. IQ packets are
 * identified using this information when matching to a handler.</li>
 * <li>The IQHandler that will handle this packet if addressed to the
 * server (no 'to' attribute).</li>
 * <li>The IQ parser to use to generate the correct IQ packet.</li>
 * </ul>
 * <p/>
 * <p>We provide this information by having all IQHandlers report
 * their info. Interested parties can watch for IQHandlers in the service
 * lookup and build appropriate data structures on the current state of
 * IQ handlers in the system.</p>
 *
 * @author Iain Shigeoka
 */
public class IQHandlerInfo {

    private String name;
    private String namespace;
    private Class iq;

    /**
     * <p>Construct an info object.</p>
     * <p>Note: the IQ child class must have a public, no-arg constructor.</p>
     *
     * @param name      The name of the root iq element
     * @param namespace The namespace of the root iq element
     * @param iq        The class of IQ packet to use in parsing
     */
    public IQHandlerInfo(String name, String namespace, Class iq) {
        this.name = name;
        this.namespace = namespace;
        this.iq = iq;
    }

    /**
     * <p>Obtain the name of the root iq element for this packet type.</p>
     *
     * @return The name of the root iq element
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Obtain the namespace of the root iq element for this packet type.</p>
     *
     * @return the namespace of the root iq element.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * <p>Obtain the name of the root iq element for this packet type.</p>
     *
     * @return the name of the root iq element.
     */
    /*public IQ parse(XMLStreamReader xpp) throws XMLStreamException {
        IQ packet = null;
        try {
            packet = (IQ)iq.newInstance();
            packet.parse(xpp);
        }
        catch (InstantiationException e) {
            throw new XMLStreamException(e.getMessage());
        }
        catch (IllegalAccessException e) {
            throw new XMLStreamException(e.getMessage());
        }
        return packet;
    }*/
}
