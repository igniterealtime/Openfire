<%@ page contentType="text/html; charset=UTF-8" %>
<%--
  -
  - Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

<%@ page import="org.jivesoftware.openfire.SessionManager,
                 org.jivesoftware.openfire.session.ComponentSession,
                 org.jivesoftware.util.JiveGlobals,
                 org.jivesoftware.util.StringUtils,
                 org.jivesoftware.util.ParamUtils"
    errorPage="error.jsp"
%>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TreeMap" %>
<%@ page import="org.slf4j.Logger" %>
<%@ page import="org.slf4j.LoggerFactory" %>
<%@ page import="org.jivesoftware.openfire.cluster.ClusterManager" %>
<%@ page import="org.jivesoftware.openfire.session.LocalSession" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%  // Get parameters
    String jid = ParamUtils.getParameter(request, "jid");

    // Handle a "go back" click:
    if (request.getParameter("back") != null) {
        response.sendRedirect("component-session-summary.jsp");
        return;
    }

    // Get the session & address objects
    SessionManager sessionManager = webManager.getSessionManager();
    ComponentSession componentSession = sessionManager.getComponentSession(jid);
    
    pageContext.setAttribute( "componentSession", componentSession );

    // Number dateFormatter for all numbers on this page:
    NumberFormat numFormatter = NumberFormat.getNumberInstance();
    final Logger Log = LoggerFactory.getLogger("component-session-details.jsp");

    pageContext.setAttribute("clusteringEnabled", ClusterManager.isClusteringStarted() || ClusterManager.isClusteringStarting() );
%>

<html>
    <head>
        <title><fmt:message key="component.session.details.title"/></title>
        <meta name="pageID" content="component-session-summary"/>
    </head>
    <body>

<p>
<fmt:message key="component.session.details.info">
    <fmt:param value="<b>${fn:escapeXml(jid)}</b>" />
</fmt:message>

</p>

<div class="jive-table">
<table cellpadding="0" cellspacing="0" border="0" width="100%">
<thead>
    <tr>
        <th colspan="2">
            <fmt:message key="component.session.details.title" />
        </th>
    </tr>
</thead>
<tbody>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.domain" />
        </td>
        <td>
            <c:out value="${componentSession.address}"/>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.name" />
        </td>
        <td>
            <c:out value="${componentSession.externalComponent.name}"/>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.category" />:
        </td>
        <td>
            <c:out value="${componentSession.externalComponent.category}"/>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.type" />:
        </td>
        <td>
            <c:if test="${componentSession.externalComponent.category eq 'gateway'}">
                <c:choose>
                    <c:when test="${componentSession.externalComponent.type eq 'msn'}">
                        <img src="images/msn.gif" width="16" height="16" border="0" alt="MSN">&nbsp;
                    </c:when>
                    <c:when test="${componentSession.externalComponent.type eq 'aim'}">
                        <img src="images/aim.gif" width="16" height="16" border="0" alt="AIM">&nbsp;
                    </c:when>
                    <c:when test="${componentSession.externalComponent.type eq 'yahoo'}">
                        <img src="images/yahoo.gif" width="22" height="16" border="0" alt="Yahoo!">&nbsp;
                    </c:when>
                    <c:when test="${componentSession.externalComponent.type eq 'icq'}">
                        <img src="images/icq.gif" width="16" height="16" border="0" alt="ICQ">&nbsp;
                    </c:when>
                </c:choose>
            </c:if>
            <c:out value="${componentSession.externalComponent.type}"/>
        </td>
    </tr>
    <c:if test="${clusteringEnabled}">
        <td class="c1">
            <fmt:message key="component.session.label.node" />
        </td>
        <td>
            <% if (componentSession instanceof LocalSession) { %>
            <fmt:message key="component.session.local" />
            <% } else { %>
            <fmt:message key="component.session.remote" />
            <% } %>
        </td>
    </c:if>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.creation" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(componentSession.getCreationDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="component.session.label.last_active" />
        </td>
        <td>
            <%= JiveGlobals.formatDateTime(componentSession.getLastActiveDate()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.statistics" />
        </td>
        <td>
            <fmt:message key="session.details.received" />
            <%= numFormatter.format(componentSession.getNumClientPackets()) %>/<%= numFormatter.format(componentSession.getNumServerPackets()) %>
        </td>
    </tr>
    <tr>
        <td class="c1">
            <fmt:message key="session.details.hostname" />
        </td>
        <td>
            <% try { %>
                <%= StringUtils.escapeHTMLTags(componentSession.getHostAddress()) %>
                /
                <%= StringUtils.escapeHTMLTags(componentSession.getHostName()) %>
            <% } catch (java.net.UnknownHostException e) { %>
                Invalid session/connection
            <% } %>
        </td>
    </tr>
</tbody>
</table>
</div>
<br>
    <%  // Show Software Version if there is :
       try {
        if (!componentSession.getSoftwareVersion().isEmpty()) {
    %>
        <div class="jive-table">
            <table cellpadding="3" cellspacing="1" border="0" width="100%">
                <thead>
                    <tr>
                        <th colspan="2">
                            <fmt:message key="session.details.software_version"/>
                        </th>
                    </tr>
                </thead>
                <tbody>
                    <% 
                        Map<String, String> treeMap = new TreeMap<String, String>(componentSession.getSoftwareVersion());
                        for (Map.Entry<String, String> entry : treeMap.entrySet()){ %>
                            <tr>
                                <td class="c1">
                                    <%= StringUtils.escapeHTMLTags(entry.getKey().substring(0, 1).toUpperCase()+""+entry.getKey().substring(1)) %>:
                                </td>
                                <td>
                                    <%= StringUtils.escapeHTMLTags(entry.getValue())%>
                                </td>
                            </tr>
                        <% 
                        }
                    %>
                </tbody>
            </table>
        </div>
    <%  } 
    } catch (Exception e) { 
       Log.error(e.getMessage(), e);%>
        Invalid session/connection
    <%} %>
<br>
<form action="component-session-details.jsp">
<center>
<input type="submit" name="back" value="<fmt:message key="session.details.back_button" />">
</center>
</form>

    </body>
</html>
