<%@ taglib uri="core" prefix="c"%>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 org.jivesoftware.messenger.XMPPServerInfo,
                 java.util.Iterator,
                 org.jivesoftware.messenger.ServerPort"
%>
<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>
<c:set var="admin" value="${admin.manager}" />

<!-- Define Title and BreadCrumbs -->
<c:set var="title" value="Server Properties"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="server-props.jsp" />
<%@ include file="top.jsp" %>



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
      <td colspan=2>
        <c:out value="${admin.multiUserChatServer.serviceName}" />
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

<%@ include file="bottom.jsp" %>
