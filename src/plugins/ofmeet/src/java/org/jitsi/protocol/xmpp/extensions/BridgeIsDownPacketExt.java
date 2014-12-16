/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp.extensions;

import net.java.sip.communicator.impl.protocol.jabber.extensions.*;

/**
 * Packet extension sent in focus MUC presence to notify users about JVB failure.
 *
 * @author Pawel Domas
 */
public class BridgeIsDownPacketExt
    extends AbstractPacketExtension
{
    private final static String ELEMENT_NAME = "bridgeIsDown";

    public BridgeIsDownPacketExt()
    {
        super("", ELEMENT_NAME);
    }
}
