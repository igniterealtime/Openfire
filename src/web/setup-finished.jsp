<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.messenger.JiveGlobals,
                 java.util.Map,
                 java.util.Iterator,
                 org.jivesoftware.messenger.ConnectionManager,
                 org.jivesoftware.database.DbConnectionManager"
%>

<%
    boolean showSidebar = false;
    // First, update with XMPPSettings
    Map xmppSettings = (Map)session.getAttribute("xmppSettings");
    Iterator iter = xmppSettings.keySet().iterator();
    while(iter.hasNext()){
        String name = (String)iter.next();
        String value = (String)xmppSettings.get(name);
        JiveGlobals.setProperty(name, value);
    }
    // Shut down connection provider. Some connection providers (such as the
    // embedded provider) require a clean shut-down.
    DbConnectionManager.getConnectionProvider().destroy();    
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
<fmt:message key="title" /> Setup Complete!
</p>

<p>
This installation of <fmt:message key="title" /> is now complete.
To continue:
</p>

<ol>
    <li>
        Please <b style="font-size:1.2em;">restart</b> the server.
    </li>
    <li>
        <%
            String server = request.getServerName();
            String port = JiveGlobals.getProperty("adminConsole.port");
        %>
        <a href="http://<%= server %>:<%= port %>/index.jsp">Login to the admin console</a>.
    </li>
</ol>

<%@ include file="setup-footer.jsp" %>

