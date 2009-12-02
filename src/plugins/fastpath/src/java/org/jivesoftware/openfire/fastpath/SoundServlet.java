/**
 * $RCSfile: ,v $
 * $Revision: 1.0 $
 * $Date: 2005/05/25 04:20:03 $
 *
 * Copyright (C) 1999-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.fastpath;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.StringUtils;
import org.jivesoftware.xmpp.workgroup.Workgroup;
import org.jivesoftware.xmpp.workgroup.WorkgroupManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.packet.JID;

/**
 * Servlet that writes out sound files.
 */
public class SoundServlet extends HttpServlet {

	private static final Logger Log = LoggerFactory.getLogger(SoundServlet.class);
	
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
           Log.error(e.getMessage(), e);
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
            Log.error(e.getMessage(), e);
        }
    }
}