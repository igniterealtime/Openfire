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
 * See: http://code.google.com/apis/talk/jep_extensions/gmail.html
 *
 * @author Daniel Henninger
 */
public class GoogleNewMailExtension implements PacketExtension {

    public GoogleNewMailExtension() {
        
    }

    public static String ELEMENT_NAME = "new-mail";
    public static String NAMESPACE = "google:mail:notify";

    public String getElementName() {
        return ELEMENT_NAME;
    }

    public String getNamespace() {
        return NAMESPACE;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace()).append("\"/>");
        return buf.toString();
    }

    public static class Provider implements PacketExtensionProvider {

        public PacketExtension parseExtension(XmlPullParser xmlPullParser) throws Exception {
            return new GoogleNewMailExtension();
        }
        
    }

}
