/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2004 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution.
 */

package org.jivesoftware.messenger;

import org.jivesoftware.messenger.spi.BasicServer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

public class XMPPBootServlet extends HttpServlet {

    private XMPPServer server;
    private Object serverLock = new Object();

    public void init(ServletConfig servletConfig) throws ServletException {
        if (server == null) {
            synchronized (serverLock) {
                if (server == null) {
                    server = new BasicServer();
                }
            }
        }
    }

    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException {
        // does nothing
    }
}
