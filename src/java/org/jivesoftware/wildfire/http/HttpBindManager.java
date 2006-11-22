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
package org.jivesoftware.wildfire.http;

import org.mortbay.jetty.servlet.ServletHolder;

import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public class HttpBindManager {
    private static HttpBindManager instance = new HttpBindManager();

    public static HttpBindManager getInstance() {
        return instance;
    }

    /**
     * Returns all the servlets that are part of the http-bind service.
     *
     * @return all the servlets that are part of the http-bind service.
     */
    public Map<ServletHolder, String> getServlets() {
        Map<ServletHolder, String> servlets = new HashMap<ServletHolder, String>();
        servlets.put(new ServletHolder(new HttpBindServlet(new HttpSessionManager())),
                "/http-bind/");

        return servlets;
    }
}
