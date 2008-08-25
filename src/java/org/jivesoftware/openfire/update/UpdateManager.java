/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.update;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.MessageRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.util.*;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.*;
import java.util.*;

/**
 * Service that frequently checks for new server or plugins releases. By default the service
 * will check every 48 hours for updates. Use the system property <tt>update.frequency</tt>
 * to set new values.<p>
 * <p/>
 * New versions of plugins can be downloaded and installed. However, new server releases
 * should be manually installed.
 *
 * @author Gaston Dombiak
 */
public class UpdateManager extends BasicModule {

    protected static DocumentFactory docFactory = DocumentFactory.getInstance();

    /**
     * URL of the servlet (JSP) that provides the "check for update" service.
     */
    private static String updateServiceURL = "http://www.igniterealtime.org/projects/openfire/versions.jsp";

    /**
     * Information about the available server update.
     */
    private Update serverUpdate;

    /**
     * List of plugins that need to be updated.
     */
    private Collection<Update> pluginUpdates = new ArrayList<Update>();

    /**
     * List of plugins available at igniterealtime.org.
     */
    private Map<String, AvailablePlugin> availablePlugins = new HashMap<String, AvailablePlugin>();

    /**
     * Thread that performs the periodic checks for updates.
     */
    private Thread thread;

    /**
     * Router to use for sending notitication messages to admins.
     */
    private MessageRouter router;
    private String serverName;


    public UpdateManager() {
        super("Update manager");
    }

    public void start() throws IllegalStateException {
        super.start();
        startService();
    }

    /**
     * Starts sevice that checks for new updates.
     */
    private void startService() {
        // Thread that performs the periodic checks for updates
        thread = new Thread("Update Manager") {
            public void run() {
                try {
                    // Sleep for 5 seconds before starting to work. This is required because
                    // this module has a dependency on the PluginManager, which is loaded
                    // after all other modules.
                    Thread.sleep(5000);
                    // Load last saved information (if any)
                    loadSavedInfo();
                    while (isServiceEnabled()) {
                        waitForNextCheck();
                        // Check if the service is still enabled
                        if (isServiceEnabled()) {
                            try {
                                // Check for server updates
                                checkForServerUpdate(true);
                                // Refresh list of available plugins and check for plugin updates
                                checkForPluginsUpdates(true);
                            }
                            catch (Exception e) {
                                Log.error("Error checking for updates", e);
                            }
                            // Keep track of the last time we checked for updates. 
                            long now = System.currentTimeMillis();
                            JiveGlobals.setProperty("update.lastCheck", String.valueOf(now));
                            // As an extra precaution, make sure that that the value
                            // we just set is saved. If not, return to make sure that
                            // no additional update checks are performed until Openfire
                            // is restarted.
                            if (now != JiveGlobals.getLongProperty("update.lastCheck", 0)) {
                                Log.error("Error: update service check did not save correctly. " +
                                        "Stopping update service.");
                                return;
                            }
                        }
                    }
                }
                catch (InterruptedException e) {
                    Log.error(e);
                }
                finally {
                    // Clean up reference to this thread
                    thread = null;
                }
            }

            private void waitForNextCheck() throws InterruptedException {
                long lastCheck = JiveGlobals.getLongProperty("update.lastCheck", 0);
                if (lastCheck == 0) {
                    // This is the first time the server is used (since we added this feature)
                    Thread.sleep(30000);
                }
                else {
                    long elapsed = System.currentTimeMillis() - lastCheck;
                    long frequency = getCheckFrequency() * JiveConstants.HOUR;
                    // Sleep until we've waited the appropriate amount of time.
                    while (elapsed < frequency) {
                        Thread.sleep(frequency - elapsed);
                        // Update the elapsed time. This check is necessary just in case the
                        // thread woke up early.
                        elapsed = System.currentTimeMillis() - lastCheck;
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    public void initialize(XMPPServer server) {
        super.initialize(server);
        router = server.getMessageRouter();
        serverName = server.getServerInfo().getXMPPDomain();
    }

    /**
     * Queries the igniterealtime.org server for new server and plugin updates.
     *
     * @param notificationsEnabled true if admins will be notified when new updates are found.
     * @throws Exception if some error happens during the query.
     */
    public synchronized void checkForServerUpdate(boolean notificationsEnabled) throws Exception {
        // Get the XML request to include in the HTTP request
        String requestXML = getServerUpdateRequest();
        // Send the request to the server
        HttpClient httpClient = new HttpClient();
        // Check if a proxy should be used
        if (isUsingProxy()) {
            HostConfiguration hc = new HostConfiguration();
            hc.setProxy(getProxyHost(), getProxyPort());
            httpClient.setHostConfiguration(hc);
        }
        PostMethod postMethod = new PostMethod(updateServiceURL);
        NameValuePair[] data = {
                new NameValuePair("type", "update"),
                new NameValuePair("query", requestXML)
        };
        postMethod.setRequestBody(data);
        if (httpClient.executeMethod(postMethod) == 200) {
            // Process answer from the server
            String responseBody = postMethod.getResponseBodyAsString();
            processServerUpdateResponse(responseBody, notificationsEnabled);
        }
    }

    public synchronized void checkForPluginsUpdates(boolean notificationsEnabled) throws Exception {
        // Get the XML request to include in the HTTP request
        String requestXML = getAvailablePluginsUpdateRequest();
        // Send the request to the server
        HttpClient httpClient = new HttpClient();
        // Check if a proxy should be used
        if (isUsingProxy()) {
            HostConfiguration hc = new HostConfiguration();
            hc.setProxy(getProxyHost(), getProxyPort());
            httpClient.setHostConfiguration(hc);
        }
        PostMethod postMethod = new PostMethod(updateServiceURL);
        NameValuePair[] data = {
                new NameValuePair("type", "available"),
                new NameValuePair("query", requestXML)
        };
        postMethod.setRequestBody(data);
        if (httpClient.executeMethod(postMethod) == 200) {
            // Process answer from the server
            String responseBody = postMethod.getResponseBodyAsString();
            processAvailablePluginsResponse(responseBody, notificationsEnabled);
        }
    }

    /**
     * Download and install latest version of plugin.
     *
     * @param url the URL of the latest version of the plugin.
     * @return true if the plugin was successfully downloaded and installed.
     */
    public boolean downloadPlugin(String url) {
        boolean installed = false;
        // Download and install new version of plugin
        HttpClient httpClient = new HttpClient();
        // Check if a proxy should be used
        if (isUsingProxy()) {
            HostConfiguration hc = new HostConfiguration();
            hc.setProxy(getProxyHost(), getProxyPort());
            httpClient.setHostConfiguration(hc);
        }
        GetMethod getMethod = new GetMethod(url);
        //execute the method
        try {
            int statusCode = httpClient.executeMethod(getMethod);
            if (statusCode == 200) {
                //get the resonse as an InputStream
                InputStream in = getMethod.getResponseBodyAsStream();
                String pluginFilename = url.substring(url.lastIndexOf("/") + 1);
                installed = XMPPServer.getInstance().getPluginManager()
                        .installPlugin(in, pluginFilename);
                in.close();
                if (installed) {
                    // Remove the plugin from the list of plugins to update
                    for (Update update : pluginUpdates) {
                        if (update.getURL().equals(url)) {
                            update.setDownloaded(true);
                        }
                    }
                    // Save response in a file for later retrieval
                    saveLatestServerInfo();
                }
            }
        }
        catch (IOException e) {
            Log.warn("Error downloading new plugin version", e);
        }
        return installed;
    }

    /**
     * Returns true if the plugin downloaded from the specified URL has been downloaded. Plugins
     * may be downloaded but not installed. The install process may take like 30 seconds to
     * detect new plugins to install.
     *
     * @param url the URL of the latest version of the plugin.
     * @return true if the plugin downloaded from the specified URL has been downloaded.
     */
    public boolean isPluginDownloaded(String url) {
        String pluginFilename = url.substring(url.lastIndexOf("/") + 1);
        return XMPPServer.getInstance().getPluginManager().isPluginDownloaded(pluginFilename);
    }

    /**
     * Returns the list of available plugins to install as reported by igniterealtime.org.
     * Currently installed plugins will not be included or plugins that require a newer
     * server version.
     *
     * @return the list of available plugins to install as reported by igniterealtime.org.
     */
    public List<AvailablePlugin> getNotInstalledPlugins() {
        List<AvailablePlugin> plugins = new ArrayList<AvailablePlugin>(availablePlugins.values());
        XMPPServer server = XMPPServer.getInstance();
        // Remove installed plugins from the list of available plugins
        for (Plugin plugin : server.getPluginManager().getPlugins()) {
            String pluginName = server.getPluginManager().getName(plugin);
            for (Iterator<AvailablePlugin> it = plugins.iterator(); it.hasNext();) {
                AvailablePlugin availablePlugin = it.next();
                if (availablePlugin.getName().equals(pluginName)) {
                    it.remove();
                    break;
                }
            }
        }
        // Remove plugins that require a newer server version
        String serverVersion = XMPPServer.getInstance().getServerInfo().getVersion().getVersionString();
        for (Iterator<AvailablePlugin> it=plugins.iterator(); it.hasNext();) {
            AvailablePlugin plugin = it.next();
            if (serverVersion.compareTo(plugin.getMinServerVersion()) < 0) {
                it.remove();
            }
        }
        return plugins;
    }

    /**
     * Returns the message to send to admins when new updates are available. When sending
     * this message information about the new updates avaiable will be appended.
     *
     * @return the message to send to admins when new updates are available.
     */
    public String getNotificationMessage() {
        return LocaleUtils.getLocalizedString("update.notification-message");
    }

    /**
     * Returns true if the check for updates service is enabled.
     *
     * @return true if the check for updates service is enabled.
     */
    public boolean isServiceEnabled() {
        return JiveGlobals.getBooleanProperty("update.service-enabled", true);
    }

    /**
     * Sets if the check for updates service is enabled.
     *
     * @param enabled true if the check for updates service is enabled.
     */
    public void setServiceEnabled(boolean enabled) {
        JiveGlobals.setProperty("update.service-enabled", enabled ? "true" : "false");
        if (enabled && thread == null) {
            startService();
        }
    }

    /**
     * Returns true if admins should be notified by IM when new updates are available.
     *
     * @return true if admins should be notified by IM when new updates are available.
     */
    public boolean isNotificationEnabled() {
        return JiveGlobals.getBooleanProperty("update.notify-admins", true);
    }

    /**
     * Sets if admins should be notified by IM when new updates are available.
     *
     * @param enabled true if admins should be notified by IM when new updates are available.
     */
    public void setNotificationEnabled(boolean enabled) {
        JiveGlobals.setProperty("update.notify-admins", enabled ? "true" : "false");
    }

    /**
     * Returns the frequency to check for updates. By default, this will happen every 48 hours.
     * The frequency returned will never be less than 12 hours.
     *
     * @return the frequency to check for updates in hours.
     */
    public int getCheckFrequency() {
        int frequency = JiveGlobals.getIntProperty("update.frequency", 48);
        if (frequency < 12) {
            return 12;
        }
        else {
            return frequency;
        }
    }

    /**
     * Sets the frequency to check for updates. By default, this will happen every 48 hours.
     *
     * @param checkFrequency the frequency to check for updates.
     */
    public void setCheckFrequency(int checkFrequency) {
        JiveGlobals.setProperty("update.frequency", Integer.toString(checkFrequency));
    }

    /**
     * Returns true if a proxy is being used to connect to igniterealtime.org or false if
     * a direct connection should be attempted.
     *
     * @return true if a proxy is being used to connect to igniterealtime.org.
     */
    public boolean isUsingProxy() {
        return getProxyHost() != null;
    }

    /**
     * Returns the host of the proxy to use to connect to igniterealtime.org or <tt>null</tt>
     * if no proxy is used.
     *
     * @return the host of the proxy or null if no proxy is used.
     */
    public String getProxyHost() {
        return JiveGlobals.getProperty("update.proxy.host");
    }

    /**
     * Sets the host of the proxy to use to connect to igniterealtime.org or <tt>null</tt>
     * if no proxy is used.
     *
     * @param host the host of the proxy or null if no proxy is used.
     */
    public void setProxyHost(String host) {
        if (host == null) {
            // Remove the property
            JiveGlobals.deleteProperty("update.proxy.host");
        }
        else {
            // Create or update the property
            JiveGlobals.setProperty("update.proxy.host", host);
        }
    }

    /**
     * Returns the port of the proxy to use to connect to igniterealtime.org or -1 if no
     * proxy is being used.
     *
     * @return the port of the proxy to use to connect to igniterealtime.org or -1 if no
     *         proxy is being used.
     */
    public int getProxyPort() {
        return JiveGlobals.getIntProperty("update.proxy.port", -1);
    }

    /**
     * Sets the port of the proxy to use to connect to igniterealtime.org or -1 if no
     * proxy is being used.
     *
     * @param port the port of the proxy to use to connect to igniterealtime.org or -1 if no
     *        proxy is being used.
     */
    public void setProxyPort(int port) {
        JiveGlobals.setProperty("update.proxy.port", Integer.toString(port));
    }

    /**
     * Returns the server update or <tt>null</tt> if the server is up to date.
     *
     * @return the server update or null if the server is up to date.
     */
    public Update getServerUpdate() {
        return serverUpdate;
    }

    /**
     * Returns the plugin update or <tt>null</tt> if the plugin is up to date.
     *
     * @param pluginName     the name of the plugin (as described in the meta-data).
     * @param currentVersion current version of the plugin that is installed.
     * @return the plugin update or null if the plugin is up to date.
     */
    public Update getPluginUpdate(String pluginName, String currentVersion) {
        for (Update update : pluginUpdates) {
            // Check if this is the requested plugin
            if (update.getComponentName().equals(pluginName)) {
                // Check if the plugin version is right
                if (update.getLatestVersion().compareTo(currentVersion) > 0) {
                    return update;
                }
            }
        }
        return null;
    }

    private String getServerUpdateRequest() {
        XMPPServer server = XMPPServer.getInstance();
        Element xmlRequest = docFactory.createDocument().addElement("version");
        // Add current openfire version
        Element openfire = xmlRequest.addElement("openfire");
        openfire.addAttribute("current", server.getServerInfo().getVersion().getVersionString());
        return xmlRequest.asXML();
    }

    private String getAvailablePluginsUpdateRequest() {
        Element xmlRequest = docFactory.createDocument().addElement("available");
        // Add locale so we can get current name and description of plugins
        Element locale = xmlRequest.addElement("locale");
        locale.addText(JiveGlobals.getLocale().toString());
        return xmlRequest.asXML();
    }

    private void processServerUpdateResponse(String response, boolean notificationsEnabled)
            throws DocumentException {
        // Reset last known update information
        serverUpdate = null;
        SAXReader xmlReader = new SAXReader();
        xmlReader.setEncoding("UTF-8");
        Element xmlResponse = xmlReader.read(new StringReader(response)).getRootElement();
        // Parse response and keep info as Update objects
        Element openfire = xmlResponse.element("openfire");
        if (openfire != null) {
            // A new version of openfire was found
            String latestVersion = openfire.attributeValue("latest");
            String changelog = openfire.attributeValue("changelog");
            String url = openfire.attributeValue("url");
            // Keep information about the available server update
            serverUpdate = new Update("Openfire", latestVersion, changelog, url);
        }
        // Check if we need to send notifications to admins
        if (notificationsEnabled && isNotificationEnabled() && serverUpdate != null) {
            Collection<JID> admins = XMPPServer.getInstance().getAdmins();
            Message notification = new Message();
            notification.setFrom(serverName);
            notification.setBody(getNotificationMessage() + " " + serverUpdate.getComponentName() +
                    " " + serverUpdate.getLatestVersion());
            for (JID jid : admins) {
                notification.setTo(jid);
                router.route(notification);
            }
        }
        // Save response in a file for later retrieval
        saveLatestServerInfo();
    }

    private void processAvailablePluginsResponse(String response, boolean notificationsEnabled)
            throws DocumentException {
        // Reset last known list of available plugins
        availablePlugins = new HashMap<String, AvailablePlugin>();

        // Parse response and keep info as AvailablePlugin objects
        SAXReader xmlReader = new SAXReader();
        xmlReader.setEncoding("UTF-8");
        Element xmlResponse = xmlReader.read(new StringReader(response)).getRootElement();
        Iterator plugins = xmlResponse.elementIterator("plugin");
        while (plugins.hasNext()) {
            Element plugin = (Element) plugins.next();
            String pluginName = plugin.attributeValue("name");
            String latestVersion = plugin.attributeValue("latest");
            String icon = plugin.attributeValue("icon");
            String readme = plugin.attributeValue("readme");
            String changelog = plugin.attributeValue("changelog");
            String url = plugin.attributeValue("url");
            String licenseType = plugin.attributeValue("licenseType");
            String description = plugin.attributeValue("description");
            String author = plugin.attributeValue("author");
            String minServerVersion = plugin.attributeValue("minServerVersion");
            String fileSize = plugin.attributeValue("fileSize");
            AvailablePlugin available = new AvailablePlugin(pluginName, description, latestVersion,
                    author, icon, changelog, readme, licenseType, minServerVersion, url, fileSize);
            // Add plugin to the list of available plugins at js.org
            availablePlugins.put(pluginName, available);
        }

        // Figure out local plugins that need to be updated
        buildPluginsUpdateList();

        // Check if we need to send notifications to admins
        if (notificationsEnabled && isNotificationEnabled() && !pluginUpdates.isEmpty()) {
            Collection<JID> admins = XMPPServer.getInstance().getAdmins();
            for (Update update : pluginUpdates) {
                Message notification = new Message();
                notification.setFrom(serverName);
                notification.setBody(getNotificationMessage() + " " + update.getComponentName() +
                        " " + update.getLatestVersion());
                for (JID jid : admins) {
                    notification.setTo(jid);
                    router.route(notification);
                }
            }
        }

        // Save information of available plugins
        saveAvailablePluginsInfo();
    }

    /**
     * Recreate the list of plugins that need to be updated based on the list of
     * available plugins at igniterealtime.org.
     */
    private void buildPluginsUpdateList() {
        // Reset list of plugins that need to be updated
        pluginUpdates = new ArrayList<Update>();
        XMPPServer server = XMPPServer.getInstance();
        // Compare local plugins versions with latest ones
        for (Plugin plugin : server.getPluginManager().getPlugins()) {
            String pluginName = server.getPluginManager().getName(plugin);
            AvailablePlugin latestPlugin = availablePlugins.get(pluginName);
            String currentVersion = server.getPluginManager().getVersion(plugin);
            if (latestPlugin != null &&
                    latestPlugin.getLatestVersion().compareTo(currentVersion) > 0) {
                // Check if the update can run in the current version of the server
                String serverVersion =
                        XMPPServer.getInstance().getServerInfo().getVersion().getVersionString();
                if (serverVersion.compareTo(latestPlugin.getMinServerVersion()) >= 0) {
                    Update update = new Update(pluginName, latestPlugin.getLatestVersion(),
                            latestPlugin.getChangelog(), latestPlugin.getURL());
                    pluginUpdates.add(update);
                }
            }
        }
    }

    /**
     * Saves to conf/server-update.xml information about the latest Openfire release that is
     * available for download.
     */
    private void saveLatestServerInfo() {
        Element xmlResponse = docFactory.createDocument().addElement("version");
        if (serverUpdate != null) {
            Element component = xmlResponse.addElement("openfire");
            component.addAttribute("latest", serverUpdate.getLatestVersion());
            component.addAttribute("changelog", serverUpdate.getChangelog());
            component.addAttribute("url", serverUpdate.getURL());
        }
        // Write data out to conf/server-update.xml file.
        Writer writer = null;
        try {
            // Create the conf folder if required
            File file = new File(JiveGlobals.getHomeDirectory(), "conf");
            if (!file.exists()) {
                file.mkdir();
            }
            file = new File(JiveGlobals.getHomeDirectory() + File.separator + "conf",
                    "server-update.xml");
            // Delete the old server-update.xml file if it exists
            if (file.exists()) {
                file.delete();
            }
            // Create new version.xml with returned data
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
            xmlWriter.write(xmlResponse);
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (IOException e1) {
                    Log.error(e1);
                }
            }
        }
    }

    /**
     * Saves to conf/available-plugins.xml the list of plugins that are available
     * at igniterealtime.org.
     */
    private void saveAvailablePluginsInfo() {
        //  XML to store in the file
        Element xml = docFactory.createDocument().addElement("available");
        for (AvailablePlugin plugin : availablePlugins.values()) {
            Element component = xml.addElement("plugin");
            component.addAttribute("name", plugin.getName());
            component.addAttribute("latest", plugin.getLatestVersion());
            component.addAttribute("changelog", plugin.getChangelog());
            component.addAttribute("url", plugin.getURL());
            component.addAttribute("author", plugin.getAuthor());
            component.addAttribute("description", plugin.getDescription());
            component.addAttribute("icon", plugin.getIcon());
            component.addAttribute("minServerVersion", plugin.getMinServerVersion());
            component.addAttribute("readme", plugin.getReadme());
            component.addAttribute("licenseType", plugin.getLicenseType());
            component.addAttribute("fileSize", Long.toString(plugin.getFileSize()));
        }
        // Write data out to conf/available-plugins.xml file.
        Writer writer = null;
        try {
            // Create the conf folder if required
            File file = new File(JiveGlobals.getHomeDirectory(), "conf");
            if (!file.exists()) {
                file.mkdir();
            }
            file = new File(JiveGlobals.getHomeDirectory() + File.separator + "conf",
                    "available-plugins.xml");
            // Delete the old version.xml file if it exists
            if (file.exists()) {
                file.delete();
            }
            // Create new version.xml with returned data
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
            xmlWriter.write(xml);
        }
        catch (Exception e) {
            Log.error(e);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (IOException e1) {
                    Log.error(e1);
                }
            }
        }
    }

    /**
     * Loads list of available plugins and latest available server version from
     * conf/available-plugins.xml and conf/server-update.xml respectively.
     */
    private void loadSavedInfo() {
        // Load server update information
        loadLatestServerInfo();
        // Load available plugins information
        loadAvailablePluginsInfo();
        // Recreate list of plugins to update
        buildPluginsUpdateList();
    }

    private void loadLatestServerInfo() {
        Document xmlResponse;
        File file = new File(JiveGlobals.getHomeDirectory() + File.separator + "conf",
                "server-update.xml");
        if (!file.exists()) {
            return;
        }
        // Check read privs.
        if (!file.canRead()) {
            Log.warn("Cannot retrieve server updates. File must be readable: " + file.getName());
            return;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlResponse = xmlReader.read(reader);
        }
        catch (Exception e) {
            Log.error("Error reading server-update.xml", e);
            return;
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Exception e) {
                    // Do nothing
                }
            }
        }
        // Parse info and recreate update information (if still required)
        Element openfire = xmlResponse.getRootElement().element("openfire");
        if (openfire != null) {
            String latestVersion = openfire.attributeValue("latest");
            String changelog = openfire.attributeValue("changelog");
            String url = openfire.attributeValue("url");
            // Check if current server version is correct
            String serverVersion =
                    XMPPServer.getInstance().getServerInfo().getVersion().getVersionString();
            if (serverVersion.compareTo(latestVersion) < 0) {
                serverUpdate = new Update("Openfire", latestVersion, changelog, url);
            }
        }
    }

    private void loadAvailablePluginsInfo() {
        Document xmlResponse;
        File file = new File(JiveGlobals.getHomeDirectory() + File.separator + "conf",
                "available-plugins.xml");
        if (!file.exists()) {
            return;
        }
        // Check read privs.
        if (!file.canRead()) {
            Log.warn("Cannot retrieve available plugins. File must be readable: " + file.getName());
            return;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            SAXReader xmlReader = new SAXReader();
            xmlReader.setEncoding("UTF-8");
            xmlResponse = xmlReader.read(reader);
        }
        catch (Exception e) {
            Log.error("Error reading available-plugins.xml", e);
            return;
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (Exception e) {
                    // Do nothing
                }
            }
        }
        // Parse info and recreate available plugins
        Iterator it = xmlResponse.getRootElement().elementIterator("plugin");
        while (it.hasNext()) {
            Element plugin = (Element) it.next();
            String pluginName = plugin.attributeValue("name");
            String latestVersion = plugin.attributeValue("latest");
            String icon = plugin.attributeValue("icon");
            String readme = plugin.attributeValue("readme");
            String changelog = plugin.attributeValue("changelog");
            String url = plugin.attributeValue("url");
            String licenseType = plugin.attributeValue("licenseType");
            String description = plugin.attributeValue("description");
            String author = plugin.attributeValue("author");
            String minServerVersion = plugin.attributeValue("minServerVersion");
            String fileSize = plugin.attributeValue("fileSize");
            AvailablePlugin available = new AvailablePlugin(pluginName, description, latestVersion,
                    author, icon, changelog, readme, licenseType, minServerVersion, url, fileSize);
            // Add plugin to the list of available plugins at js.org
            availablePlugins.put(pluginName, available);
        }
    }

    /**
     * Returns a previously fetched list of updates.
     *
     * @return a previously fetched list of updates.
     */
    public Collection<Update> getPluginUpdates() {
        return pluginUpdates;
    }
}
