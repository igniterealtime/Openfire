/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 1999-2003 Jive Software. All rights reserved.
 *
 * This software is the proprietary information of Jive Software. Use is subject to license terms.
 */
package org.jivesoftware.messenger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;

public class XMPPBootServlet extends HttpServlet {

    private XMPPBootContainer container;
    private Object containerLock = new Object();

    public void init(ServletConfig servletConfig) throws ServletException {
        if (container == null) {
            synchronized (containerLock) {
                if (container == null) {
                    container = new XMPPBootContainer();
                }
            }
        }
    }

    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException {
        // does nothing
    }
}
