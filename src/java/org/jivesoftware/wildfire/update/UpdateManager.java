package org.jivesoftware.wildfire.update;

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
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.LocaleUtils;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.XMLWriter;
import org.jivesoftware.wildfire.MessageRouter;
import org.jivesoftware.wildfire.XMPPServer;
import org.jivesoftware.wildfire.container.BasicModule;
import org.jivesoftware.wildfire.container.Plugin;
import org.jivesoftware.wildfire.container.PluginManager;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;

import java.io.*;
import java.util.*;

/**
 * Service that frequently checks for new server or plugins releases. By default the service
 * will check every 48 hours for updates. Use the system property <tt>update.frequency</tt>
 * to set new values.<p>
 *
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
    private static String updateServiceURL = "http://www.jivesoftware.org/wildfire/versions.jsp";

    /**
     * List of components that need to be updated.
     */
    private Collection<Update> toUpdate = new ArrayList<Update>();

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
        if (isServiceEnabled()) {
            startService();
        }
    }

    /**
     * Starts sevice that checks for new updates.
     */
    private void startService() {
        // Thread that performs the periodic checks for updates
        thread = new Thread("Update Manager") {
            public void run() {
                try {
                    // Sleep for 15 seconds before starting to work
                    Thread.sleep(15000);
                    // Load last saved response (if any)
                    Element xmlResponse = loadResponse();
                    if (xmlResponse != null) {
                        // Parse response and recreate Update objects
                        parseResponse(xmlResponse);
                        // Check if components were manually updated
                        checkForManualUpdates();
                    }
                    while (isServiceEnabled()) {
                        waitForNextChecking();
                        // Check if the service is still enabled
                        if (isServiceEnabled()) {
                            try {
                                checkForUpdates(true);
                            }
                            catch (Exception e) {
                                Log.error("Error checking for updates", e);
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

            private void waitForNextChecking() throws InterruptedException {
                long lastCheck = Long.parseLong(JiveGlobals.getProperty("update.lastCheck", "0"));
                if (lastCheck == 0) {
                    // This is the first time the server is used (since we added this feature)
                    Thread.sleep(45000);
                }
                else  {
                    long elapsed = System.currentTimeMillis() - lastCheck;
                    int frequency = getCheckFrequency() * 60 * 60 * 1000;
                    if (elapsed < frequency) {
                        // Sleep again before performing the next checking
                        Thread.sleep(frequency - elapsed);
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
        serverName = server.getServerInfo().getName();
    }

    /**
     * Queries the jivesoftware.org server for new server and plugin updates.
     *
     * @param notificationsEnabled true if admins will be notified when new updates are found.
     * @throws Exception if some error happens during the query.
     */
    public synchronized void checkForUpdates(boolean notificationsEnabled) throws Exception {
        // Keep track of the last time we checked for updates
        JiveGlobals.setProperty("update.lastCheck", String.valueOf(System.currentTimeMillis()));
        // Get the XML request to include in the HTTP request
        String requestXML = getUpdatesRequest();
        // Send the request to the server
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(updateServiceURL);
        NameValuePair[] data = {
                new NameValuePair("type", "update"),
                new NameValuePair("query", requestXML)
        };
        postMethod.setRequestBody(data);
        int responseCode = httpClient.executeMethod(postMethod);
        String responseBody = postMethod.getResponseBodyAsString();
        // Process answer from the server
        processUpdatesResponse(responseBody, notificationsEnabled);
    }

    /**
     * Returns the list of available (i.e. not installed) plugins as reported
     * by jivesoftware.org.
     *
     * @return the alphabetically sorted list of available plugins as reported
     *         by jivesoftware.org.
     */
    public List<AvailablePlugin> getAvailablePlugins() {
        try {
            // Get the XML request to include in the HTTP request
            String requestXML = getAvailableRequest();
            // Send the request to the server
            HttpClient httpClient = new HttpClient();
            PostMethod postMethod = new PostMethod(updateServiceURL);
            NameValuePair[] data = {
                    new NameValuePair("type", "available"),
                    new NameValuePair("query", requestXML)
            };
            postMethod.setRequestBody(data);
            int responseCode = httpClient.executeMethod(postMethod);
            String responseBody = postMethod.getResponseBodyAsString();
            // Process answer from the server
            return processAvailableResponse(responseBody);
        }
        catch (Exception e) {
            Log.error("Error while getting list of available plugins", e);
        }
        return Collections.emptyList();
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
     *
     * @return the frequency to check for updates in hours.
     */
    public int getCheckFrequency() {
        return JiveGlobals.getIntProperty("update.frequency", 48);
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
     * Returns the list of components that need to be updated. The server and server plugins
     * are considered components. The returned list belongs to the last time the check for
     * updates process was executred.
     *
     * @return the list of components that need to be updated.
     */
    public Collection<Update> getToUpdate() {
        return toUpdate;
    }

    /**
     * Returns the server update or <tt>null</tt> if the server is up to date.
     *
     * @return the server update or null if the server is up to date.
     */
    public Update getServerUpdate() {
        for (Update update : toUpdate) {
            if (update.isServer()) {
                return update;
            }
        }
        return null;
    }

    /**
     * Returns the plugin update or <tt>null</tt> if the plugin is up to date.
     *
     * @param pluginName the name of the plugin (as described in the meta-data).
     * @param currentVersion current version of the plugin that is installed.
     * @return the plugin update or null if the plugin is up to date.
     */
    public Update getPluginUpdate(String pluginName, String currentVersion) {
        for (Update update : toUpdate) {
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

    private String getUpdatesRequest() {
        XMPPServer server = XMPPServer.getInstance();
        Element xmlRequest = docFactory.createDocument().addElement("version");
        // Add current wildfire version
        Element wildfire = xmlRequest.addElement("wildfire");
        wildfire.addAttribute("current", server.getServerInfo().getVersion().getVersionString());
        // Add plugins information
        for (Plugin plugin : server.getPluginManager().getPlugins()) {
            Element pluginElement = xmlRequest.addElement("plugin");
            pluginElement.addAttribute("name", server.getPluginManager().getName(plugin));
            pluginElement.addAttribute("current", server.getPluginManager().getVersion(plugin));
        }
        return xmlRequest.asXML();
    }

    private String getAvailableRequest() {
        XMPPServer server = XMPPServer.getInstance();
        Element xmlRequest = docFactory.createDocument().addElement("available");
        // Add information of currently installed plugins
        for (Plugin plugin : server.getPluginManager().getPlugins()) {
            Element pluginElement = xmlRequest.addElement("plugin");
            pluginElement.addAttribute("name", server.getPluginManager().getName(plugin));
        }
        return xmlRequest.asXML();
    }

    /**
     * Discards updates of components that were manually updated while the server was stopped.
     */
    private void checkForManualUpdates() {
        Collection<Update> toDelete = new ArrayList<Update>();
        PluginManager pluginManager = XMPPServer.getInstance().getPluginManager();
        for (Update update : toUpdate) {
            String latestVersion = update.getLatestVersion();
            if (update.isServer()) {
                // Check if current server version is correct
                String serverVersion =
                        XMPPServer.getInstance().getServerInfo().getVersion().getVersionString();
                if (serverVersion.compareTo(latestVersion) >= 0) {
                    toDelete.add(update);
                }
            }
            else {
                // Check if current plugin version is correct
                String pluginName = update.getURL().substring(update.getURL().lastIndexOf("/")+1);
                Plugin plugin = pluginManager
                        .getPlugin(pluginName.substring(0, pluginName.length() - 4).toLowerCase());
                if (plugin != null &&
                        pluginManager.getVersion(plugin).compareTo(latestVersion) >= 0) {
                    toDelete.add(update);
                }
            }
        }
        if (!toDelete.isEmpty()) {
            // Remove old version of list of updates
            toUpdate.removeAll(toDelete);
            // Save response in a file for later retrieval
            saveResponse();
        }
    }

    private void processUpdatesResponse(String response, boolean notificationsEnabled)
            throws DocumentException {
        // Reset list of components that need to be updated
        toUpdate = new ArrayList<Update>();
        Element xmlResponse = new SAXReader().read(new StringReader(response)).getRootElement();
        // Parse response and keep info as Update objects
        parseResponse(xmlResponse);
        // Save response in a file for later retrieval
        saveResponse();
        // Check if we need to send notifications to admins
        if (notificationsEnabled && isNotificationEnabled() && !toUpdate.isEmpty()) {
            Collection<JID> admins = XMPPServer.getInstance().getAdmins();
            for (Update update : toUpdate) {
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
    }

    /**
     * Parses the XML response from the server and generates {@link Update} objects.
     *
     * @param xmlResponse the XML response answered by the server.
     */
    private void parseResponse(Element xmlResponse) {
        Element wildfire = xmlResponse.element("wildfire");
        if (wildfire != null) {
            // A new version of wildfire was found
            String latestVersion = wildfire.attributeValue("latest");
            String changelog = wildfire.attributeValue("changelog");
            String url = wildfire.attributeValue("url");
            Update update = new Update("Wildfire", latestVersion, changelog, url);
            // Add component to the list of components that need to be updated
            toUpdate.add(update);
        }
        Iterator plugins = xmlResponse.elementIterator("plugin");
        while (plugins.hasNext()) {
            // A new version of a plugin was found
            Element plugin = (Element) plugins.next();
            String pluginName = plugin.attributeValue("name");
            String latestVersion = plugin.attributeValue("latest");
            String changelog = plugin.attributeValue("changelog");
            String url = plugin.attributeValue("url");
            Update update = new Update(pluginName, latestVersion, changelog, url);
            // Add component to the list of components that need to be updated
            toUpdate.add(update);
        }
    }

    private List<AvailablePlugin> processAvailableResponse(String response) throws DocumentException {
        List<AvailablePlugin> answer = new ArrayList<AvailablePlugin>();
        Element xmlResponse = new SAXReader().read(new StringReader(response)).getRootElement();
        // Parse response and keep info as AvailablePlugin objects
        Iterator plugins = xmlResponse.elementIterator("plugin");
        while (plugins.hasNext()) {
            // A new version of a plugin was found
            Element plugin = (Element) plugins.next();
            String pluginName = plugin.attributeValue("name");
            String latestVersion = plugin.attributeValue("latest");
            String icon = plugin.attributeValue("icon");
            String readme = plugin.attributeValue("readme");
            String changelog = plugin.attributeValue("changelog");
            String url = plugin.attributeValue("url");
            boolean isCommercial = "true".equals(plugin.attributeValue("commercial"));
            String description = plugin.attributeValue("description");
            String author = plugin.attributeValue("author");
            String minServerVersion = plugin.attributeValue("minServerVersion");
            AvailablePlugin available = new AvailablePlugin(pluginName, description, latestVersion,
                    author, icon, changelog, readme, isCommercial, minServerVersion, url);
            // Add component to the list of components that need to be updated
            answer.add(available);
        }
        // Sort answer alphabetically
        Collections.sort(answer, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((AvailablePlugin)o1).getName().compareTo(((AvailablePlugin)o2).getName());
            }
        });
        return answer;
    }

    /**
     * Saves the XML response from the server to a file. The file is named conf/updates.xml.
     * Only not installed plugins will be saved. Each time a plugin is installed the
     * file will be saved again.
     */
    private void saveResponse() {
        // Recreate XML response based on non-installed updates
        Element xmlResponse = docFactory.createDocument().addElement("version");
        for (Update update : toUpdate) {
            if (update.isDownloaded()) {
                continue;
            }
            Element component;
            if (update.isServer()) {
                component = xmlResponse.addElement("wildfire");
            }
            else {
                component = xmlResponse.addElement("plugin");
                component.addAttribute("name", update.getComponentName());
            }
            component.addAttribute("latest", update.getLatestVersion());
            component.addAttribute("changelog", update.getChangelog());
            component.addAttribute("url", update.getURL());
        }
        // Write data out to conf/updates.xml file.
        Writer writer = null;
        try {
            // Create the conf folder if required
            File file = new File(JiveGlobals.getHomeDirectory(), "conf");
            if (!file.exists()) {
                file.mkdir();
            }
            file = new File(JiveGlobals.getHomeDirectory() + File.separator + "conf",
                    "updates.xml");
            // Delete the old version.xml file if it exists
            if (file.exists()) {
                file.delete();
            }
            // Create new version.xml with returned data
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
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
     * Returns the last recieved XML response from the server or <tt>null</tt> if non was
     * ever saved before.
     *
     * @return the last recieved XML response from the server or null if non was found.
     */
    private Element loadResponse() {
        Document xmlResponse;
        File file =
                new File(JiveGlobals.getHomeDirectory() + File.separator + "conf", "updates.xml");
        if (!file.exists()) {
            return null;
        }
        // Check read privs.
        if (!file.canRead()) {
            Log.warn("Cannot retrieve available updates. File must be readable: " + file.getName());
            return null;
        }
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            SAXReader xmlReader = new SAXReader();
            xmlResponse = xmlReader.read(reader);
        }
        catch (Exception e) {
            Log.error("Error reading updates.xml", e);
            return null;
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
        return xmlResponse.getRootElement();
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
                    for (Update update : toUpdate) {
                        if (update.getURL().equals(url)) {
                            update.setDownloaded(true);
                        }
                    }
                    // Save response in a file for later retrieval
                    saveResponse();
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
}
