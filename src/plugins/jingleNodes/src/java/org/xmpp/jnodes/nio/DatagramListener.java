package org.xmpp.jnodes.nio;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

public interface DatagramListener {

    public void datagramReceived(SelDatagramChannel channel, ByteBuffer buffer, SocketAddress address);

}
