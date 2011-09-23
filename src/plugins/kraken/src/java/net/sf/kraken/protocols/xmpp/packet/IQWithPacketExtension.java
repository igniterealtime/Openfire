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

import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.PacketExtension;

/**
 * @author Daniel Henninger
 */
public class IQWithPacketExtension extends IQ {

    public IQWithPacketExtension() {
    }

    public IQWithPacketExtension(PacketExtension extension) {
        this.addExtension(extension);
    }

    public IQWithPacketExtension(PacketExtension extension, IQ.Type type) {
        this.addExtension(extension);
        this.setType(type);
    }

    public IQWithPacketExtension(String to, PacketExtension extension) {
        this.addExtension(extension);
        this.setTo(to);
    }

    public IQWithPacketExtension(String to, PacketExtension extension, IQ.Type type) {
        this.addExtension(extension);
        this.setTo(to);
        this.setType(type);
    }

    @Override
    public String getChildElementXML() {
        return this.getExtensionsXML();
    }

}
