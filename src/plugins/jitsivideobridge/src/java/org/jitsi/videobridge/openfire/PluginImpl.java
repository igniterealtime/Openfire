/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge.openfire;

import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.cert.Certificate;

import org.jitsi.service.neomedia.*;
import org.jitsi.util.*;
import org.jitsi.videobridge.*;
import org.jivesoftware.openfire.container.*;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.util.*;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.handler.IQHandler;
import org.jivesoftware.openfire.IQHandlerInfo;
import org.jivesoftware.openfire.StreamID;
import org.jivesoftware.openfire.Connection;
import org.jivesoftware.openfire.SessionManager;
import org.jivesoftware.openfire.SessionPacketRouter;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.auth.AuthToken;

import org.slf4j.*;
import org.slf4j.Logger;

import org.xmpp.component.*;
import org.xmpp.packet.*;

import com.rayo.core.*;
import com.rayo.core.verb.*;
import com.rayo.core.validation.*;
import com.rayo.core.xml.providers.*;

import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import org.dom4j.*;


/**
 * Implements <tt>org.jivesoftware.openfire.container.Plugin</tt> to integrate
 * Jitsi Video Bridge into Openfire.
 *
 * @author Lyubomir Marinov
 * @author Damian Minkov
 */
public class PluginImpl  implements Plugin, PropertyEventListener
{
    /**
     * The logger.
     */
    private static final Logger Log = LoggerFactory.getLogger(PluginImpl.class);

    /**
     * RAYO videobridge namespace
     */
    private static final String RAYO_COLIBRI = "urn:xmpp:rayo:colibri:1";
    /**
     * The name of the property that contains the name of video conference application
     */
    public static final String CHECKREPLAY_PROPERTY_NAME = "org.jitsi.videobridge.video.srtpcryptocontext.checkreplay";

    /**
     * The name of the property that contains the name of video conference application
     */
    public static final String VIDEO_CONFERENCE_PROPERTY_NAME = "org.jitsi.videobridge.video.conference.name";

    /**
     * The name of the property that contains the maximum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String MAX_PORT_NUMBER_PROPERTY_NAME = "org.jitsi.videobridge.media.MAX_PORT_NUMBER";

    /**
     * The name of the property that contains the minimum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String MIN_PORT_NUMBER_PROPERTY_NAME = "org.jitsi.videobridge.media.MIN_PORT_NUMBER";

    /**
     * The minimum port number default value.
     */
    public static final int MIN_PORT_DEFAULT_VALUE = 50000;

    /**
     * The maximum port number default value.
     */
    public static final int MAX_PORT_DEFAULT_VALUE = 60000;

    /**
     * The Jabber component which has been added to {@link #componentManager}
     * i.e. Openfire.
     */
    private Component component;

    /**
     * The <tt>ComponentManager</tt> to which the {@link #component} of this
     * <tt>Plugin</tt> has been added.
     */
    private ComponentManager componentManager;

    /**
     * The subdomain of the address of {@link #component} with which it has been
     * added to {@link #componentManager}.
     */
    private String subdomain;

    /**
     * RAYO IQ Handler for colibri
     *
     */
	private IQHandler colibriIQHandler = null;

    /**
     * RAYO Colibri Provider for colibri
     *
     */
    private ColibriProvider colibriProvider = null;

    /**
     * Destroys this <tt>Plugin</tt> i.e. releases the resources acquired by
     * this <tt>Plugin</tt> throughout its life up until now and prepares it for
     * garbage collection.
     *
     * @see Plugin#destroyPlugin()
     */

    public void destroyPlugin()
    {
        PropertyEventDispatcher.removeListener(this);

        if ((componentManager != null) && (subdomain != null))
        {
            try
            {
                componentManager.removeComponent(subdomain);
            }
            catch (ComponentException ce)
            {
                // TODO Auto-generated method stub
            }
            componentManager = null;
            subdomain = null;
            component = null;
        }

		//destroyIQHandlers();
    }

    /**
     * Initializes this <tt>Plugin</tt>.
     *
     * @param manager the <tt>PluginManager</tt> which loads and manages this
     * <tt>Plugin</tt>
     * @param pluginDirectory the directory into which this <tt>Plugin</tt> is
     * located
     * @see Plugin#initializePlugin(PluginManager, File)
     */
    public void initializePlugin(PluginManager manager, File pluginDirectory)
    {
        PropertyEventDispatcher.addListener(this);

		System.setProperty("net.java.sip.communicator.SC_HOME_DIR_LOCATION", pluginDirectory.getPath());
		System.setProperty("net.java.sip.communicator.SC_HOME_DIR_NAME", ".");
		System.setProperty("org.jitsi.impl.neomedia.transform.srtp.SRTPCryptoContext.checkReplay", JiveGlobals.getProperty(CHECKREPLAY_PROPERTY_NAME, "false"));

		// start video conference web application

		try {
			String appName = JiveGlobals.getProperty(VIDEO_CONFERENCE_PROPERTY_NAME, "jitmeet");
			Log.info("Initialize Web App " + appName);

			ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();
			WebAppContext context = new WebAppContext(contexts, pluginDirectory.getPath(), "/" + appName);
			context.setWelcomeFiles(new String[]{"index.html"});

			//createIQHandlers();

		}
		catch(Exception e) {
			Log.error( "Jitsi Videobridge web app initialize error", e);
		}


        // Let's check for custom configuration
        String maxVal = JiveGlobals.getProperty(MAX_PORT_NUMBER_PROPERTY_NAME);
        String minVal = JiveGlobals.getProperty(MIN_PORT_NUMBER_PROPERTY_NAME);

        if(maxVal != null)
            setIntProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                maxVal);
        if(minVal != null)
            setIntProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                minVal);

        checkNatives();

        ComponentManager componentManager = ComponentManagerFactory.getComponentManager();
        String subdomain = ComponentImpl.SUBDOMAIN;
        Component component = new ComponentImpl();
        boolean added = false;

        try
        {
            componentManager.addComponent(subdomain, component);
            added = true;
        }
        catch (ComponentException ce)
        {
            ce.printStackTrace(System.err);
        }
        if (added)
        {
            this.componentManager = componentManager;
            this.subdomain = subdomain;
            this.component = component;
        }
        else
        {
            this.componentManager = null;
            this.subdomain = null;
            this.component = null;
        }
    }

    /**
     * Checks whether we have folder with extracted natives, if missing
     * find the appropriate jar file and extract them. Normally this is
     * done once when plugin is installed or updated.
     * If folder with natives exist add it to the java.library.path so
     * libjitsi can use those native libs.
     */
    private void checkNatives()
    {
        // Find the root path of the class that will be our plugin lib folder.
        try
        {
            String binaryPath =
                (new URL(ComponentImpl.class.getProtectionDomain()
                    .getCodeSource().getLocation(), ".")).openConnection()
                    .getPermission().getName();

            File pluginJarfile = new File(binaryPath);
            File nativeLibFolder =
                new File(pluginJarfile.getParentFile(), "native");

            if(!nativeLibFolder.exists())
            {
                nativeLibFolder.mkdirs();

                // lets find the appropriate jar file to extract and
                // extract it
                String jarFileSuffix = null;
                if(OSUtils.IS_LINUX32)
                {
                    jarFileSuffix = "-native-linux-32.jar";
                }
                else if(OSUtils.IS_LINUX64)
                {
                    jarFileSuffix = "-native-linux-64.jar";
                }
                else if(OSUtils.IS_WINDOWS32)
                {
                    jarFileSuffix = "-native-windows-32.jar";
                }
                else if(OSUtils.IS_WINDOWS64)
                {
                    jarFileSuffix = "-native-windows-64.jar";
                }
                else if(OSUtils.IS_MAC)
                {
                    jarFileSuffix = "-native-macosx.jar";
                }

                String nativeLibsJarPath =
                    pluginJarfile.getCanonicalPath();
                nativeLibsJarPath =
                    nativeLibsJarPath.replaceFirst("\\.jar", jarFileSuffix);

                JarFile jar = new JarFile(nativeLibsJarPath);
                Enumeration en = jar.entries();
                while (en.hasMoreElements())
                {
                    try
                    {
                        JarEntry file = (JarEntry) en.nextElement();
                        File f = new File(nativeLibFolder, file.getName());
                        if (file.isDirectory())
                        {
                            continue;
                        }

                        InputStream is = jar.getInputStream(file);
                        FileOutputStream fos = new FileOutputStream(f);
                        while (is.available() > 0)
                        {
                            fos.write(is.read());
                        }
                        fos.close();
                        is.close();
                    }
                    catch(Throwable t)
                    {}
                }

                Log.info("Native lib folder created and natives extracted");
            }
            else
                Log.info("Native lib folder already exist.");

            String newLibPath =
                nativeLibFolder.getCanonicalPath() + File.pathSeparator +
                    System.getProperty("java.library.path");

            System.setProperty("java.library.path", newLibPath);

            // this will reload the new setting
            Field fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
            fieldSysPath.setAccessible(true);
            fieldSysPath.set(System.class.getClassLoader(), null);
        }
        catch (Exception e)
        {
            Log.error(e.getMessage(), e);
        }
    }

    /**
     * Returns the value of max port if set or the default one.
     * @return the value of max port if set or the default one.
     */
    public String getMaxPort()
    {
        String val = System.getProperty(
            DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME);

        if(val != null)
            return val;
        else
            return String.valueOf(MAX_PORT_DEFAULT_VALUE);
    }

    /**
     * Returns the value of min port if set or the default one.
     * @return the value of min port if set or the default one.
     */
    public String getMinPort()
    {
        String val = System.getProperty(
            DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME);

        if(val != null)
            return val;
        else
            return String.valueOf(MIN_PORT_DEFAULT_VALUE);
    }

    /**
     * A property was set. The parameter map <tt>params</tt> will contain the
     * the value of the property under the key <tt>value</tt>.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void propertySet(String property, Map params)
    {
        if(property.equals(MAX_PORT_NUMBER_PROPERTY_NAME))
        {
            setIntProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                (String)params.get("value"));
        }
        else if(property.equals(MIN_PORT_NUMBER_PROPERTY_NAME))
        {
            setIntProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                (String)params.get("value"));
        }
    }

    /**
     * Sets int property.
     * @param property the property name.
     * @param value the value to change.
     */
    private void setIntProperty(String property, String value)
    {
        try
        {
            // let's just check that value is integer
            int port = Integer.valueOf(value);

            if(port >= 1 && port <= 65535)
                System.setProperty(property, value);
        }
        catch(NumberFormatException ex)
        {
            Log.error("Error setting port", ex);
        }
    }

    /**
     * A property was deleted.
     *
     * @param property the name of the property deleted.
     * @param params event parameters.
     */
    public void propertyDeleted(String property, Map params)
    {
        if(property.equals(MAX_PORT_NUMBER_PROPERTY_NAME))
        {
            System.setProperty(
                DefaultStreamConnector.MAX_PORT_NUMBER_PROPERTY_NAME,
                String.valueOf(MAX_PORT_DEFAULT_VALUE));
        }
        else if(property.equals(MIN_PORT_NUMBER_PROPERTY_NAME))
        {
            System.setProperty(
                DefaultStreamConnector.MIN_PORT_NUMBER_PROPERTY_NAME,
                String.valueOf(MIN_PORT_DEFAULT_VALUE));
        }
    }

    /**
     * An XML property was set. The parameter map <tt>params</tt> will contain the
     * the value of the property under the key <tt>value</tt>.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void xmlPropertySet(String property, Map params)
    {
        propertySet(property, params);
    }

    /**
     * An XML property was deleted.
     *
     * @param property the name of the property.
     * @param params event parameters.
     */
    public void xmlPropertyDeleted(String property, Map params)
    {
        propertyDeleted(property, params);
    }

    /**
     *
     *
     */

	private void createIQHandlers()
	{
		colibriProvider = new ColibriProvider();
        colibriProvider.setValidator(new Validator());

		XMPPServer server = XMPPServer.getInstance();
		colibriIQHandler = new ColibriIQHandler();
		server.getIQRouter().addHandler(colibriIQHandler);
	}

    /**
     *
     *
     */

	private void destroyIQHandlers()
	{
		XMPPServer server = XMPPServer.getInstance();

		if (colibriIQHandler != null)
		{
			server.getIQRouter().removeHandler(colibriIQHandler);
			colibriIQHandler = null;
		}
	}

    /**
     *
     *
     */

    private class ColibriIQHandler extends IQHandler implements MUCEventListener
    {
		private ConcurrentHashMap<String, FocusAgent> sessions;
		private MultiUserChatManager mucManager;

        public ColibriIQHandler()
        {
			super("Rayo: XEP 0327 - Colibri");
			sessions = new ConcurrentHashMap<String, FocusAgent>();

			MUCEventDispatcher.addListener(this);
			mucManager = XMPPServer.getInstance().getMultiUserChatManager();
		}

		/**
		 *
		 *
		 */
        @Override public IQ handleIQ(IQ iq)
        {
			try {
				Log.info("ColibriIQHandler handleIQ \n" + iq.toString());

				final Element element = iq.getChildElement();
				IQ reply = null;

				Object object = colibriProvider.fromXML(element);

				if (object instanceof ColibriCommand) {
					ColibriCommand command = (ColibriCommand) object;
					reply = handleColibriCommand(command, iq);

				} else {
					reply = IQ.createResultIQ(iq);
					reply.setError(PacketError.Condition.not_allowed);
				}

				return reply;

			} catch(Exception e) {
				return null;
			}
		}
		/**
		 *
		 *
		 */
        @Override public IQHandlerInfo getInfo()
        {
			return new IQHandlerInfo("colibri", RAYO_COLIBRI);
		}
		/**
		 *
		 *
		 */
		private IQ handleColibriCommand(ColibriCommand command, IQ iq)
		{
			Log.info("ColibriIQHandler handleColibriCommand " + command);

			IQ reply = IQ.createResultIQ(iq);
			String vBridge = command.getVideobridge();
			Element conference = command.getConference();

			if (vBridge != null)
			{
				String focusAgentName = "colibri.focus.agent." + vBridge;
				JID user = iq.getFrom();

				if (sessions.containsKey(focusAgentName))
				{
					FocusAgent focusAgent = sessions.get(focusAgentName);

					if (focusAgent.isUser(user))
					{
						focusAgent.handleColibriCommand(reply, user, conference);

					} else {
						reply.setError(PacketError.Condition.item_not_found);
					}

				} else {
					reply.setError(PacketError.Condition.not_allowed);
				}
			}

			return reply;
		}
		/**
		 *
		 *
		 */
		public void roomCreated(JID roomJID)
		{

		}
		/**
		 *
		 *
		 */
		public void roomDestroyed(JID roomJID)
		{

		}
		/**
		 *
		 *
		 */
		public void occupantJoined(JID roomJID, JID user, String nickname)
		{
			MUCRoom mucRoom = mucManager.getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());

			Log.info("ColibriIQHandler occupantJoined " + roomJID + " " + user + " " + nickname);

			String focusAgentName = "colibri.focus.agent." + roomJID.getNode();

			FocusAgent focusAgent;

			Participant participant = new Participant(nickname, user);

			if (sessions.containsKey(focusAgentName))
			{
				focusAgent = sessions.get(focusAgentName);

			} else {
				focusAgent = new FocusAgent(focusAgentName, roomJID);
				LocalClientSession session = SessionManager.getInstance().createClientSession(focusAgent, new BasicStreamID(focusAgentName + "-" + System.currentTimeMillis() ) );
				focusAgent.setRouter( new SessionPacketRouter(session));
				AuthToken authToken = new AuthToken(focusAgentName, true);
				session.setAuthToken(authToken, focusAgentName);
				sessions.put(focusAgentName, focusAgent);

				Presence presence = new Presence();
				focusAgent.getRouter().route(presence);
			}

			focusAgent.createColibriChannel(participant);
		}
		/**
		 *
		 *
		 */
		public void occupantLeft(JID roomJID, JID user)
		{
			MUCRoom mucRoom = mucManager.getMultiUserChatService(roomJID).getChatRoom(roomJID.getNode());

			Log.info("ColibriIQHandler occupantLeft " + roomJID + " " + user);

			String focusAgentName = "colibri.focus.agent." + roomJID.getNode();

			if (sessions.containsKey(focusAgentName))
			{
				FocusAgent focusAgent = sessions.get(focusAgentName);

				focusAgent.removeColibriChannel(user);
			}
		}
		/**
		 *
		 *
		 */
		public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname)
		{

		}
		/**
		 *
		 *
		 */
		public void messageReceived(JID roomJID, JID user, String nickname, Message message)
		{

		}
		/**
		 *
		 *
		 */
		public void roomSubjectChanged(JID roomJID, JID user, String newSubject)
		{

		}
		/**
		 *
		 *
		 */
		public void privateMessageRecieved(JID a, JID b, Message message)
		{

		}
    }

    /**
     *
     *
     */
	public class BasicStreamID implements StreamID
	{
		/**
		 *
		 *
		 */
		String id;

		/**
		 *
		 *
		 */
		public BasicStreamID(String id) {
			this.id = id;
		}
		/**
		 *
		 *
		 */
		public String getID() {
			return id;
		}
		/**
		 *
		 *
		 */
		public String toString() {
			return id;
		}
		/**
		 *
		 *
		 */
		public int hashCode() {
			return id.hashCode();
		}
	}

    /**
     *
     *
     */
	public class Participant
	{
		/**
		 *
		 *
		 */
		public String audioChannelId;
		/**
		 *
		 *
		 */
		public String videoChannelId;
		/**
		 *
		 *
		 */
		private String nickname;
		/**
		 *
		 *
		 */
		private JID user;
		/**
		 *
		 *
		 */
		public Participant(String nickname, JID user) {
			this.nickname = nickname;
			this.user = user;
		}
		/**
		 *
		 *
		 */
		public String getNickname() {
			return nickname;
		}
		/**
		 *
		 *
		 */
		public String toString() {
			return user + " " + nickname;
		}
		/**
		 *
		 *
		 */
		public JID getUser() {
			return user;
		}
	}

	public class FocusAgent extends VirtualConnection
	{
		private SessionPacketRouter router;
		private String focusName;
		private JID roomJid;
		private String focusId = null;
		private int count = 0;
		private LocalClientSession session;
		private Participant firstParticipant;
		private String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
		public ConcurrentHashMap<String, Participant> users = new ConcurrentHashMap<String, Participant>();
		public ConcurrentHashMap<String, Participant> ids = new ConcurrentHashMap<String, Participant>();
		public ConcurrentHashMap<String, Participant> channels = new ConcurrentHashMap<String, Participant>();

		/**
		 *
		 *
		 */
		public FocusAgent(String focusName, JID roomJid) {
			this.focusName = focusName;
			this.roomJid = roomJid;
		}
		/**
		 *
		 *
		 */
		public void notifyUser(Participant participant, Element conference)
		{
			routeColibriEvent(participant, conference, true);
		}
		/**
		 *
		 *
		 */
		public void answerUser(Participant participant, Element conference)
		{
			String nickname	= participant.getNickname();

        	IQ iq = new IQ(IQ.Type.set);
			iq.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
			iq.setTo("jitsi-videobridge." + domainName);

			String id = nickname + "-" + System.currentTimeMillis();
			ids.put(id, participant);
			iq.setID(id);

			iq.setChildElement(conference.createCopy());
			router.route(iq);

			routeColibriEvent(participant, conference, true);
		}
		/**
		 *
		 *
		 */
		public void addUser(Participant participant, Element conference)
		{
			users.put(participant.getUser().toString(), participant);

			Presence presence = new Presence();
			presence.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
			presence.setTo(participant.getUser());

			ColibriOfferEvent event = new ColibriOfferEvent();
			event.setMuc(roomJid);
			event.setNickname(participant.getNickname());
			event.setParticipant(participant.getUser());
			event.setConference(conference);
			presence.getElement().add(colibriProvider.toXML(event));

			router.route(presence);

			for ( Iterator i = conference.elementIterator("content"); i.hasNext(); )
			{
				Element content = (Element) i.next();
				Element channel = content.element("channel");

				if ("audio".equals(content.attributeValue("name")))
				{
					participant.audioChannelId = channel.attributeValue("id");
					channels.put(participant.audioChannelId, participant);

				} else {
					participant.videoChannelId = channel.attributeValue("id");
					channels.put(participant.videoChannelId, participant);
				}
			}
		}
		/**
		 *
		 *
		 */
		public void updateUser(Participant participant, Element conference)
		{

		}
		/**
		 *
		 *
		 */
		public boolean isUser(JID user)
		{
			return users.containsKey(user.toString());
		}
		/**
		 *
		 *
		 */
		public void createColibriChannel(Participant participant)
		{
			Log.info("createColibriChannel " + participant + " " + count);

			count++;

			if (count == 1)
			{
				firstParticipant = participant;		// can't start until we have at least 2 people
				return;
			}

			String nickname	= participant.getNickname();

        	IQ iq = new IQ(IQ.Type.get);
			iq.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
			iq.setTo("jitsi-videobridge." + domainName);

			String id = nickname + "-" + System.currentTimeMillis();
			ids.put(id, participant);
			iq.setID(id);

			Element conferenceIq = iq.setChildElement("conference", "http://jitsi.org/protocol/colibri");

			if (focusId != null)
			{
				conferenceIq.addAttribute("id", focusId);
			}

			Element audioContent = conferenceIq.addElement("content").addAttribute("name", "audio");
			audioContent.addElement("channel").addAttribute("initiator", "true").addAttribute("expire", "15");

			Element videoContent = conferenceIq.addElement("content").addAttribute("name", "video");
			videoContent.addElement("channel").addAttribute("initiator", "true").addAttribute("expire", "15");

			router.route(iq);
		}
		/**
		 *
		 *
		 */
		public void removeColibriChannel(JID user)
		{
			String username = user.toString();

			count--;

			if (count <= 1)
			{
				focusId = null;	// invalidate current focus session
				count = 0;
			}

			if (users.containsKey(username))
			{
				Participant participant = users.remove(username);
				routeColibriEvent(participant, null, false);
			}

			Log.info("removeColibriChannel " + count);
		}
		/**
		 *
		 *
		 */
		public void handleColibriCommand(IQ reply, JID user, Element conference)
		{
			reply.setError(PacketError.Condition.not_allowed);		// not implemented
		}
		/**
		 *
		 *
		 */
		public SessionPacketRouter getRouter()
		{
			return router;
		}
		/**
		 *
		 *
		 */
		public void setRouter(SessionPacketRouter router)
		{
			this.router = router;
		}
		/**
		 *
		 *
		 */
		public void closeVirtualConnection()
		{
			Log.debug("FocusAgent - close ");

		}
		/**
		 *
		 *
		 */
		public byte[] getAddress() {
			return focusName.getBytes();
		}
		/**
		 *
		 *
		 */
		public String getHostAddress() {
			return focusName;
		}

		public String getHostName()  {
			return "0.0.0.0";
		}
		/**
		 *
		 *
		 */
		public void systemShutdown() {

		}
		/**
		 *
		 *
		 */
		public void deliver(Packet packet) throws UnauthorizedException
		{
			Log.info("FocusAgent deliver\n" + packet);

			IQ iq = (IQ) packet;

			if (iq.getType() == IQ.Type.result)
			{
				Element conference = iq.getChildElement().createCopy();
				focusId = conference.attributeValue("id");
				String id = packet.getID();

				if (ids.containsKey(id))
				{
					Participant participant = ids.remove(id);

					if (users.containsKey(participant.getUser().toString()))
						updateUser(participant, conference);
					else
						addUser(participant, conference);

					Log.info("FocusAgent response for user " + participant + " " + focusId + "\n" + conference);

				} else Log.error("FocusAgent deliver cannot find iq owner " + id + "\n" + packet);

				if (firstParticipant != null)			// send pending channel request for first participant
				{
					createColibriChannel(firstParticipant);
					firstParticipant = null;
				}


			} else if (iq.getType() == IQ.Type.error)  {
				focusId = null;	// error
				count = 0;
				Log.error("Videobrideg error \n" + packet);

				for (Participant reciepient : users.values())
				{
					Log.info("routeColibriEvent - E " + reciepient);
					sendRayoEvent(reciepient, null, false, reciepient);
				}


			} else if (iq.getType() == IQ.Type.set || iq.getType() == IQ.Type.get)  {
				JID user = iq.getFrom();
				Element root = iq.getChildElement();
				Element conference = null;

				if (user.toString().equals("jitsi-videobridge." + domainName)) // SSRC notification from videobridge
				{
/*
					conference = root.createCopy();
					String channelId = conference.element("content").element("channel").attributeValue("id");

					if (channels.containsKey(channelId))
						notifyUser(channels.get(channelId), conference);

					else Log.error("FocusAgent deliver cannot find channel owner " + channelId + "\n" + packet);
*/
				} else {

					conference = root.element("conference").createCopy();	// rayo from participant

					IQ reply = IQ.createResultIQ(iq);

					if (users.containsKey(user.toString()))
						answerUser(users.get(user.toString()), conference);
					else
						reply.setError(PacketError.Condition.not_allowed);

					router.route(reply);
				}

			} else {
				Log.warn("Unexpected Videobrideg message \n" + packet);
			}
		}
		/**
		 *
		 *
		 */
		public void deliverRawText(String text)
		{
			Log.debug("FocusAgent deliverRawText\n" + text);
		}
		/**
		 *
		 *
		 */
		private void routeColibriEvent(Participant participant, Element conference, boolean isAdd)
		{
			Log.info("routeColibriEvent - P " + participant);

			for (Participant reciepient : users.values())
			{
				if (participant.getUser().toString().equals(reciepient.getUser().toString()) == false)
				{
					Log.info("routeColibriEvent - R " + reciepient);
					sendRayoEvent(reciepient, conference, isAdd, participant);
				}
			}
		}
		/**
		 *
		 *
		 */
		private void sendRayoEvent(Participant reciepient, Element conference, boolean isAdd, Participant participant)
		{
			Presence presence = new Presence();
			presence.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
			presence.setTo(reciepient.getUser());

			if (isAdd)
			{
				AddSourceEvent event = new AddSourceEvent();
				event.setMuc(roomJid);
				event.setNickname(participant.getNickname());
				event.setParticipant(participant.getUser());
				event.setConference(conference);

				presence.getElement().add(colibriProvider.toXML(event));

			} else {
				RemoveSourceEvent event = new RemoveSourceEvent();
				event.setMuc(roomJid);
				event.setNickname(participant.getNickname());
				event.setParticipant(participant.getUser());
				event.setActive(focusId != null);

				presence.getElement().add(colibriProvider.toXML(event));
			}

			router.route(presence);
		}
		/**
		 *
		 *
		 */
		public Certificate[] getPeerCertificates() {
			return null;
		}

	}
}
