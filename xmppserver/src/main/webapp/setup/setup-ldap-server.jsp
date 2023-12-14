<%--
  -
  - Copyright (C) 2006-2007 Jive Software, 2018-2019 Ignite Realtime Foundation. All rights reserved.
  -
  - Licensed under the Apache License, Version 2.0 (the "License");
  - you may not use this file except in compliance with the License.
  - You may obtain a copy of the License at
  -
  -     http://www.apache.org/licenses/LICENSE-2.0
  -
  - Unless required by applicable law or agreed to in writing, software
  - distributed under the License is distributed on an "AS IS" BASIS,
  - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  - See the License for the specific language governing permissions and
  - limitations under the License.
--%>
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%
    // Redirect if we've already run setup:
    if (!XMPPServer.getInstance().isSetupMode()) {
        response.sendRedirect("setup-completed.jsp");
        return;
    }
%>

<%
    String serverType = "";
    if (request.getParameter("save") != null || request.getParameter("test") != null) {
        serverType = ParamUtils.getStringParameter(request, "serverType", "");
        // Sanitise the serverType
        switch (serverType) {
            case "activedirectory":
            case "openldap":
            case "other":
                break;
            default:
                serverType = "";
        }
    }

    boolean initialSetup = true;
    String currentPage = "setup-ldap-server.jsp";
    String testPage = "setup-ldap-server_test.jsp?serverType="+ serverType;
    String nextPage = "setup-ldap-user.jsp?serverType=" + serverType;
    Map<String, String> meta = new HashMap<String, String>();
    meta.put("currentStep", "3");

    pageContext.setAttribute( "serverType", serverType );
    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>
<%@ include file="ldap-server.jspf" %>
