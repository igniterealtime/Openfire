/**
 * $Revision$
 * $Date$
 *
 * Copyright 2006-2010 Daniel Henninger.  All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package net.sf.kraken.protocols.xmpp.packet;

import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * A PacketExtension that implements XEP-0224: Attention
 * 
 * This extension is expected to be added to message stanzas of type 'headline.'
 * Please refer to the XEP for more implementation guidelines.
 * 
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a
 *      href="http://xmpp.org/extensions/xep-0224.html">XEP-0224:&nbsp;Attention</a>
 */
public class AttentionExtension implements PacketExtension {

    /**
     * The XML element name of an 'attention' extension.
     */
    public static final String ELEMENT_NAME = "attention";

    /**
     * The namespace that qualifies the XML element of an 'attention' extension.
     */
    public static final String NAMESPACE = "urn:xmpp:attention:0";

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.smack.packet.PacketExtension#getElementName()
     */
    public String getElementName() {
        return ELEMENT_NAME;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.smack.packet.PacketExtension#getNamespace()
     */
    public String getNamespace() {
        return NAMESPACE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jivesoftware.smack.packet.PacketExtension#toXML()
     */
    public String toXML() {
        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(getElementName()).append(" xmlns=\"").append(
                getNamespace()).append("\"/>");
        return sb.toString();
    }

    /**
     * A {@link PacketExtensionProvider} for the {@link AttentionExtension}. As
     * Attention elements have no state/information other than the element name
     * and namespace, this implementation simply returns new instances of
     * {@link AttentionExtension}.
     * 
     * @author Guus der Kinderen, guus.der.kinderen@gmail.com
s     */
    public static class Provider implements PacketExtensionProvider {

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.jivesoftware.smack.provider.PacketExtensionProvider#parseExtension
         * (org.xmlpull.v1.XmlPullParser)
         */
        public PacketExtension parseExtension(XmlPullParser arg0)
                throws Exception {
            return new AttentionExtension();
        }
    }
}
