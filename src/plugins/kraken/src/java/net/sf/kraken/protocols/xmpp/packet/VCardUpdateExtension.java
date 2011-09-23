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

import net.sf.kraken.type.NameSpace;

import org.jivesoftware.smack.packet.PacketExtension;

/**
 * Borrowed directly from Spark.
 */
public class VCardUpdateExtension implements PacketExtension {

    private String photoHash;

    public void setPhotoHash(String hash) {
        photoHash = hash;
    }

    public String getElementName() {
        return "x";
    }

    public String getNamespace() {
        return NameSpace.VCARD_TEMP_X_UPDATE;
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        buf.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace()).append("\">");
        buf.append("<photo>");
        buf.append(photoHash);
        buf.append("</photo>");
        buf.append("</").append(getElementName()).append(">");
        return buf.toString();
    }
    
}
