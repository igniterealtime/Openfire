/*
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 CoolServlets, Inc. All rights reserved.
 *
 * This software is the proprietary information of CoolServlets, Inc.
 * Use is subject to license terms.
 */
package org.jivesoftware.messenger.container.spi;

import org.jivesoftware.database.DbConnectionManager;
import org.jivesoftware.messenger.container.*;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XPPReader;
import org.jivesoftware.messenger.JiveGlobals;
import org.jivesoftware.messenger.auth.UnauthorizedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.dom4j.Document;


/**
 * <p>The initial container that hosts all other modules (and containers).</p>
 * <p/>
 * <p>The boostrap container operates in a similar manner to the basic container
 * but alters the standard module layout for better packaging of server.
 * Notice that the bootstrap server runs from the messengerHome directory whether
 * the server is run in standalone mode (messengerHome == server directory) or
 * as a web-app (messengerHome == setting in messenger_init.xml).</p>
 * <p/>
 * <p>Bootstrap systems should have a directory layout that looks like:</p>
 * <p/>
 * <ul>
 * <li>conf - Contains configuration information for the server as a whole
 * <ul>
 * <li>jive-messenger.xml - The bootstrap configuration file.</li>
 * </ul>
 * </li>
 * <li>logs - The root directory for log files</li>
 * <li>security - The security keystores for the server (if any)</li>
 * <li>plugins - The module directory for loading server modules</li>
 * </li>
 * <p/>
 * <p>Note that the root lib directory is NOT part
 * of the container's responsibilties. The jars in that
 * directory are loaded by the ServerStarter in standalone mode, or by the
 * web-app server from the WAR or web-app/WEB-INF/lib directory
 * when deployed into an app server.</p>
 *
 * @author Derek DeMoro
 * @author Iain Shigeoka
 */
public abstract class BootstrapContainer implements Container, ServiceLookupProvider {

    /**
     * <p>Obtain the full class names of the setup server modules.</p>
     * <p/>
     * <p>Setup modules are required for the setup admin UI and should
     * be the minimum subset that will allow the setup to proceed.</p>
     *
     * @return An array of the full class names of the modules to load
     */
    protected abstract String[] getSetupModuleNames();

    /**
     * <p>Obtain the full class names of the boot server modules.</p>
     * <p/>
     * <p>Boot modules are required for the runtime behavior of the server
     * and MUST not depend on other modules (including other boot modules).
     * These are the bootstrap modules that enable other modules to startup
     * without worrying about startup dependencies.</p>
     *
     * @return An array of the full class names of the modules to load
     */
    protected abstract String[] getBootModuleNames();

    /**
     * <p>Obtain the full class names of the core server modules.</p>
     * <p/>
     * <p>These modules are a core part of the standard runtime service
     * profile. They often depend on the boot modules for proper operation.</p>
     *
     * @return An array of the full class names of the modules to load
     */
    protected abstract String[] getCoreModuleNames();

    /**
     * <p>Obtain the full class names of the standard server modules.</p>
     * <p/>
     * <p>These modules are part of the standard runtime server
     * profile. They often depend on the boot and core modules for proper
     * operation.</p>
     *
     * @return An array of the full class names of the modules to load
     */
    protected abstract String[] getStandardModuleNames();

    /**
     * <p>Obtain the short filename to be used in the license and
     * configuration full filename.</p>
     * <p/>
     * <p>The file name is typically the 'bare' product name (e.g.
     * messenger, nntp, etc). The license file name will become
     * <tt>jive_name.license</tt> and the config file <tt>jive-name.xml</tt> and are
     * located in the <tt>config</tt> directory.</p>
     *
     * @return The name used to generate
     */
    protected abstract String getFileCoreName();

    /**
     * <p>The root context for the server. modules will use this as
     * a parent context to their own module context.</p>
     */
    private XMLModuleContext context;
    /**
     * <p>The lookup for the bootstrap container.</p>
     */
    private ServiceLookup lookup;
    /**
     * <p>The registration of the service with itself.</p>
     */
    private ServiceRegistration containerRegistration;

    /**
     * All modules loaded by this container
     */
    private List modules = new ArrayList();

    /**
     * Location of the messengerHome directory. All configuration files should be
     * located here.
     */
    private File messengerHome;
    private File logDir;
    private File configFile;
    private ClassLoader loader;

    /**
     * True if in setup mode
     */
    private boolean setupMode = true;

    private static final String JIVE_LOG_DIR = "logs";
    private static final String STARTER_CLASSNAME =
            "org.jivesoftware.messenger.container.starter.ServerStarter";
    private static final String WRAPPER_CLASSNAME =
            "org.tanukisoftware.wrapper.WrapperManager";

    /**
     * Construct the server bootstrap server.
     * <p/>
     * the server could not be started
     */
    public BootstrapContainer() {
        ServiceLookupFactory.setLookupProvider(this);
        start();
    }

    /**
     * <p>Starts the container.</p>
     */
    private void start() {
        try {
            // Let's specify jive_messenger.xml as the new config file.
            locateJiveHome();
            context = new XMLModuleContext(null, configFile, messengerHome, logDir);
            if ("true".equals(context.getProperty("setup"))) {
                setupMode = false;
            }

            if (isStandAlone()) {
                Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());
            }

            lookup = new ServiceLookupImpl();
            ServiceItem serverItem = new ServiceItem(null, this, null);
            containerRegistration = lookup.register(serverItem);

            loader = Thread.currentThread().getContextClassLoader();

            if (setupMode) {
                loadCorePlugins(getSetupModuleNames());
            }
            else {
                verifyDataSource();
                loadCorePlugins(getBootModuleNames());
                loadCorePlugins(getCoreModuleNames());
                loadCorePlugins(getStandardModuleNames());
            }
            startCorePlugins();
            loadPlugins(setupMode);
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.error(e);
            System.out.println(LocaleUtils.getLocalizedString("startup.error"));
            shutdownContainer();
        }
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
            PreparedStatement stmt = conn.prepareStatement("SELECT userID FROM jiveUser WHERE userID=1");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
            }
            rs.close();
        }
        catch (Exception e) {
            System.out.println("Database setup or configuration error: " +
                    "Please verify your database settings and check the " +
                    "logs/error.log file for detailed error messages.");
            Log.error("Database could not be accessed", e);
            throw new IllegalArgumentException();
        }
        finally {
            if (conn != null) {
                try {
                    conn.close();
                }
                catch (SQLException e) {
                    Log.error("Could not close open connection ", e);
                }
            }
        }
    }

    /**
     * Load plugins that are integrated with the core server.
     * <p/>
     *
     */
    private void loadCorePlugins(String[] modules) {
        for (int i = 0; i < modules.length; i++) {
            Module mod = null;
            boolean isInitialized = false;
            try {
                Class modClass = loader.loadClass(modules[i]);
                mod = (Module)modClass.newInstance();
                mod.initialize(context, this);
                isInitialized = true;
                this.modules.add(mod);
            }
            catch (Exception e) {
                if (isInitialized) {
                    mod.stop();
                    mod.destroy();
                }
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * <p>Following the loading and initialization of all the plug-ins
     * this method is called to iterate through the known modules and
     * start them.</p>
     */
    private void startCorePlugins() {
        Iterator modIter = modules.iterator();
        while (modIter.hasNext()) {
            boolean started = false;
            Module mod = null;
            try {
                mod = (Module)modIter.next();
                mod.start();
            }
            catch (Exception e) {
                if (started && mod != null) {
                    mod.stop();
                    mod.destroy();
                }
                Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
            }
        }
    }

    /**
     * Makes a best effort attempt to shutdown the server
     */
    private void shutdownContainer() {
        // Unregister the container first
        if (containerRegistration != null) {
            containerRegistration.cancel();
            containerRegistration = null;
        }
        // Now get all modules and stop and destroy them
        Iterator loadedModules = modules.iterator();
        while (loadedModules.hasNext()) {
            Module mod = (Module)loadedModules.next();
            mod.stop();
            mod.destroy();
        }
        modules.clear();
        // TODO: hack to allow safe stopping
        Log.info("Jive Messenger stopped");
    }

    /**
     * Loads the plugins for the container.
     *
     * @param setupMode True if starting in setup mode.
     */
    private void loadPlugins(boolean setupMode) {
        File pluginDir = new File(messengerHome + "/plugins");
        if (pluginDir.exists()) {
            File[] plugins = pluginDir.listFiles();
            for (int i = 0; i < plugins.length; i++) {
                if (plugins[i].isDirectory()) {
                    if (setupMode) {
                        // Only load web-admin plug-in
                        if ("admin".equals(plugins[i].getName())) {
                            loadPlugin(plugins[i]);
                        }
                    }
                    else {
                        loadPlugin(plugins[i]);
                    }
                }
            }
        }
        else {
            Log.info("startup.missing-plugins");
        }
    }

    /**
     * Loads a plug-in module into the container. Loading consists of the
     * following steps:
     * <p/>
     * <ul>
     * <li>Add all jars in the <tt>lib</tt> dir (if it exists) to the class loader</li>
     * <li>Add all files in <tt>classes</tt> dir (if it exists) to the class loader</li>
     * <li>Locate and load <tt>module.xml</tt> into the context</li>
     * <li>For each jive.module entry, load the given class as a module and start it</li>
     * </ul>
     *
     * @param pluginDir The root directory for the plug-in
     */
    private void loadPlugin(File pluginDir) {
        Module mod = null;
        try {
            File moduleConfig = new File(pluginDir, "module.xml");
            if (moduleConfig.exists()) {
                ModuleContext modContext =
                        new XMLModuleContext(context, moduleConfig, pluginDir, logDir);
                JiveModuleLoader modLoader = new JiveModuleLoader(pluginDir.toString());
                mod = modLoader.loadModule(modContext.getProperty("module"));
                mod.initialize(modContext, this);
                mod.start();
                this.modules.add(mod);
            }
            else {
                Log.warn("Plugin " + pluginDir +
                        " not loaded: no module.xml configuration file found");
            }
        }
        catch (Exception e) {
            Log.error(LocaleUtils.getLocalizedString("admin.error"), e);
        }
    }

    public boolean isSetupMode() {
        return setupMode;
    }

    public ServiceLookup getServiceLookup() throws UnauthorizedException {
        return lookup;
    }

    public Object startService(Class service) throws UnauthorizedException {
        Object instance = lookup.lookup(service);
        if (instance == null) {
            if (service.getName().equals("org.jivesoftware.messenger.user.UserManager")) {
                loadCorePlugins(new String[]{
                    "org.jivesoftware.messenger.user.spi.UserManagerImpl"});
                instance = lookup.lookup(service);
            }
        }
        return instance;
    }

    public void stopService(Class service) throws UnauthorizedException {
        Iterator modIter = modules.iterator();
        while (modIter.hasNext()) {
            Object mod = modIter.next();
            if (mod.getClass().isAssignableFrom(service)) {
                modIter.remove();
                ((Module)mod).stop();
                ((Module)mod).destroy();
            }
        }
    }

    public ModuleContext getModuleContext() throws UnauthorizedException {
        return context;
    }

    public Entry getLocalServerAttribute() throws UnauthorizedException {
        return new Entry() {
        };
    }

    /**
     * <p>Restarts the container and all it's modules.</p>
     */
    public void restart() {
        if (isStandAlone() && isRestartable()) {
            try {
                Class wrapperClass = Class.forName(WRAPPER_CLASSNAME);
                Method restartMethod = wrapperClass.getMethod("restart", (Class)null);
                restartMethod.invoke(null, (Class)null);
            }
            catch (Exception e) {
                Log.error("Could not restart container", e);
            }
        }
    }

    public void stop() throws UnauthorizedException {
        // Only do a system exit if we're running standalone
        if (isStandAlone()) {
            // if we're in a wrapper, we have to tell the wrapper to shut us down
            if (isRestartable()) {
                try {
                    Class wrapperClass = Class.forName(WRAPPER_CLASSNAME);
                    Method stopMethod = wrapperClass.getMethod("stop",
                            new Class[]{Integer.TYPE});
                    stopMethod.invoke(null, new Object[]{new Integer(0)});
                }
                catch (Exception e) {
                    Log.error("Could not stop container", e);
                }
            }
            else {
                shutdownContainer();
                Thread shutdownThread = new ShutdownThread();
                shutdownThread.setDaemon(true);
                shutdownThread.start();
            }
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
            if (containerRegistration != null) {
                shutdownContainer();
            }
            Log.info("Server halted");
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
     * @throws FileNotFoundException if there was a problem with the home
     *                               directory provided
     */
    private File verifyHome(String homeGuess, String jiveConfigName) throws
            FileNotFoundException {
        String forumsConfigName = "jive_config.xml";
        File realHome = null;
        File guess = new File(homeGuess);
        File configFileGuess = new File(guess, jiveConfigName);
        if (configFileGuess.exists()) {
            realHome = guess;
        }
        File forumsHome = new File(guess, forumsConfigName);
        if (forumsHome.exists()) {
            throw new FileNotFoundException();
        }
        return realHome;
    }

    /**
     * <p>Retrieve the jive home for the container.</p>
     *
     * @throws FileNotFoundException If jiveHome could not be located
     */
    private void locateJiveHome() throws FileNotFoundException {
        String jiveConfigName = "conf" + File.separator + "jive-" + getFileCoreName() + ".xml";
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

        // If we still don't have jiveHome, let's assume this is standalone
        // and just look for jiveHome in a standard sub-dir location and verify
        // by looking for the config file
        if (messengerHome == null) {
            try {
                messengerHome = verifyHome("..", jiveConfigName);
            }
            catch (FileNotFoundException fe) {
                String path = "..";
                try {
                    path = new File("..").getCanonicalPath();
                }
                catch (Exception e) {
                    System.err.println("Could not resolve path to '..'");
                }
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
                    Document doc = XPPReader.parseDocument(new InputStreamReader(in),
                            this.getClass());
                    String path = doc.getRootElement().getText();
                    try {
                        if (path != null) {
                            messengerHome = verifyHome(path,
                                    jiveConfigName);
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
                try {
                    if (in != null) {
                        in.close();
                    }
                }
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
            configFile = new File(messengerHome, jiveConfigName);
            logDir = new File(messengerHome, JIVE_LOG_DIR);
        }
    }
}
