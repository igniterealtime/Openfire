package org.jivesoftware.openfire.websocket;

import java.net.InetSocketAddress;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.PacketDeliverer;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.nio.OfflinePacketDeliverer;
import org.xmpp.packet.Packet;
import org.xmpp.packet.StreamError;

/**
 * Following the conventions of the BOSH implementation, this class extends {@link VirtualConnection}
 * and delegates the expected XMPP connection behaviors to the corresponding {@link XmppWebSocket}.
 */
public class WebSocketConnection extends VirtualConnection
{
	private static final String CLIENT_NAMESPACE = "jabber:client";
    private InetSocketAddress remotePeer;
    private XmppWebSocket socket;
    private PacketDeliverer backupDeliverer;

    public WebSocketConnection(XmppWebSocket socket, InetSocketAddress remotePeer) {
    	this.socket = socket;
    	this.remotePeer = remotePeer;
    }

	@Override
    public void closeVirtualConnection()
    {
        socket.closeSession();
    }

	@Override
    public byte[] getAddress() {
        return remotePeer.getAddress().getAddress();
    }

	@Override
    public String getHostAddress() {
        return remotePeer.getAddress().getHostAddress();
    }

	@Override
    public String getHostName()  {
    	return remotePeer.getHostName();
    }

	@Override
    public void systemShutdown() {
        deliverRawText(new StreamError(StreamError.Condition.system_shutdown).toXML());
    	close();
    }
    
	@Override
    public void deliver(Packet packet) throws UnauthorizedException
    {
    	if (Namespace.NO_NAMESPACE.equals(packet.getElement().getNamespace())) {
    		packet.getElement().add(Namespace.get(CLIENT_NAMESPACE));
    	}
    	if (validate()) {
    		deliverRawText(packet.toXML());
    	} else {
    		// use fallback delivery mechanism (offline)
    		getPacketDeliverer().deliver(packet);
    	}
    }

	@Override
    public void deliverRawText(String text)
    {
    	socket.deliver(text);
    }

	@Override
    public boolean validate() {
        return socket.isWebSocketOpen();
    }

	@Override
    public boolean isSecure() {
        return socket.isWebSocketSecure();
    }

	@Override
    public PacketDeliverer getPacketDeliverer() {
    	if (backupDeliverer == null) {
    		backupDeliverer = new OfflinePacketDeliverer();
    	}
        return backupDeliverer;
    }

	@Override
	public boolean isCompressed() {
		return XmppWebSocket.isCompressionEnabled();
	}
}
