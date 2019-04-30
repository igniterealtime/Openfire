/*
 * Copyright (C) 2012 Issa Gorissen <issa-gorissen@usa.net>. All rights reserved.
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
package org.jivesoftware.openfire.crowd;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXB;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;
import org.apache.commons.lang3.StringUtils;

import org.jivesoftware.openfire.crowd.jaxb.AuthenticatePost;
import org.jivesoftware.openfire.crowd.jaxb.Group;
import org.jivesoftware.openfire.crowd.jaxb.Groups;
import org.jivesoftware.openfire.crowd.jaxb.User;
import org.jivesoftware.openfire.crowd.jaxb.Users;



public class CrowdManager {
    private static final Logger LOG = LoggerFactory.getLogger(CrowdManager.class);
    private static final Object O = new Object();
    private static final String APPLICATION_XML = "application/xml";
    private static final Header HEADER_CONTENT_TYPE_APPLICATION_XML = new BasicHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_XML);
    private static final Header HEADER_ACCEPT_APPLICATION_XML = new BasicHeader(HttpHeaders.ACCEPT, APPLICATION_XML);
    private static final Header HEADER_ACCEPT_CHARSET_UTF8 = new BasicHeader(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());

    private static CrowdManager INSTANCE;
    private CloseableHttpClient client;
    private URI crowdServer;
    private RequestConfig requestConfig;
    private HttpClientContext clientContext;

    public static CrowdManager getInstance() {
        if (INSTANCE == null) {
            synchronized (O) {
                if (INSTANCE == null) {
                    INSTANCE = new CrowdManager();
                }
            }
        }
        return INSTANCE;
    }
    
    private CrowdManager() {
        try {
            // loading crowd.properties file
            CrowdProperties crowdProps = new CrowdProperties();

            crowdServer = new URI(crowdProps.getCrowdServerUrl()).resolve("rest/usermanagement/latest/");
            final HttpHost target = HttpHost.create(crowdServer.toString());
            final PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            connectionManager.setDefaultMaxPerRoute(crowdProps.getHttpMaxConnections());

            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                new AuthScope(target.getHostName(), target.getPort()),
                new UsernamePasswordCredentials(crowdProps.getApplicationName(), crowdProps.getApplicationPassword()));
            final AuthCache authCache = new BasicAuthCache();
            authCache.put(target, new BasicScheme());
            clientContext = HttpClientContext.create();
            clientContext.setAuthCache(authCache);


            // setting Proxy config in place if needed
            final String proxyString;
            final HttpRoutePlanner httpRoutePlanner;
            if (StringUtils.isNotBlank(crowdProps.getHttpProxyHost()) && crowdProps.getHttpProxyPort() > 0) {
                final HttpHost proxyHost = new HttpHost(crowdProps.getHttpProxyHost(), crowdProps.getHttpProxyPort());
                httpRoutePlanner = new DefaultProxyRoutePlanner(proxyHost);
                proxyString = proxyHost.toString();
                if (StringUtils.isNotBlank(crowdProps.getHttpProxyUsername()) || StringUtils.isNotBlank(crowdProps.getHttpProxyPassword())) {
                    credentialsProvider.setCredentials(new AuthScope(crowdProps.getHttpProxyHost(), crowdProps.getHttpProxyPort()),
                        new UsernamePasswordCredentials(crowdProps.getHttpProxyUsername(), crowdProps.getHttpProxyPassword()));
                }
            } else {
                httpRoutePlanner =  new DefaultRoutePlanner(null);
                proxyString = "<none>";
            }

            client = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultCredentialsProvider(credentialsProvider)
                .setRoutePlanner(httpRoutePlanner)
                .build();

            requestConfig = RequestConfig.custom()
                .setConnectTimeout(crowdProps.getHttpConnectionTimeout())
                .setSocketTimeout(crowdProps.getHttpSocketTimeout())
                .build();


            if (LOG.isDebugEnabled()) {
                LOG.debug("HTTP Client config");
                LOG.debug(crowdServer.toString());
                LOG.debug("Max connections: {}", connectionManager.getDefaultMaxPerRoute());
                LOG.debug("Socket timeout: {}", requestConfig.getSocketTimeout());
                LOG.debug("Connect timeout: {}", requestConfig.getConnectTimeout());
                LOG.debug("Proxy: {}", proxyString);
                LOG.debug("Crowd application name: {}", crowdProps.getApplicationName());
            }
        } catch (Exception e) {
            LOG.error("Failure to load the Crowd manager", e);
        }
    }
    
    
    
    /**
     * Authenticates a user with crowd. If authentication failed, raises a <code>RemoteException</code>
     * @param username the username
     * @param password the password
     * @throws RemoteException if an exception occurred communicating with the crowd server
     */
    public void authenticate(String username, String password) throws RemoteException {
        username = JID.unescapeNode(username);
        LOG.debug("authenticate '" + String.valueOf(username) + "'");

        final AuthenticatePost authenticatePost = new AuthenticatePost();
        authenticatePost.value = password;
        final StringWriter writer = new StringWriter();
        JAXB.marshal(authenticatePost, writer);

        final HttpUriRequest postRequest = RequestBuilder.post(crowdServer.resolve("authentication?username=" + urlEncode(username)))
            .setConfig(requestConfig)
            .setEntity(new StringEntity(writer.toString(), StandardCharsets.UTF_8))
            .setHeader(HEADER_CONTENT_TYPE_APPLICATION_XML)
            .build();

        try(final CloseableHttpResponse response = client.execute(postRequest, clientContext)) {

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                handleHTTPError(response);
            }
            
        } catch (IOException ioe) {
            handleError(ioe);
        }
        
        LOG.info("authenticated user:" + username);
    }
    
    
    /**
     * Get all the users from Crowd
     * @return a List of User containing all the users stored in Crowd
     * @throws RemoteException if an exception occurred communicating with the crowd server
     */
    public List<User> getAllUsers() throws RemoteException {
        LOG.debug("fetching all crowd users");
        
        int maxResults = 100;
        int startIndex = 0;
        List<User> results = new ArrayList<>();
        StringBuilder request = new StringBuilder("search?entity-type=user&expand=user&restriction=active%3dtrue")
            .append("&max-results=").append(maxResults)
            .append("&start-index=");
        
        try {
            while (true) {
                final HttpUriRequest getRequest = RequestBuilder.get(crowdServer.resolve(request.toString() + startIndex))
                    .setConfig(requestConfig)
                    .addHeader(HEADER_ACCEPT_APPLICATION_XML)
                    .addHeader(HEADER_ACCEPT_CHARSET_UTF8)
                    .build();

                try(final CloseableHttpResponse response = client.execute(getRequest, clientContext)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        handleHTTPError(response);
                    }
                    final Users users = JAXB.unmarshal(response.getEntity().getContent(), Users.class);
                    if (users != null && users.user != null) {
                        for (final User user : users.user) {
                            user.name = JID.escapeNode(user.name);
                            results.add(user);
                        }

                        if (users.user.size() != maxResults) {
                            break;
                        } else {
                            startIndex += maxResults;
                        }
                    } else {
                        break;
                    }
                }
            }
            
        } catch (IOException ioe) {
            handleError(ioe);
        }
        
        return results;
    }
    
    
    /**
     * Get all the crowd groups
     * @return a List of group names
     * @throws RemoteException if an exception occurred communicating with the crowd server
     */
    public List<String> getAllGroupNames() throws RemoteException {
        LOG.debug("fetch all crowd groups");

        int maxResults = 100;
        int startIndex = 0;
        List<String> results = new ArrayList<>();
        StringBuilder request = new StringBuilder("search?entity-type=group&restriction=active%3dtrue")
            .append("&max-results=").append(maxResults)
            .append("&start-index=");
        
        try {
            while (true) {
                final HttpUriRequest getRequest = RequestBuilder.get(crowdServer.resolve(request.toString() + startIndex))
                    .setConfig(requestConfig)
                    .addHeader(HEADER_ACCEPT_APPLICATION_XML)
                    .addHeader(HEADER_ACCEPT_CHARSET_UTF8)
                    .build();

                try (final CloseableHttpResponse response = client.execute(getRequest, clientContext)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        handleHTTPError(response);
                    }
                    final Groups groups = JAXB.unmarshal(response.getEntity().getContent(), Groups.class);
                    if (groups != null && groups.group != null) {
                        for (final Group group : groups.group) {
                            results.add(group.name);
                        }

                        if (groups.group.size() != maxResults) {
                            break;
                        } else {
                            startIndex += maxResults;
                        }
                    } else {
                        break;
                    }
                }
            }
            
        } catch (IOException ioe) {
            handleError(ioe);
        }
        
        return results;
    }
    
    
    /**
     * Get all the groups of a given username
     * @param username the user
     * @return a List of groups name
     * @throws RemoteException if an exception occurred communicating with the crowd server
     */
    public List<String> getUserGroups(String username) throws RemoteException {
        username = JID.unescapeNode(username);
        LOG.debug("fetch all crowd groups for user:" + username);
        
        int maxResults = 100;
        int startIndex = 0;
        List<String> results = new ArrayList<>();
        StringBuilder request = new StringBuilder("user/group/nested?username=").append(urlEncode(username))
            .append("&max-results=").append(maxResults)
            .append("&start-index=");
        
        try {
            while (true) {
                final HttpUriRequest getRequest = RequestBuilder.get(crowdServer.resolve(request.toString() + startIndex))
                    .setConfig(requestConfig)
                    .addHeader(HEADER_ACCEPT_APPLICATION_XML)
                    .addHeader(HEADER_ACCEPT_CHARSET_UTF8)
                    .build();

                try (final CloseableHttpResponse response = client.execute(getRequest, clientContext)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        handleHTTPError(response);
                    }
                    final Groups groups = JAXB.unmarshal(response.getEntity().getContent(), Groups.class);

                    if (groups != null && groups.group != null) {
                        for (final Group group : groups.group) {
                            results.add(group.name);
                        }

                        if (groups.group.size() != maxResults) {
                            break;
                        } else {
                            startIndex += maxResults;
                        }
                    } else {
                        break;
                    }
                }
            }
            
        } catch (IOException ioe) {
            handleError(ioe);
        }
        
        return results;
    }
    
    
    /**
     * Get the description of a group from crowd
     * @param groupName the name of the group
     * @return a Group object
     * @throws RemoteException if an exception occurred communicating with the crowd server
     */
    public Group getGroup(String groupName) throws RemoteException {
        LOG.debug("Get group:" + groupName + " from crowd");

        final HttpUriRequest getRequest = RequestBuilder.get(crowdServer.resolve("group?groupname=" + urlEncode(groupName)))
            .setConfig(requestConfig)
            .addHeader(HEADER_ACCEPT_APPLICATION_XML)
            .addHeader(HEADER_ACCEPT_CHARSET_UTF8)
            .build();

        Group group = null;
        try (final CloseableHttpResponse response = client.execute(getRequest, clientContext)) {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                handleHTTPError(response);
            }
            group = JAXB.unmarshal(response.getEntity().getContent(), Group.class);
            
        } catch (IOException ioe) {
            handleError(ioe);
        }
        return group;
    }
    
    
    /**
     * Get the members of the given group
     * @param groupName the name of the group
     * @return a List of String with the usernames members of the given group
     * @throws RemoteException if an exception occurred communicating with the crowd server
     */
    public List<String> getGroupMembers(String groupName) throws RemoteException {
        LOG.debug("Get all members for group:" + groupName);
        
        int maxResults = 100;
        int startIndex = 0;
        List<String> results = new ArrayList<>();
        StringBuilder request = new StringBuilder("group/user/nested?groupname=").append(urlEncode(groupName))
            .append("&max-results=").append(maxResults)
            .append("&start-index=");
        
        try {
            while (true) {
                final HttpUriRequest getRequest = RequestBuilder.get(crowdServer.resolve(request.toString() + startIndex))
                    .setConfig(requestConfig)
                    .addHeader(HEADER_ACCEPT_APPLICATION_XML)
                    .addHeader(HEADER_ACCEPT_CHARSET_UTF8)
                    .build();

                try (final CloseableHttpResponse response = client.execute(getRequest, clientContext)) {
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        handleHTTPError(response);
                    }
                    final Users users = JAXB.unmarshal(response.getEntity().getContent(), Users.class);

                    if (users != null && users.user != null) {
                        for (final User user : users.user) {
                            results.add(JID.escapeNode(user.name));
                        }

                        if (users.user.size() != maxResults) {
                            break;
                        } else {
                            startIndex += maxResults;
                        }
                    } else {
                        break;
                    }
                }
            }
            
        } catch (IOException ioe) {
            handleError(ioe);
        }
        
        return results;
    }
    
    private String urlEncode(String str) {
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (UnsupportedEncodingException uee) {
            LOG.error("UTF-8 not supported ?", uee);
            return str;
        }
    }
    
    
    private void handleHTTPError(CloseableHttpResponse response) throws RemoteException {
        final StatusLine statusLine = response.getStatusLine();
        final int status = statusLine.getStatusCode();
        final String statusText = statusLine.getReasonPhrase();
        final String body = response.getEntity().toString();

        StringBuilder strBuf = new StringBuilder();
        strBuf.append("Crowd returned HTTP error code:").append(status);
        strBuf.append(" - ").append(statusText);
        if (StringUtils.isNotBlank(body)) {
            strBuf.append("\n").append(body);
        }
        
        throw new RemoteException(strBuf.toString());
    }
    
    private void handleError(Exception e) throws RemoteException {
        LOG.error("Error occured while consuming Crowd REST service", e);
        throw new RemoteException(e.getMessage());
    }

}
