<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.container.Container,
                 org.jivesoftware.messenger.container.ServiceLookup,
                 org.jivesoftware.messenger.container.ServiceLookupFactory,
                 org.jivesoftware.messenger.auth.UnauthorizedException"
%>

<%  boolean showSidebar = false; %>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
<fmt:message key="title" bundle="${lang}" /> Setup Complete!
</p>

<p>
This installation of <fmt:message key="title" bundle="${lang}" /> is now complete. Please close this window and
restart your server.
Launch the admin using either the launcher or start as a service to enter the admin tool.

</p>

<center><input type="button" value="Exit Setup" onClick="javascript:window.close();"></a>


<%@ include file="setup-footer.jsp" %>

