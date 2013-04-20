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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.jivesoftware.util.JiveGlobals;


/**
 * Reading the file crowd.properties which should be found in the conf folder
 * of openfire
 *
 */
public class CrowdProperties {
	private static final String APPLICATION_NAME = "application.name";
	private static final String APPLICATION_PASSWORD = "application.password";
	private static final String CROWD_SERVER_URL = "crowd.server.url";
	private static final String HTTP_PROXY_HOST = "http.proxy.host";
	private static final String HTTP_PROXY_PORT = "http.proxy.port";
	private static final String HTTP_PROXY_USERNAME = "http.proxy.username";
	private static final String HTTP_PROXY_PASSWORD = "http.proxy.password";
	private static final String HTTP_MAX_CONNECTIONS = "http.max.connections";
	private static final String HTTP_TIMEOUT = "http.timeout";
	private static final String HTTP_SOCKET_TIMEOUT = "http.socket.timeout";
	
	private Properties props;
	
	public CrowdProperties() throws IOException {
		props = new Properties();
		
		File file = new File(JiveGlobals.getHomeDirectory() + File.separator + "conf" + File.separator + "crowd.properties");
		if (!file.exists()) {
			throw new IOException("The file crowd.properties is missing from Openfire conf folder");
		} else {
			try {
				props.load(new FileInputStream(file));
			} catch (IOException ioe) {
				throw new IOException("Unable to load crowd.properties file");
			}
		}
		
		// checking for required info in file
		if (StringUtils.isBlank(props.getProperty(APPLICATION_NAME))
				|| StringUtils.isBlank(props.getProperty(APPLICATION_PASSWORD))
				|| StringUtils.isBlank(props.getProperty(CROWD_SERVER_URL))) {
			
			throw new IOException("crowd.properties is missing required information (app name, app passwd, crowd url)");
		}
	}
	
	public String getApplicationName() {
		return props.getProperty(APPLICATION_NAME);
	}
	
	public String getApplicationPassword() {
		return props.getProperty(APPLICATION_PASSWORD);
	}
	
	public String getCrowdServerUrl() {
		String url = props.getProperty(CROWD_SERVER_URL);
		if (!url.endsWith("/")) {
			url += "/";
		}
		return url;
	}
	
	public String getHttpProxyHost() {
		return props.getProperty(HTTP_PROXY_HOST);
	}
	
	public int getHttpProxyPort() {
		return getIntegerValue(HTTP_PROXY_PORT, 0);
	}
	
	public String getHttpProxyUsername() {
		return noNull(props.getProperty(HTTP_PROXY_USERNAME));
	}
	
	public String getHttpProxyPassword() {
		return noNull(props.getProperty(HTTP_PROXY_PASSWORD));
	}
	
	public int getHttpMaxConnections() {
		return getIntegerValue(HTTP_MAX_CONNECTIONS, 20);
	}
	
	public int getHttpConnectionTimeout() {
		return getIntegerValue(HTTP_TIMEOUT, 5000);
	}

	public int getHttpSocketTimeout() {
		return getIntegerValue(HTTP_SOCKET_TIMEOUT, 20000);
	}
	
	private int getIntegerValue(String propKey, int defaultValue) {
		int i = 0;
		try {
			i = Integer.parseInt(props.getProperty(propKey));
		} catch (NumberFormatException nfe) {
			i = defaultValue;
		}
		return i;
	}
	
	private String noNull(String str) {
		return (str != null) ? str : "";
	}
}
