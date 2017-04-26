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

    @Override
    public void contextInitialized(ServletContextEvent event) {
        if (XMPPServer.getInstance() != null) {
            // Running in standalone mode so do nothing
            return;
        }
        XMPPServer server = new XMPPServer();
        event.getServletContext().setAttribute(XMPP_KEY, server);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        XMPPServer server = (XMPPServer) event.getServletContext().getAttribute(XMPP_KEY);
        if (null != server) {
            server.stop();
        }
    }

}
