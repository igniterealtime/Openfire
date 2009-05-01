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
package org.jivesoftware.openfire.http;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.util.JiveGlobals;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Serves up the flash cross domain xml file which allows other domains to access http-binding
 * using flash.
 *
 * @author Alexander Wenckus
 */
public class FlashCrossDomainServlet extends HttpServlet {

    public static String CROSS_DOMAIN_TEXT = "<?xml version=\"1.0\"?>" +
            "<!DOCTYPE cross-domain-policy SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">" +
            "<cross-domain-policy>" +
            "<site-control permitted-cross-domain-policies=\"all\"/>" +
            "<allow-access-from domain=\"*\" to-ports=\"";

    public static String CROSS_DOMAIN_MIDDLE_TEXT = "\" secure=\"";
    public static String CROSS_DOMAIN_END_TEXT = "\"/></cross-domain-policy>";
    
    private static String CROSS_DOMAIN_SECURE_ENABLED = "httpbind.crossdomain.secure";
    private static boolean CROSS_DOMAIN_SECURE_DEFAULT = true;
    
    @Override
    protected void doGet(HttpServletRequest httpServletRequest,
                         HttpServletResponse response) throws
            ServletException, IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(CROSS_DOMAIN_TEXT);
        getPortList(builder);
        builder.append(CROSS_DOMAIN_MIDDLE_TEXT);
        getSecure(builder);
        builder.append(CROSS_DOMAIN_END_TEXT);
        builder.append("\n");
        response.setContentType("text/xml");
        response.getOutputStream().write(builder.toString().getBytes());
    }
    
    private StringBuilder getPortList(StringBuilder builder) {
        boolean multiple = false;
        if(XMPPServer.getInstance().getConnectionManager().getClientListenerPort() > 0) {
            builder.append(XMPPServer.getInstance().getConnectionManager().getClientListenerPort());
            multiple = true;
        }
        if(XMPPServer.getInstance().getConnectionManager().getClientSSLListenerPort() > 0) {
            if(multiple) {
                builder.append(",");
            }
            builder.append(XMPPServer.getInstance().getConnectionManager().getClientSSLListenerPort());
            multiple = true;
        }
        
        if(HttpBindManager.getInstance().isHttpBindEnabled()) {
            // ports for http-binding may not be strictly needed in here, but it doesn't hurt
            if(HttpBindManager.getInstance().getHttpBindUnsecurePort() > 0) {
                if(multiple) {
                    builder.append(",");
                }
                builder.append(HttpBindManager.getInstance().getHttpBindUnsecurePort());
                multiple = true;
            }
            if (HttpBindManager.getInstance().isHttpsBindActive()) {
                if (multiple) {
                    builder.append(",");
                }
                builder.append(HttpBindManager.getInstance().getHttpBindSecurePort());
            }
        }
        
        return builder;
    }
    
    private StringBuilder getSecure(StringBuilder builder) {
        if (JiveGlobals.getBooleanProperty(CROSS_DOMAIN_SECURE_ENABLED,CROSS_DOMAIN_SECURE_DEFAULT)) {
            builder.append("true");
        } else {
            builder.append("false");
        }
        return builder;
    }
}
