<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.HashMap,
                 java.util.Map,
                 org.jivesoftware.messenger.*,
                 org.jivesoftware.messenger.user.*,
                 java.util.*,
                 java.text.*,
                 org.jivesoftware.messenger.chat.*"
%>
<%@ include file="global.jsp" %>
<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>


<!-- Define BreadCrumbs -->
<c:set var="title" value="Jive Messenger Admin"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="index.jsp" />
<%@ include file="top.jsp" %>

  
<p>Welcome to the Jive Messenger Admin tool.</p>

<table class="box" cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeaderBlue"><td colspan="2" align="center"><fmt:message key="title" bundle="${lang}" /> Information</td></tr>
<tr><td colspan="2">
<tr>  
    <td class="jive-label">   
        Version:
    </td>
    <td>
        <fmt:message key="title" bundle="${lang}" /> <%= admin.getXMPPServer().getServerInfo().getVersion().getVersionString() %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        JVM Version and Vendor:
    </td>
    <td>
        <%= System.getProperty("java.version") %> <%= System.getProperty("java.vendor") %>
    </td>
</tr>
<tr>
    <td class="jive-label">
        Appserver:
    </td>
    <td>
        <%= application.getServerInfo() %>
    </td>
</tr>
</table>
</div>





<%@ include file="bottom.jsp" %>




