package org.jivesoftware.messenger;

import org.xmpp.packet.Packet;

public interface Component {

    void processPacket(Packet packet);
}
