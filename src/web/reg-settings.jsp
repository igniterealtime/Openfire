<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.ParamUtils,
                 org.jivesoftware.messenger.handler.IQRegisterHandler,
                 org.jivesoftware.messenger.handler.IQAuthHandler"
%>
   
<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="Registration and Login Settings"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="reg-settings.jsp" />
<%@ include file="top.jsp" %>

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

Use the forms below to change various aspects of user registration and login.

<form action="reg-settings.jsp">
<c:if test="${success}" > 
  <p class="jive-success-text">
    Settings updated.
  </p>
</c:if>
<table  cellpadding="3" cellspacing="0" border="0" width="600">
<tr class="tableHeader"><td colspan="2" align="left">Inband Account Registration</td></tr>
<tr><td colspan="2" class="text">
    Inband account registration allows users to create accounts on the server automatically using most
    clients. It does not affect the ability to create new accounts through this web administration
    interface. Administrators may want to disable this option so users are required to register by
    other means (e.g. sending requests to the server administrator or through your own custom web
    interface).
</td></tr>
    <tr valign="top" class="">
        <td width="1%" nowrap>
            <input type="radio" name="inbandEnabled" value="true" id="rb01"
             <%= ((inbandEnabled) ? "checked" : "") %>>
        </td>
        <td width="99%">
            <label for="rb01"><b>Enabled</b></label> - Users can automatically create new accounts.
        </td>
    </tr>
        <td width="1%" nowrap>
            <input type="radio" name="inbandEnabled" value="false" id="rb02"
             <%= ((!inbandEnabled) ? "checked" : "") %>>
        </td>
        <td width="99%">
            <label for="rb02"><b>Disabled</b></label> - Users can not automatically create new
            accounts.
        </td>
    </tr>
    </table>

</ul>  
<br>
<table  cellpadding="3" cellspacing="0" border="0" width="600">
<tr class="tableHeader"><td colspan="2" align="left">Anonymous Login</td></tr>
<tr><td class="text" colspan="2">
    You can choose to enable or disable anonymous user login. If it is enabled, anyone can
    connect to the server and create a new session. If it is disabled only users who have
    accounts will be able to connect.
</td></tr>
    <tr valign="top" class="">
        <td width="1%" nowrap>
            <input type="radio" name="anonLogin" value="true" id="rb03"
             <%= ((anonLogin) ? "checked" : "") %>>
        </td>
        <td width="99%">
            <label for="rb03"><b>Enabled</b></label> - Anyone may login to the server.
        </td>
    </tr>
        <td width="1%" nowrap>
            <input type="radio" name="anonLogin" value="false" id="rb04"
             <%= ((!anonLogin) ? "checked" : "") %>>
        </td>
        <td width="99%">
            <label for="rb04"><b>Disabled</b></label> - Only registered users may login.
        </td>
    </tr>
    </table>

</ul>

<br>

<input type="submit" name="save" value="Save Settings">

</form>

<%@ include file="bottom.jsp" %>
