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
import org.dom4j.Element;

public class PayloadFragment extends AbstractFragment {

    public PayloadFragment(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws XMLStreamException {
        xmlSerializer.writeStartElement(name);
        xmlSerializer.writeDefaultNamespace(namespace);
        Iterator frags = getFragments();
        while (frags != null && frags.hasNext()) {
            ((XMPPFragment)frags.next()).send(xmlSerializer, version);
        }
        xmlSerializer.writeEndElement();
    }

    public XMPPFragment createDeepCopy() {

        PayloadFragment payload = new PayloadFragment(namespace, name);
        Iterator frags = getFragments();
        while (frags != null && frags.hasNext()) {
            payload.addFragment((XMPPFragment)frags.next());
        }
        return payload;
    }

    /**
     * <p>Converts the given fragment into a payload fragment.</p>
     * <p>All sub-elements of the original fragment are broken out into separate
     * MetaDataFragments. Currently only supports XMPPDOMFragment objects and their descendents.
     * During conversion attributes of the root element is lost.</p>
     *
     * @param frag The fragment to convert (must be XMPPDOMFragment or descendent)
     * @return The converted fragment with all sub-fragments broken up into separate fragments.
     * @throws IllegalArgumentException If the given fragment does not implement XMPPDOMFragment
     */
    public static PayloadFragment convertToPayload(XMPPFragment frag) throws IllegalArgumentException {
        PayloadFragment payload = null;
        if (frag instanceof XMPPDOMFragment) {
            XMPPDOMFragment dom = (XMPPDOMFragment)frag;
            payload = new PayloadFragment(dom.getNamespace(), dom.getName());
            Iterator frags = dom.getRootElement().elementIterator();
            while (frags.hasNext()) {
                payload.addFragment(new MetaDataFragment((Element)frags.next()));
            }
        }
        else {
            throw new IllegalArgumentException();
        }
        return payload;
    }
}
