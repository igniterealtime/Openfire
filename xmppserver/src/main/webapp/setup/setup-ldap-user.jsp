<%--
  -
  - Copyright (C) 2006-2007 Jive Software, 2017-2019 Ignite Realtime Foundation. All rights reserved.
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
    boolean initialSetup = true;
    String currentPage = "setup-ldap-user.jsp";
    String testPage = "setup-ldap-user_test.jsp";
    String nextPage =  "setup-ldap-group.jsp";
    Map<String, String> meta = new HashMap<>();
    meta.put("currentStep", "3");

    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>
<%@ include file="ldap-user.jspf" %>
