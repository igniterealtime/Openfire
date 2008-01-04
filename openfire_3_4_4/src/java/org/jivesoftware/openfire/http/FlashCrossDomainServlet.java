/**
 * $RCSfile$
 * $Revision: $
 * $Date: $
 *
 * Copyright (C) 2006 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */
package org.jivesoftware.openfire.http;

import org.jivesoftware.openfire.XMPPServer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
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
            "<allow-access-from domain=\"*\" to-ports=\"";

    public static String CROSS_DOMAIN_END_TEXT = "\" /></cross-domain-policy>";

    @Override
    protected void doGet(HttpServletRequest httpServletRequest,
                         HttpServletResponse response) throws
            ServletException, IOException {
        StringBuilder builder = new StringBuilder();
        builder.append(CROSS_DOMAIN_TEXT +
                XMPPServer.getInstance().getConnectionManager().getClientListenerPort() +
                CROSS_DOMAIN_END_TEXT);
        builder.append("\n");
        response.setContentType("text/xml");
        response.getOutputStream().write(builder.toString().getBytes());
    }
}
