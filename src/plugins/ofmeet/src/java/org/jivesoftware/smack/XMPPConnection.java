/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.smack;

import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.Certificate;

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

import java.util.concurrent.*;

import org.jivesoftware.smack.Connection.ListenerWrapper;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.PacketParserUtils;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.*;
import java.security.*;
import java.util.Collection;
import java.util.Iterator;
import javax.net.SocketFactory;
import javax.net.ssl.*;
import javax.security.auth.callback.*;

import org.xmlpull.mxp1.MXParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import org.ifsoft.websockets.BasicStreamID;

import org.xmpp.packet.*;

import org.dom4j.*;


public class XMPPConnection extends Connection
{
	private static Logger Log = LoggerFactory.getLogger( "XMPPConnection" );
    String connectionID;
    private String user;
    private boolean connected;
    private boolean authenticated;
    private boolean wasAuthenticated;
    private boolean anonymous;
    private boolean usingTLS;
    Roster roster;
    private SSLContext customSslContext;
    private boolean usingCompression;
    private LocalClientSession session;

    OpenfirePacketWriter packetWriter;
    OpenfirePacketReader packetReader;
    SmackConnection smackConnection;


    public XMPPConnection(String serviceName, CallbackHandler callbackHandler)
    {
        super(new ConnectionConfiguration(serviceName));
        connectionID = null;
        user = null;
        connected = false;
        authenticated = false;
        wasAuthenticated = false;
        anonymous = false;
        usingTLS = false;
        roster = null;
        customSslContext = null;
        config.setCompressionEnabled(false);
        config.setSASLAuthenticationEnabled(true);
        config.setDebuggerEnabled(DEBUG_ENABLED);
        config.setCallbackHandler(callbackHandler);
    }

    public XMPPConnection(String serviceName)
    {
        super(new ConnectionConfiguration(serviceName));
        connectionID = null;
        user = null;
        connected = false;
        authenticated = false;
        wasAuthenticated = false;
        anonymous = false;
        usingTLS = false;
        roster = null;
        customSslContext = null;
        config.setCompressionEnabled(false);
        config.setSASLAuthenticationEnabled(true);
        config.setDebuggerEnabled(DEBUG_ENABLED);
    }

    public XMPPConnection(ConnectionConfiguration config)
    {
        super(config);
        connectionID = null;
        user = null;
        connected = false;
        authenticated = false;
        wasAuthenticated = false;
        anonymous = false;
        usingTLS = false;
        roster = null;
        customSslContext = null;
    }

    public XMPPConnection(ConnectionConfiguration config, CallbackHandler callbackHandler)
    {
        super(config);
        connectionID = null;
        user = null;
        connected = false;
        authenticated = false;
        wasAuthenticated = false;
        anonymous = false;
        usingTLS = false;
        roster = null;
        customSslContext = null;
        config.setCallbackHandler(callbackHandler);
    }

    public String getConnectionID()
    {
        if(!isConnected())
            return null;
        else
            return connectionID;
    }

    public String getUser()
    {
        if(!isAuthenticated())
            return null;
        else
            return user;
    }

    public void setCustomSslContext(SSLContext customSslContext)
    {
        this.customSslContext = customSslContext;
    }

    public synchronized void login(String username, String password, String resource) throws XMPPException
    {
		Log.info("XMPPConnection login");

        if(!isConnected())
            throw new IllegalStateException("Not connected to server.");

        if(authenticated)
            throw new IllegalStateException("Already logged in to server.");

        try {
			username = username.toLowerCase().trim();
			user = username;
			config.setServiceName(StringUtils.parseServer("openfire"));

			JID userJid = XMPPServer.getInstance().createJID(username, resource);

			session = (LocalClientSession) SessionManager.getInstance().getSession(userJid);

			if (session != null)
			{
				session.close();
				SessionManager.getInstance().removeSession(session);
			}

			AuthToken authToken = null;

			try {
				authToken = AuthFactory.authenticate( username, password );

			} catch ( UnauthorizedException e ) {
				authToken = new AuthToken(resource, true);
			}

			session = SessionManager.getInstance().createClientSession( smackConnection, new BasicStreamID("ofmeet-focus-" + System.currentTimeMillis()));
			smackConnection.setRouter( new SessionPacketRouter( session ) );
			session.setAuthToken(authToken, resource);

			authenticated = true;
			anonymous = false;

			if(roster == null)
				roster = new Roster(this);

			if(config.isRosterLoadedAtLogin())
				roster.reload();

			packetWriter.sendPacket(new Presence(org.jivesoftware.smack.packet.Presence.Type.available));
			config.setLoginInfo(username, password, resource);

			if(config.isDebuggerEnabled() && debugger != null)  debugger.userHasLogged(user);

		} catch (Exception e) {
			Log.error("XMPPConnection login error", e);
		}
    }

    public synchronized void loginAnonymously() throws XMPPException
    {

    }

    public Roster getRoster()
    {
        // synchronize against login()
        synchronized(this) {
            // if connection is authenticated the roster is already set by login()
            // or a previous call to getRoster()
            if (!isAuthenticated() || isAnonymous()) {
                if (roster == null) {
                    roster = new Roster(this);
                }
                return roster;
            }
        }

        if (!config.isRosterLoadedAtLogin()) {
            roster.reload();
        }
        // If this is the first time the user has asked for the roster after calling
        // login, we want to wait for the server to send back the user's roster. This
        // behavior shields API users from having to worry about the fact that roster
        // operations are asynchronous, although they'll still have to listen for
        // changes to the roster. Note: because of this waiting logic, internal
        // Smack code should be wary about calling the getRoster method, and may need to
        // access the roster object directly.
        if (!roster.rosterInitialized) {
            try {
                synchronized (roster) {
                    long waitTime = SmackConfiguration.getPacketReplyTimeout();
                    long start = System.currentTimeMillis();
                    while (!roster.rosterInitialized) {
                        if (waitTime <= 0) {
                            break;
                        }
                        roster.wait(waitTime);
                        long now = System.currentTimeMillis();
                        waitTime -= now - start;
                        start = now;
                    }
                }
            }
            catch (InterruptedException ie) {
                // Ignore.
            }
        }
        return roster;
    }

    public boolean isConnected()
    {
        return connected;
    }

    public boolean isSecureConnection()
    {
        return isUsingTLS();
    }

    public boolean isAuthenticated()
    {
        return authenticated;
    }

    public boolean isAnonymous()
    {
        return anonymous;
    }

    protected void shutdown(Presence unavailablePresence)
    {
        packetWriter.sendPacket(unavailablePresence);

        setWasAuthenticated(authenticated);
        authenticated = false;
        connected = false;

        packetReader.shutdown();
        packetWriter.shutdown();
    }

    public synchronized void disconnect(Presence unavailablePresence)
    {
        shutdown(unavailablePresence);

        if(roster != null)
        {
            roster.cleanup();
            roster = null;
        }
        wasAuthenticated = false;

        packetWriter.cleanup();
        packetReader.cleanup();

		if (session != null)
		{
			session.close();
			SessionManager.getInstance().removeSession(session);
		}
    }

    public void sendPacket(Packet packet)
    {
        if(!isConnected())
            throw new IllegalStateException("Not connected to server.");

        if(packet == null)
        {
            throw new NullPointerException("Packet is null.");
        } else
        {
            packetWriter.sendPacket(packet);
            return;
        }
    }

    /**
     * @deprecated Method addPacketWriterInterceptor is deprecated
     */

    public void addPacketWriterInterceptor(PacketInterceptor packetInterceptor, PacketFilter packetFilter)
    {
        addPacketInterceptor(packetInterceptor, packetFilter);
    }

    /**
     * @deprecated Method removePacketWriterInterceptor is deprecated
     */

    public void removePacketWriterInterceptor(PacketInterceptor packetInterceptor)
    {
        removePacketInterceptor(packetInterceptor);
    }

    /**
     * @deprecated Method addPacketWriterListener is deprecated
     */

    public void addPacketWriterListener(PacketListener packetListener, PacketFilter packetFilter)
    {
        addPacketSendingListener(packetListener, packetFilter);
    }

    /**
     * @deprecated Method removePacketWriterListener is deprecated
     */

    public void removePacketWriterListener(PacketListener packetListener)
    {
        removePacketSendingListener(packetListener);
    }

    private void connectUsingConfiguration(ConnectionConfiguration config) throws XMPPException
    {
        String host = config.getHost();
        int port = config.getPort();
        initConnection(host);
    }

    private void initConnection(String host) throws XMPPException
    {
        boolean isFirstInitialization = packetReader == null || packetWriter == null;

        if (!isFirstInitialization) {
            usingCompression = false;
        }

        try {
            if (isFirstInitialization) {

                packetWriter = new OpenfirePacketWriter(this);
                packetReader = new OpenfirePacketReader(this);

				smackConnection = new SmackConnection(host, packetWriter, packetReader);

                // If debugging is enabled, we should start the thread that will listen for
                // all packets and then log them.

                if (config.isDebuggerEnabled()) {
                    addPacketListener(debugger.getReaderListener(), null);

                    if (debugger.getWriterListener() != null) {
                        addPacketSendingListener(debugger.getWriterListener(), null);
                    }
                }
            }
            else {
                packetWriter.init();
                packetReader.init();
            }

            // Start the packet writer. This will open a XMPP stream to the server
            packetWriter.startup();
            // Start the packet reader. The startup() method will block until we
            // get an opening stream packet back from server.
            packetReader.startup();

            // Make note of the fact that we're now connected.
            connected = true;

            // Start keep alive process (after TLS was negotiated - if available)
            packetWriter.startKeepAliveProcess();


            if (isFirstInitialization) {
                // Notify listeners that a new connection has been established
                for (ConnectionCreationListener listener : getConnectionCreationListeners()) {
                    listener.connectionCreated(this);
                }
            }
            else if (!wasAuthenticated) {
                packetReader.notifyReconnection();
            }

        }
        catch (Exception ex) {
            // An exception occurred in setting up the connection. Make sure we shut down the

            if (packetWriter != null) {
                try {
                    packetWriter.shutdown();
                }
                catch (Throwable ignore) { /* ignore */ }
                packetWriter = null;
            }

            if (packetReader != null) {
                try {
                    packetReader.shutdown();
                }
                catch (Throwable ignore) { /* ignore */ }
                packetReader = null;
            }

            this.setWasAuthenticated(authenticated);
            authenticated = false;
            connected = false;

            throw ex;        // Everything stoppped. Now throw the exception.
        }
    }

    public boolean isUsingTLS()
    {
        return usingTLS;
    }

    public boolean isUsingCompression()
    {
        return usingCompression;
    }

    public void connect() throws XMPPException
    {
        connectUsingConfiguration(config);

        if(connected && wasAuthenticated)
            try
            {
                if(isAnonymous())
                    loginAnonymously();
                else
                    login(config.getUsername(), config.getPassword(), config.getResource());

            }
            catch(XMPPException e)
            {
                e.printStackTrace();
            }
    }

    private void setWasAuthenticated(boolean wasAuthenticated)
    {
        if(!this.wasAuthenticated)
            this.wasAuthenticated = wasAuthenticated;
    }

    public Socket getSocket()
    {
        return null;
    }

    private class OpenfirePacketWriter
    {
    	public XMPPConnection connection;

		OpenfirePacketWriter(XMPPConnection connection)
		{
			this.connection = connection;
			init();
		}

    	public void init()
    	{

		}


    	public void sendPacket(Packet packet)
    	{
			try {
				String data = packet.toXML();
				Log.debug("OpenfirePacketWriter sendPacket " + data );
				smackConnection.getRouter().route(DocumentHelper.parseText(data).getRootElement());

            	connection.firePacketInterceptors(packet);
            	connection.firePacketSendingListeners(packet);

			} catch ( Exception e ) {
				Log.error( "An error occurred while attempting to route the packet : ", e );
			}
		}

		public void startup()
		{

		}

		public void shutdown()
		{

		}

		void cleanup()
		{
			Log.info("OpenfirePacketWriter cleanup");

			connection.interceptors.clear();
			connection.sendListeners.clear();
		}


		public void startKeepAliveProcess()
		{
			Log.info("OpenfirePacketWriter startKeepAliveProcess");
		}
	}

	private class OpenfirePacketReader
	{
    	public XMPPConnection connection;
    	private ExecutorService listenerExecutor;

		OpenfirePacketReader(XMPPConnection connection)
		{
			this.connection = connection;
			init();
		}

    	public void init()
    	{
			Log.info("OpenfirePacketReader init");

			listenerExecutor = Executors.newSingleThreadExecutor(new ThreadFactory()
			{
				public Thread newThread(Runnable runnable)
				{
					Thread thread = new Thread(runnable, "Smack Listener Processor (" + connection.connectionCounterValue + ")");
					thread.setDaemon(true);
					return thread;
				}
			});
		}

		public void startup()
		{
			Log.info("OpenfirePacketReader startup");
		}

		public void shutdown()
		{
			Log.info("OpenfirePacketReader shutdown");

        	listenerExecutor.shutdown();
		}

		public void cleanup()
		{
			Log.info("OpenfirePacketReader cleanup");

			connection.interceptors.clear();
			connection.sendListeners.clear();
		}

		public void processPacket(Packet packet)
		{
			if (packet == null) {
				return;
			}

			Log.debug("OpenfirePacketReader processPacket\n" + packet.toXML());

			// Loop through all collectors and notify the appropriate ones.

			for (PacketCollector collector: connection.getPacketCollectors())
			{
				collector.processPacket(packet);
			}

			// Deliver the incoming packet to listeners.
			listenerExecutor.submit(new ListenerNotification(packet));
		}

		public void notifyReconnection()
		{
			Log.info("OpenfirePacketReader notifyReconnection");

			for (ConnectionListener listener : connection.getConnectionListeners())
			{
				try {
					listener.reconnectionSuccessful();
				}
				catch (Exception e) {

				}
			}
		}

		private class ListenerNotification implements Runnable
		{
			private Packet packet;

			public ListenerNotification(Packet packet) {
				this.packet = packet;
			}

			public void run()
			{
				for (ListenerWrapper listenerWrapper : connection.recvListeners.values())
				{
					listenerWrapper.notifyListener(packet);
				}
			}
		}
	}


	public class SmackConnection extends VirtualConnection
	{
		private SessionPacketRouter router;
		private String remoteAddr;
		private String hostName;
		private LocalClientSession session;
		private boolean isSecure = false;
		private OpenfirePacketReader reader;
		private OpenfirePacketWriter writer;

		public SmackConnection(String hostName, OpenfirePacketWriter writer,  OpenfirePacketReader reader)
		{
			this.remoteAddr = hostName;
			this.hostName = hostName;
			this.reader = reader;
		}

		public void setReader(OpenfirePacketReader reader) {
			this.reader = reader;
		}

		public void setWriter(OpenfirePacketWriter writer) {
			this.writer = writer;
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
			Log.info("SmackConnection - close ");

			if (this.reader!= null) this.reader.shutdown();
			if (this.writer!= null) this.writer.shutdown();
			if (this.reader!= null) this.reader.cleanup();
			if (this.writer!= null) this.writer.cleanup();
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

		public void deliver(org.xmpp.packet.Packet packet) throws UnauthorizedException
		{
			deliverRawText(packet.toXML());
		}

		public void deliverRawText(String text)
		{
			Log.debug("SmackConnection - deliverRawText\n" + text);

			try {
				StringReader stringReader = new StringReader(text);

				XmlPullParser parser = new MXParser();
				parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
				parser.setInput(stringReader);

            	int eventType = parser.getEventType();

            	do {
					if (eventType == XmlPullParser.START_TAG)
					{
						if (parser.getName().equals("message")) {
							this.reader.processPacket(PacketParserUtils.parseMessage(parser));
						}
						else if (parser.getName().equals("iq")) {
							this.reader.processPacket(PacketParserUtils.parseIQ(parser, reader.connection));
						}
						else if (parser.getName().equals("presence")) {
							this.reader.processPacket(PacketParserUtils.parsePresence(parser));
						}
					}

					else if (eventType == XmlPullParser.END_TAG) {

					}

                	eventType = parser.next();
            	} while (eventType != XmlPullParser.END_DOCUMENT);
			}

			catch (Exception e) {
				Log.error("deliverRawText error", e);
			}
		}

        @Override
        public org.jivesoftware.openfire.spi.ConnectionConfiguration getConfiguration()
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
}
