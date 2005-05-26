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
                 org.jivesoftware.admin.AdminPageBean,
                 org.jivesoftware.util.LocaleUtils"
    errorPage="error.jsp"
%>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<jsp:useBean id="pageinfo" scope="request" class="org.jivesoftware.admin.AdminPageBean" />
<%  // Title of this page and breadcrumbs
    String title = LocaleUtils.getLocalizedString("reg.settings.title");
    pageinfo.setTitle(title);
    pageinfo.getBreadcrumbs().add(new AdminPageBean.Breadcrumb(LocaleUtils.getLocalizedString("global.main"), "index.jsp"));
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
<fmt:message key="reg.settings.info" />
</p>

<form action="reg-settings.jsp">

<c:if test="success" >

    <div class="jive-success">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/success-16x16.gif" width="16" height="16" border="0"></td>
        <td class="jive-icon-label">
        <fmt:message key="reg.settings.update" />
        </td></tr>
    </tbody>
    </table>
    </div><br>

</c:if>

<fieldset>
    <legend><fmt:message key="reg.settings.inband_account" /></legend>
    <div>
    <p>
    <fmt:message key="reg.settings.inband_account_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
                <input type="radio" name="inbandEnabled" value="true" id="rb01"
                 <%= ((inbandEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb01"><b><fmt:message key="reg.settings.enable" /></b></label> - 
                <fmt:message key="reg.settings.auto_create_user" />
            </td>
        </tr>
        <tr>
            <td width="1%">
                <input type="radio" name="inbandEnabled" value="false" id="rb02"
                 <%= ((!inbandEnabled) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb02"><b><fmt:message key="reg.settings.disable" /></b></label> - <fmt:message key="reg.settings.not_auto_create" />
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<fieldset>
    <legend><fmt:message key="reg.settings.anonymous_login" /></legend>
    <div>
    <p>
    <fmt:message key="reg.settings.anonymous_login_info" />
    </p>
    <table cellpadding="3" cellspacing="0" border="0" width="100%">
    <tbody>
        <tr>
            <td width="1%">
            <input type="radio" name="anonLogin" value="true" id="rb03"
             <%= ((anonLogin) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb03"><b><fmt:message key="reg.settings.enable" /></b></label> - <fmt:message key="reg.settings.anyone_login" />
            </td>
        </tr>
        <tr>
            <td width="1%">
            <input type="radio" name="anonLogin" value="false" id="rb04"
             <%= ((!anonLogin) ? "checked" : "") %>>
            </td>
            <td width="99%">
                <label for="rb04"><b><fmt:message key="reg.settings.disable" /></b></label> - <fmt:message key="reg.settings.only_registered_login" />
            </td>
        </tr>
    </tbody>
    </table>
    </div>
</fieldset>

<br><br>

<input type="submit" name="save" value="<fmt:message key="global.save_settings" />">

</form>

<jsp:include page="bottom.jsp" flush="true" />
