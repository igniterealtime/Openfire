package org.jivesoftware.messenger;

public interface Component {

    void processPacket(XMPPPacket packet);
}
