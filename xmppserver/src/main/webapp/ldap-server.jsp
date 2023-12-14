<%--
  -
  - Copyright (C) 2006 Jive Software, 2017-2022 Ignite Realtime Foundation. All rights reserved.
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
<%
    String serverType = null;
    boolean initialSetup = false;
    String currentPage = "ldap-server.jsp";
    String testPage = "setup/setup-ldap-server_test.jsp";
    String nextPage = "ldap-user.jsp";
    Map<String, String> meta = new HashMap<>();
    meta.put("pageID", "profile-settings");

    pageContext.setAttribute( "serverType", serverType );
    pageContext.setAttribute( "initialSetup", initialSetup );
    pageContext.setAttribute( "currentPage", currentPage );
    pageContext.setAttribute( "testPage", testPage );
    pageContext.setAttribute( "nextPage", nextPage );
    pageContext.setAttribute( "meta", meta );
%>

<style title="setupStyle" media="screen">
    @import "style/ldap.css";
</style>

<script src="js/setup.js"></script>
                        
<%@ include file="setup/ldap-server.jspf" %>
