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

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.handler.IQRegisterHandler,
                 org.jivesoftware.messenger.handler.IQAuthHandler,
                 org.jivesoftware.admin.AdminPageBean"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>

<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = "Registration Settings";
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb("Main", "index.jsp"));
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(title, "reg-settings.jsp"));
    pageinfo.setPageID("server-reg-and-login");
%>

<jsp:include page="top.jsp" flush="true" />
<jsp:include page="title.jsp" flush="true" />

<c:set var="success" value="false" />

<%  // Get parameters
    boolean save = request.getParameter("save") != null;
    boolean inbandEnabled = ParamUtils.getBooleanParameter(request,"inbandEnabled");
    boolean anonLogin = ParamUtils.getBooleanParameter(request,"anonLogin");

    // Get an IQRegisterHandler:
    IQRegisterHandler regHandler = new IQRegisterHandler();
    IQAuthHandler authHandler = new IQAuthHandler();

    if (save) {
        regHandler.setInbandRegEnabled(inbandEnabled);
        authHandler.setAllowAnonymous(anonLogin);
%>
<c:set var="success" value="true" />
<%
    }

    // Reset the value of page vars:
    inbandEnabled = regHandler.isInbandRegEnabled();
    anonLogin = authHandler.isAllowAnonymous();
%>

<p>
Use the forms below to change various aspects of user registration and login.
</p>

<form action="reg-settings.jsp">

<c:if test="success" >

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        Settings updated successfully.
        </td></tr>
    </tbody>
    </table>
    </div><br>

</c:if>

<fieldset>
    <legend>Inband Account Registration</legend>
    <div>
    <p>
    Inband account registration allows users to create accounts on the server automatically using most
    clients. It does not affect the ability to create new accounts through this web administration
    interface. Administrators may want to disable this option so users are required to register by
    other means (e.g. sending requests to the server administrator or through your own custom web
    interface).
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
                <input type="radio" name="inbandEnabled" value="true" id="rb01"
                 <%= ((inbandEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b>Enabled</b></label> - Users can automatically create new accounts.
            </td>
        </tr>
        <tr>
            <td width="1%">
                <input type="radio" name="inbandEnabled" value="false" id="rb02"
                 <%= ((!inbandEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b>Disabled</b></label> - Users can not automatically create new accounts.
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend>Anonymous Login</legend>
    <div>
    <p>
    You can choose to enable or disable anonymous user login. If it is enabled, anyone can
    connect to the server and create a new session. If it is disabled only users who have
    accounts will be able to connect.
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="anonLogin" value="true" id="rb03"
             <%= ((anonLogin) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03"><b>Enabled</b></label> - Anyone may login to the server.
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="anonLogin" value="false" id="rb04"
             <%= ((!anonLogin) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb04"><b>Disabled</b></label> - Only registered users may login.
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />
