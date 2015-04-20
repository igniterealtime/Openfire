/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.impl.protocol.xmpp;

import net.java.sip.communicator.service.protocol.*;
import org.jivesoftware.smack.packet.*;

/**
 * Partial implementation of {@link OperationSetMeetToolsImpl}.
 *
 * @author Pawel Domas
 */
public class OperationSetMeetToolsImpl
    implements OperationSetJitsiMeetTools
{
    @Override
    public void addSupportedFeature(String featureName)
    {

    }

    @Override
    public void removeSupportedFeature(String s)
    {

    }

    @Override
    public void sendPresenceExtension(ChatRoom chatRoom,
                                      PacketExtension extension)
    {
        ((ChatRoomImpl)chatRoom).sendPresenceExtension(extension);
    }

    @Override
    public void setPresenceStatus(ChatRoom chatRoom, String statusMessage)
    {

    }

    @Override
    public void addRequestListener(
        JitsiMeetRequestListener listener)
    {

    }

    @Override
    public void removeRequestListener(
        JitsiMeetRequestListener listener)
    {

    }
}
