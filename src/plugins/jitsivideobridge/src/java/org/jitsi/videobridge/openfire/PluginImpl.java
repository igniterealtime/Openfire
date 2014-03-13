/*
 * Jitsi Videobridge, OpenSource video conferencing.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.videobridge.openfire;

import org.ice4j.socket.*;
import java.beans.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.cert.Certificate;

import javax.media.*;
import javax.media.protocol.*;
import javax.media.format.*;

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
import org.eclipse.jetty.http.security.*;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.*;

import org.dom4j.*;
import org.jitsi.videobridge.*;
import org.jitsi.impl.neomedia.*;
import org.jitsi.impl.neomedia.format.*;
import org.jitsi.impl.neomedia.device.*;
import org.jitsi.impl.neomedia.conference.*;
import org.jitsi.impl.neomedia.jmfext.media.renderer.audio.*;
import org.jitsi.service.neomedia.*;
import org.jitsi.service.neomedia.device.*;
import org.jitsi.service.neomedia.event.*;
import org.jitsi.service.neomedia.format.*;
import org.jitsi.service.libjitsi.*;
import org.jitsi.util.*;

import org.ifsoft.*;
import org.ifsoft.rtp.*;
import org.ifsoft.sip.*;

import net.sf.fmj.media.rtp.*;

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
     * SIP registration status.
     */
    public static String sipRegisterStatus = "";
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
     * The name of the property that contains the security username for basic authentication
     */
    public static final String USERNAME_PROPERTY_NAME = "org.jitsi.videobridge.security.username";

    /**
     * The name of the property that contains the security password for basic authentication
     */
    public static final String PASSWORD_PROPERTY_NAME = "org.jitsi.videobridge.security.password";

    /**
     * The name of the property that contains the maximum port number that we'd
     * like our RTP managers to bind upon.
     */
    public static final String RECORD_PROPERTY_NAME = "org.jitsi.videobridge.media.record";

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
     * The name of the property that contains the default SIP port.
     */
	public static final String SIP_PORT_PROPERTY_NAME = "org.jitsi.videobridge.sip.port.number";

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

    /**
     * The <tt>Videobridge</tt> which creates, lists and destroys
     * {@link Conference} instances and which is being represented as a Jabber
     * component by this instance.
     */
    private Videobridge videoBridge;

    /**
	 *
     */
    private ComponentImpl componentImpl;


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

		destroyIQHandlers();
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
			String appName = JiveGlobals.getProperty(VIDEO_CONFERENCE_PROPERTY_NAME, "jitsi");
			Log.info("Initialize Web App " + appName);

			ContextHandlerCollection contexts = HttpBindManager.getInstance().getContexts();
			WebAppContext context = new WebAppContext(contexts, pluginDirectory.getPath(), "/" + appName);
			context.setWelcomeFiles(new String[]{"index.html"});

			String username = JiveGlobals.getProperty(USERNAME_PROPERTY_NAME, null);
			String password = JiveGlobals.getProperty(PASSWORD_PROPERTY_NAME, "jitsi");

			if (username != null && "".equals(username) == false)
			{
				context.setSecurityHandler(basicAuth(username, password, "Private!"));
			}

			createIQHandlers();

			Properties properties = new Properties();
			String hostName = XMPPServer.getInstance().getServerInfo().getHostname();
			String logDir = pluginDirectory.getAbsolutePath() + File.separator + ".." + File.separator + ".." + File.separator + "logs" + File.separator;
			String port = JiveGlobals.getProperty(SIP_PORT_PROPERTY_NAME, "5060");

			properties.setProperty("com.voxbone.kelpie.hostname", hostName);
			properties.setProperty("com.voxbone.kelpie.ip", hostName);
			properties.setProperty("com.voxbone.kelpie.sip_port", port);

			properties.setProperty("javax.sip.IP_ADDRESS", hostName);

			properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "99");
			properties.setProperty("gov.nist.javax.sip.SERVER_LOG", logDir + "sip_server.log");
			properties.setProperty("gov.nist.javax.sip.DEBUG_LOG", logDir + "sip_debug.log");

			new SipService(properties);

			Log.info("Initialize SIP Stack at " + hostName + ":" + port);

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
        checkRecordingFolder(pluginDirectory);

        ComponentManager componentManager = ComponentManagerFactory.getComponentManager();
        String subdomain = ComponentImpl.SUBDOMAIN;
        Component component = new ComponentImpl();
        boolean added = false;

        try
        {
            componentManager.addComponent(subdomain, component);
            added = true;
            componentImpl = (ComponentImpl) component;
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

     */
    private static final SecurityHandler basicAuth(String username, String password, String realm) {

    	HashLoginService l = new HashLoginService();
        l.putUser(username, Credential.getCredential(password), new String[] {"user"});
        l.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("myrealm");
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;

    }

    /**
     * Checks whether we have folder for video recordings, if missing, create it
     */
    private void checkRecordingFolder(File pluginDirectory)
    {
		String rayoHome = JiveGlobals.getHomeDirectory() + File.separator + "resources" + File.separator + "spank" + File.separator + "rayo";

        try
        {
			File rayoFolderPath = new File(rayoHome);

            if(!rayoFolderPath.exists())
            {
                rayoFolderPath.mkdirs();
                Log.info("Rayo home folder created");

			}

			File recordingFolderPath = new File(rayoHome + File.separator + "video_recordings");

            if(!recordingFolderPath.exists())
            {
                recordingFolderPath.mkdirs();
                Log.info("Rayo video recordings folder created");

			}
        }
        catch (Exception e)
        {
            Log.error(e.getMessage(), e);
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
                (new URL(Videobridge.class.getProtectionDomain()
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
    public Videobridge getVideoBridge()
    {
        return componentImpl.getVideoBridge();
    }

    /**
     *
     *
     */

    private class ColibriIQHandler extends IQHandler implements MUCEventListener
    {
		private ConcurrentHashMap<String, FocusAgent> sessions;
		private ConcurrentHashMap<String, JID> registry;
		private ConcurrentHashMap<String, Participant> pending;
		private MultiUserChatManager mucManager;

        public ColibriIQHandler()
        {
			super("Rayo: XEP 0327 - Colibri");
			sessions = new ConcurrentHashMap<String, FocusAgent>();
			registry = new ConcurrentHashMap<String, JID>();
			pending = new ConcurrentHashMap<String, Participant>();

			MUCEventDispatcher.addListener(this);
			mucManager = XMPPServer.getInstance().getMultiUserChatManager();
		}

		/**
		 *
		 *
		 */
        @Override public IQ handleIQ(IQ iq)
        {
			IQ reply = IQ.createResultIQ(iq);

			try {
				Log.info("ColibriIQHandler handleIQ \n" + iq.toString());
				final Element element = iq.getChildElement();
				JID from = iq.getFrom();
				String fromId = from.toString();

				Object object = colibriProvider.fromXML(element);

				if (object instanceof RegisterCommand) {
					registry.put(fromId, from);

				} else if (object instanceof UnRegisterCommand) {

					if (registry.containsKey(fromId)) registry.remove(fromId, from);

				} else if (object instanceof ColibriOfferCommand) {

					ColibriOfferCommand offer = (ColibriOfferCommand) object;
					String key = fromId + offer.getMuc().toString();

					if (pending.containsKey(key))
					{
						Participant participant = pending.get(key);
						String focusAgentName = "jitsi.videobridge." + offer.getMuc().getNode();

						if (sessions.containsKey(focusAgentName))
						{
							FocusAgent focusAgent = sessions.get(focusAgentName);
							focusAgent.createColibriChannel(participant);
						} else {
							reply.setError(PacketError.Condition.not_allowed);
						}


					} else {
						reply.setError(PacketError.Condition.not_allowed);
					}

				} else if (object instanceof ColibriExpireCommand) {

					ColibriExpireCommand expire = (ColibriExpireCommand) object;
					String key = fromId + expire.getMuc().toString();

					if (pending.containsKey(key))
					{
						Participant participant = pending.get(key);
						String focusAgentName = "jitsi.videobridge." + expire.getMuc().getNode();

						if (sessions.containsKey(focusAgentName))
						{
							FocusAgent focusAgent = sessions.get(focusAgentName);
							focusAgent.removeColibriChannel(participant.getUser());
						} else {
							reply.setError(PacketError.Condition.not_allowed);
						}


					} else {
						reply.setError(PacketError.Condition.not_allowed);
					}

				} else if (object instanceof UnInviteCommand) {

					UnInviteCommand uninvite = (UnInviteCommand) object;

					String focusAgentName = "jitsi.videobridge." + uninvite.getMuc().getNode();

					if (sessions.containsKey(focusAgentName))
					{
						FocusAgent focusAgent = sessions.get(focusAgentName);
						focusAgent.uninviteNewParticipant(uninvite.getCallId(), reply);
					} else {
						reply.setError(PacketError.Condition.not_allowed);
					}

				} else if (object instanceof InviteCommand) {

					InviteCommand invite = (InviteCommand) object;

					String focusAgentName = "jitsi.videobridge." + invite.getMuc().getNode();

					if (sessions.containsKey(focusAgentName))
					{
						FocusAgent focusAgent = sessions.get(focusAgentName);
						focusAgent.inviteNewParticipant(invite.getFrom(), invite.getTo(), reply);
					} else {
						reply.setError(PacketError.Condition.not_allowed);
					}

				} else {
					reply.setError(PacketError.Condition.not_allowed);
				}

				return reply;

			} catch(Exception e) {
				Log.error("ColibriIQHandler handleIQ", e);
				reply.setError(PacketError.Condition.not_allowed);
				return reply;
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
		public void roomCreated(JID roomJID)
		{

		}
		/**
		 *
		 *
		 */
		public void roomDestroyed(JID roomJID)
		{
			Log.info("ColibriIQHandler roomDestroyed " + roomJID);

			String focusAgentName = "jitsi.videobridge." + roomJID.getNode();

			if (sessions.containsKey(focusAgentName))
			{
				FocusAgent focusAgent = sessions.remove(focusAgentName);
				focusAgent.closeSession();
				focusAgent = null;
			}
		}
		/**
		 *
		 *
		 */
		public void occupantJoined(JID roomJID, JID user, String nickname)
		{
			Log.info("ColibriIQHandler occupantJoined " + roomJID + " " + user + " " + nickname);

			String focusAgentName = "jitsi.videobridge." + roomJID.getNode();

			FocusAgent focusAgent;

			Participant participant = new Participant(nickname, user, focusAgentName);

			if (sessions.containsKey(focusAgentName))
			{
				focusAgent = sessions.get(focusAgentName);

			} else {
				focusAgent = new FocusAgent(focusAgentName, roomJID);
				LocalClientSession session = SessionManager.getInstance().createClientSession(focusAgent, new BasicStreamID(focusAgentName + "-" + System.currentTimeMillis() ) );
				focusAgent.setRouter( new SessionPacketRouter(session), session);
				AuthToken authToken = new AuthToken(focusAgentName, true);
				session.setAuthToken(authToken, focusAgentName);
				sessions.put(focusAgentName, focusAgent);

				Presence presence = new Presence();
				focusAgent.getRouter().route(presence);
			}

			if (registry.containsKey(user.toString()))
			{
				focusAgent.createColibriChannel(participant);

			} else {

				pending.put(user.toString() + roomJID.toString(), participant);
			}
		}
		/**
		 *
		 *
		 */
		public void occupantLeft(JID roomJID, JID user)
		{
			Log.info("ColibriIQHandler occupantLeft " + roomJID + " " + user);

			String focusAgentName = "jitsi.videobridge." + roomJID.getNode();

			if (sessions.containsKey(focusAgentName))
			{
				FocusAgent focusAgent = sessions.get(focusAgentName);

				focusAgent.removeColibriChannel(user);
			}

			pending.remove(user.toString() + roomJID.toString());
		}
		/**
		 *
		 *
		 */
		public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname)
		{
			Log.info("ColibriIQHandler nicknameChanged " + roomJID + " " + user);

			String focusAgentName = "jitsi.videobridge." + roomJID.getNode();

			if (sessions.containsKey(focusAgentName))
			{
				FocusAgent focusAgent = sessions.get(focusAgentName);

				focusAgent.changeNickname(user, newNickname);
			}
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
		private String focusName;
		/**
		 *
		 *
		 */
		private Recorder recorder = null;
		/**
		 *
		 *
		 */
		public MediaStream mediaStream = null;
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
		private PropertyChangeListener streamPropertyChangeListener = new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent ev)
			{
				String propertyName = ev.getPropertyName();
				String prefix = MediaStreamImpl.class.getName() + ".rtpConnector.";

				if (propertyName.startsWith(prefix))
				{
					Object newValue = ev.getNewValue();

					if (newValue instanceof RTPConnectorInputStream)
					{
						String rtpConnectorPropertyName = propertyName.substring(prefix.length());
						DatagramPacketFilter datagramPacketFilter = null;

						if (rtpConnectorPropertyName.equals("dataInputStream"))
						{
							datagramPacketFilter = new DatagramPacketFilter()
							{
								public boolean accept(DatagramPacket p)
								{
									byte[] data = p.getData();

									try{
										RTPPacket packet = RTPPacket.parseBytes(BitAssistant.bytesToArray(data));
										byte[] videoFrame = BitAssistant.bytesFromArray(packet.getPayload());

										//Log.info("Video media " + " " + packet.getPayloadType() + " " + packet.getSequenceNumber() + " " + packet.getTimestamp() + " " + packet.getPayload());
										if (recorder != null) recorder.write(videoFrame, 0, videoFrame.length);

									} catch (Exception e) {

									}
									return true;
								}
							};
						}

						if (datagramPacketFilter != null)
						{
							((RTPConnectorInputStream) newValue).addDatagramPacketFilter(datagramPacketFilter);
						}
					}
				}
			}
		};
		/**
		 *
		 *
		 */
		public Participant(String nickname, JID user, String focusName) {
			this.nickname = nickname;
			this.user = user;
			this.focusName = focusName;
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
		public void setNickname(String nickname) {
			this.nickname = nickname;
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
		/**
		 *
		 *
		 */
		public void addMediaStream(MediaStream mediaStream)
		{
			boolean recordMedia = "true".equals(JiveGlobals.getProperty(RECORD_PROPERTY_NAME, "false"));

			if (recordMedia)
			{
				this.mediaStream = mediaStream;
				mediaStream.addPropertyChangeListener(streamPropertyChangeListener);

				String recordingPath = JiveGlobals.getHomeDirectory() + File.separator + "resources" + File.separator + "spank" + File.separator + "rayo"  + File.separator + "video_recordings";
				String fileName = "video-" + focusName + "-" + nickname + "-" + System.currentTimeMillis() + ".rtp";

				try {
					recorder = new Recorder(recordingPath, fileName, "rtp", false, 0, 0);

				} catch (Exception e) {
					Log.error("Error creating video recording " + fileName + " " + recordingPath, e);
				}
			}
		}

		/**
		 *
		 *
		 */
		public void removeMediaStream()
		{
			if (this.mediaStream != null)
			{
				mediaStream.removePropertyChangeListener(streamPropertyChangeListener);

				if (recorder != null)
				{
					recorder.done();
					recorder = null;
				}
			}
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
		private String domainName = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
		private Recorder recorder = null;
		private AudioMixingPushBufferDataSource outDataSource;
		private SSRCFactoryImpl ssrcFactory = new SSRCFactoryImpl();
    	private long initialLocalSSRC;

		public ConcurrentHashMap<String, Participant> users = new ConcurrentHashMap<String, Participant>();
		public ConcurrentHashMap<String, Participant> ids = new ConcurrentHashMap<String, Participant>();
		public ConcurrentHashMap<String, Element> ssrcs = new ConcurrentHashMap<String, Element>();
		public ConcurrentHashMap<String, CallSession> callSessions = new ConcurrentHashMap<String, CallSession>();

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
		public void processUserAnswer(Participant participant, Element conference)
		{
			Log.info("processUserAnswer " + participant);

			String nickname	= participant.getNickname();

        	IQ iq = new IQ(IQ.Type.set);
			iq.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
			iq.setTo("jitsi-videobridge." + domainName);

			String id = "answer-" + nickname + "-" + System.currentTimeMillis();
			ids.put(id, participant);
			iq.setID(id);

			iq.setChildElement(conference.createCopy());
			router.route(iq);

			ssrcs.put(participant.getUser().toString(), conference);

			for (Participant reciepient : users.values())
			{
				if (participant.getUser().toString().equals(reciepient.getUser().toString()) == false)
				{
					Element conf = ssrcs.get(reciepient.getUser().toString());

					if (conf != null)
					{
						Presence presence = new Presence();
						presence.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
						presence.setTo(participant.getUser());

						AddSourceEvent event = new AddSourceEvent();
						event.setMuc(roomJid);
						event.setNickname(reciepient.getNickname());
						event.setParticipant(reciepient.getUser());
						event.setConference(conf);
						presence.getElement().add(colibriProvider.toXML(event));

						router.route(presence);
					}
				}
			}

			bridgeJoin(true, participant);
		}
		/**
		 *
		 *
		 */
		public void bridgeJoin(boolean join, Participant participant)
		{
			String roomName = roomJid.getNode();
			MUCRoom room = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(roomName);

			Log.info("bridgeJoin event to room occupants of " + roomName);

			for ( MUCRole role : room.getOccupants())
			{
				String jid = role.getUserAddress().toString();
				Log.info("bridgeJoin event to room occupant " + jid);

				Presence presence = new Presence();
				presence.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
				presence.setTo(jid);

				if (join)
				{
					JoinBridgeEvent event = new JoinBridgeEvent(roomName, participant.getUser(), participant.getNickname());
					presence.getElement().add(colibriProvider.toXML(event));

				} else {
					LeaveBridgeEvent event = new LeaveBridgeEvent(roomName, participant.getUser(), participant.getNickname());
					presence.getElement().add(colibriProvider.toXML(event));
				}
				router.route(presence);
			}
		}

		/**
		 *
		 *
		 */
		public void addUser(Participant participant, Element conference)
		{
			Log.info("addUser " + participant);

			count++;
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

				if ("video".equals(content.attributeValue("name")))
				{
						participant.videoChannelId = channel.attributeValue("id");

						String focusJid = XMPPServer.getInstance().createJID(focusName, focusName).toString();
						Content vbContent = getVideoBridge().getConference(focusId, focusJid).getOrCreateContent("video");

						if (vbContent != null)
						{
							Channel videoChannel = vbContent.getChannel(participant.videoChannelId);

							if (videoChannel != null)
							{
								participant.addMediaStream(videoChannel.getMediaStream());
							}
						}
				}

				if ("audio".equals(content.attributeValue("name")))
				{
						participant.audioChannelId = channel.attributeValue("id");
				}
			}
		}
		/**
		 *
		 *
		 */
		public void broadcastSSRC(Participant participant)
		{
			Log.info("broadcastSSRC " + participant + " " + count);

			if (ssrcs.containsKey(participant.getUser().toString()))
			{
				Element conference = ssrcs.get(participant.getUser().toString());
				routeColibriEvent(participant, conference, true);	// send to others
			}
		}
		/**
		 *
		 *
		 */
		public boolean isUser(JID user)
		{
			return users.containsKey(user.toString())  && focusId != null;
		}
		/**
		 *
		 *
		 */
		public void createColibriChannel(Participant participant)
		{
			Log.info("createColibriChannel " + participant + " " + count);

			String nickname	= participant.getNickname();

        	IQ iq = new IQ(IQ.Type.get);
			iq.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
			iq.setTo("jitsi-videobridge." + domainName);

			String id = "offer-" + nickname + "-" + System.currentTimeMillis();
			ids.put(id, participant);
			iq.setID(id);

			Element conferenceIq = iq.setChildElement("conference", "http://jitsi.org/protocol/colibri");

			if (focusId != null)
			{
				conferenceIq.addAttribute("id", focusId);
			}

			Element audioContent = conferenceIq.addElement("content").addAttribute("name", "audio");
			audioContent.addElement("channel").addAttribute("initiator", "true").addAttribute("expire", "15").addAttribute("rtp-level-relay-type", "mixer");

			Element videoContent = conferenceIq.addElement("content").addAttribute("name", "video");
			videoContent.addElement("channel").addAttribute("initiator", "true").addAttribute("expire", "15");

			router.route(iq);
		}
		/**
		 *
		 *
		 */
		public void expireColibriChannel(Participant participant)
		{
			Log.info("expireColibriChannel " + participant + " " + focusId + " " + participant.audioChannelId + " " + participant.videoChannelId);

			if (focusId != null && participant.audioChannelId != null && participant.videoChannelId != null)
			{
				String nickname	= participant.getNickname();

				IQ iq = new IQ(IQ.Type.get);
				iq.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
				iq.setTo("jitsi-videobridge." + domainName);

				String id = "expire-" + nickname + "-" + System.currentTimeMillis();
				//ids.put(id, participant);
				//iq.setID(id);

				Element conferenceIq = iq.setChildElement("conference", "http://jitsi.org/protocol/colibri");
				conferenceIq.addAttribute("id", focusId);

				Element audioContent = conferenceIq.addElement("content").addAttribute("name", "audio");
				audioContent.addElement("channel").addAttribute("id", participant.audioChannelId).addAttribute("expire", "0");

				Element videoContent = conferenceIq.addElement("content").addAttribute("name", "video");
				videoContent.addElement("channel").addAttribute("id", participant.videoChannelId).addAttribute("expire", "0");

				router.route(iq);

				if (participant.mediaStream != null)
				{
                	participant.removeMediaStream();
				}
			}
		}
		/**
		 *
		 *
		 */
		public void changeNickname(JID user, String nickName)
		{
			Participant participant = users.get(user.toString());

			if (participant != null)
			{
				participant.setNickname(nickName);
			}
		}


		/**
		 *
		 *
		 */
		public void uninviteNewParticipant(String callId, IQ reply)
		{
			Log.info("uninviteNewParticipant " + callId);

			CallSession cs = callSessions.remove(callId);

			if (cs != null)
			{
				SipService.sendBye(cs);

			} else {
				reply.setError(PacketError.Condition.not_allowed);
			}
		}
		/**
		 *
		 *
		 */
		public void inviteNewParticipant(URI fromUri, URI toUri, IQ reply)
		{
			Log.info("inviteNewParticipant " + fromUri + " " + toUri);

			String from = fromUri.toString();
			String to = toUri.toString();
			String hostname = XMPPServer.getInstance().getServerInfo().getHostname();

			boolean toSip = to.indexOf("sip:") == 0 ;
			boolean toPhone = to.indexOf("tel:") == 0;
			boolean toXmpp = to.indexOf("xmpp:") == 0;

			if (toSip)
			{
				from = "sip:jitsi-videobridge" + System.currentTimeMillis() + "@" + hostname;

			} else if (toPhone){

				String outboundProxy = JiveGlobals.getProperty("voicebridge.default.proxy.outboundproxy", null);
				String sipUsername = JiveGlobals.getProperty("voicebridge.default.proxy.sipauthuser", null);

				if (outboundProxy != null && sipUsername != null)
				{
					to = "sip:" + to.substring(4) + "@" + outboundProxy;
					from = "sip:" + sipUsername + "@" + outboundProxy;

				} else {
					reply.setError(PacketError.Condition.not_allowed);
					return;
				}

			} else if (toXmpp){
				reply.setError(PacketError.Condition.not_allowed);
				return;

			} else {
				reply.setError(PacketError.Condition.not_allowed);
				return;
			}

			Conference conference = null;
			String focusJid = XMPPServer.getInstance().createJID(focusName, focusName).toString();

			if (focusId != null)
			{
				conference = getVideoBridge().getConference(focusId, focusJid);
			}

			if (conference != null)
			{
				MediaService mediaService = LibJitsi.getMediaService();
				MediaStream mediaStream = mediaService.createMediaStream(null, org.jitsi.service.neomedia.MediaType.AUDIO, mediaService.createSrtpControl(SrtpControlType.MIKEY));
				mediaStream.setName("rayo-" + System.currentTimeMillis());
				mediaStream.setSSRCFactory(ssrcFactory);
				mediaStream.setDevice(conference.getOrCreateContent("audio").getMixer());

				initialLocalSSRC = ssrcFactory.doGenerateSSRC() & 0xFFFFFFFFL;

				CallSession cs = new CallSession(mediaStream, hostname);
				callSessions.put(toUri.toString(), cs);

				cs.jabberRemote = to;
				cs.jabberLocal = from;

				SipService.sendInvite(cs);

			} else {
				reply.setError(PacketError.Condition.not_allowed);
			}
		}

		/**
		 *
		 *
		 */
		public void removeColibriChannel(JID user)
		{
			String username = user.toString();

			if (users.containsKey(username))
			{
				count--;

				if (count < 1)
				{
					closeColibri();
				}

				Participant participant = users.get(username);
				expireColibriChannel(participant);

				routeColibriEvent(participant, null, false);	// send to others

				for (Participant reciepient : users.values())
				{
					if (participant.getUser().toString().equals(reciepient.getUser().toString()) == false)
					{
						Element conf = ssrcs.get(reciepient.getUser().toString());

						if (conf != null)							// send others to me
						{
							Presence presence = new Presence();
							presence.setFrom(XMPPServer.getInstance().createJID(focusName, focusName));
							presence.setTo(participant.getUser());

							RemoveSourceEvent event = new RemoveSourceEvent();
							event.setMuc(roomJid);
							event.setNickname(reciepient.getNickname());
							event.setParticipant(reciepient.getUser());
							event.setConference(conf);
							presence.getElement().add(colibriProvider.toXML(event));

							router.route(presence);
						}
					}
				}
				users.remove(username);
				ssrcs.remove(username);

				bridgeJoin(false, participant);
			}

			Log.info("removeColibriChannel " + count);
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
		public void closeColibri()
		{
			Log.info("FocusAgent - closeColibri ");

			count = 0;
			focusId = null;	// invalidate current focus

			if (recorder != null)
			{
				try {
					recorder.done();
					outDataSource.disconnect();
					outDataSource.stop();

					for (CallSession callSession : callSessions.values())
					{
						callSession.mediaStream.stop();
						callSession.mediaStream.close();
					}

					callSessions.clear();

				} catch (Exception e) {}

				recorder = null;
				outDataSource = null;
			}
		}
		/**
		 *
		 *
		 */
		public void setRouter(SessionPacketRouter router, LocalClientSession session)
		{
			this.router = router;
			this.session = session;
		}
		/**
		 *
		 *
		 */
		public void closeSession()
		{
			Log.debug("FocusAgent - closeSession ");

			if (session != null)
			{
				session.close();
				session = null;
			}

		}
		/**
		 *
		 *
		 */
		public void closeVirtualConnection()
		{
			Log.debug("FocusAgent - closeVirtualConnection ");
			closeColibri();
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
			closeColibri();
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

				if (focusId == null)
				{
					focusId = conference.attributeValue("id");
				}

				String id = packet.getID();

				if (ids.containsKey(id))
				{
					Participant participant = ids.remove(id);
					String username = participant.getUser().toString();

					Log.info("FocusAgent response for user " + participant + " " + focusId + "\n" + conference);

					if (id.startsWith("offer-") && users.containsKey(username) == false)
					{
						addUser(participant, conference);
					}

					if (id.startsWith("answer-"))
					{
						broadcastSSRC(participant);
					}
				}


			} else if (iq.getType() == IQ.Type.error)  {
				closeColibri();
				Log.error("Videobrideg error \n" + packet);

				for (Participant reciepient : users.values())
				{
					sendRayoEvent(reciepient, null, false, reciepient);
				}


			} else if (iq.getType() == IQ.Type.set || iq.getType() == IQ.Type.get)  {
				JID user = iq.getFrom();
				Element root = iq.getChildElement();
				Element conference = null;

				if (user.toString().equals("jitsi-videobridge." + domainName)) // SSRC notification from videobridge, brodcast, create recorder
				{
					conference = root.createCopy();	// rayo from participant

					boolean recordMedia = "true".equals(JiveGlobals.getProperty(RECORD_PROPERTY_NAME, "false"));

					if (recordMedia && recorder == null)
					{
						try {
							synchronized (this)
							{
								Log.info("Creating recorder file " + iq.getTo());

								String focusJid = XMPPServer.getInstance().createJID(focusName, focusName).toString();
								Content content = getVideoBridge().getConference(focusId, focusJid).getOrCreateContent("audio");
								AudioMixerMediaDevice mediaDevice = (AudioMixerMediaDevice) content.getMixer();
								outDataSource = mediaDevice.createOutputDataSource();

								outDataSource.connect();

								BufferTransferHandler transferHandler = new BufferTransferHandler()
								{
									private final Buffer buffer = new Buffer();

									public void transferData(PushBufferStream stream)
									{
										try {
											stream.read(buffer);

											byte[] data = (byte[]) buffer.getData();
											byte[] ulawData = new byte[data.length /2];
											AudioConversion.linearToUlaw(data, 0, ulawData, 0);

											if (recorder != null) recorder.write(ulawData, 0, ulawData.length);

										} catch (Exception e) {

											Log.error("transferData exception", e);
										}
									}
								};

								AudioMixingPushBufferStream stream = (AudioMixingPushBufferStream) outDataSource.getStreams()[0];
								stream.setTransferHandler(transferHandler);

								AudioFormat format = stream.getFormat();

								String recordingPath = JiveGlobals.getHomeDirectory() + File.separator + "resources" + File.separator + "spank" + File.separator + "rayo"  + File.separator + "video_recordings";
								String fileName = "audio-" + focusName + "-" + System.currentTimeMillis() + ".au";
								boolean pcmu = format.getEncoding() == AudioFormat.ULAW;
								int sampleRate = (int) format.getSampleRate();
								int channels = format.getChannels();

								recorder = new Recorder(recordingPath, fileName, "au", true, sampleRate, channels);

								outDataSource.start();

							}

						} catch (Exception e) {

							Log.error("Error creating recording file", e);
						}
					}

				} else {

					conference = root.element("conference").createCopy();	// rayo from participant

					IQ reply = IQ.createResultIQ(iq);

					if (users.containsKey(user.toString()))
						processUserAnswer(users.get(user.toString()), conference);
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
			Log.info("sendRayoEvent - " + reciepient);

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
				router.route(presence);

			} else {
				RemoveSourceEvent event = new RemoveSourceEvent();
				event.setMuc(roomJid);
				event.setNickname(participant.getNickname());
				event.setParticipant(participant.getUser());
				event.setActive(focusId != null);

				Element conf = ssrcs.get(participant.getUser().toString());

				if (conf != null)
				{
					event.setConference(conf);
					presence.getElement().add(colibriProvider.toXML(event));
					router.route(presence);
				}
			}
		}
		/**
		 *
		 *
		 */
		public Certificate[] getPeerCertificates() {
			return null;
		}

		/**
		 * Generates a new synchronization source (SSRC) identifier.
		 *
		 * @param
		 * @param i the number of times that the method has been executed prior to
		 * the current invocation
		 * @return a randomly chosen <tt>int</tt> value which is to be utilized as a
		 * new synchronization source (SSRC) identifier should it be found to be
		 * globally unique within the associated RTP session or
		 * <tt>Long.MAX_VALUE</tt> to cancel the operation
		 */
		private long ssrcFactoryGenerateSSRC(String cause, int i)
		{
			if (initialLocalSSRC != -1)
			{
				if (i == 0)
					return (int) initialLocalSSRC;
				else if (cause.equals(GenerateSSRCCause.REMOVE_SEND_STREAM.name()))
					return Long.MAX_VALUE;
			}
			return ssrcFactory.doGenerateSSRC();
		}

		private class SSRCFactoryImpl implements SSRCFactory
		{
			private int i = 0;

			/**
			 * The <tt>Random</tt> instance used by this <tt>SSRCFactory</tt> to
			 * generate new synchronization source (SSRC) identifiers.
			 */
			private final Random random = new Random();

			public int doGenerateSSRC()
			{
				return random.nextInt();
			}

			/**
			 * {@inheritDoc}
			 */
			@Override
			public long generateSSRC(String cause)
			{
				return ssrcFactoryGenerateSSRC(cause, i++);
			}
		}


	}
}
