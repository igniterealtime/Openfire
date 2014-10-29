package com.ifsoft.websockets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.TaskEngine;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.session.*;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;

import java.security.cert.Certificate;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import org.xmpp.packet.*;

import org.dom4j.*;


@WebSocket(maxTextMessageSize = 64 * 1024) public class HubSocket
{
	private static Logger Log = LoggerFactory.getLogger( "HubSocket" );
	private HeartbeatTask heartbeatTask = null;
	private String localDomain;
	private List<String> remoteDomains;
	private HubConnection conn1 = null;
	private HubConnection conn2 = null;

    @SuppressWarnings("unused") private Session session;

    public HubSocket(String localDomain, String remoteDomains) {
		this.localDomain = localDomain;
		this.remoteDomains = Arrays.asList(remoteDomains.split(","));
    }

    public void sendString(String string)
    {
        try {
			session.getRemote().sendString(string);

        } catch (Exception e) {
            Log.error("sendString error", e);
        }
	}

	public void disconnect()
	{
		try {
			if (session != null && session.isOpen())
			{
				session.close();
			}
		} catch ( Exception e ) {

			try {
				session.disconnect();
			} catch ( Exception e1 ) {

			}
		}

		session = null;
	}

    @OnWebSocketClose public void onClose(int statusCode, String reason)
    {
        Log.info("HubSocket Connection closed: " + statusCode + " " + reason);

        if (heartbeatTask != null)
        {
			TaskEngine.getInstance().cancelScheduledTask(heartbeatTask);
		}
        this.session = null;
    }

    @OnWebSocketConnect public void onConnect(Session session)
    {
        Log.info("HubSocket onConnect: " + session);
        this.session = session;
        try {
			heartbeatTask = new HeartbeatTask();
			TaskEngine.getInstance().scheduleAtFixedRate(heartbeatTask, 0, 10000);

			conn1 = new HubConnection( session.getRemoteAddress().getAddress().getHostAddress(), session.getRemoteAddress().getAddress().getHostName() );
			conn1.setSocket(this);
			StreamID streamID1 = new BasicStreamID("url" + System.currentTimeMillis());
			HubServerSession xmppSession = new HubServerSession(localDomain, conn1, streamID1);
			conn1.registerCloseListener(new HubServerSessionListener(), xmppSession);
			xmppSession.setLocalDomain(localDomain);

			conn2 = new HubConnection( session.getRemoteAddress().getAddress().getHostAddress(), session.getRemoteAddress().getAddress().getHostName() );
			conn2.setSocket(this);
			StreamID streamID2 = new BasicStreamID("url" + System.currentTimeMillis());
			HubOutgoingServerSession xmppOutSession = new HubOutgoingServerSession(localDomain, conn2, streamID2);
			conn2.init(xmppOutSession);
			xmppOutSession.setAddress(new JID(null, localDomain, null));
			xmppOutSession.addHostname(localDomain);
			SessionManager.getInstance().outgoingServerSessionCreated((LocalOutgoingServerSession) xmppOutSession);

			for (String remoteDomain : remoteDomains)
			{
				xmppSession.addValidatedDomain(remoteDomain);
				xmppOutSession.addAuthenticatedDomain(remoteDomain);
				XMPPServer.getInstance().getRoutingTable().addServerRoute(new JID(null, remoteDomain, null, true), xmppOutSession);
			}



        } catch (Exception e) {
            Log.error("onConnect error", e);
        }
    }

    @OnWebSocketMessage  public void onMessage(String msg)
    {
        Log.info("HubSocket OnWebSocketMessage: " + msg);

		if ( !"".equals( msg.trim()) && conn1 != null)
		{
			try {
				Log.debug("onMessage : Received : " + msg );
				conn1.getRouter().route(DocumentHelper.parseText(msg).getRootElement());

			} catch ( Exception e ) {
				Log.error( "An error occurred while attempting to route the packet : ", e );
			}
		}
    }

    @OnWebSocketError  public void onError(Throwable e)
    {
        Log.info("HubSocket OnWebSocketError", e);
    }

    private class HeartbeatTask extends TimerTask
    {
        @Override public void run()
        {
			try {
				session.getRemote().sendString(" ");

			} catch (Exception e) {
				Log.error("HeartbeatTask error", e);
			}
        }
	}

    private class HubServerSessionListener implements ConnectionCloseListener
    {
        public void onConnectionClose(Object handback)
        {
            IncomingServerSession session = (IncomingServerSession)handback;

            for (String hostname : session.getValidatedDomains())
            {
                SessionManager.getInstance().unregisterIncomingServerSession(hostname, session);
            }
        }
    }

	public class HubConnection extends VirtualConnection
	{
		private SessionPacketRouter router;
		private String remoteAddr;
		private String hostName;
		private HubSocket socket;
		private boolean isSecure = true;

		public HubConnection( String remoteAddr, String hostName ) {
			this.remoteAddr = remoteAddr;
			this.hostName = hostName;
		}

		public void setSocket( HubSocket socket ) {
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
			Log.debug("HubConnection - close ");
			this.socket.disconnect();
		}

		public byte[] getAddress() {
			return remoteAddr.getBytes();
		}

		public String getHostAddress() {
			return remoteAddr;
		}

		public String getHostName()  {
			return ( hostName != null ) ? hostName : "0.0.0.0";
		}

		public void systemShutdown() {

		}

		public void deliver(Packet packet) throws UnauthorizedException
		{
			deliverRawText(packet.toXML());
		}

		public void deliverRawText(String text)
		{
			this.socket.sendString(text);
		}

		public Certificate[] getPeerCertificates() {
			return null;
		}

	}
}