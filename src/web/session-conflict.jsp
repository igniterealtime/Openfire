<%@ taglib uri="core" prefix="c" %>
<%@ taglib uri="fmt" prefix="fmt" %>
<%--
  -	$RCSfile$
  -	$Revision$
  -	$Date$
--%>

<%@ page import="org.jivesoftware.util.*,
                 java.util.Iterator,
                 org.jivesoftware.messenger.*,
                 java.util.Date,
                 java.text.DateFormat,
                 java.util.HashMap,
                 java.util.Map" %>

<!-- Define Administration Bean -->
<jsp:useBean id="admin" class="org.jivesoftware.util.WebManager"  />
<% admin.init(request, response, session, application, out ); %>

<!-- Define BreadCrumbs -->
<c:set var="title" value="Resource Conflict Policy"  />
<c:set var="breadcrumbs" value="${admin.breadCrumbs}"  />
<c:set target="${breadcrumbs}" property="Home" value="main.jsp" />
<c:set target="${breadcrumbs}" property="${title}" value="session-conflict.jsp" />
<%@ include file="top.jsp" %>

<%  // Get parameters
    boolean update = request.getParameter("update") != null;
    int kickPolicy = ParamUtils.getIntParameter(request,"kickPolicy",Integer.MAX_VALUE);
    int kickValue = ParamUtils.getIntParameter(request,"kickValue",-2);

  

    if (kickPolicy == -2) {
        kickPolicy = admin.getSessionManager().getConflictKickLimit();
    }

    // Update the session kick policy if requested
    Map errors = new HashMap();
    if (update) {
        // Validate params
        if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
            if (kickValue <= 1) {
                errors.put("kickValue","");
            }
        }
        // If no errors, continue:
        if (errors.size() == 0) {
            if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
                admin.getSessionManager().setConflictKickLimit(kickValue);
            }
            else {
                admin.getSessionManager().setConflictKickLimit(kickPolicy);
            }
            %>
              <p class="jive-success-text">
    Settings updated.
    </p>
            <%
          
          
        }
    }

    // Update variable values
    kickPolicy = admin.getSessionManager().getConflictKickLimit();
%>
<table cellpadding="3" cellspacing="1" border="0" width="600">
<tr class="tableHeader"><td colspan="2" align="left">Conflict Policy</td></tr>
<tr><td colspan="2" class="text">
<fmt:message key="title" bundle="${lang}" /> allows multiple logins to the same user account by assigning a unique "resource name"
to each connection. If a connection requests a resource name that is already in use, the server must
decide how to handle the conflict. The options on this page allow you to determine if the server
always kicks off existing connections, never kicks off existing connections, or sets the number of
login attempts that should be rejected before kicking off an
existing connection. The last option allows users to receive an error when logging in that
allows them to request a different resource name.

<%  if (ParamUtils.getBooleanParameter(request,"success")) { %>

    <p class="jive-success-text">
    Settings updated.
    </p>

<%  } %>

<form action="session-conflict.jsp">


<tr valign="top" class="">
    <td width="1%" nowrap>
        <input type="radio" name="kickPolicy" value="0" id="rb01"
         <%= ((kickPolicy==0) ? "checked" : "") %>>
    </td>
    <td width="99%">
        <label for="rb01"><b>Always kick</b></label> - If there is a resource conflict,
        immediately kick the other resource.
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        <input type="radio" name="kickPolicy" value="<%= SessionManager.NEVER_KICK %>" id="rb02"
         <%= ((kickPolicy==SessionManager.NEVER_KICK) ? "checked" : "") %>>
    </td>
    <td width="99%">
        <label for="rb02"><b>Never kick</b></label> - If there is a resource conflict, don't
        allow the new resource to log in.
    </td>
</tr>
<tr valign="top" class="">
    <td width="1%" nowrap>
        <input type="radio" name="kickPolicy" value="1" id="rb04"
         <%= ((kickPolicy==1) ? "checked" : "") %>>
    </td>
    <td width="99%">
        <label for="rb04"><b>Allow one login attempt</b></label> - If there is a resource conflict,
        report an error one time but don't kick the existing connection.
    </td>
</tr>
<%  // Figure out if the kick policy is neither 0 nor SessionManager.NEVER_KICK:
    boolean assignedKickPolicy = false;
    if (kickPolicy != 0 && kickPolicy != 1 && kickPolicy != SessionManager.NEVER_KICK) {
       assignedKickPolicy = true;
    }
%>
<tr valign="top">
    <td width="1%" nowrap>
        <input type="radio" name="kickPolicy" value="<%= Integer.MAX_VALUE %>" id="rb03"
         onfocus="this.form.kickValue.focus();"
         <%= ((assignedKickPolicy) ? "checked" : "") %>>
    </td>
    <td width="99%">
        <label for="rb03"><b>Assign kick value</b></label> - Specify the number of login
        attempts allowed before conflicting resources are kicked. You must specify a
        number greater than one.
    </td>
</tr>
<tr valign="top">
    <td width="1%" nowrap>
        &nbsp;
    </td>
    <td width="99%">
        <%  if (errors.get("kickValue") != null) { %>

            <span class="jive-error-text">
            Please enter a value greater than 1.
            </span><br>

        <%  } %>

        <input type="text" name="kickValue" value="<%= ((assignedKickPolicy) ? ""+kickPolicy : "") %>"
         size="5" maxlength="10"
         onclick="this.form.kickPolicy[3].checked=true;">
    </td>
</tr>
</table>

<br>

<input type="submit" name="update" value="Save Settings">

</form>

<%@ include file="bottom.jsp" %>
