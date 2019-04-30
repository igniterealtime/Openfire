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
package org.jivesoftware.openfire.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serves up the flash cross domain xml file which allows other domains to
 * access http-binding using flash.
 * 
 * This implementation will first try to serve
 * {@code &lt;OpenfireHome&gt;/conf/crossdomain.xml}. If this file is
 * unavailable, a crossdomain file will be generated dynamically, based on the
 * current settings of the Openfire BOSH functionality.
 * 
 * @author Alexander Wenckus
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class FlashCrossDomainServlet extends HttpServlet {

    private static Logger Log = LoggerFactory.getLogger(FlashCrossDomainServlet.class);
    
    public static String CROSS_DOMAIN_TEXT = "<?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">\n" +
            "<cross-domain-policy>\n" +
            "\t<site-control permitted-cross-domain-policies=\"all\"/>\n" +
            "\t<allow-access-from domain=\"*\" to-ports=\"";

    public static String CROSS_DOMAIN_MIDDLE_TEXT = "\" secure=\"";
    public static String CROSS_DOMAIN_END_TEXT = "\"/>\n</cross-domain-policy>";
    
    private static String CROSS_DOMAIN_SECURE_ENABLED = "httpbind.crossdomain.secure";
    private static boolean CROSS_DOMAIN_SECURE_DEFAULT = true;
    
    @Override
    protected void doGet(HttpServletRequest httpServletRequest,
                         HttpServletResponse response) throws
            ServletException, IOException {
        response.setContentType("text/xml");
        response.getOutputStream().write(getCrossDomainContent().getBytes());
    }
    
    /**
     * Returns the content for {@code crossdomain.xml}, either by generating
     * content, or by passing the provided file in
     * {@code &lt;OpenfireHome&gt;/conf/crossdomain.xml}
     * 
     * @return content for the {@code crossdomain.xml} that should be served
     *         for this service.
     */
    public static String getCrossDomainContent() {
        final String override = getContent(getOverride());
        if (override != null && override.trim().length() > 0) {
            return override;
        } else {
            return generateOutput();
        }
    }
    
    /**
     * Returns {@code &lt;OpenfireHome&gt;/conf/crossdomain.xml} as a File
     * object (even if the file does not exist on the file system).
     * 
     * @return {@code &lt;OpenfireHome&gt;/conf/crossdomain.xml}
     */
    private static File getOverride() {
        final StringBuilder sb = new StringBuilder();
        sb.append(JiveGlobals.getHomeDirectory());
        if (!sb.substring(sb.length()-1).startsWith(File.separator)) {
            sb.append(File.separator);
        }
        sb.append("conf");
        sb.append(File.separator);

        return new File(sb.toString(), "crossdomain.xml");
    }
    
    /**
     * Return content of the provided file as a String.
     * 
     * @param file
     *            The file from which to get the content.
     * @return String-based content of the provided file.
     */
    private static String getContent(File file) {
        final StringBuilder content = new StringBuilder();
        if (file.canRead()) {
            try (BufferedReader in = new BufferedReader(new FileReader(file))) {
                String str;
                while ((str = in.readLine()) != null) {
                    content.append(str);
                    content.append('\n');
                }
            } catch (IOException ex) {
                Log.warn("Unexpected exception while trying to read file: " + file.getName(), ex);
                return null;
            }
        }

        return content.toString();
    }
    
    /**
     * Dynamically generates content for a non-restrictive {@code crossdomain.xml} file.
     */
    private static String generateOutput() {
        final StringBuilder builder = new StringBuilder();
        builder.append(CROSS_DOMAIN_TEXT);
        getPortList(builder);
        builder.append(CROSS_DOMAIN_MIDDLE_TEXT);
        getSecure(builder);
        builder.append(CROSS_DOMAIN_END_TEXT);
        builder.append("\n");
        
        return builder.toString();
    }
    
    private static StringBuilder getPortList(StringBuilder builder) {
        boolean multiple = false;
        if(XMPPServer.getInstance().getConnectionManager().getClientListenerPort() > 0) {
            builder.append(XMPPServer.getInstance().getConnectionManager().getClientListenerPort());
            multiple = true;
        }
        if(XMPPServer.getInstance().getConnectionManager().getClientSSLListenerPort() > 0) {
            if(multiple) {
                builder.append(',');
            }
            builder.append(XMPPServer.getInstance().getConnectionManager().getClientSSLListenerPort());
            multiple = true;
        }
        
        if(HttpBindManager.getInstance().isHttpBindEnabled()) {
            // ports for http-binding may not be strictly needed in here, but it doesn't hurt
            if(HttpBindManager.getInstance().getHttpBindUnsecurePort() > 0) {
                if(multiple) {
                    builder.append(',');
                }
                builder.append(HttpBindManager.getInstance().getHttpBindUnsecurePort());
                multiple = true;
            }
            if (HttpBindManager.getInstance().isHttpsBindActive()) {
                if (multiple) {
                    builder.append(',');
                }
                builder.append(HttpBindManager.getInstance().getHttpBindSecurePort());
            }
        }
        
        return builder;
    }
    
    private static StringBuilder getSecure(StringBuilder builder) {
        if (JiveGlobals.getBooleanProperty(CROSS_DOMAIN_SECURE_ENABLED,CROSS_DOMAIN_SECURE_DEFAULT)) {
            builder.append("true");
        } else {
            builder.append("false");
        }
        return builder;
    }
}
