<%@ taglib uri="core" prefix="c"%><%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 java.text.DateFormat,
                 org.jivesoftware.messenger.handler.IQAuthHandler" %>

<%-- Define Administration Bean --%>
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="Session Settings"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="session-settings.jsp" />
<c:set var="sbar" value="session" scope="page" />
<jsp:include page="top.jsp" flush="true" />

<c:set var="success" value="${param.success}" />


<%  // Get parameters
    boolean save = "true".equals(request.getParameter("save"));
    boolean success = "true".equals(request.getParameter("success"));
    boolean guestLogin = ParamUtils.getBooleanParameter(request,"guestLogin");

    IQAuthHandler authHandler = new IQAuthHandler();

    // Save properties if requested
    if (save) {
        authHandler.setAllowAnonymous(guestLogin);

        // Done so redirect
        response.sendRedirect("session-settings.jsp?success=true");
        return;
    }

    // Reset the page variables to their current state
    guestLogin = authHandler.isAllowAnonymous();
%>

<c:if test="${success}">
    <p class="jive-success-text">
    Settings saved successfully.
    </p>
</c:if>
<form action="session-settings.jsp" method="post">

<table cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeader"><td colspan="3" align="left">Anonymous Logins</td></tr>
<tr><td colspan=3 class="text">


    By default, anonymous logins are disabled. Enabling them would allow guests
    without accounts to join the server. To enable this, choose
    "enable" below.
   
</td></tr>
 <input type="hidden" name="save" value="true">
    <tr class="">
        <td width="1%">
            <input type="radio" name="guestLogin" value="true" id="rb01"
             <%= (guestLogin ? "checked" : "") %>>
        </td>
        <td>
            <label for="rb01">Enable anonymous logins</label>
        </td>
    </tr>
    <tr>
        <td>
            <input type="radio" name="guestLogin" value="false" id="rb02"
             <%= (!guestLogin ? "checked" : "") %>>
        </td>
        <td>
            <label for="rb02">Disable anonymous logins</label>
        </td>
    </tr>
    </table>
</ul>
</p>

<br>

<input type="submit" value="Save Settings">

</form>

<jsp:include page="bottom.jsp" flush="true" />