package org.ifsoft.websockets;

import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.Certificate;

import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.xmpp.packet.Packet;



public class WSConnection extends VirtualConnection
{
	private static Logger Log = LoggerFactory.getLogger( "WSConnection" );
    private SessionPacketRouter router;
    private String remoteAddr;
    private String hostName;
    private LocalClientSession session;
    private XMPPServlet.XMPPWebSocket socket;
    private boolean isSecure = false;

    public WSConnection( String remoteAddr, String hostName ) {
    	this.remoteAddr = remoteAddr;
    	this.hostName = hostName;
    }

    public void setSocket( XMPPServlet.XMPPWebSocket socket ) {
    	this.socket = socket;
    }

	public boolean isSecure() {
        return isSecure;
    }

	public void setSecure(boolean isSecure) {
        this.isSecure = isSecure;
    }

	public SessionPacketRouter getRouter()
	{
		return router;
	}

	public void setRouter(SessionPacketRouter router)
	{
		this.router = router;
	}

    public void closeVirtualConnection()
    {
        Log.debug("WSConnection - close ");
        this.socket.disconnect();
    }

    public byte[] getAddress() {
        return remoteAddr.getBytes();
    }

    public String getHostAddress() {
        return remoteAddr;
    	// return wsSocket.getHostAddress();
    }

    public String getHostName()  {
    	return ( hostName != null ) ? hostName : "0.0.0.0";
    	// return ( wsSocket.getHostName() == null ) ? wsSocket.getHostName() : "0.0.0.0";
    }

    public void systemShutdown() {

    }

    public void deliver(Packet packet) throws UnauthorizedException
    {
		deliverRawText(packet.toXML());
    }

    public void deliverRawText(String text)
    {
    	this.socket.deliver(text);
    }

    @Override
    public ConnectionConfiguration getConfiguration()
    {
        // TODO Here we run into an issue with the ConnectionConfiguration introduced in Openfire 4:
        //      it is not extensible in the sense that unforeseen connection types can be added.
        //      For now, null is returned, as this object is likely to be unused (its lifecycle is
        //      not managed by a ConnectionListener instance).
        return null;
    }

    public Certificate[] getPeerCertificates() {
        return null;
    }

}
