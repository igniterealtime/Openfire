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

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.DocumentHelper;
import org.dom4j.Document;
import org.dom4j.io.XMPPPacketReader;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.XMPPServerInfo;
import org.jivesoftware.openfire.auth.AuthFactory;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET;
import static org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.POST;
import static org.jivesoftware.openfire.clearspace.WSUtils.getReturn;
import org.jivesoftware.openfire.component.ExternalComponentConfiguration;
import org.jivesoftware.openfire.component.ExternalComponentManager;
import org.jivesoftware.openfire.component.ExternalComponentManagerListener;
import org.jivesoftware.openfire.container.BasicModule;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.net.MXParser;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.*;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmpp.packet.JID;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.*;


/**
 * Centralized administration of Clearspace connections. The {@link #getInstance()} method
 * should be used to get an instace. The following properties configure this manager:
 *
 * <ul>
 *      <li>clearspace.uri</li>
 *      <li>clearspace.sharedSecret</li>
 * </ul>
 *
 * @author Daniel Henninger
 */
public class ClearspaceManager extends BasicModule implements ExternalComponentManagerListener {
    private ConfigClearspaceTask configClearspaceTask;

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

    /**
     * This is the username of the user that Openfires uses to connect
     * to Clearspace. It is fixed a well known by Openfire and Clearspace.
     */
    private static final String OPENFIRE_USERNAME = "openfire_SHRJKZCNU53";

    private static final String WEBSERVICES_PATH = "rpc/rest/";

    protected static final String IM_URL_PREFIX = "imService/";

    private static ThreadLocal<XMPPPacketReader> localParser = null;
    private static XmlPullParserFactory factory = null;

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
    }

    private static final Map<String, String> exceptionMap;

    static {
        exceptionMap = new HashMap<String, String>();
        exceptionMap.put("com.jivesoftware.base.UserNotFoundException", "org.jivesoftware.openfire.user.UserNotFoundException");
        exceptionMap.put("com.jivesoftware.base.UserAlreadyExistsException", "org.jivesoftware.openfire.user.UserAlreadyExistsException");
        exceptionMap.put("com.jivesoftware.base.GroupNotFoundException", "org.jivesoftware.openfire.group.GroupNotFoundException");
        exceptionMap.put("com.jivesoftware.base.GroupAlreadyExistsException", "org.jivesoftware.openfire.group.GroupAlreadyExistsException");
        exceptionMap.put("org.acegisecurity.BadCredentialsException", "org.jivesoftware.openfire.auth.UnauthorizedException");
    }

    private static ClearspaceManager instance = new ClearspaceManager();

    private Map<String, String> properties;
    private String uri;
    private String host;
    private int port;
    private String sharedSecret;

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
     *      Clearspace host and shared secret.
     */
    public ClearspaceManager(Map<String, String> properties) {
        super("Clearspace integration module for testing only");
        this.properties = properties;

        this.uri = properties.get("clearspace.uri");
        if (!this.uri.endsWith("/")) {
            this.uri = this.uri + "/";
        }
        sharedSecret = properties.get("clearspace.sharedSecret");

        if (Log.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Created new ClearspaceManager() instance, fields:\n");
            buf.append("\t URI: ").append(uri).append("\n");
            buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

            Log.debug("ClearspaceManager: " + buf.toString());
        }
    }

    /**
     * Constructs a new ClearspaceManager instance. Typically, {@link #getInstance()} should be
     * called instead of this method. ClearspaceManager instances should only be created directly
     * for testing purposes.
     *
     */
    public ClearspaceManager() {
        super("Clearspace integration module");
        // Create a special Map implementation to wrap XMLProperties. We only implement
        // the get, put, and remove operations, since those are the only ones used. Using a Map
        // makes it easier to perform LdapManager testing.
        this.properties = new Map<String, String>() {

            public String get(Object key) {
                return JiveGlobals.getXMLProperty((String)key);
            }

            public String put(String key, String value) {
                JiveGlobals.setXMLProperty(key, value);
                // Always return null since XMLProperties doesn't support the normal semantics.
                return null;
            }

            public String remove(Object key) {
                JiveGlobals.deleteXMLProperty((String)key);
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

        this.uri = JiveGlobals.getXMLProperty("clearspace.uri");
        sharedSecret = JiveGlobals.getXMLProperty("clearspace.sharedSecret");

        if (uri != null && !"".equals(uri.trim())) {
            try {
                URL url = new URL(uri);
                host = url.getHost();
                port = url.getPort();
            } catch (MalformedURLException e) {
                // this won't happen
            }
        }

        if (Log.isDebugEnabled()) {
            StringBuilder buf = new StringBuilder();
            buf.append("Created new ClearspaceManager() instance, fields:\n");
            buf.append("\t URI: ").append(uri).append("\n");
            buf.append("\t sharedSecret: ").append(sharedSecret).append("\n");

            Log.debug("ClearspaceManager: " + buf.toString());
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
            String path = ClearspaceUserProvider.USER_URL_PREFIX + "users/count";
            Element element = executeRequest(GET, path);
            getReturn(element);
            return true;
        } catch (Exception e) {
            // Nothing to do.
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
        this.sharedSecret = sharedSecret;
        properties.put("clearspace.sharedSecret", sharedSecret);
        // Set new password for external component
        ExternalComponentConfiguration configuration = new ExternalComponentConfiguration("clearspace",
                ExternalComponentConfiguration.Permission.allowed, sharedSecret);
        try {
            ExternalComponentManager.allowAccess(configuration);
        }
        catch (ModificationNotAllowedException e) {
            Log.warn("Failed to configure password for Clearspace", e);
        }
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

            // Starts the clearspace configuration task
            startClearspaceConfig();
        }
    }

    /**
     *
     */
    private void startClearspaceConfig() {
        // Start the task if it is not currently running
        if (configClearspaceTask == null) {
            configClearspaceTask = new ConfigClearspaceTask();
            TaskEngine.getInstance().schedule(configClearspaceTask, 0, JiveConstants.MINUTE);

        }/*
        try {
            configClearspace();
        } catch (UnauthorizedException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }  */
    }

    private synchronized void configClearspace() throws UnauthorizedException {
        try {
            List<String> bindInterfaces = getServerInterfaces();
            if (bindInterfaces.size() == 0) {
                // We aren't up and running enough to tell Clearspace what interfaces to bind to.
                return;
            }

            XMPPServerInfo serverInfo = XMPPServer.getInstance().getServerInfo();

            String path = IM_URL_PREFIX + "configureComponent/";

            // Creates the XML with the data
            Document groupDoc =  DocumentHelper.createDocument();
            Element rootE = groupDoc.addElement("configureComponent");
            Element domainE = rootE.addElement("domain");
            domainE.setText(serverInfo.getXMPPDomain());
            for (String bindInterface : bindInterfaces) {
                Element hostsE = rootE.addElement("hosts");
                hostsE.setText(bindInterface);
            }
            Element portE = rootE.addElement("port");
            portE.setText(String.valueOf(ExternalComponentManager.getServicePort()));

            executeRequest(POST, path, rootE.asXML());

            //Done, Clearspace was configured correctly, clear the task
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
        }
        return bindInterfaces;
    }

    private void updateClearspaceSharedSecret(String newSecret) {

        try {
            String path = IM_URL_PREFIX + "updateSharedSecret/";

            // Creates the XML with the data
            Document groupDoc =  DocumentHelper.createDocument();
            Element rootE = groupDoc.addElement("updateSharedSecret");
            rootE.addElement("newSecret").setText(newSecret);

            executeRequest(POST, path, groupDoc.asXML());
        } catch (UnauthorizedException ue) {
            // TODO: what should happen here? should continue?
        } catch (Exception e) {
            // TODO: what should happen here? should continue?
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
     *
     * urlSuffix should be of the form /userService/users
     *
     * @param type Must be GET or DELETE
     * @param urlSuffix The url suffix of the rest request
     * @return The response as a xml doc.
     * @throws Exception Thrown if there are issues parsing the request.
     */
    public Element executeRequest(HttpType type, String urlSuffix) throws Exception {
        assert (type == HttpType.GET || type == HttpType.DELETE);
        return executeRequest(type, urlSuffix, null);
    }

    public Element executeRequest(HttpType type, String urlSuffix, String xmlParams)
            throws Exception
    {
        Log.debug("Outgoing REST call ["+type+"] to "+urlSuffix+": "+xmlParams);
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
            // Excecutes the resquest
            client.executeMethod(method);

            // Parses the result
            String body = method.getResponseBodyAsString();
            Log.debug("Outgoing REST call results: "+body);
            Element response = localParser.get().parseDocument(body).getRootElement();

            // Check for exceptions
            checkFault(response);

            // Since there is no exception, returns the response
            return response;
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
            // If there is no massege, save the class only
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
                // failed to create an specific exception, creating a standar one.
                exception = new Exception(exceptionText);
            }

            throw exception;
        }

    }

    /**
     * Returns the Clearspace user id the user by username.
     * @param username Username to retrieve ID of.
     * @return The ID number of the user in Clearspace.
     * @throws org.jivesoftware.openfire.user.UserNotFoundException If the user was not found.
     */
    protected long getUserID(String username) throws UserNotFoundException {
        // todo implement cache
        if(username.contains("@")) {
            if (!XMPPServer.getInstance().isLocal(new JID(username))) {
                throw new UserNotFoundException("Cannot load user of remote server: " + username);
            }
            username = username.substring(0,username.lastIndexOf("@"));
        }
        return getUserID(XMPPServer.getInstance().createJID(username, null));
    }

    /**
     * Returns the Clearspace user id the user by JID.
     * @param user JID of user to retrieve ID of.
     * @return The ID number of the user in Clearspace.
     * @throws org.jivesoftware.openfire.user.UserNotFoundException If the user was not found.
     */
    protected long getUserID(JID user) throws UserNotFoundException {
        // TODO: implement cache, after we are listening for user events from Clearspace.
        XMPPServer server = XMPPServer.getInstance();
        String username = server.isLocal(user) ? JID.unescapeNode(user.getNode()) : user.toString();
        try {
            String path = ClearspaceUserProvider.USER_URL_PREFIX + "users/" + username;
            Element element = executeRequest(org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET, path);

            return Long.valueOf(WSUtils.getElementText(element.selectSingleNode("return"), "ID"));
        } catch (UserNotFoundException unfe) {
            // It is a supported exception, throw it again
            throw unfe;
        } catch (Exception e) {
            // It is not asupperted exception, wrap it into a UserNotFoundException
            throw new UserNotFoundException("Unexpected error", e);
        }
    }

    /**
     * Returns the Clearspace group id of the group.
     * @param groupname Name of the group to retrieve ID of.
     * @return The ID number of the group in Clearspace.
     * @throws org.jivesoftware.openfire.group.GroupNotFoundException If the group was not found.
     */
    protected long getGroupID(String groupname) throws GroupNotFoundException {
        // TODO: implement cache, after we are listening for group events from Clearspace.
        try {
            String path = ClearspaceGroupProvider.URL_PREFIX + "groups/" + groupname;
            Element element = executeRequest(org.jivesoftware.openfire.clearspace.ClearspaceManager.HttpType.GET, path);

            return Long.valueOf(WSUtils.getElementText(element.selectSingleNode("return"), "ID"));
        } catch (GroupNotFoundException gnfe) {
            // It is a supported exception, throw it again
            throw gnfe;
        } catch (Exception e) {
            // It is not asupperted exception, wrap it into a GroupNotFoundException
            throw new GroupNotFoundException("Unexpected error", e);
        }
    }

    private class ConfigClearspaceTask extends TimerTask {

        public void run() {
            try {
                configClearspace();
            } catch (UnauthorizedException e) {
                // TODO: mark that there is an authorization problem
            }
        }
    }
}