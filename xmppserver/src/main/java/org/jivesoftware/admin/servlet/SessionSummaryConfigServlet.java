/*
 * Copyright (C) 2025 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.admin.servlet;

import org.jivesoftware.openfire.cluster.ClusterManager;
import org.jivesoftware.util.*;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for the admin console page that configures what columns are visible on the 'client sessions' page.
 *
 * @author Guus der Kinderen, guus@goodbytes.nl
 */
@WebServlet(value = "/session-summary-config.jsp")
public class SessionSummaryConfigServlet extends HttpServlet
{
    protected void setStandardAttributes(final HttpServletRequest request, final HttpServletResponse response, final WebManager webManager)
    {
        final String csrf = StringUtils.randomString(16);
        CookieUtils.setCookie(request, response, "csrf", csrf, -1);
        request.setAttribute("csrf", csrf);

        request.setAttribute("clusteringEnabled", ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting() );

        request.setAttribute("showName", webManager.getPageProperty("session-summary", "console.showNameColumn", 1) == 1);
        request.setAttribute("showResource", webManager.getPageProperty("session-summary", "console.showResourceColumn", 0) == 1);
        request.setAttribute("showVersion", webManager.getPageProperty("session-summary", "console.showVersionColumn", 1) == 1);
        request.setAttribute("showClusterNode", webManager.getPageProperty("session-summary", "console.showClusterNodeColumn", 1) == 1);
        request.setAttribute("showStatus", webManager.getPageProperty("session-summary", "console.showStatusColumn", 1) == 1);
        request.setAttribute("showPresence", webManager.getPageProperty("session-summary", "console.showPresenceColumn", 1) == 1);
        request.setAttribute("showRxTx", webManager.getPageProperty("session-summary", "console.showRxTxColumn", 1) == 1);
        request.setAttribute("showIp", webManager.getPageProperty("session-summary", "console.ShowIpColumn", 1) == 1);
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init(request, response, session, session.getServletContext());

        setStandardAttributes(request, response, webManager);

        request.getRequestDispatcher("session-summary-config-page.jsp").forward(request, response);
    }

    @Override
    protected void doPost(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
    {
        final HttpSession session = request.getSession();
        final WebManager webManager = new WebManager();
        webManager.init(request, response, session, session.getServletContext());

        final Map<String, Object> errors = new HashMap<>();

        final Cookie csrfCookie = CookieUtils.getCookie(request, "csrf");
        if (csrfCookie == null || !csrfCookie.getValue().equals(request.getParameter("csrf"))) {
            errors.put("csrf", null);
        } else {
            // Checkboxes that are unchecked do not register as a parameter. Thus, if the parameter is absent, then the user unchecked the checkbox!
            final boolean showName = ParamUtils.getBooleanParameter(request, "showName", false);
            final boolean showResource = ParamUtils.getBooleanParameter(request, "showResource", false);
            final boolean showVersion = ParamUtils.getBooleanParameter(request, "showVersion", false);
            final boolean showClusterNode = ParamUtils.getBooleanParameter(request, "showClusterNode", false);
            final boolean showStatus = ParamUtils.getBooleanParameter(request, "showStatus", false);
            final boolean showPresence = ParamUtils.getBooleanParameter(request, "showPresence", false);
            final boolean showRxTx = ParamUtils.getBooleanParameter(request, "showRxTx", false);
            final boolean showIp = ParamUtils.getBooleanParameter(request, "showIp", false);

            webManager.setPageProperty("session-summary", "console.showNameColumn", showName ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showResourceColumn", showResource ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showVersionColumn", showVersion ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showClusterNodeColumn", showClusterNode ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showStatusColumn", showStatus ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showPresenceColumn", showPresence ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showRxTxColumn", showRxTx ? 1 : 0);
            webManager.setPageProperty("session-summary", "console.showIpColumn", showIp ? 1 : 0);
        }

        request.setAttribute("errors", errors);
        setStandardAttributes(request, response, webManager);
        request.getRequestDispatcher("session-summary.jsp").forward(request, response);
    }
}
