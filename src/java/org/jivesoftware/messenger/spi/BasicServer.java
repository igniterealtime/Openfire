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

package org.jivesoftware.messenger.spi;

import org.dom4j.Document;
import org.dom4j.io.SAXReader;
import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.*;
import org.jivesoftware.messenger.roster.RosterManager;
import org.jivesoftware.messenger.audit.AuditManager;
import org.jivesoftware.messenger.audit.spi.AuditManagerImpl;
import org.jivesoftware.messenger.container.Module;
import org.jivesoftware.messenger.container.PluginManager;
import org.jivesoftware.messenger.disco.IQDiscoInfoHandler;
import org.jivesoftware.messenger.disco.IQDiscoItemsHandler;
import org.jivesoftware.messenger.disco.ServerFeaturesProvider;
import org.jivesoftware.messenger.disco.ServerItemsProvider;
import org.jivesoftware.messenger.handler.*;
import org.jivesoftware.messenger.muc.spi.MultiUserChatServerImpl;
import org.jivesoftware.messenger.muc.MultiUserChatServer;
import org.jivesoftware.messenger.transport.TransportHandler;
import org.jivesoftware.messenger.roster.RosterManager;
import org.jivesoftware.messenger.user.UserManager;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.Version;
import org.xmpp.packet.JID;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.*;

/**
 * Main entry point into the Jive xmpp server powered by a JDBC backend. Most of this code
 * is actually generic to any server so should be moved to a base class when we have
 * a separate server implementation.
 *
 * @author Iain Shigeoka
 */
public class BasicServer implements XMPPServer {

    private static BasicServer instance;

    private String name;
    private Version version;
    private Date startDate;
    private Date stopDate;
    private boolean initialized = false;

    /**
     * All modules loaded by this container
     */
    private Map<Class,Module> modules = new HashMap<Class,Module>();

    /**
     * Location of the messengerHome directory. All configuration files should be
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
     * Returns a singleton instance of BasicServer.
     *
     * @return an instance.
     */
    public static BasicServer getInstance() {
        return instance;
    }


    /**
     * Creates a server and starts it.
     */
    public BasicServer() {
        instance = this;
        start();
    }

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

    public boolean isLocal(JID jid) {
        boolean local = false;
        if (jid != null && name != null && name.equalsIgnoreCase(jid.getDomain())) {
            local = true;
        }
        return local;
    }

    public JID createJID(String username, String resource) {
        return new JID(username, name, resource);
    }

    private void initialize() throws FileNotFoundException {
        locateMessenger();

        name = JiveGlobals.getProperty("xmpp.domain");
        if (name == null) {
            name = "127.0.0.1";
        }

        version = new Version(2, 1, 0, Version.ReleaseStatus.Beta, -1);
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

            if (!setupMode) {
                verifyDataSource();
                loadModules();
                initModules();
                startModules();
            }
            // Load plugins.
            File pluginDir = new File(messengerHome, "plugins");
            pluginManager = new PluginManager(pluginDir);
            pluginManager.start();

            DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                    DateFormat.MEDIUM);
            List params = new ArrayList();
            params.add(version.getVersionString());
            params.add(formatter.format(new Date()));
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
        loadModule(ConnectionManagerImpl.class.getName());
        loadModule(PresenceManagerImpl.class.getName());
        loadModule(SessionManager.class.getName());
        loadModule(PacketRouterImpl.class.getName());
        loadModule(IQRouterImpl.class.getName());
        loadModule(MessageRouterImpl.class.getName());
        loadModule(PresenceRouterImpl.class.getName());
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

    private boolean isRestartable() {
        boolean restartable = false;
        try {
            restartable = Class.forName(WRAPPER_CLASSNAME) != null;
        }
        catch (ClassNotFoundException e) {
            restartable = false;
        }
        return restartable;
    }

    private boolean isStandAlone() {
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
            throw new IllegalArgumentException();
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
        // First, try to load it jiveHome as a system property.
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

        // If we still don't have messengerHome, let's assume this is standalone
        // and just look for messengerHome in a standard sub-dir location and verify
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

        // If messengerHome is still null, no outside process has set it and
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
                System.err.println("Error loading messenger_init.xml to find messengerHome.");
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
            System.err.println("Could not locate messengerHome");
            throw new FileNotFoundException();
        }
        else {
            JiveGlobals.messengerHome = messengerHome.toString();
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
        // TODO: hack to allow safe stopping
        Log.info("Jive Messenger stopped");
    }

    public ConnectionManager getConnectionManager() {
        return (ConnectionManager) modules.get(ConnectionManagerImpl.class);
    }

    public RoutingTable getRoutingTable() {
        return (RoutingTable) modules.get(RoutingTableImpl.class);
    }

    public PacketDeliverer getPacketDeliverer() {
        return (PacketDeliverer) modules.get(PacketDelivererImpl.class);
    }

    public RosterManager getRosterManager() {
        return (RosterManager) modules.get(RosterManager.class);
    }

    public PresenceManager getPresenceManager() {
        return (PresenceManager) modules.get(PresenceManagerImpl.class);
    }

    public OfflineMessageStore getOfflineMessageStore() {
        return (OfflineMessageStore) modules.get(OfflineMessageStore.class);
    }

    public OfflineMessageStrategy getOfflineMessageStrategy() {
        return (OfflineMessageStrategy) modules.get(OfflineMessageStrategy.class);
    }

    public PacketRouter getPacketRouter() {
        return (PacketRouter) modules.get(PacketRouterImpl.class);
    }

    public IQRegisterHandler getIQRegisterHandler() {
        return (IQRegisterHandler) modules.get(IQRegisterHandler.class);
    }

    public List<IQHandler> getIQHandlers() {
        List<IQHandler> answer = new ArrayList<IQHandler>();
        for (Module module : modules.values()) {
            if (module instanceof IQHandler) {
                answer.add((IQHandler)module);
            }
        }
        return answer;
    }

    public SessionManager getSessionManager() {
        return (SessionManager) modules.get(SessionManager.class);
    }

    public TransportHandler getTransportHandler() {
        return (TransportHandler) modules.get(TransportHandler.class);
    }

    public PresenceUpdateHandler getPresenceUpdateHandler() {
        return (PresenceUpdateHandler) modules.get(PresenceUpdateHandler.class);
    }

    public PresenceSubscribeHandler getPresenceSubscribeHandler() {
        return (PresenceSubscribeHandler) modules.get(PresenceSubscribeHandler.class);
    }

    public IQRouter getIQRouter() {
        return (IQRouter) modules.get(IQRouterImpl.class);
    }

    public MessageRouter getMessageRouter() {
        return (MessageRouter) modules.get(MessageRouterImpl.class);
    }

    public PresenceRouter getPresenceRouter() {
        return (PresenceRouter) modules.get(PresenceRouterImpl.class);
    }

    public UserManager getUserManager() {
        return UserManager.getInstance();
    }

    public AuditManager getAuditManager() {
        return (AuditManager) modules.get(AuditManagerImpl.class);
    }

    public List<ServerFeaturesProvider> getServerFeaturesProviders() {
        List<ServerFeaturesProvider> answer = new ArrayList<ServerFeaturesProvider>();
        for (Module module : modules.values()) {
            if (module instanceof ServerFeaturesProvider) {
                answer.add((ServerFeaturesProvider) module);
            }
        }
        return answer;
    }

    public List<ServerItemsProvider> getServerItemsProviders() {
        List<ServerItemsProvider> answer = new ArrayList<ServerItemsProvider>();
        for (Module module : modules.values()) {
            if (module instanceof ServerItemsProvider) {
                answer.add((ServerItemsProvider) module);
            }
        }
        return answer;
    }

    public IQDiscoInfoHandler getIQDiscoInfoHandler() {
        return (IQDiscoInfoHandler) modules.get(IQDiscoInfoHandler.class);
    }

    public PrivateStorage getPrivateStorage() {
        return (PrivateStorage) modules.get(PrivateStorage.class);
    }

    public MultiUserChatServer getMultiUserChatServer() {
        return (MultiUserChatServer) modules.get(MultiUserChatServerImpl.class);
    }
}