<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
  -
  - Copyright (C) 2004 Jive Software. All rights reserved.
  -
  - This software is published under the terms of the GNU Public License (GPL),
  - a copy of which is included in this distribution.
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.XMPPServerInfo,
                 java.util.Iterator,
                 org.jivesoftware.messenger.ServerPort,
                 org.jivesoftware.admin.AdminPageBean"
%>

<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>
<c:set var="admin" value="${admin.manager}" />

<%  // Title of this page and breadcrumbs
    String title = "Server Properties";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "main.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "server-props.jsp"));
    pageinfo.setPageID("server-props");
%>
<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<p>
Below is a list of information for this <fmt:message key="short.title" bundle="${lang}" /> server.
</p>


<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="3" align="center">General Information</td></tr>
<tr>
    <td class="jive-label">
        Server Version:
    </td>
    <td colspan="2">
        <fmt:message key="short.title" bundle="${lang}" /> Server <c:out value="${admin.serverInfo.version.versionString}" />
    </td>
</tr>
<tr>
    <td class="jive-label">
        JVM Version and Vendor:
    </td>
    <td colspan="2">
        <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Appserver (Admin Tool):
    </td>
    <td colspan="2">
        <%= application.getServerInfo() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Server Name:
    </td>
    <td>
      <c:out value="${admin.serverInfo.name}" />
    </td><td align=right nowrap>
        <a href="server-props-edit-form.jsp"
         title="Click to edit..."
         >Edit <img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
     </td>
</tr>
<c:if test="${!empty admin.multiUserChatServer}">
  <tr>
      <td class="jive-label">
          Multi User Chat Server Name:
      </td>
      <td>
        <c:out value="${admin.multiUserChatServer.serviceName}" />
      </td><td align=right nowrap>
        <a href="muc-server-props-edit-form.jsp"
         title="Click to edit..."
         >Edit <img src="images/edit-16x16.gif" width="17" height="17" border="0"></a>
     </td>
  </tr>
</c:if>
</table>

<c:forEach var="port" items="${admin.serverInfo.serverPorts}">
<br>
<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
  <tr class="tableHeaderBlue"><td colspan="3" align="center">Open Server Ports</td></tr>
    <tr valign="top">
        <td class="jive-label">
            IP and Port:
        </td>
        <td>
            <b><c:out value="${port.IPAddress}" />:<c:out value="${port.port}" /></b>
        </td>
    </tr>
    <tr valign="top">
        <td class="jive-label">
            Domain Name(s):
        </td>
        <td>
        <c:set var="sep" value="" />
         <c:forEach var="name" items="${port.domainNames}">
           <c:out value="${sep}" /><c:out value="${name}" />
           <c:set var="set" value=", " />
         </c:forEach>
        </td>
    </tr>
    <tr valign="top">
        <td class="jive-label">
            Security Type:
        </td>
        <td>
        <c:choose>
        <c:when test="${empty port.securityType}">

                NORMAL
        </c:when>
        <c:otherwise>
           <c:choose>
             <c:when test="${port.securityType == 'TLS'}">

                    TLS (SSL)
              </c:when>
              <c:otherwise>
                <c:out value="${port.securityType}" />
              </c:otherwise>
            </c:choose>
          </c:otherwise>
        </c:choose>
        </td>
    </tr>
    </table>
    </div>
</c:forEach>

<jsp:include page="bottom.jsp" flush="true" />
