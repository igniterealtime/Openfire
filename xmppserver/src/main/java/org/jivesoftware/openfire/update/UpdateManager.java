/*
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

package org.jivesoftware.openfire.update;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.util.EntityUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.container.PluginMetadata;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.SystemProperty;
import org.jivesoftware.util.Version;
import org.jivesoftware.util.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

/**
 * Service that frequently checks for new server or plugins releases. By default the service
 * will check every 48 hours for updates. Use the system property {@code update.frequency}
 * to set new values.
 * <p>
 * New versions of plugins can be downloaded and installed. However, new server releases
 * should be manually installed.</p>
 *
 * @author Gaston Dombiak
 */
public class UpdateManager extends BasicModule {

    private static final SystemProperty<Boolean> ENABLED = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("update.service-enabled")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();
    private static final SystemProperty<Boolean> NOTIFY_ADMINS = SystemProperty.Builder.ofType(Boolean.class)
        .setKey("update.notify-admins")
        .setDynamic(true)
        .setDefaultValue(true)
        .build();
    static final SystemProperty<Instant> LAST_UPDATE_CHECK = SystemProperty.Builder.ofType(Instant.class)
        .setKey("update.lastCheck")
        .setDynamic(true)
        .build();
    private static final SystemProperty<Duration> UPDATE_FREQUENCY = SystemProperty.Builder.ofType(Duration.class)
        .setKey("update.frequency")
        .setDynamic(false)
        .setChronoUnit(ChronoUnit.HOURS)
        .setDefaultValue(Duration.ofHours(48))
        .setMinValue(Duration.ofHours(12))
        .build();
    private static final SystemProperty<String> PROXY_HOST = SystemProperty.Builder.ofType(String.class)
        .setKey("update.proxy.host")
        .setDynamic(true)
        .build();
    private static final SystemProperty<Integer> PROXY_PORT = SystemProperty.Builder.ofType(Integer.class)
        .setKey("update.proxy.port")
        .setDynamic(true)
        .setDefaultValue(-1)
        .setMinValue(-1)
        .setMaxValue(65535)
        .build();

    private static final Logger Log = LoggerFactory.getLogger(UpdateManager.class);

    private static final DocumentFactory docFactory = DocumentFactory.getInstance();

    /**
     * URL of the servlet (JSP) that provides the "check for update" service.
     */
    private static final String updateServiceURL = "https://www.igniterealtime.org/projects/openfire/versions.jsp";

    /**
     * Information about the available server update.
     */
    private Update serverUpdate;

    /**
     * List of plugins that need to be updated.
     */
    private Collection<Update> pluginUpdates = new ArrayList<>();

    /**
     * List of plugins available at igniterealtime.org.
     */
    private Map<String, AvailablePlugin> availablePlugins = new HashMap<>();

    /**
     * Thread that performs the periodic checks for updates.
     */
    private Thread thread;

    public UpdateManager() {
        super("Update manager");
        ENABLED.addListener(this::enableService);
    }

    @Override
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
            @Override
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
                            final Instant lastUpdate = Instant.now();
                            LAST_UPDATE_CHECK.setValue(lastUpdate);
                            // As an extra precaution, make sure that that the value
                            // we just set is saved. If not, return to make sure that
                            // no additional update checks are performed until Openfire
                            // is restarted.
                            if(!lastUpdate.equals(LAST_UPDATE_CHECK.getValue())) {
                                Log.error("Error: update service check did not save correctly. " +
                                        "Stopping update service.");
                                return;
                            }
                        }
                    }
                }
                catch (InterruptedException e) {
                    Log.error(e.getMessage(), e);
                }
                finally {
                    // Clean up reference to this thread
                    thread = null;
                }
            }

            private void waitForNextCheck() throws InterruptedException {
                final Instant lastCheck = LAST_UPDATE_CHECK.getValue();
                if (lastCheck == null) {
                    // This is the first time the server is used (since we added this feature)
                    Thread.sleep(30000);
                }
                else {
                    final Duration updateFrequency = UPDATE_FREQUENCY.getValue();
                    // This check is necessary just in case the thread woke up early.
                    while (lastCheck.plus(updateFrequency).isAfter(Instant.now())) {
                        Thread.sleep(Duration.between(Instant.now(), lastCheck.plus(updateFrequency)).toMillis());
                    }
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
    }

    private void stopService() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    @Override
    public void initialize(XMPPServer server) {
        super.initialize(server);

        JiveGlobals.migrateProperty(ENABLED.getKey());
        JiveGlobals.migrateProperty(NOTIFY_ADMINS.getKey());
    }

    /**
     * Queries the igniterealtime.org server with a request that contains the currently installed
     * server version. It's response indicates if a server update (a newer version of Openfire) is
     * available.
     *
     * @param notificationsEnabled true if admins will be notified when new updates are found.
     * @throws Exception if some error happens during the query.
     */
    public synchronized void checkForServerUpdate(boolean notificationsEnabled) throws Exception {
        final Optional<String> response = getResponse("update", getServerUpdateRequest());
        if (response.isPresent()) {
            processServerUpdateResponse(response.get(), notificationsEnabled);
        }
    }

    /**
     * Queries the igniterealtime.org server. It's response is expected to include a list of
     * plugins that are available on the server / for download.
     *
     * @param notificationsEnabled true if admins will be notified when new updates are found.
     * @throws Exception if some error happens during the query.
     */
    public synchronized void checkForPluginsUpdates(boolean notificationsEnabled) throws Exception {
        final Optional<String> response = getResponse("available", getAvailablePluginsUpdateRequest());
        if (response.isPresent()) {
            processAvailablePluginsResponse(response.get(), notificationsEnabled);
        }
    }

    private Optional<String> getResponse(final String requestType, final String requestXML) throws IOException {
        final HttpUriRequest postRequest = RequestBuilder.post(updateServiceURL)
            .addParameter("type", requestType)
            .addParameter("query", requestXML)
            .build();

        try (final CloseableHttpClient httpClient = HttpClients.custom().setRoutePlanner(getRoutePlanner()).build();
             final CloseableHttpResponse response = httpClient.execute(postRequest)) {
            final int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                return Optional.of(EntityUtils.toString(response.getEntity()));
            } else {
                return Optional.empty();
            }
        }
    }

    private HttpRoutePlanner getRoutePlanner() {
        if (isUsingProxy()) {
            return new DefaultProxyRoutePlanner(new HttpHost(getProxyHost(), getProxyPort()));
        } else {
            return new DefaultRoutePlanner(null);
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
        if (isKnownPlugin(url)) {
            final HttpGet httpGet = new HttpGet(url);

            try (final CloseableHttpClient httpClient = HttpClients.custom().setRoutePlanner(getRoutePlanner()).build();
                 final CloseableHttpResponse response = httpClient.execute(httpGet)) {
                final int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode == HttpStatus.SC_OK) {
                    String pluginFilename = url.substring(url.lastIndexOf("/") + 1);
                    installed = XMPPServer.getInstance().getPluginManager()
                        .installPlugin(response.getEntity().getContent(), pluginFilename);
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
            } catch (IOException e) {
                Log.warn("Error downloading new plugin version", e);
            }
        } else {
            Log.error("Invalid plugin download URL: " +url);
        }
        return installed;
    }
    
    /**
     * Check if the plugin URL is in the known list of available plugins.
     * 
     * i.e. that it's an approved download source.
     * 
     * @param url The URL of the plugin to download.
     * @return true if the URL is in the list. Otherwise false.
     */
    private boolean isKnownPlugin(String url) {
        for (String pluginName : availablePlugins.keySet()) {
            if (availablePlugins.get(pluginName).getDownloadURL().toString().equals(url)) {
                return true;
            }
        }
        
        return false;
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
        return XMPPServer.getInstance().getPluginManager().isInstalled( pluginFilename);
    }

    /**
     * Returns the list of available plugins, sorted alphabetically, to install as reported by igniterealtime.org.
     *
     * Currently downloaded plugins will not be included, nor will plugins that require a newer or older server version.
     *
     * @return the list of available plugins to install as reported by igniterealtime.org.
     */
    public List<AvailablePlugin> getNotInstalledPlugins()
    {
        final List<AvailablePlugin> result = new ArrayList<>( availablePlugins.values() );
        final PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        final Version currentServerVersion = XMPPServer.getInstance().getServerInfo().getVersion();

        // Iterate over the plugins, remove those that are of no interest.
        final Iterator<AvailablePlugin> iterator = result.iterator();
        while ( iterator.hasNext() )
        {
            final AvailablePlugin availablePlugin = iterator.next();

            // Remove plugins that are already downloaded from the list of available plugins.
            if ( pluginManager.isInstalled( availablePlugin.getCanonicalName() ) )
            {
                iterator.remove();
                continue;
            }

            // Remove plugins that require a newer server version.
            if ( availablePlugin.getMinServerVersion() != null && availablePlugin.getMinServerVersion().isNewerThan( currentServerVersion ) )
            {
                iterator.remove();
            }

            // Remove plugins that require an older server version.
            if ( availablePlugin.getPriorToServerVersion() != null && !availablePlugin.getPriorToServerVersion().isNewerThan( currentServerVersion ) )
            {
                iterator.remove();
            }
        }

        // Sort alphabetically.
        result.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        return result;
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
        return ENABLED.getValue();
    }

    /**
     * Sets if the check for updates service is enabled.
     *
     * @param enabled true if the check for updates service is enabled.
     */
    public void setServiceEnabled(final boolean enabled) {
        ENABLED.setValue(enabled);
    }

    private void enableService(final boolean enabled) {
        if (enabled && thread == null) {
            startService();
        } else if (!enabled && thread != null) {
            stopService();
        }
    }

    /**
     * Returns true if admins should be notified by IM when new updates are available.
     *
     * @return true if admins should be notified by IM when new updates are available.
     */
    public boolean isNotificationEnabled() {
        return NOTIFY_ADMINS.getValue();
    }

    /**
     * Sets if admins should be notified by IM when new updates are available.
     *
     * @param enabled true if admins should be notified by IM when new updates are available.
     */
    public void setNotificationEnabled(final boolean enabled) {
        NOTIFY_ADMINS.setValue(enabled);
    }

    /**
     * Returns true if a proxy is being used to connect to igniterealtime.org or false if
     * a direct connection should be attempted.
     *
     * @return true if a proxy is being used to connect to igniterealtime.org.
     */
    public boolean isUsingProxy() {
        return !StringUtils.isBlank(getProxyHost()) && getProxyPort() > 0;
    }

    /**
     * Returns the host of the proxy to use to connect to igniterealtime.org or {@code null}
     * if no proxy is used.
     *
     * @return the host of the proxy or null if no proxy is used.
     */
    public String getProxyHost() {
        return PROXY_HOST.getValue();
    }

    /**
     * Sets the host of the proxy to use to connect to igniterealtime.org or {@code null}
     * if no proxy is used.
     *
     * @param host the host of the proxy or null if no proxy is used.
     */
    public void setProxyHost(String host) {
        PROXY_HOST.setValue(host);
    }

    /**
     * Returns the port of the proxy to use to connect to igniterealtime.org or -1 if no
     * proxy is being used.
     *
     * @return the port of the proxy to use to connect to igniterealtime.org or -1 if no
     *         proxy is being used.
     */
    public int getProxyPort() {
        return PROXY_PORT.getValue();
    }

    /**
     * Sets the port of the proxy to use to connect to igniterealtime.org or -1 if no
     * proxy is being used.
     *
     * @param port the port of the proxy to use to connect to igniterealtime.org or -1 if no
     *        proxy is being used.
     */
    public void setProxyPort(int port) {
        PROXY_PORT.setValue(port);
    }

    /**
     * Returns the server update or {@code null} if the server is up to date.
     *
     * @return the server update or null if the server is up to date.
     */
    public Update getServerUpdate() {
        return serverUpdate;
    }

    /**
     * Returns the plugin update or {@code null} if the plugin is up to date.
     *
     * @param pluginName     the name of the plugin (as described in the meta-data).
     * @param currentVersion current version of the plugin that is installed.
     * @return the plugin update or null if the plugin is up to date.
     */
    public Update getPluginUpdate(String pluginName, Version currentVersion) {
        for (Update update : pluginUpdates) {
            // Check if this is the requested plugin
            if (update.getComponentName().equals(pluginName)) {
                // Check if the plugin version is right
                if (new Version(update.getLatestVersion()).isNewerThan( currentVersion ) ) {
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
        throws DocumentException, SAXException {
        // Reset last known update information
        serverUpdate = null;
        SAXReader xmlReader = setupSAXReader();
        Element xmlResponse = xmlReader.read(new StringReader(response)).getRootElement();
        // Parse response and keep info as Update objects
        Element openfire = xmlResponse.element("openfire");
        if (openfire != null) {
            // A new version of openfire was found
            Version latestVersion = new Version(openfire.attributeValue("latest"));
            if (latestVersion.isNewerThan(XMPPServer.getInstance().getServerInfo().getVersion())) {
                URL changelog = null;
                try
                {
                    changelog = new URL( openfire.attributeValue("changelog") );
                }
                catch ( MalformedURLException e )
                {
                    Log.warn( "Unable to parse URL from openfire changelog value '{}'.", openfire.attributeValue("changelog"), e );
                }

                URL url = null;
                try
                {
                    url = new URL( openfire.attributeValue("url") );
                }
                catch ( MalformedURLException e )
                {
                    Log.warn( "Unable to parse URL from openfire download url value '{}'.", openfire.attributeValue("url"), e );
                }
                // Keep information about the available server update
                serverUpdate = new Update("Openfire", latestVersion.getVersionString(), String.valueOf(changelog), String.valueOf(url));
            }
        }
        // Check if we need to send notifications to admins
        if (notificationsEnabled && isNotificationEnabled() && serverUpdate != null) {
            XMPPServer.getInstance().sendMessageToAdmins(getNotificationMessage() +
                " " + serverUpdate.getComponentName() +
                " " + serverUpdate.getLatestVersion());
        }
        // Save response in a file for later retrieval
        saveLatestServerInfo();
    }

    private void processAvailablePluginsResponse(String response, boolean notificationsEnabled)
        throws DocumentException, SAXException {
        // Reset last known list of available plugins
        availablePlugins = new HashMap<>();

        // Parse response and keep info as AvailablePlugin objects
        SAXReader xmlReader = setupSAXReader();
        Element xmlResponse = xmlReader.read(new StringReader(response)).getRootElement();
        Iterator plugins = xmlResponse.elementIterator("plugin");
        while (plugins.hasNext()) {
            Element plugin = (Element) plugins.next();
            AvailablePlugin available = AvailablePlugin.getInstance( plugin );
            // Add plugin to the list of available plugins at js.org
            availablePlugins.put(available.getName(), available);
        }

        // Figure out local plugins that need to be updated
        buildPluginsUpdateList();

        // Check if we need to send notifications to admins
        if (notificationsEnabled && isNotificationEnabled() && !pluginUpdates.isEmpty()) {
            for (Update update : pluginUpdates) {
                XMPPServer.getInstance().sendMessageToAdmins(getNotificationMessage() +
                    " " + update.getComponentName() +
                    " " + update.getLatestVersion());
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
        pluginUpdates = new ArrayList<>();
        XMPPServer server = XMPPServer.getInstance();
        Version currentServerVersion = XMPPServer.getInstance().getServerInfo().getVersion();
        // Compare local plugins versions with latest ones
        for ( final PluginMetadata plugin : server.getPluginManager().getMetadataExtractedPlugins().values() )
        {
            final AvailablePlugin latestPlugin = availablePlugins.get( plugin.getName() );

            if (latestPlugin == null)
            {
                continue;
            }

            final Version latestPluginVersion = latestPlugin.getVersion();

            if ( latestPluginVersion.isNewerThan( plugin.getVersion() ) )
            {
                // Check if the update can run in the current version of the server
                final Version pluginMinServerVersion = latestPlugin.getMinServerVersion();
                if ( pluginMinServerVersion != null && pluginMinServerVersion.isNewerThan( currentServerVersion ))
                {
                    continue;
                }

                final Version pluginPriorToServerVersion = latestPlugin.getPriorToServerVersion();
                if ( pluginPriorToServerVersion != null && !pluginPriorToServerVersion.isNewerThan( currentServerVersion ))
                {
                    continue;
                }

                final Update update = new Update( plugin.getName(), latestPlugin.getVersion().getVersionString(), latestPlugin.getChangelog().toExternalForm(), latestPlugin.getDownloadURL().toExternalForm() );
                pluginUpdates.add(update);
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
            component.addAttribute( "changelog", serverUpdate.getChangelog() );
            component.addAttribute( "url", serverUpdate.getURL() );
        }
        // Write data out to conf/server-update.xml file.
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
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
                XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
                xmlWriter.write(xmlResponse);
            }
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
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
            component.addAttribute("latest", plugin.getVersion() != null ? plugin.getVersion().getVersionString() : null);
            component.addAttribute("changelog", plugin.getChangelog() != null ? plugin.getChangelog().toExternalForm() : null );
            component.addAttribute("url", plugin.getDownloadURL() != null ? plugin.getDownloadURL().toExternalForm() : null );
            component.addAttribute("author", plugin.getAuthor());
            component.addAttribute("description", plugin.getDescription());
            component.addAttribute("icon", plugin.getIcon() != null ? plugin.getIcon().toExternalForm() : null );
            component.addAttribute("minServerVersion", plugin.getMinServerVersion() != null ? plugin.getMinServerVersion().getVersionString() : null);
            component.addAttribute("priorToServerVersion", plugin.getPriorToServerVersion() != null ? plugin.getPriorToServerVersion().getVersionString() : null);
            component.addAttribute("readme", plugin.getReadme() != null ? plugin.getReadme().toExternalForm() : null );
            component.addAttribute( "licenseType", plugin.getLicense() );
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
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
            OutputFormat prettyPrinter = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(writer, prettyPrinter);
            xmlWriter.write(xml);
        }
        catch (Exception e) {
            Log.error(e.getMessage(), e);
        }
        finally {
            if (writer != null) {
                try {
                    writer.close();
                }
                catch (IOException e1) {
                    Log.error(e1.getMessage(), e1);
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
        try (FileReader reader = new FileReader(file)){
            SAXReader xmlReader = setupSAXReader();
            xmlResponse = xmlReader.read(reader);
        } catch (Exception e) {
            Log.error("Error reading server-update.xml", e);
            return;
        }

        // Parse info and recreate update information (if still required)
        Element openfire = xmlResponse.getRootElement().element("openfire");
        if (openfire != null) {
            Version latestVersion = new Version(openfire.attributeValue("latest"));
            URL changelog = null;
            try
            {
                changelog = new URL( openfire.attributeValue("changelog") );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "Unable to parse URL from openfire changelog value '{}'.", openfire.attributeValue("changelog"), e );
            }

            URL url = null;
            try
            {
                url = new URL( openfire.attributeValue("url") );
            }
            catch ( MalformedURLException e )
            {
                Log.warn( "Unable to parse URL from openfire download url value '{}'.", openfire.attributeValue("url"), e );
            }
            // Check if current server version is correct
            Version currentServerVersion = XMPPServer.getInstance().getServerInfo().getVersion();
            if (latestVersion.isNewerThan(currentServerVersion)) {
                serverUpdate = new Update("Openfire", latestVersion.getVersionString(), String.valueOf(changelog), String.valueOf(url) );
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
        try (FileReader reader = new FileReader(file)) {
            SAXReader xmlReader = setupSAXReader();
            xmlResponse = xmlReader.read(reader);
        } catch (Exception e) {
            Log.error("Error reading available-plugins.xml", e);
            return;
        }

        // Parse info and recreate available plugins
        Iterator it = xmlResponse.getRootElement().elementIterator("plugin");
        while (it.hasNext()) {
            Element plugin = (Element) it.next();
            final AvailablePlugin instance = AvailablePlugin.getInstance( plugin );
            // Add plugin to the list of available plugins at js.org
            availablePlugins.put(instance.getName(), instance);
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

    private SAXReader setupSAXReader() throws SAXException {
        SAXReader xmlReader = new SAXReader();
        xmlReader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        xmlReader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        xmlReader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        xmlReader.setEncoding("UTF-8");
        return xmlReader;
    }
}
