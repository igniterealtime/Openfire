/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.openfire.clearspace;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.dom4j.*;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.IQResultListener;
import org.jivesoftware.openfire.IQRouter;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.POST;
import org.jivesoftware.openfire.component.*;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.muc.spi.MultiUserChatServiceImpl;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.*;
import org.jivesoftware.util.cache.DefaultCache;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.IQ;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


/**
 * Centralized administration of Clearspace connections. The {@link #getInstance()} method
 * should be used to get an instance. The following properties configure this manager:
 * <p/>
 * <ul>
 * <li>clearspace.uri</li>
 * <li>clearspace.sharedSecret</li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class ClearspaceManager extends BasicModule implements ExternalComponentManagerListener, ComponentEventListener {
    /**
     * This is the username of the user that Openfires uses to connect
     * to Clearspace. It is fixed a well known by Openfire and Clearspace.
     */
    private static final String OPENFIRE_USERNAME = "openfire_SHRJKZCNU53";
    private static final String WEBSERVICES_PATH = "rpc/rest/";
    protected static final String IM_URL_PREFIX = "imService/";
    public  static final String MUC_SUBDOMAIN = "clearspace-conference";
    private static final String MUC_DESCRIPTION = "Clearspace Conference Services";
    public  static final String CLEARSPACE_COMPONENT = "clearspace";

    private static ThreadLocal<XMPPPacketReader> localParser = null;
    private static XmlPullParserFactory factory = null;
    /**
     * This map is used to transale exceptions from CS to OF
     */
    private static final Map<String, String> exceptionMap;

    private static ClearspaceManager instance;

    static {
        try {
            factory = XmlPullParserFactory.newInstance(MXParser.class.getName(), null);
            factory.setNamespaceAware(true);
        }
        catch (XmlPullParserException e) {
            Log.error("Error creating a parser factory", e);
        }
        // Create xmpp parser to keep in each thread
        localParser = new ThreadLocal<XMPPPacketReader>() {
            protected XMPPPacketReader initialValue() {
                XMPPPacketReader parser = new XMPPPacketReader();
                factory.setNamespaceAware(true);
                parser.setXPPFactory(factory);
                return parser;
            }
        };

        // Add a new exception map from CS to OF and it will be automatically translated.
        exceptionMap = new HashMap<String, String>();
        exceptionMap.put("com.jivesoftware.base.UserNotFoundException", "org.jivesoftware.openfire.user.UserNotFoundException");
        exceptionMap.put("com.jivesoftware.base.UserAlreadyExistsException", "org.jivesoftware.openfire.user.UserAlreadyExistsException");
        exceptionMap.put("com.jivesoftware.base.GroupNotFoundException", "org.jivesoftware.openfire.group.GroupNotFoundException");
        exceptionMap.put("com.jivesoftware.base.GroupAlreadyExistsException", "org.jivesoftware.openfire.group.GroupAlreadyExistsException");
        exceptionMap.put("org.acegisecurity.BadCredentialsException", "org.jivesoftware.openfire.auth.UnauthorizedException");
    }

    private ConfigClearspaceTask configClearspaceTask;
    private Map<String, String> properties;
    private String uri;
    private String host;
    private int port;
    private String sharedSecret;
    private Map<String, Long> userIDCache;
    private Map<String, Long> groupIDCache;
    /**
     * Records transcripts for group chat rooms in Clearspace.
     */
    private ClearspaceMUCTranscriptManager mucTranscriptManager = new ClearspaceMUCTranscriptManager(TaskEngine.getInstance());
    /**
     * Keep the domains of Clearspace components
     */
    private final List<String> clearspaces = new ArrayList<String>();

    /**
     * Provides singleton access to an instance of the ClearspaceManager class.
     *
     * @return an ClearspaceManager instance.
     */
    public static ClearspaceManager getInstance() {
        return instance;
    }

    /**
     * Constructs a new ClearspaceManager instance. Typically, {@link #getInstance()} should be
     * called instead of this method. ClearspaceManager instances should only be created directly
     * for testing purposes.
     *
     * @param properties the Map that contains properties used by the Clearspace manager, such as
     *                   Clearspace host and shared secret.
     */
    public ClearspaceManager(Map<String, String> properties) {
        super("Clearspace integration module for testing only");
        this.properties = properties;

        init();
    }

    /**
     * Constructs a new ClearspaceManager instance. Typically, {@link #getInstance()} should be
     * called instead of this method. ClearspaceManager instances should only be created directly
     * for testing purposes.
     */
    public ClearspaceManager() {
        super("Clearspace integration module");
        // Create a special Map implementation to wrap XMLProperties. We only implement
        // the get, put, and remove operations, since those are the only ones used. Using a Map
        // makes it easier to perform LdapManager testing.
        this.properties = new Map<String, String>() {

            public String get(Object key) {
                return JiveGlobals.getXMLProperty((String) key);
            }

            public String put(String key, String value) {
                JiveGlobals.setXMLProperty(key, value);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }

            public String remove(Object key) {
                JiveGlobals.deleteXMLProperty((String) key);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }


            public int size() {
                return 0;
            }

            public boolean isEmpty() {
                return false;
            }

            public boolean containsKey(Object key) {
                return false;
            }

            public boolean containsValue(Object value) {
                return false;
            }

            public void putAll(Map<? extends String, ? extends String> t) {
            }

            public void clear() {
            }

            public Set<String> keySet() {
                return null;
            }

            public Collection<String> values() {
                return null;
            }

            public Set<Entry<String, String>> entrySet() {
                return null;
            }
        };

        init();
        instance = this;
    }

    private void init() {
        this.uri = properties.get("clearspace.uri");
        if (uri != null) {
            if (!this.uri.endsWith("/")) {
                this.uri = this.uri + "/";
            }
            // Updates the host/port attributes based on the uri
            updateHostPort();
        }
        sharedSecret = properties.get("clearspace.sharedSecret");

        // Creates the cache maps
        userIDCache = new DefaultCache<String, Long>("clearspace.userid", 1000, JiveConstants.DAY);
        groupIDCache = new DefaultCache<String, Long>("clearspace.groupid", 1000, JiveConstants.DAY);


        if (Log.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Created new ClearspaceManager() instance, fields:\n");
            buf.append("\t URI: ").append(uri).append("\n");
            buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

            Log.debug("ClearspaceManager: " + buf.toString());
        }
    }

    /**
     * Updates the host port attributes based on the URI.
     */
    private void updateHostPort() {
        if (uri != null && !"".equals(uri.trim())) {
            try {
                URL url = new URL(uri);
                host = url.getHost();
                port = url.getPort();
            } catch (MalformedURLException e) {
                // this won't happen
            }
        }
    }

    /**
     * Check a username/password pair for valid authentication.
     *
     * @param username Username to authenticate against.
     * @param password Password to use for authentication.
     * @return True or false of the authentication succeeded.
     */
    public Boolean checkAuthentication(String username, String password) {
        try {
            String path = ClearspaceAuthProvider.URL_PREFIX + "authenticate/" + username + "/" + password;
            executeRequest(GET, path);
            return true;
        } catch (Exception e) {
            // Nothing to do.
        }

        return false;
    }

    /**
     * Tests the web services connection with Clearspace given the manager's current configuration.
     *
     * @return True if connection test was successful.
     */
    public Boolean testConnection() {
        // Test invoking a simple method
        try {
            // If there is a problem with the URL or the user/password this service throws an exception
            String path = IM_URL_PREFIX + "testCredentials";
            executeRequest(GET, path);

            return true;
        } catch (Exception e) {
            // It is not ok, return false.
        }

        return false;
    }

    /**
     * Returns the Clearspace service URI; e.g. <tt>https://localhost:80/clearspace</tt>.
     * This value is stored as the Jive Property <tt>clearspace.uri</tt>.
     *
     * @return the Clearspace service URI.
     */
    public String getConnectionURI() {
        return uri;
    }

    /**
     * Sets the URI of the Clearspace service; e.g., <tt>https://localhost:80/clearspace</tt>.
     * This value is stored as the Jive Property <tt>clearspace.uri</tt>.
     *
     * @param uri the Clearspace service URI.
     */
    public void setConnectionURI(String uri) {
        if (!uri.endsWith("/")) {
            uri = uri + "/";
        }
        this.uri = uri;
        properties.put("clearspace.uri", uri);

        //Updates the host/port attributes
        updateHostPort();

        if (isEnabled()) {
            startClearspaceConfig();
        }
    }

    /**
     * Returns the password, configured in Clearspace, that Openfire will use to authenticate
     * with Clearspace to perform it's integration.
     *
     * @return the password Openfire will use to authenticate with Clearspace.
     */
    public String getSharedSecret() {
        return sharedSecret;
    }

    /**
     * Sets the shared secret for the Clearspace service we're connecting to.
     *
     * @param sharedSecret the password configured in Clearspace to authenticate Openfire.
     */
    public void setSharedSecret(String sharedSecret) {
        // Set new password for external component
        ExternalComponentConfiguration configuration = new ExternalComponentConfiguration("clearspace",
                ExternalComponentConfiguration.Permission.allowed, sharedSecret);
        try {
            ExternalComponentManager.allowAccess(configuration);
        }
        catch (ModificationNotAllowedException e) {
            Log.warn("Failed to configure password for Clearspace", e);
        }

        // After updating the component information we can update the field, but not before.
        // If it is done before, OF won't be able to execute the updateSharedsecret webservice
        // since it would try with the new password.
        this.sharedSecret = sharedSecret;
        properties.put("clearspace.sharedSecret", sharedSecret);
    }

    /**
     * Returns true if Clearspace is being used as the backend of Openfire. When
     * integrated with Clearspace then users and groups will be pulled out from
     * Clearspace. User authentication will also rely on Clearspace.
     *
     * @return true if Clearspace is being used as the backend of Openfire.
     */
    public boolean isEnabled() {
        return AuthFactory.getAuthProvider() instanceof ClearspaceAuthProvider;
    }

    public void start() throws IllegalStateException {
        super.start();
        if (isEnabled()) {
            // Before starting up service make sure there is a default secret
            if (ExternalComponentManager.getDefaultSecret() == null ||
                    "".equals(ExternalComponentManager.getDefaultSecret())) {
                try {
                    ExternalComponentManager.setDefaultSecret(StringUtils.randomString(10));
                }
                catch (ModificationNotAllowedException e) {
                    Log.warn("Failed to set a default secret to external component service", e);
                }
            }
            // Make sure that external component service is enabled
            if (!ExternalComponentManager.isServiceEnabled()) {
                try {
                    ExternalComponentManager.setServiceEnabled(true);
                }
                catch (ModificationNotAllowedException e) {
                    Log.warn("Failed to start external component service", e);
                }
            }
            // Listen for changes to external component settings
            ExternalComponentManager.addListener(this);
            // List for registration of new components
            InternalComponentManager.getInstance().addListener(this);
            // Set up custom clearspace MUC service
            // Create service if it doesn't exist, load if it does.
            MultiUserChatServiceImpl muc = (MultiUserChatServiceImpl)XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService(MUC_SUBDOMAIN);
            if (muc == null) {
                try {
                    muc = XMPPServer.getInstance().getMultiUserChatManager().createMultiUserChatService(MUC_SUBDOMAIN, MUC_DESCRIPTION, true);
                }
                catch (AlreadyExistsException e) {
                    Log.error("ClearspaceManager: Found no "+MUC_SUBDOMAIN+" service, but got already exists when creation attempted?  Service probably not started!");
                }
            }
            if (muc != null) {
                // Set up special delegate for Clearspace MUC service
                muc.setMUCDelegate(new ClearspaceMUCEventDelegate());
                // Set up additional features for Clearspace MUC service
                muc.addExtraFeature("clearspace:service");
                // Set up additional identity of conference service to Clearspace MUC service
                muc.addExtraIdentity("conference", "Clearspace Chat Service", "text");
            }

            // Starts the clearspace configuration task
            startClearspaceConfig();

            // Starts the Clearspace MUC transcript manager
            // TODO: Uncomment when the transcript manager is implemented completely
            //mucTranscriptManager.start();
        }
    }

    public void stop() {
        super.stop();

        // Stops the Clearspace MUC transcript manager
        // TODO: Uncomment when the transcript manager is implemented completely
        //mucTranscriptManager.stop();

        // Unregister/shut down custom MUC service
        XMPPServer.getInstance().getMultiUserChatManager().unregisterMultiUserChatService(MUC_SUBDOMAIN);
    }

    /**
     *
     */
    private synchronized void startClearspaceConfig() {
        // If the task is running, stop it
        if (configClearspaceTask != null) {
            configClearspaceTask.cancel();
            Log.debug("Stopping previous configuration Clearspace task.");
        }

        // Create and schedule a confi task every minute
        configClearspaceTask = new ConfigClearspaceTask();
        // Wait some time to start the task until Openfire has binding address
        TaskEngine.getInstance().schedule(configClearspaceTask, JiveConstants.SECOND * 10, JiveConstants.MINUTE);
        Log.debug("Starting configuration Clearspace task in 10 seconds.");
    }

    private synchronized void configClearspace() throws UnauthorizedException {

        Log.debug("Starting Clearspace configuration.");

        List<String> bindInterfaces = getServerInterfaces();
        if (bindInterfaces.size() == 0) {
            // We aren't up and running enough to tell Clearspace what interfaces to bind to.
            Log.debug("No bind interfaces found to config Clearspace");
            throw new IllegalStateException("There are no binding interfaces.");
        }

        try {

            XMPPServerInfo serverInfo = XMPPServer.getInstance().getServerInfo();

            String path = IM_URL_PREFIX + "configureComponent/";

            // Creates the XML with the data
            Document groupDoc = DocumentHelper.createDocument();
            Element rootE = groupDoc.addElement("configureComponent");
            Element domainE = rootE.addElement("domain");
            domainE.setText(serverInfo.getXMPPDomain());
            for (String bindInterface : bindInterfaces) {
                Element hostsE = rootE.addElement("hosts");
                hostsE.setText(bindInterface);
            }
            Element portE = rootE.addElement("port");
            portE.setText(String.valueOf(ExternalComponentManager.getServicePort()));

            Log.debug("Trying to configure Clearspace with: Domain: " + serverInfo.getXMPPDomain() + ", hosts: " +
                    bindInterfaces.toString() + ", port: " + port);

            executeRequest(POST, path, rootE.asXML());

            //Done, Clearspace was configured correctly, clear the task
            Log.debug("Clearspace was configured, stopping the task.");
            TaskEngine.getInstance().cancelScheduledTask(configClearspaceTask);
            configClearspaceTask = null;

        } catch (UnauthorizedException ue) {
            throw ue;
        } catch (Exception e) {
            // It is not supported exception, wrap it into an UnsupportedOperationException
            throw new UnsupportedOperationException("Unexpected error", e);
        }
    }

    private List<String> getServerInterfaces() {

        List<String> bindInterfaces = new ArrayList<String>();

        String interfaceName = JiveGlobals.getXMLProperty("network.interface");
        String bindInterface = null;
        if (interfaceName != null) {
            if (interfaceName.trim().length() > 0) {
                bindInterface = interfaceName;
            }
        }

        int adminPort = JiveGlobals.getXMLProperty("adminConsole.port", 9090);
        int adminSecurePort = JiveGlobals.getXMLProperty("adminConsole.securePort", 9091);

        if (bindInterface == null) {
            try {
                Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
                for (NetworkInterface netInterface : Collections.list(nets)) {
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    for (InetAddress address : Collections.list(addresses)) {
                        if ("127.0.0.1".equals(address.getHostAddress())) {
                            continue;
                        }
                        if (address.getHostAddress().startsWith("0.")) {
                            continue;
                        }
                        Socket socket = new Socket();
                        InetSocketAddress remoteAddress = new InetSocketAddress(address, adminPort > 0 ? adminPort : adminSecurePort);
                        try {
                            socket.connect(remoteAddress);
                            bindInterfaces.add(address.getHostAddress());
                            break;
                        } catch (IOException e) {
                            // Ignore this address. Let's hope there is more addresses to validate
                        }
                    }
                }
            } catch (SocketException e) {
                // We failed to discover a valid IP address where the admin console is running
                return null;
            }
        } else {
            bindInterfaces.add(bindInterface);
        }

        return bindInterfaces;
    }

    private void updateClearspaceSharedSecret(String newSecret) {

        try {
            String path = IM_URL_PREFIX + "updateSharedSecret/";

            // Creates the XML with the data
            Document groupDoc = DocumentHelper.createDocument();
            Element rootE = groupDoc.addElement("updateSharedSecret");
            rootE.addElement("newSecret").setText(newSecret);

            executeRequest(POST, path, groupDoc.asXML());
        } catch (UnauthorizedException ue) {
            Log.error("Error updating the password of Clearspace", ue);
        } catch (Exception e) {
            Log.error("Error updating the password of Clearspace", e);
        }

    }

    public void serviceEnabled(boolean enabled) throws ModificationNotAllowedException {
        // Do not let admins shutdown the external component service
        if (!enabled) {
            throw new ModificationNotAllowedException("Service cannot be disabled when integrated with Clearspace.");
        }
    }

    public void portChanged(int newPort) throws ModificationNotAllowedException {
        startClearspaceConfig();
    }

    public void defaultSecretChanged(String newSecret) throws ModificationNotAllowedException {
        // Do nothing
    }

    public void permissionPolicyChanged(ExternalComponentManager.PermissionPolicy newPolicy)
            throws ModificationNotAllowedException {
        // Do nothing
    }

    public void componentAllowed(String subdomain, ExternalComponentConfiguration configuration)
            throws ModificationNotAllowedException {
        if (subdomain.startsWith("clearspace")) {
            updateClearspaceSharedSecret(configuration.getSecret());
        }
    }

    public void componentBlocked(String subdomain) throws ModificationNotAllowedException {
        if (subdomain.startsWith("clearspace")) {
            throw new ModificationNotAllowedException("Communication with Clearspace cannot be blocked.");
        }
    }

    public void componentSecretUpdated(String subdomain, String newSecret) throws ModificationNotAllowedException {
        if (subdomain.startsWith("clearspace")) {
            updateClearspaceSharedSecret(newSecret);
        }
    }

    public void componentConfigurationDeleted(String subdomain) throws ModificationNotAllowedException {
        // Do not let admins delete configuration of Clearspace component
        if (subdomain.startsWith("clearspace")) {
            throw new ModificationNotAllowedException("Use 'Profile Settings' to change password.");
        }
    }

    /**
     * Makes a rest request of either type GET or DELETE at the specified urlSuffix.
     * <p/>
     * urlSuffix should be of the form /userService/users
     *
     * @param type      Must be GET or DELETE
     * @param urlSuffix The url suffix of the rest request
     * @return The response as a xml doc.
     * @throws ConnectException Thrown if there are issues perfoming the request.
     * @throws Exception Thrown if the response from Clearspace contains an exception.
     */
    public Element executeRequest(HttpType type, String urlSuffix) throws ConnectException, Exception {
        assert (type == HttpType.GET || type == HttpType.DELETE);
        return executeRequest(type, urlSuffix, null);
    }

    public Element executeRequest(HttpType type, String urlSuffix, String xmlParams)
            throws ConnectException, Exception {
        if (Log.isDebugEnabled()) {
            Log.debug("Outgoing REST call [" + type + "] to " + urlSuffix + ": " + xmlParams);
        }

        String wsUrl = getConnectionURI() + WEBSERVICES_PATH + urlSuffix;

        String secret = getSharedSecret();

        HttpClient client = new HttpClient();
        HttpMethod method;

        // Configures the authentication
        client.getParams().setAuthenticationPreemptive(true);
        Credentials credentials = new UsernamePasswordCredentials(OPENFIRE_USERNAME, secret);
        AuthScope scope = new AuthScope(host, port, AuthScope.ANY_REALM);
        client.getState().setCredentials(scope, credentials);

        // Creates the method
        switch (type) {
            case GET:
                method = new GetMethod(wsUrl);
                break;
            case POST:
                PostMethod pm = new PostMethod(wsUrl);
                StringRequestEntity requestEntity = new StringRequestEntity(xmlParams);
                pm.setRequestEntity(requestEntity);
                method = pm;
                break;
            case PUT:
                PutMethod pm1 = new PutMethod(wsUrl);
                StringRequestEntity requestEntity1 = new StringRequestEntity(xmlParams);
                pm1.setRequestEntity(requestEntity1);
                method = pm1;
                break;
            case DELETE:
                method = new DeleteMethod(wsUrl);
                break;
            default:
                throw new IllegalArgumentException();
        }

        method.setRequestHeader("Accept", "text/xml");
        method.setDoAuthentication(true);

        try {
            // Executes the request
            client.executeMethod(method);

            // Parses the result
            String body = method.getResponseBodyAsString();
            if (Log.isDebugEnabled()) {
                Log.debug("Outgoing REST call results: " + body);
            }

            // Checks the http status
            if (method.getStatusCode() != 200) {
                throw new ConnectException("Error connecting to Clearspace, http status code: " + method.getStatusCode());
            }

            Element response = localParser.get().parseDocument(body).getRootElement();

            // Check for exceptions
            checkFault(response);

            // Since there is no exception, returns the response
            return response;
        } catch (DocumentException e) {
            throw new ConnectException("Error parsing the response of Clearspace.", e);
        } catch (HttpException e) {
            throw new ConnectException("Error peforming http request to Clearspace", e);
        } catch (IOException e) {
            throw new ConnectException("Error peforming http request to Clearspace.", e);
        } finally {
            method.releaseConnection();
        }
    }

    private void checkFault(Element response) throws Exception {
        Node node = response.selectSingleNode("ns1:faultstring");
        if (node != null) {
            String exceptionText = node.getText();

            // Text accepted samples:
            // 'java.lang.Exception: Exception message'
            // 'java.lang.Exception'

            // Get the exception class and message if any
            int index = exceptionText.indexOf(":");
            String className;
            String message;
            // If there is no message, save the class only
            if (index == -1) {
                className = exceptionText;
                message = null;
            } else {
                // Else save both
                className = exceptionText.substring(0, index);
                message = exceptionText.substring(index + 2);
            }

            // Map the exception to a Openfire one, if possible
            if (exceptionMap.containsKey(className)) {
                className = exceptionMap.get(className);
            }

            //Tries to create an instance with the message
            Exception exception;
            try {
                Class exceptionClass = Class.forName(className);
                if (message == null) {
                    exception = (Exception) exceptionClass.newInstance();
                } else {
                    Constructor constructor = exceptionClass.getConstructor(String.class);
                    exception = (Exception) constructor.newInstance(message);
                }
            } catch (Exception e) {
                // failed to create an specific exception, creating a standard one.
                exception = new Exception(exceptionText);
            }

            throw exception;
        }

    }

    /**
     * Returns the Clearspace user id the user by username.
     *
     * @param username Username to retrieve ID of.
     * @return The ID number of the user in Clearspace.
     * @throws org.jivesoftware.openfire.user.UserNotFoundException
     *          If the user was not found.
     */
    protected long getUserID(String username) throws UserNotFoundException {
        // Gets the part before of @ of the username param
        if (username.contains("@")) {
            // User's id are only for local users
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0, username.lastIndexOf("@"));
        }

        // Checks if it is in the cache
        if (userIDCache.containsKey(username)) {
            return userIDCache.get(username);
        }

        // Gets the user's ID from Clearspace
        try {
            String path = ClearspaceUserProvider.USER_URL_PREFIX + "users/" + username;
            Element element = executeRequest(org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET, path);

            Long id = Long.valueOf(WSUtils.getElementText(element.selectSingleNode("return"), "ID"));

            userIDCache.put(username, id);

            return id;
        } catch (UserNotFoundException unfe) {
            // It is a supported exception, throw it again
            throw unfe;
        } catch (Exception e) {
            // It is not a supported exception, wrap it into a UserNotFoundException
            throw new UserNotFoundException("Unexpected error", e);
        }
    }

    /**
     * Returns the Clearspace user id the user by JID.
     *
     * @param user JID of user to retrieve ID of.
     * @return The ID number of the user in Clearspace.
     * @throws org.jivesoftware.openfire.user.UserNotFoundException
     *          If the user was not found.
     */
    protected long getUserID(JID user) throws UserNotFoundException {
        // User's id are only for local users
        XMPPServer server = XMPPServer.getInstance();
        if (!server.isLocal(user)) {
            throw new UserNotFoundException("Cannot load user of remote server: " + user.toString());
        }
        String username = JID.unescapeNode(user.getNode());
        return getUserID(username);
    }

    /**
     * Returns the Clearspace group id of the group.
     *
     * @param groupname Name of the group to retrieve ID of.
     * @return The ID number of the group in Clearspace.
     * @throws org.jivesoftware.openfire.group.GroupNotFoundException
     *          If the group was not found.
     */
    protected long getGroupID(String groupname) throws GroupNotFoundException {
        if (groupIDCache.containsKey(groupname)) {
            return groupIDCache.get(groupname);
        }
        try {
            String path = ClearspaceGroupProvider.URL_PREFIX + "groups/" + groupname;
            Element element = executeRequest(org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET, path);

            Long id = Long.valueOf(WSUtils.getElementText(element.selectSingleNode("return"), "ID"));
            // Saves it into the cache
            groupIDCache.put(groupname, id);

            return id;
        } catch (GroupNotFoundException gnfe) {
            // It is a supported exception, throw it again
            throw gnfe;
        } catch (Exception e) {
            // It is not a supported exception, wrap it into a GroupNotFoundException
            throw new GroupNotFoundException("Unexpected error", e);
        }
    }

    /**
     * Returns true if a given JID belongs to a known Clearspace component domain.
     * @param address Address to check.
     * @return True if the specified address is a Clearspace component.
     */
    public boolean isFromClearspace(JID address) {
        return clearspaces.contains(address.getDomain());
    }

    /**
     * Sends an IQ packet to the Clearspace external component and returns the IQ packet
     * returned by CS or <tt>null</tt> if no answer was received before the specified
     * timeout.
     *
     * @param packet IQ packet to send.
     * @param timeout milliseconds to wait before timing out.
     * @return IQ packet returned by Clearspace responsing the packet we sent.
     */
    public IQ query(final IQ packet, int timeout) {
        // Complain if FROM is empty
        if (packet.getFrom() == null) {
            throw new IllegalStateException("IQ packets with no FROM cannot be sent to Clearspace");
        }
        // If CS is not connected then return null
        if (clearspaces.isEmpty()) {
            return null;
        }
        // Set the target address to the IQ packet. Roate list so we distribute load
        String component;
        synchronized (clearspaces) {
            component = clearspaces.get(0);
            Collections.rotate(clearspaces, 1);
        }
        packet.setTo(component);
        final LinkedBlockingQueue<IQ> answer = new LinkedBlockingQueue<IQ>(8);
        final IQRouter router = XMPPServer.getInstance().getIQRouter();
        router.addIQResultListener(packet.getID(), new IQResultListener() {
            public void receivedAnswer(IQ packet) {
                answer.offer(packet);
            }

            public void answerTimeout(String packetId) {
                Log.warn("No answer from Clearspace was received for IQ stanza: " + packet);
            }
        });
        XMPPServer.getInstance().getIQRouter().route(packet);
        IQ reply = null;
        try {
            reply = answer.poll(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Ignore
        }
        return reply;
    }

    public void componentRegistered(JID componentJID) {
        // Do nothing
    }

    public void componentUnregistered(JID componentJID) {
        // Remove stored information about this component
        clearspaces.remove(componentJID.getDomain());
    }

    public void componentInfoReceived(IQ iq) {
        // Check if it's a Clearspace component
        boolean isClearspace = false;
        Element childElement = iq.getChildElement();
        for (Iterator it = childElement.elementIterator("identity"); it.hasNext();) {
            Element identity = (Element)it.next();
            if ("component".equals(identity.attributeValue("category")) &&
                    "clearspace".equals(identity.attributeValue("type"))) {
                isClearspace = true;
            }
        }
        // If component is Clearspace then keep track of the component
        if (isClearspace) {
            clearspaces.add(iq.getFrom().getDomain());
        }
    }

    private class ConfigClearspaceTask extends TimerTask {

        public void run() {
            try {
                Log.debug("Trying to configure Clearspace.");
                configClearspace();
            } catch (UnauthorizedException e) {
                Log.warn("Unauthorization problem trying to configure Clearspace, trying again in 1 minute", e);
                // TODO: Mark that there is an authorization problem
            } catch (Exception e) {
                Log.warn("Unknown problem trying to configure Clearspace, trying again in 1 minute", e);
            }
        }
    }

    /**
     * Different kind of HTTP request types
     */
    public enum HttpType {

        /**
         * Represents an HTTP Get request. And it's equivalent to a SQL SELECTE.
         */
        GET,

        /**
         * Represents an HTTP Post request. And it's equivalent to a SQL UPDATE.
         */
        POST,

        /**
         * Represents an HTTP Delete request. And it's equivalent to a SQL DELETE.
         */
        DELETE,

        /**
         * Represents an HTTP Put requests.And it's equivalent to a SQL CREATE.
         */
        PUT
    }
}
