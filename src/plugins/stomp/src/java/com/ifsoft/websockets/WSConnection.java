package com.ifsoft.websockets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.Certificate;

import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.xmpp.packet.Packet;

import com.ifsoft.websockets.servlet.XMPPServlet;

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

    public Certificate[] getPeerCertificates() {
        return null;
    }

}
