/*
 * Copyright (C) 2008 Jive Software. All rights reserved.
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
package org.jivesoftware.openfire.archive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.MonitoringPlugin;
import org.jivesoftware.util.NotFoundException;
import org.jivesoftware.util.ParamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConversationPDFServlet extends HttpServlet {

	private static final Logger Log = LoggerFactory.getLogger(ConversationPDFServlet.class);
	
    @Override
	public void init() throws ServletException {

    }

    @Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long conversationID = ParamUtils.getLongParameter(request, "conversationID", -1);
        if (conversationID == -1) {
            return;
        }

        MonitoringPlugin plugin = (MonitoringPlugin)XMPPServer.getInstance().getPluginManager().getPlugin(
        		MonitoringConstants.NAME);
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
                // the content length is needed for MSIE!!!
                response.setContentLength(stream.size());
                // write ByteArrayOutputStream to the ServletOutputStream
                ServletOutputStream out = response.getOutputStream();
                stream.writeTo(out);
                out.flush();
            }
            catch (NotFoundException nfe) {
                Log.error(nfe.getMessage(), nfe);
            }
        }

    }
}
