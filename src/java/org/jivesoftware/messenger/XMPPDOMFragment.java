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

import org.jivesoftware.util.XPPWriter;
import org.jivesoftware.messenger.spi.AbstractFragment;
import java.util.Iterator;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

/**
 * Stores the fragment in a dom4j DOM model. Efficiency of the fragment is
 * relatively low but is the most flexible way to store fragment information.
 *
 * @author Iain Shigeoka
 */
public class XMPPDOMFragment extends AbstractFragment implements XMPPFragment {

    /**
     * The document holding this fragment's data.
     */
    private Element root;

    /**
     * Constructor using a given Document to represent the packet.
     */
    public XMPPDOMFragment(Element root) {
        this.root = root;
        name = root.getName();
        namespace = root.getNamespaceURI();
    }

    /**
     * Constructor creates it's own Document to represent the packet.
     */
    public XMPPDOMFragment() {
        root = DocumentHelper.createElement("jive");
    }

    /**
     * Obtain the root element of the DOM tree representing the data in this fragment.
     *
     * @return the root element of the DOM tree or null if none has been set
     */
    public Element getRootElement() {
        return root;
    }

    public void send(XMLStreamWriter xmlSerializer, int version) throws
            XMLStreamException {
        XPPWriter.write(root, xmlSerializer);
    }

    public XMPPFragment createDeepCopy() {
        XMPPFragment frag = new XMPPDOMFragment((Element)root.clone());

        Iterator frags = getFragments();
        while (frags.hasNext()) {
            frag.addFragment((XMPPFragment)frags.next());
        }
        return frag;
    }
}
