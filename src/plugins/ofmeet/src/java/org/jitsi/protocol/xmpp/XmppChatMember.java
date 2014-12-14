/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;

/**
 * XMPP extended interface of {@link ChatRoomMember}.
 *
 * @author Pawel Domas
 */
public interface XmppChatMember
    extends ChatRoomMember
{
    /**
     * Returns ths original user's connection Jabber ID and not the MUC address.
     */
    String getJabberID();
}
