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
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXB;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang.StringUtils;

import org.jivesoftware.openfire.crowd.jaxb.AuthenticatePost;
import org.jivesoftware.openfire.crowd.jaxb.Group;
import org.jivesoftware.openfire.crowd.jaxb.Groups;
import org.jivesoftware.openfire.crowd.jaxb.User;
import org.jivesoftware.openfire.crowd.jaxb.Users;



public class CrowdManager {
	private static final Logger LOG = LoggerFactory.getLogger(CrowdManager.class);
	private static final Object O = new Object();
	private static final String APPLICATION_XML = "application/xml";
	private static final Header HEADER_ACCEPT_APPLICATION_XML = new Header("Accept", APPLICATION_XML);
	private static final Header HEADER_ACCEPT_CHARSET_UTF8 = new Header("Accept-Charset", "UTF-8");

	private static CrowdManager INSTANCE;
	
	private HttpClient client;
	private URI crowdServer;

	public static CrowdManager getInstance() {
		if (INSTANCE == null) {
			synchronized (O) {
				if (INSTANCE == null) {
					CrowdManager manager = new CrowdManager();
					if (manager != null)
						INSTANCE = manager;
				}
			}
		}
		return INSTANCE;
	}
	
	private CrowdManager() {
		try {
			// loading crowd.properties file
			CrowdProperties crowdProps = new CrowdProperties();
			
			MultiThreadedHttpConnectionManager threadedConnectionManager = new MultiThreadedHttpConnectionManager();
			HttpClient hc = new HttpClient(threadedConnectionManager);
	
			HttpClientParams hcParams = hc.getParams();
			hcParams.setAuthenticationPreemptive(true);
			
			HttpConnectionManagerParams hcConnectionParams = hc.getHttpConnectionManager().getParams();
			hcConnectionParams.setDefaultMaxConnectionsPerHost(crowdProps.getHttpMaxConnections());
			hcConnectionParams.setMaxTotalConnections(crowdProps.getHttpMaxConnections());
			hcConnectionParams.setConnectionTimeout(crowdProps.getHttpConnectionTimeout());
			hcConnectionParams.setSoTimeout(crowdProps.getHttpSocketTimeout());
			
			crowdServer = new URI(crowdProps.getCrowdServerUrl()).resolve("rest/usermanagement/latest/");
			
			// setting BASIC authentication in place for connection with Crowd
			HttpState httpState = hc.getState();
			Credentials crowdCreds = new UsernamePasswordCredentials(crowdProps.getApplicationName(), crowdProps.getApplicationPassword());
			httpState.setCredentials(new AuthScope(crowdServer.getHost(), crowdServer.getPort()), crowdCreds);
			
			// setting Proxy config in place if needed
			if (StringUtils.isNotBlank(crowdProps.getHttpProxyHost()) && crowdProps.getHttpProxyPort() > 0) {
				hc.getHostConfiguration().setProxy(crowdProps.getHttpProxyHost(), crowdProps.getHttpProxyPort());
				
				if (StringUtils.isNotBlank(crowdProps.getHttpProxyUsername()) || StringUtils.isNotBlank(crowdProps.getHttpProxyPassword())) {
					Credentials proxyCreds = new UsernamePasswordCredentials(crowdProps.getHttpProxyUsername(), crowdProps.getHttpProxyPassword());
					httpState.setProxyCredentials(new AuthScope(crowdProps.getHttpProxyHost(), crowdProps.getHttpProxyPort()), proxyCreds);
				}
			}
			
			if (LOG.isDebugEnabled()) {
				LOG.debug("HTTP Client config");
				LOG.debug(crowdServer.toString());
				LOG.debug("Max connections:" + hcConnectionParams.getMaxTotalConnections());
				LOG.debug("Socket timeout:" + hcConnectionParams.getSoTimeout());
				LOG.debug("Connect timeout:" + hcConnectionParams.getConnectionTimeout());
				LOG.debug("Proxy host:" + crowdProps.getHttpProxyHost() + ":" + crowdProps.getHttpProxyPort());
				LOG.debug("Crowd application name:" + crowdProps.getApplicationName());
			}
			
			client = hc;
		} catch (Exception e) {
			LOG.error("Failure to load the Crowd manager", e);
		}
	}
	
	
	
	/**
	 * Authenticates a user with crowd. If authentication failed, raises a <code>RemoteException</code>
	 * @param username
	 * @param password
	 * @throws RemoteException
	 * @throws UnsupportedEncodingException 
	 */
	public void authenticate(String username, String password) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("authenticate '" + String.valueOf(username) + "'");
		
		PostMethod post = new PostMethod(crowdServer.resolve("authentication?username=" + urlEncode(username)).toString());
		
		AuthenticatePost creds = new AuthenticatePost();
		creds.value = password;
		try {
			StringWriter writer = new StringWriter();
			JAXB.marshal(creds, writer);
			post.setRequestEntity(new StringRequestEntity(writer.toString(), APPLICATION_XML, "UTF-8"));
			
			int httpCode = client.executeMethod(post);
			if (httpCode != 200) {
				handleHTTPError(post);
			}
			
		} catch (IOException ioe) {
			handleError(ioe);
		} finally {
			post.releaseConnection();
		}
	}
	
	
	/**
	 * Get all the users from Crowd
	 * @return a List of User containing all the users stored in Crowd
	 * @throws RemoteException
	 */
	public List<User> getAllUsers() throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("fetching all crowd users");
		
		int maxResults = 100;
		int startIndex = 0;
		List<User> results = new ArrayList<User>();
		StringBuilder request = new StringBuilder("search?entity-type=user&expand=user&restriction=active%3dtrue")
			.append("&max-results=").append(maxResults)
			.append("&start-index=");
		
		try {
			while (true) {
				GetMethod get = createGetMethodXmlResponse(crowdServer.resolve(request.toString() + startIndex));
				Users users = null;
				
				try {
					int httpCode = client.executeMethod(get);
					if (httpCode != 200) {
						handleHTTPError(get);
					}
					users = JAXB.unmarshal(get.getResponseBodyAsStream(), Users.class);
				} finally {
					get.releaseConnection();
				}
				
				if (users != null && users.user != null) {
					for (User user : users.user) {
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
			
		} catch (IOException ioe) {
			handleError(ioe);
		}
		
		return results;
	}
	
	
	/**
	 * Get all the crowd groups
	 * @return a List of group names
	 * @throws RemoteException
	 */
	public List<String> getAllGroupNames() throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("fetch all crowd groups");

		int maxResults = 100;
		int startIndex = 0;
		List<String> results = new ArrayList<String>();
		StringBuilder request = new StringBuilder("search?entity-type=group&restriction=active%3dtrue")
			.append("&max-results=").append(maxResults)
			.append("&start-index=");
		
		try {
			while (true) {
				GetMethod get = createGetMethodXmlResponse(crowdServer.resolve(request.toString() + startIndex));
				Groups groups = null;
				
				try {
					int httpCode = client.executeMethod(get);
					if (httpCode != 200) {
						handleHTTPError(get);
					}
					groups = JAXB.unmarshal(get.getResponseBodyAsStream(), Groups.class);
				} finally {
					get.releaseConnection();
				}
				
				if (groups != null && groups.group != null) {
					for (Group group : groups.group) {
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
			
		} catch (IOException ioe) {
			handleError(ioe);
		}
		
		return results;
	}
	
	
	/**
	 * Get all the groups of a given username
	 * @param username
	 * @return a List of groups name
	 * @throws RemoteException
	 */
	public List<String> getUserGroups(String username) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("fetch all crowd groups for user:" + username);
		
		int maxResults = 100;
		int startIndex = 0;
		List<String> results = new ArrayList<String>();
		StringBuilder request = new StringBuilder("user/group/nested?username=").append(urlEncode(username))
			.append("&max-results=").append(maxResults)
			.append("&start-index=");
		
		try {
			while (true) {
				GetMethod get = createGetMethodXmlResponse(crowdServer.resolve(request.toString() + startIndex));
				Groups groups = null;
				
				try {
					int httpCode = client.executeMethod(get);
					if (httpCode != 200) {
						handleHTTPError(get);
					}
					groups = JAXB.unmarshal(get.getResponseBodyAsStream(), Groups.class);
				} finally {
					get.releaseConnection();
				}
				
				if (groups != null && groups.group != null) {
					for (Group group : groups.group) {
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
			
		} catch (IOException ioe) {
			handleError(ioe);
		}
		
		return results;
	}
	
	
	/**
	 * Get the description of a group from crowd
	 * @param groupName
	 * @return a Group object
	 * @throws RemoteException
	 */
	public Group getGroup(String groupName) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("Get group:" + groupName + " from crowd");
		
		GetMethod get = createGetMethodXmlResponse(crowdServer.resolve("group?groupname=" + urlEncode(groupName)));
		Group group = null;
		
		try {
			int httpCode = client.executeMethod(get);
			if (httpCode != 200) {
				handleHTTPError(get);
			}
			
			group = JAXB.unmarshal(get.getResponseBodyAsStream(), Group.class);
			
		} catch (IOException ioe) {
			handleError(ioe);
		} finally {
			get.releaseConnection();
		}
		
		return group;
	}
	
	
	/**
	 * Get the members of the given group
	 * @param groupName
	 * @return a List of String with the usernames members of the given group
	 * @throws RemoteException
	 */
	public List<String> getGroupMembers(String groupName) throws RemoteException {
		if (LOG.isDebugEnabled()) LOG.debug("Get all members for group:" + groupName);
		
		int maxResults = 100;
		int startIndex = 0;
		List<String> results = new ArrayList<String>();
		StringBuilder request = new StringBuilder("group/user/nested?groupname=").append(urlEncode(groupName))
			.append("&max-results=").append(maxResults)
			.append("&start-index=");
		
		try {
			while (true) {
				GetMethod get = createGetMethodXmlResponse(crowdServer.resolve(request.toString() + startIndex));
				Users users = null;
				
				try {
					int httpCode = client.executeMethod(get);
					if (httpCode != 200) {
						handleHTTPError(get);
					}
					users = JAXB.unmarshal(get.getResponseBodyAsStream(), Users.class);
				} finally {
					get.releaseConnection();
				}
				
				if (users != null && users.user != null) {
					for (org.jivesoftware.openfire.crowd.jaxb.User user : users.user) {
						results.add(user.name);
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
	
	
	private void handleHTTPError(HttpMethod method) throws RemoteException {
		int status = method.getStatusCode();
		String statusText = method.getStatusText();
		String body = null;
		try {
			body = method.getResponseBodyAsString();
		} catch (IOException ioe) {
			LOG.warn("Unable to retreive Crowd http response body", ioe);
		}
		
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
	
	private GetMethod createGetMethodXmlResponse(URI uri) {
		GetMethod get = new GetMethod(uri.toString());
		get.addRequestHeader(HEADER_ACCEPT_APPLICATION_XML);
		get.addRequestHeader(HEADER_ACCEPT_CHARSET_UTF8);
		return get;
	}

}
