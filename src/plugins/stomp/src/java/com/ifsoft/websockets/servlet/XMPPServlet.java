package com.ifsoft.websockets.servlet;

import org.jivesoftware.util.JiveGlobals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.*;
import java.util.*;
import java.text.*;
import java.net.*;
import java.security.cert.Certificate;
import java.util.concurrent.ConcurrentHashMap;
import java.math.BigInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;
import org.eclipse.jetty.util.FutureCallback;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;

import org.jivesoftware.util.ParamUtils;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.database.SequenceManager;
import org.jivesoftware.openfire.component.InternalComponentManager;

import org.jivesoftware.openfire.plugin.StompPlugin;
import com.ifsoft.websockets.*;

import org.xmpp.packet.*;

import org.dom4j.*;

import asia.stampy.common.gateway.HostPort;
import asia.stampy.common.gateway.SecurityMessageListener;
import asia.stampy.common.gateway.StampyMessageListener;
import asia.stampy.common.heartbeat.HeartbeatContainer;
import asia.stampy.common.heartbeat.StampyHeartbeatContainer;
import asia.stampy.common.message.StampyMessage;
import asia.stampy.common.message.StompMessageType;
import asia.stampy.server.listener.subscription.StampyAcknowledgementHandler;
import asia.stampy.server.listener.validate.ServerMessageValidationListener;
import asia.stampy.server.message.error.ErrorMessage;
import asia.stampy.server.message.message.MessageMessage;

import asia.stampy.server.openfire.*;
import asia.stampy.server.openfire.connect.*;
import asia.stampy.server.openfire.heartbeat.*;
import asia.stampy.server.openfire.receipt.*;
import asia.stampy.server.openfire.subscription.*;
import asia.stampy.server.openfire.transaction.*;


public final class XMPPServlet extends WebSocketServlet
{
    private static Logger Log = LoggerFactory.getLogger( "XMPPServlet" );

    private ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> sockets;
    private String remoteAddr;
    private StompPlugin plugin;
    private ServerOpenfireMessageGateway gateway = new ServerOpenfireMessageGateway();

    public XMPPServlet()
    {
		plugin = (StompPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("stomp");
    	sockets = plugin.getSockets();
    }

	@Override public void configure(WebSocketServletFactory factory)
	{
		//factory.getPolicy().setIdleTimeout(10000);
		factory.setCreator(new WSocketCreator());
	}

	public class WSocketCreator implements WebSocketCreator
	{
		@Override public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
		{
			for (String subprotocol : req.getSubProtocols())
			{
				//if ("xmpp".equals(subprotocol))
				//{
					XMPPWebSocket socket =  new XMPPWebSocket();

					if (doWebSocketConnect(req.getHttpServletRequest(), socket))
					{
						resp.setAcceptedSubProtocol(subprotocol);
						return socket;

					} else return null;
				//}
			}
			return null;
		}

		private boolean doWebSocketConnect(HttpServletRequest request, XMPPWebSocket socket)
		{
/*
			try {
				String username = URLDecoder.decode( ParamUtils.getParameter(request, "username"), "UTF-8");
				String password = URLDecoder.decode( ParamUtils.getParameter(request, "password"), "UTF-8");
				String resource = URLDecoder.decode( ParamUtils.getParameter(request, "resource"), "UTF-8");
				String register = ParamUtils.getParameter(request, "register");

				username = JID.escapeNode( username );

				String user = username.equals("null") ? resource : username;
				String digest = getMD5(user + password + resource );
				JID userJid = XMPPServer.getInstance().createJID(user, resource);

				Log.info( digest + " : doWebSocketConnect : Digest created for " + userJid + " : " + register );

				LocalClientSession session = (LocalClientSession) SessionManager.getInstance().getSession(userJid);

				if (session != null)
				{
					int conflictLimit = SessionManager.getInstance().getConflictKickLimit();

					if (conflictLimit == SessionManager.NEVER_KICK) {
						return false;
					}

					int conflictCount = session.incrementConflictCount();

					if (conflictCount > conflictLimit) {
						session.close();
						SessionManager.getInstance().removeSession(session);
					}
					else {
						return false;
					}
				}

				// get remote addr
				String remoteAddr = request.getRemoteAddr();

				if ( JiveGlobals.getProperty("websockets.header.remoteaddr") != null && request.getHeader( JiveGlobals.getProperty("websockets.header.remoteaddr") ) != null) {
					remoteAddr = request.getHeader( JiveGlobals.getProperty("websockets.header.remoteaddr") );
				}

				try {

					WSConnection wsConnection = new WSConnection( remoteAddr, request.getRemoteHost() );
					socket.setWSConnection(digest, wsConnection);

					AuthToken authToken;

					try {
						session = SessionManager.getInstance().createClientSession( wsConnection, new BasicStreamID("url" + System.currentTimeMillis()));
						wsConnection.setRouter( new SessionPacketRouter( session ) );

						if (username.equals("null") && password.equals("null"))	// anonymous user
						{
							authToken = new AuthToken(resource, true);

						} else {

							try {
								String userName = JID.unescapeNode(username);

								if (register != null && register.equals("true") && XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled())	// if register, create new user
								{
									UserManager userManager = XMPPServer.getInstance().getUserManager();

									try {
										userManager.getUser(userName);
									}
									catch (UserNotFoundException e) {
										userManager.createUser(userName, password, null, null);
									}
								}

								authToken = AuthFactory.authenticate( userName, password );
							} catch ( UnauthorizedException e ) {
								Log.error( "An error occurred while attempting to create a web socket (USERNAME: " + username + " RESOURCE: " + resource + " ) : ", e );
								return false;
							} catch ( Exception e ) {
								Log.error( "An error occurred while attempting to create a web socket : ", e );
								return false;
							}
						}

						session.setAuthToken(authToken, resource);
						socket.setSession( session );

					}
					catch (Exception e1) {
						Log.error( "An error occurred while attempting to create a new socket " + e1);
						return false;
					}

					Log.info( "Created new socket for digest " + digest );
					Log.info( "Total websockets created : " + sockets.size() );

				} catch ( Exception e ) {
					Log.error( "An error occurred while attempting to create a new socket " + e);
					return false;
				}


			} catch ( Exception e ) {
				if (socket.getSession() != null) SessionManager.getInstance().removeSession(socket.getSession());
				return false;
			}
*/

			StampyHeartbeatContainer heartbeat = new HeartbeatContainer();
			ServerOpenfireChannelHandler channelHandler = new ServerOpenfireChannelHandler();
			channelHandler.setGateway(gateway);
			channelHandler.setHeartbeatContainer(heartbeat);
			gateway.setHandler(channelHandler);

			SecurityMessageListener sml = new SecurityMessageListener() {

				@Override
				public void messageReceived(StampyMessage<?> message, HostPort hostPort) throws Exception {
					// simply do nothing
				}

				@Override
				public boolean isForMessage(StampyMessage<?> message) {
					return false;
				}

				@Override
				public StompMessageType[] getMessageTypes() {
					return null;
				}
			};
			gateway.addMessageListener(sml);

			ServerMessageValidationListener smvl = new ServerMessageValidationListener();
			gateway.addMessageListener(smvl);

			OpenfireConnectResponseListener ncrl = new OpenfireConnectResponseListener();
			ncrl.setGateway(gateway);
			gateway.addMessageListener(ncrl);

			OpenfireConnectStateListener ncsl = new OpenfireConnectStateListener();
			ncsl.setGateway(gateway);
			gateway.addMessageListener(ncsl);

			OpenfireHeartbeatListener nhbl = new OpenfireHeartbeatListener();
			nhbl.setHeartbeatContainer(heartbeat);
			nhbl.setGateway(gateway);
			gateway.addMessageListener(nhbl);

			OpenfireTransactionListener ntl = new OpenfireTransactionListener();
			ntl.setGateway(gateway);
			gateway.addMessageListener(ntl);

			StampyAcknowledgementHandler sah = new StampyAcknowledgementHandler() {
				@Override
				public void noAcknowledgementReceived(String id) {
					Log.info("No acknowledgement received for " + id);
				}

				@Override
				public void nackReceived(String id, String receipt, String transaction) throws Exception {
					Log.info("Nack message received for " + id);
				}

				@Override
				public void ackReceived(String id, String receipt, String transaction) throws Exception {
					Log.info("Ack message received for " + id);
				}
			};
			OpenfireAcknowledgementListenerAndInterceptor nalai = new OpenfireAcknowledgementListenerAndInterceptor();
			nalai.setAckTimeoutMillis(200);
			nalai.setHandler(sah);
			nalai.setGateway(gateway);
			gateway.addOutgoingMessageInterceptor(nalai);
			gateway.addMessageListener(nalai);

			OpenfireReceiptListener nrl = new OpenfireReceiptListener();
			nrl.setGateway(gateway);
			gateway.addMessageListener(nrl);

			gateway.addMessageListener(new StampyMessageListener() {

				@Override
				public void messageReceived(StampyMessage<?> message, HostPort hostPort) throws Exception {
					Log.info("server received: " + message.getMessageType() + " " + message.getHeader().getHeaders());
				}

				@Override
				public boolean isForMessage(StampyMessage<?> message) {
					// message listener for all messages
					return true;
				}

				@Override
				public StompMessageType[] getMessageTypes() {
					// all message types
					return StompMessageType.values();
				}
			});

			socket.setHandler(channelHandler, request.getRemoteHost(), request.getRemotePort());

			return true;
		}

		private String getMD5(String input)
		{
			try {
				MessageDigest md = MessageDigest.getInstance("MD5");
				byte[] messageDigest = md.digest(input.getBytes());
				BigInteger number = new BigInteger(1, messageDigest);
				String hashtext = number.toString(16);
				// Now we need to zero pad it if you actually want the full 32 chars.
				while (hashtext.length() < 32) {
					hashtext = "0" + hashtext;
				}
				return hashtext;
			}
			catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}

	}

	@WebSocket public class XMPPWebSocket {

		private Session wsSession;
		private WSConnection wsConnection;
		private String digest;
		private LocalClientSession xmppSession;
		private String remoteHost;
		private int remotePort;
		private ServerOpenfireChannelHandler handler;

		public void setWSConnection(String digest, WSConnection wsConnection) {
			this.digest = digest;
			this.wsConnection = wsConnection;

			wsConnection.setSocket(this);
			sockets.put(digest, this);

			Log.info(digest + " : setWSConnection");
		}

		public void setHandler(ServerOpenfireChannelHandler handler, String remoteHost, int remotePort) {

			this.handler = handler;
			this.remoteHost = remoteHost;
			this.remotePort = remotePort;
		}

		public String getDigest() {
			return digest;
		}

		public String getRemoteHost() {
			return remoteHost;
		}

		public int getRemotePort() {
			return remotePort;
		}

		public void setSession( LocalClientSession xmppSession ) {
			this.xmppSession = xmppSession;
		}

		public LocalClientSession getSession() {
			return xmppSession;
		}

		public boolean isOpen() {
			return wsSession.isOpen();
		}

		@OnWebSocketConnect public void onConnect(Session wsSession)
		{
			try {
				this.wsSession = wsSession;
				//wsConnection.setSecure(wsSession.isSecure());
				handler.channelConnected(this);
				Log.info(digest + " : onConnect");

			} catch ( Exception e ) {
				Log.error( "An error occurred while attempting to connect socket", e );
			}
		}

		@OnWebSocketClose public void onClose(int statusCode, String reason)
		{
			try {
				//sockets.remove(digest);
				//if (xmppSession != null) xmppSession.close();
				//xmppSession = null;

				handler.channelDisconnected(this);

			} catch ( Exception e ) {
				Log.error( "An error occurred while attempting to remove the socket and xmppSession", e );
			}

			Log.info( digest + " : onClose : " + statusCode + " " + reason);
		}

		@OnWebSocketError public void onError(Throwable error)
		{
			Log.error("XMPPWebSocket onError", error);
		}

		@OnWebSocketMessage public void onTextMethod(String data)
		{
			if ( !"".equals( data.trim()))
			{
				try {
					Log.info( digest + " : onMessage : Received : " + data );
					//wsConnection.getRouter().route(DocumentHelper.parseText(data).getRootElement());
					handler.messageReceived(this, data);

				} catch ( Exception e ) {
					Log.error( "An error occurred while attempting to route the packet : ", e );
				}
			}
		}

		@OnWebSocketMessage public void onBinaryMethod(byte data[], int offset, int length)
		{
		 // simple BINARY message received
		}

		public void deliver(String packet)
        {
            if (wsSession != null && wsSession.isOpen() && !"".equals( packet.trim() ) )
            {
                try {
                	Log.info( digest + " : Delivered : " + packet );
                	wsSession.getRemote().sendString(packet);
                } catch (Exception e) {
                    Log.error("XMPPWebSocket deliver " + e);
                }
            }
        }

		public void disconnect()
        {
            Log.info( digest + " : disconnect : XMPPWebSocket disconnect");
            Log.info( "Total websockets created : " + sockets.size() );
            try {
            	if (wsSession != null && wsSession.isOpen())
	            {
	                wsSession.close();
	            }
            } catch ( Exception e ) {

            	try {
            		wsSession.disconnect();
            	} catch ( Exception e1 ) {

				}
            }
            try {
            	sockets.remove( digest );
            	SessionManager.getInstance().removeSession( xmppSession );
            } catch ( Exception e ) {
            	Log.error( "An error has occurred", e );
            }
            xmppSession = null;
        }
	}
}
