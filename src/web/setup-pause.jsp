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
                 org.jivesoftware.messenger.auth.UnauthorizedException" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<%  boolean showSidebar = false; %>

<%@ include file="setup-header.jspf" %>
<div align=center>

<p>
Please restart your server and close this window. Launch the admin using either the launcher or start as a service to enter the admin tool.
</p>

<a href="javascript:window.close();">Close Window</a>
</div>

<%@ include file="setup-footer.jsp" %>


