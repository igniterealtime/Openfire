/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.xmpp.packet.JID;
import org.jivesoftware.messenger.roster.RosterManager;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.messenger.handler.*;
import org.jivesoftware.messenger.transport.TransportHandler;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.spi.AuditManagerImpl;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.disco.ServerItemsProvider;
import org.jivesoftware.messenger.disco.IQDiscoInfoHandler;
import org.jivesoftware.messenger.disco.IQDiscoItemsHandler;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.muc.spi.MultiUserChatServerImpl;
import org.jivesoftware.messenger.spi.*;
import org.jivesoftware.messenger.container.Module;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.net.MulticastDNSService;
import org.jivesoftware.util.Version;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.database.DbConnectionManager;
import org.dom4j.io.SAXReader;
import org.dom4j.Document;

import java.util.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The main XMPP server that will load, initialize and start all the server's
 * modules. The server is unique in the JVM and could be obtained by using the
 * {@link #getInstance()} method.<p>
 *
 * The loaded modules will be initialized and may access through the server other
 * modules. This means that the only way for a module to locate another module is
 * through the server. The server maintains a list of loaded modules.<p>
 *
 * After starting up all the modules the server will load any available plugin.
 * For more information see: {@link org.jivesoftware.messenger.container.PluginManager}.
 * </p>
 *
 * @author Gaston Dombiak
 */
public class XMPPServer {

    private static XMPPServer instance;

    private String name;
    private Version version;
    private Date startDate;
    private Date stopDate;
    private boolean initialized = false;

    /**
     * All modules loaded by this server
     */
    private Map<Class,Module> modules = new HashMap<Class,Module>();

    /**
     * Location of the home directory. All configuration files should be
     * located here.
     */
    private File messengerHome;
    private ClassLoader loader;

    private PluginManager pluginManager;

    /**
     * True if in setup mode
     */
    private boolean setupMode = true;

    private static final String STARTER_CLASSNAME =
            "org.jivesoftware.messenger.starter.ServerStarter";
    private static final String WRAPPER_CLASSNAME =
            "org.tanukisoftware.wrapper.WrapperManager";

    /**
     * Returns a singleton instance of XMPPServer.
     *
     * @return an instance.
     */
    public static XMPPServer getInstance() {
        return instance;
    }

    /**
     * Creates a server and starts it.
     */
    public XMPPServer() {
        // We may only have one instance of the server running on the JVM
        if (instance != null) {
            throw new IllegalStateException("A server is already running");
        }
        instance = this;
        start();
    }

    /**
     * Returns a snapshot of the server's status.
     *
     * @return the server information current at the time of the method call.
     */
    public XMPPServerInfo getServerInfo() {
        Iterator ports;
        if (getConnectionManager() == null) {
            ports = Collections.EMPTY_LIST.iterator();
        }
        else {
            ports = getConnectionManager().getPorts();
        }
        if (!initialized) {
            throw new IllegalStateException("Not initialized yet");
        }
        return new XMPPServerInfoImpl(name, version, startDate, stopDate, ports);
    }

    /**
     * Returns true if the given address is local to the server (managed by this
     * server domain).
     *
     * @return true if the address is a local address to this server.
     */
    public boolean isLocal(JID jid) {
        boolean local = false;
        if (jid != null && name != null && name.equalsIgnoreCase(jid.getDomain())) {
            local = true;
        }
        return local;
    }

    /**
     * Creates an XMPPAddress local to this server.
     *
     * @param username the user name portion of the id or null to indicate none is needed.
     * @param resource the resource portion of the id or null to indicate none is needed.
     * @return an XMPPAddress for the server.
     */
    public JID createJID(String username, String resource) {
        return new JID(username, name, resource);
    }

    private void initialize() throws FileNotFoundException {
        // Set the name of the config file
        JiveGlobals.setConfigName("jive-messenger.xml");

        locateMessenger();

        name = JiveGlobals.getProperty("xmpp.domain");
        if (name == null) {
            name = "127.0.0.1";
        }

        version = new Version(2, 2, 0, Version.ReleaseStatus.Beta, 1);
        if ("true".equals(JiveGlobals.getXMLProperty("setup"))) {
            setupMode = false;
        }

        if (isStandAlone()) {
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
        }

        loader = Thread.currentThread().getContextClassLoader();

        initialized = true;
    }

    public void start() {
        try {
            initialize();

            // If the server has already been setup then we can start all the server's modules
            if (!setupMode) {
                verifyDataSource();
                // First load all the modules so that modules may access other modules while
                // being initialized
                loadModules();
                // Initize all the modules
                initModules();
                // Start all the modules
                startModules();
            }
            // Load plugins. First, initialize component manager.
            InternalComponentManager.getInstance().start();
            File pluginDir = new File(messengerHome, "plugins");
            pluginManager = new PluginManager(pluginDir);
            pluginManager.start();

            // Log that the server has been started

            List params = new ArrayList();
            params.add(version.getVersionString());
            params.add(JiveGlobals.formatDateTime(new Date()));
            String startupBanner = LocaleUtils.getLocalizedString("startup.name", params);
            Log.info(startupBanner);
            System.out.println(startupBanner);

            startDate = new Date();
            stopDate = null;
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.error(e);
            System.out.println(LocaleUtils.getLocalizedString("startup.error"));
            shutdownServer();
        }
    }

    private void loadModules() {
        // Load boot modules
        loadModule(RoutingTableImpl.class.getName());
        loadModule(AuditManagerImpl.class.getName());
        loadModule(RosterManager.class.getName());
        loadModule(PrivateStorage.class.getName());
        // Load core modules
        loadModule(PresenceManagerImpl.class.getName());
        loadModule(SessionManager.class.getName());
        loadModule(PacketRouter.class.getName());
        loadModule(IQRouter.class.getName());
        loadModule(MessageRouter.class.getName());
        loadModule(PresenceRouter.class.getName());
        loadModule(PacketTransporterImpl.class.getName());
        loadModule(PacketDelivererImpl.class.getName());
        loadModule(TransportHandler.class.getName());
        loadModule(OfflineMessageStrategy.class.getName());
        loadModule(OfflineMessageStore.class.getName());
        // Load standard modules
        loadModule(IQAuthHandler.class.getName());
        loadModule(IQPrivateHandler.class.getName());
        loadModule(IQRegisterHandler.class.getName());
        loadModule(IQRosterHandler.class.getName());
        loadModule(IQTimeHandler.class.getName());
        loadModule(IQvCardHandler.class.getName());
        loadModule(IQVersionHandler.class.getName());
        loadModule(PresenceSubscribeHandler.class.getName());
        loadModule(PresenceUpdateHandler.class.getName());
        loadModule(IQDiscoInfoHandler.class.getName());
        loadModule(IQDiscoItemsHandler.class.getName());
        loadModule(MultiUserChatServerImpl.class.getName());
        loadModule(MulticastDNSService.class.getName());
        // Load this module always last since we don't want to start listening for clients
        // before the rest of the modules have been started
        loadModule(ConnectionManagerImpl.class.getName());
    }

    /**
     * Loads a module.
     *
     * @param module the name of the class that implements the Module interface.
     */
    private void loadModule(String module) {
        Module mod = null;
        try {
            Class modClass = loader.loadClass(module);
            mod = (Module)modClass.newInstance();
            this.modules.put(modClass, mod);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    private void initModules() {
        for (Module module : modules.values()) {
            boolean isInitialized = false;
            try {
                module.initialize(this);
                isInitialized = true;
            }
            catch (Exception e) {
                e.printStackTrace();
                // Remove the failed initialized module
                this.modules.remove(module.getClass());
                if (isInitialized) {
                    module.stop();
                    module.destroy();
                }
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * <p>Following the loading and initialization of all the modules
     * this method is called to iterate through the known modules and
     * start them.</p>
     */
    private void startModules() {
        for (Module module : modules.values()) {
            boolean started = false;
            try {
                module.start();
            }
            catch (Exception e) {
                if (started && module != null) {
                    module.stop();
                    module.destroy();
                }
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Restarts the server and all it's modules only if the server is restartable. Otherwise do
     * nothing.
     */
    public void restart() {
        if (isStandAlone() && isRestartable()) {
            try {
                Class wrapperClass = Class.forName(WRAPPER_CLASSNAME);
                Method restartMethod = wrapperClass.getMethod("restart", (Class [])null);
                restartMethod.invoke(null, (Object [])null);
            }
            catch (Exception e) {
                Log.error("Could not restart container", e);
            }
        }
    }

    /**
     * Stops the server only if running in standalone mode. Do nothing if the server is running
     * inside of another server.
     */
    public void stop() {
        // Only do a system exit if we're running standalone
        if (isStandAlone()) {
            // if we're in a wrapper, we have to tell the wrapper to shut us down
            if (isRestartable()) {
                try {
                    Class wrapperClass = Class.forName(WRAPPER_CLASSNAME);
                    Method stopMethod = wrapperClass.getMethod("stop", new Class[]{Integer.TYPE});
                    stopMethod.invoke(null, new Object[]{0});
                }
                catch (Exception e) {
                    Log.error("Could not stop container", e);
                }
            }
            else {
                shutdownServer();
                stopDate = new Date();
                Thread shutdownThread = new ShutdownThread();
                shutdownThread.setDaemon(true);
                shutdownThread.start();
            }
        }
    }

    public boolean isSetupMode() {
        return setupMode;
    }

    public boolean isRestartable() {
        boolean restartable = false;
        try {
            restartable = Class.forName(WRAPPER_CLASSNAME) != null;
        }
        catch (ClassNotFoundException e) {
            restartable = false;
        }
        return restartable;
    }

    /**
     * Returns if the server is running in standalone mode. We consider that it's running in
     * standalone if the "org.jivesoftware.messenger.starter.ServerStarter" class is present in the
     * system.
     *
     * @return true if the server is running in standalone mode.
     */
    public boolean isStandAlone() {
        boolean standalone = false;
        try {
            standalone = Class.forName(STARTER_CLASSNAME) != null;
        }
        catch (ClassNotFoundException e) {
            standalone = false;
        }
        return standalone;
    }

    /**
     * Verify that the database is accessible.
     */
    private void verifyDataSource() {
        java.sql.Connection conn = null;
        try {
            conn = DbConnectionManager.getConnection();
            PreparedStatement stmt = conn.prepareStatement("SELECT count(*) FROM jiveID");
            ResultSet rs = stmt.executeQuery();
            rs.next();
            rs.close();
            stmt.close();
        }
        catch (Exception e) {
            System.err.println("Database setup or configuration error: " +
                    "Please verify your database settings and check the " +
                    "logs/error.log file for detailed error messages.");
            Log.error("Database could not be accessed", e);
            throw new IllegalArgumentException(e);
        }
        finally {
            if (conn != null) {
                try { conn.close(); }
                catch (SQLException e) { Log.error(e); }
            }
        }
    }

    /**
     * Verifies that the given home guess is a real Messenger home directory.
     * We do the verification by checking for the Messenger config file in
     * the config dir of jiveHome.
     *
     * @param homeGuess      a guess at the path to the home directory.
     * @param jiveConfigName the name of the config file to check.
     * @return a file pointing to the home directory or null if the
     *         home directory guess was wrong.
     * @throws java.io.FileNotFoundException if there was a problem with the home
     *                               directory provided
     */
    private File verifyHome(String homeGuess, String jiveConfigName) throws FileNotFoundException {
        File realHome = null;
        File guess = new File(homeGuess);
        File configFileGuess = new File(guess, jiveConfigName);
        if (configFileGuess.exists()) {
            realHome = guess;
        }
        File messengerHome = new File(guess, jiveConfigName);
        if (!messengerHome.exists()) {
            throw new FileNotFoundException();
        }

        try{
            return new File(realHome.getCanonicalPath());
        }
        catch(Exception ex){
           throw new FileNotFoundException();
        }
    }

    /**
     * <p>Retrieve the jive home for the container.</p>
     *
     * @throws FileNotFoundException If jiveHome could not be located
     */
    private void locateMessenger() throws FileNotFoundException {
        String jiveConfigName = "conf" + File.separator + "jive-messenger.xml";
        // First, try to load it messengerHome as a system property.
        if (messengerHome == null) {
            String homeProperty = System.getProperty("messengerHome");
            try {
                if (homeProperty != null) {
                    messengerHome = verifyHome(homeProperty, jiveConfigName);
                }
            }
            catch (FileNotFoundException fe) {

            }
        }

        // If we still don't have home, let's assume this is standalone
        // and just look for home in a standard sub-dir location and verify
        // by looking for the config file
        if (messengerHome == null) {
            try {
                messengerHome = verifyHome("..", jiveConfigName).getCanonicalFile();
            }
            catch (FileNotFoundException fe) {
            }
            catch (IOException ie) {
            }
        }

        // If home is still null, no outside process has set it and
        // we have to attempt to load the value from messenger_init.xml,
        // which must be in the classpath.
        if (messengerHome == null) {
            InputStream in = null;
            try {
                in = getClass().getResourceAsStream("/messenger_init.xml");
                if (in != null) {
                    SAXReader reader = new SAXReader();
                    Document doc = reader.read(in);
                    String path = doc.getRootElement().getText();
                    try {
                        if (path != null) {
                            messengerHome = verifyHome(path, jiveConfigName);
                        }
                    }
                    catch (FileNotFoundException fe) {
                        fe.printStackTrace();
                    }
                }
            }
            catch (Exception e) {
                System.err.println("Error loading messenger_init.xml to find home.");
                e.printStackTrace();
            }
            finally {
                try { if (in != null) { in.close(); } }
                catch (Exception e) {
                    System.err.println("Could not close open connection");
                    e.printStackTrace();
                }
            }
        }

        if (messengerHome == null) {
            System.err.println("Could not locate home");
            throw new FileNotFoundException();
        }
        else {
            JiveGlobals.home = messengerHome.toString();
        }
    }

    /**
     * <p>A thread to ensure the server shuts down no matter what.</p>
     * <p>Spawned when stop() is called in standalone mode, we wait a few
     * seconds then call system exit().</p>
     *
     * @author Iain Shigeoka
     */
    private class ShutdownHookThread extends Thread {
        /**
         * <p>Logs the server shutdown.</p>
         */
        public void run() {
            shutdownServer();
            Log.info("Server halted");
            System.err.println("Server halted");
        }
    }

    /**
     * <p>A thread to ensure the server shuts down no matter what.</p>
     * <p>Spawned when stop() is called in standalone mode, we wait a few
     * seconds then call system exit().</p>
     *
     * @author Iain Shigeoka
     */
    private class ShutdownThread extends Thread {
        /**
         * <p>Shuts down the JVM after a 5 second delay.</p>
         */
        public void run() {
            try {
                Thread.sleep(5000);
                // No matter what, we make sure it's dead
                System.exit(0);
            }
            catch (InterruptedException e) {
            }

        }
    }

    /**
     * Makes a best effort attempt to shutdown the server
     */
    private void shutdownServer() {
        // If we don't have modules then the server has already been shutdown 
        if (modules.isEmpty()) {
            return;
        }
        // Get all modules and stop and destroy them
        for (Module module : modules.values()) {
            module.stop();
            module.destroy();
        }
        modules.clear();
        // Stop all plugins
        if (pluginManager != null) {
            pluginManager.shutdown();
        }
        // Stop the Db connection manager.
        DbConnectionManager.getConnectionProvider().destroy();
        // TODO: hack to allow safe stopping
        Log.info("Jive Messenger stopped");
    }

    /**
     * Returns the <code>ConnectionManager</code> registered with this server. The
     * <code>ConnectionManager</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>ConnectionManager</code> registered with this server.
     */
    public ConnectionManager getConnectionManager() {
        return (ConnectionManager) modules.get(ConnectionManagerImpl.class);
    }

    /**
     * Returns the <code>RoutingTable</code> registered with this server. The
     * <code>RoutingTable</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>RoutingTable</code> registered with this server.
     */
    public RoutingTable getRoutingTable() {
        return (RoutingTable) modules.get(RoutingTableImpl.class);
    }

    /**
     * Returns the <code>PacketDeliverer</code> registered with this server. The
     * <code>PacketDeliverer</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>PacketDeliverer</code> registered with this server.
     */
    public PacketDeliverer getPacketDeliverer() {
        return (PacketDeliverer) modules.get(PacketDelivererImpl.class);
    }

    /**
     * Returns the <code>RosterManager</code> registered with this server. The
     * <code>RosterManager</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>RosterManager</code> registered with this server.
     */
    public RosterManager getRosterManager() {
        return (RosterManager) modules.get(RosterManager.class);
    }

    /**
     * Returns the <code>PresenceManager</code> registered with this server. The
     * <code>PresenceManager</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>PresenceManager</code> registered with this server.
     */
    public PresenceManager getPresenceManager() {
        return (PresenceManager) modules.get(PresenceManagerImpl.class);
    }

    /**
     * Returns the <code>OfflineMessageStore</code> registered with this server. The
     * <code>OfflineMessageStore</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>OfflineMessageStore</code> registered with this server.
     */
    public OfflineMessageStore getOfflineMessageStore() {
        return (OfflineMessageStore) modules.get(OfflineMessageStore.class);
    }

    /**
     * Returns the <code>OfflineMessageStrategy</code> registered with this server. The
     * <code>OfflineMessageStrategy</code> was registered with the server as a module while starting
     * up the server.
     *
     * @return the <code>OfflineMessageStrategy</code> registered with this server.
     */
    public OfflineMessageStrategy getOfflineMessageStrategy() {
        return (OfflineMessageStrategy)modules.get(OfflineMessageStrategy.class);
    }

    /**
     * Returns the <code>PacketRouter</code> registered with this server. The
     * <code>PacketRouter</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>PacketRouter</code> registered with this server.
     */
    public PacketRouter getPacketRouter() {
        return (PacketRouter)modules.get(PacketRouter.class);
    }

    /**
     * Returns the <code>IQRegisterHandler</code> registered with this server. The
     * <code>IQRegisterHandler</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>IQRegisterHandler</code> registered with this server.
     */
    public IQRegisterHandler getIQRegisterHandler() {
        return (IQRegisterHandler)modules.get(IQRegisterHandler.class);
    }

    /**
     * Returns the <code>PluginManager</code> instance registered with this server.
     *
     * @return the PluginManager instance.
     */
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    /**
     * Returns a list with all the modules registered with the server that inherit from IQHandler.
     *
     * @return a list with all the modules registered with the server that inherit from IQHandler.
     */
    public List<IQHandler> getIQHandlers() {
        List<IQHandler> answer = new ArrayList<IQHandler>();
        for (Module module : modules.values()) {
            if (module instanceof IQHandler) {
                answer.add((IQHandler)module);
            }
        }
        return answer;
    }

    /**
     * Returns the <code>SessionManager</code> registered with this server. The
     * <code>SessionManager</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>SessionManager</code> registered with this server.
     */
    public SessionManager getSessionManager() {
        return (SessionManager) modules.get(SessionManager.class);
    }

    /**
     * Returns the <code>TransportHandler</code> registered with this server. The
     * <code>TransportHandler</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>TransportHandler</code> registered with this server.
     */
    public TransportHandler getTransportHandler() {
        return (TransportHandler) modules.get(TransportHandler.class);
    }

    /**
     * Returns the <code>PresenceUpdateHandler</code> registered with this server. The
     * <code>PresenceUpdateHandler</code> was registered with the server as a module while starting
     * up the server.
     *
     * @return the <code>PresenceUpdateHandler</code> registered with this server.
     */
    public PresenceUpdateHandler getPresenceUpdateHandler() {
        return (PresenceUpdateHandler) modules.get(PresenceUpdateHandler.class);
    }

    /**
     * Returns the <code>PresenceSubscribeHandler</code> registered with this server. The
     * <code>PresenceSubscribeHandler</code> was registered with the server as a module while
     * starting up the server.
     *
     * @return the <code>PresenceSubscribeHandler</code> registered with this server.
     */
    public PresenceSubscribeHandler getPresenceSubscribeHandler() {
        return (PresenceSubscribeHandler) modules.get(PresenceSubscribeHandler.class);
    }

    /**
     * Returns the <code>IQRouter</code> registered with this server. The
     * <code>IQRouter</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>IQRouter</code> registered with this server.
     */
    public IQRouter getIQRouter() {
        return (IQRouter) modules.get(IQRouter.class);
    }

    /**
     * Returns the <code>MessageRouter</code> registered with this server. The
     * <code>MessageRouter</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>MessageRouter</code> registered with this server.
     */
    public MessageRouter getMessageRouter() {
        return (MessageRouter) modules.get(MessageRouter.class);
    }

    /**
     * Returns the <code>PresenceRouter</code> registered with this server. The
     * <code>PresenceRouter</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>PresenceRouter</code> registered with this server.
     */
    public PresenceRouter getPresenceRouter() {
        return (PresenceRouter) modules.get(PresenceRouter.class);
    }

    /**
     * Returns the <code>UserManager</code> registered with this server. The
     * <code>UserManager</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>UserManager</code> registered with this server.
     */
    public UserManager getUserManager() {
        return UserManager.getInstance();
    }

    /**
     * Returns the <code>AuditManager</code> registered with this server. The
     * <code>AuditManager</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>AuditManager</code> registered with this server.
     */
    public AuditManager getAuditManager() {
        return (AuditManager) modules.get(AuditManagerImpl.class);
    }

    /**
     * Returns a list with all the modules that provide "discoverable" features.
     *
     * @return a list with all the modules that provide "discoverable" features.
     */
    public List<ServerFeaturesProvider> getServerFeaturesProviders() {
        List<ServerFeaturesProvider> answer = new ArrayList<ServerFeaturesProvider>();
        for (Module module : modules.values()) {
            if (module instanceof ServerFeaturesProvider) {
                answer.add((ServerFeaturesProvider) module);
            }
        }
        return answer;
    }

    /**
     * Returns a list with all the modules that provide "discoverable" items associated with
     * the server.
     *
     * @return a list with all the modules that provide "discoverable" items associated with
     *         the server.
     */
    public List<ServerItemsProvider> getServerItemsProviders() {
        List<ServerItemsProvider> answer = new ArrayList<ServerItemsProvider>();
        for (Module module : modules.values()) {
            if (module instanceof ServerItemsProvider) {
                answer.add((ServerItemsProvider) module);
            }
        }
        return answer;
    }

    /**
     * Returns the <code>IQDiscoInfoHandler</code> registered with this server. The
     * <code>IQDiscoInfoHandler</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>IQDiscoInfoHandler</code> registered with this server.
     */
    public IQDiscoInfoHandler getIQDiscoInfoHandler() {
        return (IQDiscoInfoHandler) modules.get(IQDiscoInfoHandler.class);
    }

    /**
     * Returns the <code>IQDiscoItemsHandler</code> registered with this server. The
     * <code>IQDiscoItemsHandler</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>IQDiscoItemsHandler</code> registered with this server.
     */
    public IQDiscoItemsHandler getIQDiscoItemsHandler() {
        return (IQDiscoItemsHandler) modules.get(IQDiscoItemsHandler.class);
    }

    /**
     * Returns the <code>PrivateStorage</code> registered with this server. The
     * <code>PrivateStorage</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>PrivateStorage</code> registered with this server.
     */
    public PrivateStorage getPrivateStorage() {
        return (PrivateStorage) modules.get(PrivateStorage.class);
    }

    /**
     * Returns the <code>MultiUserChatServer</code> registered with this server. The
     * <code>MultiUserChatServer</code> was registered with the server as a module while starting up
     * the server.
     *
     * @return the <code>MultiUserChatServer</code> registered with this server.
     */
    public MultiUserChatServer getMultiUserChatServer() {
        return (MultiUserChatServer) modules.get(MultiUserChatServerImpl.class);
    }
}
