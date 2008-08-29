/**
 * $Revision: 1727 $
 * $Date: 2005-07-29 19:55:59 -0300 (Fri, 29 Jul 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * An XMPPContextListener starts an XMPPServer when a ServletContext is initialized and stops
 * the xmpp server when the servlet context is destroyed.
 *
 * @author evrim ulu
 * @author Gaston Dombiak
 */
public class XMPPContextListener implements ServletContextListener {

    protected String XMPP_KEY = "XMPP_SERVER";

    public void contextInitialized(ServletContextEvent event) {
        if (XMPPServer.getInstance() != null) {
            // Running in standalone mode so do nothing
            return;
        }
        XMPPServer server = new XMPPServer();
        event.getServletContext().setAttribute(XMPP_KEY, server);
    }

    public void contextDestroyed(ServletContextEvent event) {
        XMPPServer server = (XMPPServer) event.getServletContext().getAttribute(XMPP_KEY);
        if (null != server) {
            server.stop();
        }
    }

}
