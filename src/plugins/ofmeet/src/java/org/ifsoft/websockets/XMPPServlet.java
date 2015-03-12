package org.ifsoft.websockets;

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

import org.jivesoftware.openfire.plugin.ofmeet.OfMeetPlugin;
import org.jivesoftware.openfire.plugin.ofmeet.OpenfireLoginService;

import org.xmpp.packet.*;

import org.dom4j.*;

public final class XMPPServlet extends WebSocketServlet
{
    private static Logger Log = LoggerFactory.getLogger( "XMPPServlet" );

    private ConcurrentHashMap<String, XMPPServlet.XMPPWebSocket> sockets;
    private String remoteAddr;
    private OfMeetPlugin plugin;

    public XMPPServlet()
    {
		plugin = (OfMeetPlugin) XMPPServer.getInstance().getPluginManager().getPlugin("ofmeet");
    	sockets = plugin.getSockets();
    }

	@Override public void configure(WebSocketServletFactory factory)
	{
		factory.getPolicy().setMaxTextMessageSize(64000000);
		factory.setCreator(new WSocketCreator());
	}

	public class WSocketCreator implements WebSocketCreator
	{
		@Override public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp)
		{
			for (String subprotocol : req.getSubProtocols())
			{
				if ("xmpp".equals(subprotocol))
				{
					XMPPWebSocket socket =  new XMPPWebSocket();

					if (doWebSocketConnect(req.getHttpServletRequest(), socket))
					{
						resp.setAcceptedSubProtocol(subprotocol);
						return socket;

					} else return null;
				}
			}
			return null;
		}

		private boolean doWebSocketConnect(HttpServletRequest request, XMPPWebSocket socket)
		{
			try {
				boolean isExistingSession = false;

				String username = URLDecoder.decode( ParamUtils.getParameter(request, "username"), "UTF-8");
				String password = URLDecoder.decode( ParamUtils.getParameter(request, "password"), "UTF-8");
				String resource = URLDecoder.decode( ParamUtils.getParameter(request, "resource"), "UTF-8");

				String register = ParamUtils.getParameter(request, "register");

				username = JID.escapeNode( username );

				String user = username.equals("null") ? resource : username;
				String digest = getMD5(user + password + resource );
				JID userJid = XMPPServer.getInstance().createJID(user, resource);

				Log.debug( digest + " : doWebSocketConnect : Digest created for " + userJid + " : " + register );

				LocalClientSession session = (LocalClientSession) SessionManager.getInstance().getSession(userJid);

				if (session != null)
				{
					isExistingSession = true;

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

						if (username.equals("null") == false && OpenfireLoginService.authTokens.containsKey(username))
						{
							authToken = OpenfireLoginService.authTokens.get(username);

						} else {

							if (username.equals("null") && password.equals("null"))	// anonymous user
							{
								authToken = new AuthToken(resource, true);

							} else {

								if (isExistingSession && (password.equals("dummy") || password.equals("reuse")))
								{
									authToken = new AuthToken(username);

								} else {

									try {
										String userName = JID.unescapeNode(username);
										UserManager userManager = XMPPServer.getInstance().getUserManager();

										if (register != null && register.equals("true") && XMPPServer.getInstance().getIQRegisterHandler().isInbandRegEnabled())	// if register, create new user
										{
											try {
												userManager.getUser(userName);
											}
											catch (UserNotFoundException e) {
												userManager.createUser(userName, password, null, null);
											}

										} else {

											try {
												userManager.getUser(userName);
											}

											catch (UserNotFoundException e) {
												Log.error( "user not found " + userName, e );
												return false;
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
							}
						}

						session = SessionManager.getInstance().createClientSession( wsConnection, new BasicStreamID("url" + System.currentTimeMillis()));
						wsConnection.setRouter( new SessionPacketRouter( session ) );
						session.setAuthToken(authToken, resource);
						socket.setSession( session );
					}
					catch (Exception e1) {
						Log.error( "An error occurred while attempting to create a new socket " + e1);
						return false;
					}

					Log.debug( "Created new socket for digest " + digest );
					Log.debug( "Total websockets created : " + sockets.size() );

				} catch ( Exception e ) {
					Log.error( "An error occurred while attempting to create a new socket " + e);
					return false;
				}


			} catch ( Exception e ) {
				if (socket.getSession() != null) SessionManager.getInstance().removeSession(socket.getSession());
				return false;
			}

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

		public void setWSConnection(String digest, WSConnection wsConnection) {
			this.digest = digest;
			this.wsConnection = wsConnection;

			wsConnection.setSocket(this);
			sockets.put(digest, this);

			Log.debug(digest + " : setWSConnection");
		}

		public String getDigest() {
			return digest;
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
			this.wsSession = wsSession;
			wsConnection.setSecure(wsSession.isSecure());
			Log.debug(digest + " : onConnect");
		}

		@OnWebSocketClose public void onClose(int statusCode, String reason)
		{
			try {
				sockets.remove(digest);
				if (xmppSession != null) xmppSession.close();
				xmppSession = null;

			} catch ( Exception e ) {
				Log.error( "An error occurred while attempting to remove the socket and xmppSession", e );
			}

			Log.debug( digest + " : onClose : " + statusCode + " " + reason);
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
					Log.debug( digest + " : onMessage : Received : " + data );
					wsConnection.getRouter().route(DocumentHelper.parseText(data).getRootElement());

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
                	Log.debug( digest + " : Delivered : \n" + packet );
                	wsSession.getRemote().sendStringByFuture(packet);
                } catch (Exception e) {
                    Log.error("XMPPWebSocket deliver " + e);
					Log.warn( digest + " : Could not deliver : \n" + packet );
                }
            }
        }

		public void disconnect()
        {
            Log.debug( digest + " : disconnect : XMPPWebSocket disconnect");
            Log.debug( "Total websockets created : " + sockets.size() );
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
