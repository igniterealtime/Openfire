/**
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */
package org.jivesoftware.openfire.archive;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.Log;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.ParamUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 *
 */
public class ConversationPDFServlet extends HttpServlet {

    public void init() throws ServletException {

    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long conversationID = ParamUtils.getLongParameter(request, "conversationID", -1);
        if (conversationID == -1) {
            return;
        }

        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
            "monitoring");
        ConversationManager conversationManager = (ConversationManager)plugin.getModule(ConversationManager.class);
        Conversation conversation;
        if (conversationID > -1) {
            try {
                conversation = new Conversation(conversationManager, conversationID);

                ByteArrayOutputStream stream = new ConversationUtils().getConversationPDF(conversation);

                // setting some response headers
                response.setHeader("Expires", "0");
                response.setHeader("Cache-Control", "must-revalidate, post-check=0, pre-check=0");
                response.setHeader("Pragma", "public");
                // setting the content type
                response.setContentType("application/pdf");
                // the contentlength is needed for MSIE!!!
                response.setContentLength(stream.size());
                // write ByteArrayOutputStream to the ServletOutputStream
                ServletOutputStream out = response.getOutputStream();
                stream.writeTo(out);
                out.flush();
            }
            catch (NotFoundException nfe) {
                Log.error(nfe);
            }
        }

    }
}
