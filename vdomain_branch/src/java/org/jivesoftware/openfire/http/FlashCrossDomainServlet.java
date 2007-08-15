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

    @Override
    protected void doGet(HttpServletRequest httpServletRequest,
                         HttpServletResponse response) throws
            ServletException, IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\"?>\n" +
                "<!DOCTYPE cross-domain-policy " +
                "SYSTEM \"http://www.macromedia.com/xml/dtds/cross-domain-policy.dtd\">" +
                "<cross-domain-policy><allow-access-from domain=\"*\" /></cross-domain-policy>");
        response.setContentType("text/xml");
        response.getOutputStream().write(builder.toString().getBytes());
    }
}
