/**
 * $Revision: 1722 $
 * $Date: 2005-07-28 15:19:16 -0700 (Thu, 28 Jul 2005) $
 *
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

package org.jivesoftware.openfire.plugin.rest;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.plugin.rest.entity.SystemProperties;
import org.jivesoftware.openfire.plugin.rest.entity.SystemProperty;
import org.jivesoftware.openfire.plugin.rest.exceptions.ExceptionType;
import org.jivesoftware.openfire.plugin.rest.exceptions.ServiceException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;

/**
 * The Class RESTServicePlugin.
 */
public class RESTServicePlugin implements Plugin, PropertyEventListener {
	
	/** The Constant INSTANCE. */
	public static final RESTServicePlugin INSTANCE = new RESTServicePlugin();

	/** The secret. */
	private String secret;
	
	/** The allowed i ps. */
	private Collection<String> allowedIPs;
	
	/** The enabled. */
	private boolean enabled;
	
	/** The http basic auth. */
	private boolean httpBasicAuth;

	/**
	 * Gets the single instance of RESTServicePlugin.
	 *
	 * @return single instance of RESTServicePlugin
	 */
	public static RESTServicePlugin getInstance() {
		return INSTANCE;
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.openfire.container.Plugin#initializePlugin(org.jivesoftware.openfire.container.PluginManager, java.io.File)
	 */
	public void initializePlugin(PluginManager manager, File pluginDirectory) {
		secret = JiveGlobals.getProperty("plugin.restapi.secret", "");
		// If no secret key has been assigned, assign a random one.
		if ("".equals(secret)) {
			secret = StringUtils.randomString(16);
			setSecret(secret);
		}

		// See if the service is enabled or not.
		enabled = JiveGlobals.getBooleanProperty("plugin.restapi.enabled", false);

		// See if the HTTP Basic Auth is enabled or not.
		httpBasicAuth = JiveGlobals.getBooleanProperty("plugin.restapi.httpAuth.enabled", false);

		// Get the list of IP addresses that can use this service. An empty list
		// means that this filter is disabled.
		allowedIPs = StringUtils.stringToCollection(JiveGlobals.getProperty("plugin.restapi.allowedIPs", ""));

		// Listen to system property events
		PropertyEventDispatcher.addListener(this);
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.openfire.container.Plugin#destroyPlugin()
	 */
	public void destroyPlugin() {
		// Stop listening to system property events
		PropertyEventDispatcher.removeListener(this);
	}

	/**
	 * Gets the system properties.
	 *
	 * @return the system properties
	 */
	public SystemProperties getSystemProperties() {
		SystemProperties systemProperties = new SystemProperties();
		List<SystemProperty> propertiesList = new ArrayList<SystemProperty>();
		
		for(String propertyKey : JiveGlobals.getPropertyNames()) {
			String propertyValue = JiveGlobals.getProperty(propertyKey);
			propertiesList.add(new SystemProperty(propertyKey, propertyValue));
		}
		systemProperties.setProperties(propertiesList);
		return systemProperties;

	}

	/**
	 * Gets the system property.
	 *
	 * @param propertyKey the property key
	 * @return the system property
	 * @throws ServiceException the service exception
	 */
	public SystemProperty getSystemProperty(String propertyKey) throws ServiceException {
		String propertyValue = JiveGlobals.getProperty(propertyKey);
		if(propertyValue != null) {
		return new SystemProperty(propertyKey, propertyValue);
		} else {
			throw new ServiceException("Could not find property", propertyKey, ExceptionType.PROPERTY_NOT_FOUND,
					Response.Status.NOT_FOUND);
		}
	}
	
	/**
	 * Creates the system property.
	 *
	 * @param systemProperty the system property
	 */
	public void createSystemProperty(SystemProperty systemProperty) {
		JiveGlobals.setProperty(systemProperty.getKey(), systemProperty.getValue());
	}
	
	/**
	 * Delete system property.
	 *
	 * @param propertyKey the property key
	 * @throws ServiceException the service exception
	 */
	public void deleteSystemProperty(String propertyKey) throws ServiceException {
		if(JiveGlobals.getProperty(propertyKey) != null) {
			JiveGlobals.deleteProperty(propertyKey);
		} else {
			throw new ServiceException("Could not find property", propertyKey, ExceptionType.PROPERTY_NOT_FOUND,
					Response.Status.NOT_FOUND);
		}
	}
	
	/**
	 * Update system property.
	 *
	 * @param propertyKey the property key
	 * @param systemProperty the system property
	 * @throws ServiceException the service exception
	 */
	public void updateSystemProperty(String propertyKey, SystemProperty systemProperty) throws ServiceException {
		if(JiveGlobals.getProperty(propertyKey) != null) {
			if(systemProperty.getKey().equals(propertyKey)) {
				JiveGlobals.setProperty(propertyKey, systemProperty.getValue());
			} else {
				throw new ServiceException("Path property name and entity property name doesn't match", propertyKey, ExceptionType.ILLEGAL_ARGUMENT_EXCEPTION,
						Response.Status.BAD_REQUEST);
			}
		} else {
			throw new ServiceException("Could not find property for update", systemProperty.getKey(), ExceptionType.PROPERTY_NOT_FOUND,
					Response.Status.NOT_FOUND);
		}
	}

	/**
	 * Returns the secret key that only valid requests should know.
	 *
	 * @return the secret key.
	 */
	public String getSecret() {
		return secret;
	}

	/**
	 * Sets the secret key that grants permission to use the userservice.
	 *
	 * @param secret
	 *            the secret key.
	 */
	public void setSecret(String secret) {
		JiveGlobals.setProperty("plugin.restapi.secret", secret);
		this.secret = secret;
	}

	/**
	 * Gets the allowed i ps.
	 *
	 * @return the allowed i ps
	 */
	public Collection<String> getAllowedIPs() {
		return allowedIPs;
	}

	/**
	 * Sets the allowed i ps.
	 *
	 * @param allowedIPs the new allowed i ps
	 */
	public void setAllowedIPs(Collection<String> allowedIPs) {
		JiveGlobals.setProperty("plugin.restapi.allowedIPs", StringUtils.collectionToString(allowedIPs));
		this.allowedIPs = allowedIPs;
	}

	/**
	 * Returns true if the user service is enabled. If not enabled, it will not
	 * accept requests to create new accounts.
	 *
	 * @return true if the user service is enabled.
	 */
	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Enables or disables the user service. If not enabled, it will not accept
	 * requests to create new accounts.
	 *
	 * @param enabled
	 *            true if the user service should be enabled.
	 */
	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		JiveGlobals.setProperty("plugin.restapi.enabled", enabled ? "true" : "false");
	}

	/**
	 * Checks if is http basic auth.
	 *
	 * @return true, if is http basic auth
	 */
	public boolean isHttpBasicAuth() {
		return httpBasicAuth;
	}

	/**
	 * Sets the http basic auth.
	 *
	 * @param httpBasicAuth the new http basic auth
	 */
	public void setHttpBasicAuth(boolean httpBasicAuth) {
		this.httpBasicAuth = httpBasicAuth;
		JiveGlobals.setProperty("plugin.restapi.httpAuth.enabled", httpBasicAuth ? "true" : "false");
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.util.PropertyEventListener#propertySet(java.lang.String, java.util.Map)
	 */
	public void propertySet(String property, Map<String, Object> params) {
		if (property.equals("plugin.restapi.secret")) {
			this.secret = (String) params.get("value");
		} else if (property.equals("plugin.restapi.enabled")) {
			this.enabled = Boolean.parseBoolean((String) params.get("value"));
		} else if (property.equals("plugin.restapi.allowedIPs")) {
			this.allowedIPs = StringUtils.stringToCollection((String) params.get("value"));
		} else if (property.equals("plugin.restapi.httpAuth.enabled")) {
			this.httpBasicAuth = Boolean.parseBoolean((String) params.get("value"));
		}
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.util.PropertyEventListener#propertyDeleted(java.lang.String, java.util.Map)
	 */
	public void propertyDeleted(String property, Map<String, Object> params) {
		if (property.equals("plugin.restapi.secret")) {
			this.secret = "";
		} else if (property.equals("plugin.restapi.enabled")) {
			this.enabled = false;
		} else if (property.equals("plugin.restapi.allowedIPs")) {
			this.allowedIPs = Collections.emptyList();
		} else if (property.equals("plugin.restapi.httpAuth.enabled")) {
			this.httpBasicAuth = false;
		}
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.util.PropertyEventListener#xmlPropertySet(java.lang.String, java.util.Map)
	 */
	public void xmlPropertySet(String property, Map<String, Object> params) {
		// Do nothing
	}

	/* (non-Javadoc)
	 * @see org.jivesoftware.util.PropertyEventListener#xmlPropertyDeleted(java.lang.String, java.util.Map)
	 */
	public void xmlPropertyDeleted(String property, Map<String, Object> params) {
		// Do nothing
	}
}
