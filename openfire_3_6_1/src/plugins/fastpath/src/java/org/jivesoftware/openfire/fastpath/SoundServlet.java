/**
 * $RCSfile: ,v $
 * $Revision: 1.0 $
 * $Date: 2005/05/25 04:20:03 $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.openfire.fastpath;

import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.xmpp.packet.JID;
import org.xmpp.component.ComponentManagerFactory;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.openfire.user.UserNotFoundException;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet that writes out sound files.
 */
public class SoundServlet extends HttpServlet {

    public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        doGet(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        String workgroupName = request.getParameter("workgroup");
        String action = request.getParameter("action");

        Workgroup workgroup = null;
        try {
            workgroup = WorkgroupManager.getInstance().getWorkgroup(new JID(workgroupName));
        }
        catch (UserNotFoundException e) {
           ComponentManagerFactory.getComponentManager().getLog().error(e);
        }

        try {
            response.setContentType("audio/wav");
            if (action != null) {
                if ("incoming".equals(action.trim())) {
                    String incomingMessage = workgroup.getProperties().getProperty("incomingSound");
                    byte[] incomingBytes = StringUtils.decodeBase64(incomingMessage);
                    response.getOutputStream().write(incomingBytes);
                }
                else if ("outgoing".equals(action.trim())) {
                    String outgoingMessage = workgroup.getProperties().getProperty("outgoingSound");
                    String outgoingBytes = StringUtils.encodeBase64(outgoingMessage);
                    response.getOutputStream().write(outgoingBytes.getBytes("UTF-8"));
                }
            }
        }
        catch (Exception e) {
            ComponentManagerFactory.getComponentManager().getLog().error(e);
        }
    }
}