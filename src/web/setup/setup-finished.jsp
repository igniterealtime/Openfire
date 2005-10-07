<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision: 1644 $
  -	$Date: 2005-07-19 09:05:10 -0700 (Tue, 19 Jul 2005) $
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.auth.UnauthorizedException,
                 org.jivesoftware.util.JiveGlobals,
                 java.util.Map,
                 java.util.Iterator,
                 org.jivesoftware.messenger.ConnectionManager,
                 org.jivesoftware.database.DbConnectionManager,
                 org.jivesoftware.messenger.XMPPServer"
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
    Map xmlSettings = (Map)session.getAttribute("xmlSettings");
    iter = xmlSettings.keySet().iterator();
    while(iter.hasNext()){
        String name = (String)iter.next();
        String value = (String)xmlSettings.get(name);
        JiveGlobals.setXMLProperty(name, value);
    }
    // Shut down connection provider. Some connection providers (such as the
    // embedded provider) require a clean shut-down.
    DbConnectionManager.getConnectionProvider().destroy();    
%>

<%@ include file="setup-header.jspf" %>

<p class="jive-setup-page-header">
<fmt:message key="title" /> <fmt:message key="setup.finished.title" />
</p>

<p>
<fmt:message key="setup.finished.info">
    <fmt:param value="<%= LocaleUtils.getLocalizedString("title") %>" />
</fmt:message>
</p>

<ol>
    <li>
        <fmt:message key="setup.finished.restart" /> <b style="font-size:1.2em;"><fmt:message key="global.restart" /></b> <fmt:message key="setup.finished.restart2" />
    </li>
    <li>
        <%
            String url = null;
            if (XMPPServer.getInstance().isStandAlone()) {
                String server = request.getServerName();
                String port = JiveGlobals.getXMLProperty("adminConsole.port");
                url = "http://" + server + ":" + port + "/login.jsp?username=admin";
            }
            else {
                url = request.getRequestURL().toString();
                url = url.replace("setup-finished.jsp", "login.jsp?username=admin");
            }
        %>
            <a href="<%= url %>"><fmt:message key="setup.finished.login" /></a>.
    </li>
</ol>

<%@ include file="setup-footer.jsp" %>

