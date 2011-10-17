<%@ page import="org.jivesoftware.openfire.session.ComponentSession" %>
<%@ page import="java.util.Collection" %>
<%@ page import="org.jivesoftware.openfire.SessionManager" %>
<%@ page import="org.jivesoftware.util.JiveGlobals" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>

<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"/>
<%
    webManager.init(request, response, session, application, out);
    boolean save = request.getParameter("save") != null;
    boolean success = request.getParameter("success") != null;
    boolean persistentRoster = ParamUtils.getBooleanAttribute(request, "persistentEnabled");
    String[] componentsEnabled = request.getParameterValues("enabledComponents[]");

    Map<String,String> errors = new HashMap<String,String>();
    if (save) {
        for (String property : JiveGlobals.getPropertyNames("plugin.remoteroster.jids")) {
            JiveGlobals.deleteProperty(property);
        }
        if (componentsEnabled != null) {
            for (int i = 0; i < componentsEnabled.length; i++) {
                JiveGlobals.setProperty("plugin.remoteroster.jids."+componentsEnabled[i], "true");
            }
        }
        JiveGlobals.setProperty("plugin.remoteroster.persistent", (persistentRoster ? "true" : "false"));
        response.sendRedirect("rr-main.jsp?success=true");
        return;
    }

    // Get the session manager
    SessionManager sessionManager = webManager.getSessionManager();

    Collection<ComponentSession> sessions = sessionManager.getComponentSessions();

%>

<html>
<head>
    <title>
        <fmt:message key="rr.summary.title"/>
    </title>
    <meta name="pageID" content="remoteRoster"/>
    <meta name="helpPage" content=""/>


</head>
<body>

<p>
    Any components configured here will allow the external component associated with them full control over
    their domain within any user's roster.  Before enabling Remote Roster Management support for an external
    component, first connect it like you would any external component.  Once it has connected and registered
    with Openfire, it's JID should show up below and you can enable Remote Roster support.
</p>

<%  if (success) { %>

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            Settings saved!
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } else if (errors.size() > 0) { %>

    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
            Error saving settings!
        </td></tr>
    </tbody>
    </table>
    </div><br>

<%  } %>

<form action="rr-main.jsp?save" method="post">

<div class="jive-contentBoxHeader">Connected Gateway Components</div>
<div class="jive-contentBox">

<p>Select which components you want to enable remote roster on:</p>
<table>
<%
    boolean gatewayFound = false;
    for (ComponentSession componentSession : sessions) {
        if (!componentSession.getExternalComponent().getCategory().equals("gateway")) { continue; }
        gatewayFound = true;
%>
<tr>
    <td align="center"><input type="checkbox" name="enabledComponents[]" value="<%= componentSession.getExternalComponent().getInitialSubdomain() %>" <%= JiveGlobals.getBooleanProperty("plugin.remoteroster.jids."+componentSession.getExternalComponent().getInitialSubdomain(), false) ? "checked=\"checked\"" : "" %> /></td>
    <td align="left"><%= componentSession.getExternalComponent().getName() %></td>
    <td align="left"><%= componentSession.getExternalComponent().getInitialSubdomain() %></td>
</tr>
<%
    }
%>
</table>
<%
        if (!gatewayFound) {
%>
<span style="font-weight: bold">No connected external gateway components found.</span>
<%
    }
%>
</div>

<div class="jive-contentBoxHeader">Options</div>
<div class="jive-contentBox">
   <table cellpadding="3" cellspacing="0" border="0" width="100%">
   <tbody>
   <tr valign="top">
       <td width="1%" nowrap class="c1">
           Persistent Roster:
       </td>
       <td width="99%">
           <table cellpadding="0" cellspacing="0" border="0">
           <tbody>
               <tr>
                   <td>
                       <input type="radio" name="persistentEnabled" value="true" checked id="PER01">
                   </td>
                   <td><label for="PER01">Enabled (remote rosters are saved into the user's stored roster)</label></td>
               </tr>
               <tr>
                   <td>
                       <input type="radio" name="persistentEnabled" value="false" id="PER02">
                   </td>
                   <td><label for="PER02">Disabled (remote rosters exist only in memory)</label></td>
               </tr>
           </tbody>
           </table>
       </td>
   </tr>
   </tbody>
   </table>
</div>

<input type="submit" name="save" value="Save Settings" />

</form>

</body>
</html>
